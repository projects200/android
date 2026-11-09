package com.project200.undabang.e2e

import android.app.Application
import android.net.Uri
import android.util.Base64
import androidx.room.Room
import com.google.common.truth.Truth.assertThat
import com.project200.data.api.ApiService
import com.project200.data.datasource.ExerciseImageFileStore
import com.project200.data.dto.BaseResponse
import com.project200.data.dto.GetIsRegisteredData
import com.project200.data.impl.AuthRepositoryImpl
import com.project200.data.impl.RoomSessionDataCleaner
import com.project200.data.local.PreferenceManager
import com.project200.data.local.UndabangDatabase
import com.project200.data.local.UndabangTypeConverters
import com.project200.domain.manager.ExerciseSyncScheduler
import com.project200.domain.manager.FcmTokenSyncScheduler
import com.project200.domain.manager.SessionDataCleaner
import com.project200.domain.manager.TimerSyncScheduler
import com.project200.domain.model.RegistrationStatus
import com.project200.domain.usecase.ClearSessionUseCase
import com.project200.domain.usecase.LoginSessionUseCase
import com.project200.undabang.oauth.AuthManager
import com.project200.undabang.oauth.AuthStateManager
import com.project200.undabang.oauth.TokenRefreshResult
import com.project200.undabang.oauth.config.CognitoConfig
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import net.openid.appauth.AuthState
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.AuthorizationServiceDiscovery
import net.openid.appauth.ResponseTypeValues
import net.openid.appauth.TokenResponse
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE, application = Application::class)
class MaestroSessionBootstrapStageTest {
    private val context = RuntimeEnvironment.getApplication()
    private val preferences = PreferenceManager(context)
    private val api = mockk<ApiService>()
    private val config = mockk<CognitoConfig>()
    private val stateManager = mockk<AuthStateManager>()
    private val manager = mockk<AuthManager>()
    private val cleaner = mockk<SessionDataCleaner>()
    private val fcm = mockk<FcmTokenSyncScheduler>(relaxed = true)
    private val timers = mockk<TimerSyncScheduler>(relaxed = true)
    private val exercises = mockk<ExerciseSyncScheduler>(relaxed = true)
    private lateinit var trusted: AuthorizationServiceConfiguration
    private lateinit var bootstrap: MaestroSessionBootstrap
    private lateinit var login: LoginSessionUseCase
    private lateinit var repository: AuthRepositoryImpl
    private var current = AuthState()
    private var target = "member-b"
    private lateinit var original: File

    @Before
    fun setUp() {
        preferences.completePendingLogin()
        preferences.saveMemberId("member-a", "subject-a")
        every { config.cognitoRegion } returns "test-region"
        every { config.cognitoUserPoolId } returns "test-pool"
        every { config.cognitoAppClientId } returns CLIENT
        every { config.issuerUri } returns ISSUER
        every { config.redirectUri } returns "com.project200.undabang://callback"
        trusted =
            AuthorizationServiceConfiguration(
                AuthorizationServiceDiscovery(
                    JSONObject()
                        .put("issuer", ISSUER)
                        .put("authorization_endpoint", "$ISSUER/authorize")
                        .put("token_endpoint", "$ISSUER/token")
                        .put("jwks_uri", "$ISSUER/keys")
                        .put("response_types_supported", JSONArray().put("code"))
                        .put("subject_types_supported", JSONArray().put("public"))
                        .put("id_token_signing_alg_values_supported", JSONArray().put("RS256")),
                ),
            )
        mockkStatic(AuthorizationServiceConfiguration::class)
        every { AuthorizationServiceConfiguration.fetchFromIssuer(any(), any()) } answers {
            secondArg<AuthorizationServiceConfiguration.RetrieveConfigurationCallback>()
                .onFetchConfigurationCompleted(trusted, null)
        }
        every { stateManager.getCurrent() } answers { current }
        every { stateManager.clearAuthState() } answers { current = AuthState() }
        every { stateManager.update(any<AuthorizationResponse>(), any()) } answers {
            // Credential installation must never open the old account's Worker gate.
            assertThat(preferences.getMemberId()).isNull()
            assertThat(preferences.getPendingLogin()?.retainedMemberId).isEqualTo("member-a")
            current.update(firstArg<AuthorizationResponse>(), null)
        }
        every { stateManager.update(any<TokenResponse>(), any()) } answers {
            assertThat(preferences.getMemberId()).isNull()
            assertThat(preferences.getPendingLogin()).isNotNull()
            current.update(firstArg<TokenResponse>(), null)
        }
        coEvery { manager.refreshAccessToken() } coAnswers {
            assertThat(preferences.getMemberId()).isNull()
            assertThat(preferences.getPendingLogin()).isNotNull()
            val refreshed = tokens(checkNotNull(current.lastAuthorizationResponse), "rotated-refresh")
            current.update(refreshed, null)
            TokenRefreshResult.Success(refreshed)
        }
        coEvery { api.getIsRegistered() } coAnswers {
            assertThat(preferences.getMemberId()).isNull()
            assertThat(preferences.getRetainedMemberId()).isEqualTo("member-a")
            BaseResponse(true, "OK", "", GetIsRegisteredData(target, true))
        }
        coEvery { cleaner.hasStoredData() } returns true
        val files = ExerciseImageFileStore(context)
        original = File(context.filesDir, "exercise-images/stage-test.png")
        check(original.parentFile!!.isDirectory || original.parentFile!!.mkdirs())
        original.writeBytes(byteArrayOf(1, 4, 9))
        val dispatcher = UnconfinedTestDispatcher()
        repository = AuthRepositoryImpl(api, preferences, stateManager, cleaner, dispatcher, files)
        login = LoginSessionUseCase(repository, ClearSessionUseCase(repository, fcm, timers, cleaner, exercises))
        val registration = MaestroRegistrationCheck(api, preferences, stateManager, cleaner, dispatcher, files)
        bootstrap = MaestroSessionBootstrap(config, stateManager, manager, repository, registration, login, preferences)
    }

    @After
    fun tearDown() {
        original.delete()
        preferences.completePendingLogin()
        preferences.clearMemberId()
        unmockkStatic(AuthorizationServiceConfiguration::class)
    }

    @Test
    fun `peer credentials remain pending before and after verified refresh without adoption`() =
        runTest {
            val imported =
                authorizedState().apply {
                    update(tokens(checkNotNull(lastAuthorizationResponse), "latest-imported-refresh"), null)
                }
            assertThat(bootstrap.stagePendingLogin(imported, "member-b", "member-a")).isEqualTo("member-b")
            assertThat(preferences.getMemberId()).isNull()
            assertThat(preferences.getPendingLogin()?.targetMemberId).isNull()
            assertThat(current.refreshToken).isEqualTo("rotated-refresh")
            coVerify(exactly = 1) { api.getIsRegistered() }
            assertPreserved()
        }

    @Test
    fun `product resume and recreation require fresh peer approval and cancellation preserves A`() =
        runTest {
            bootstrap.stagePendingLogin(authorizedState(), "member-b", "member-a")
            val first = login.resumePendingLogin() as RegistrationStatus.ConfirmationRequired
            // Recreate both revision holders and restore AppAuth from its persisted representation.
            current = AuthState.jsonDeserialize(current.jsonSerializeString())
            val reopened = PreferenceManager(context)
            val restoredRepository =
                AuthRepositoryImpl(
                    api,
                    reopened,
                    stateManager,
                    cleaner,
                    UnconfinedTestDispatcher(),
                    ExerciseImageFileStore(context),
                )
            val restoredLogin =
                LoginSessionUseCase(
                    restoredRepository,
                    ClearSessionUseCase(restoredRepository, fcm, timers, cleaner, exercises),
                )
            val second = restoredLogin.resumePendingLogin() as RegistrationStatus.ConfirmationRequired
            assertThat(second.confirmationId).isNotEqualTo(first.confirmationId)
            assertThat(restoredLogin.confirmAccountAdoption(first.confirmationId)).isEqualTo(RegistrationStatus.Indeterminate)
            assertThat(reopened.getPendingLogin()?.confirmationId).isEqualTo(second.confirmationId)
            assertThat(reopened.getMemberId()).isNull()
            assertPreserved()
            restoredLogin.cancelLogin()
            assertThat(preferences.getPendingLogin()).isNull()
            assertThat(current.isAuthorized).isFalse()
            assertThat(preferences.getRetainedMemberId()).isEqualTo("member-a")
            assertPreserved()
        }

    @Test
    fun `authentication failure leaves pending boundary and original owner files intact`() =
        runTest {
            coEvery { manager.refreshAccessToken() } returns TokenRefreshResult.NoRefreshToken
            val failure = runCatching { bootstrap.stagePendingLogin(authorizedState(), "member-b", "member-a") }.exceptionOrNull()
            assertThat((failure as MaestroSetupFailure).reason).isEqualTo("refresh_failed")
            assertThat(preferences.getPendingLogin()).isNotNull()
            assertThat(preferences.getMemberId()).isNull()
            coVerify(exactly = 0) { api.getIsRegistered() }
            assertPreserved()
        }

    @Test
    fun `wrong server identity never adopts target or clears the pending boundary`() =
        runTest {
            target = "unexpected-member"
            val failure = runCatching { bootstrap.stagePendingLogin(authorizedState(), "member-b", "member-a") }.exceptionOrNull()
            assertThat((failure as MaestroSetupFailure).reason).isEqualTo("member_mismatch")
            assertThat(preferences.getPendingLogin()).isNotNull()
            assertThat(preferences.getMemberId()).isNull()
            assertPreserved()
        }

    @Test
    fun `untrusted credentials do not replace existing session or begin pending login`() =
        runTest {
            val failure =
                runCatching {
                    bootstrap.stagePendingLogin(authorizedState("foreign-client"), "member-b", "member-a")
                }.exceptionOrNull()
            assertThat((failure as MaestroSetupFailure).reason).isEqualTo("untrusted_session")
            assertThat(preferences.getPendingLogin()).isNull()
            verify(exactly = 0) { stateManager.update(any<TokenResponse>(), any()) }
            coVerify(exactly = 0) { manager.refreshAccessToken() }
            assertPreserved()
        }

    @Test
    fun `same account is only reactivated by the real product resume path`() =
        runTest {
            target = "member-a"
            bootstrap.stagePendingLogin(authorizedState(), "member-a", "member-a")
            assertThat(preferences.getMemberId()).isNull()
            assertThat(login.resumePendingLogin()).isEqualTo(RegistrationStatus.Registered)
            assertThat(preferences.getMemberId()).isEqualTo("member-a")
            assertThat(preferences.getPendingLogin()).isNull()
            assertPreserved()
        }

    @Test
    fun `only explicit product approval deletes actual owned files and adopts B`() =
        runTest {
            val name = "maestro-session-stage-approval.db"
            context.deleteDatabase(name)
            val database =
                Room.databaseBuilder(context, UndabangDatabase::class.java, name)
                    .addTypeConverter(UndabangTypeConverters(Moshi.Builder().add(KotlinJsonAdapterFactory()).build()))
                    .allowMainThreadQueries()
                    .setQueryExecutor { it.run() }
                    .setTransactionExecutor { it.run() }
                    .build()
            try {
                val dispatcher = UnconfinedTestDispatcher()
                val files = ExerciseImageFileStore(context)
                val realCleaner = RoomSessionDataCleaner(database, dispatcher, files)
                val realRepository = AuthRepositoryImpl(api, preferences, stateManager, realCleaner, dispatcher, files)
                val realLogin =
                    LoginSessionUseCase(
                        realRepository,
                        ClearSessionUseCase(realRepository, fcm, timers, realCleaner, exercises),
                    )
                val registration = MaestroRegistrationCheck(api, preferences, stateManager, realCleaner, dispatcher, files)
                val staging = MaestroSessionBootstrap(config, stateManager, manager, realRepository, registration, realLogin, preferences)
                staging.stagePendingLogin(authorizedState(), "member-b", "member-a")
                assertThat(original.exists()).isTrue()
                val confirmation = realLogin.resumePendingLogin() as RegistrationStatus.ConfirmationRequired
                assertThat(preferences.getMemberId()).isNull()
                assertThat(original.readBytes()).isEqualTo(byteArrayOf(1, 4, 9))
                assertThat(realLogin.confirmAccountAdoption("stale-confirmation")).isEqualTo(RegistrationStatus.Indeterminate)
                assertThat(original.exists()).isTrue()
                assertThat(realLogin.confirmAccountAdoption(confirmation.confirmationId)).isEqualTo(RegistrationStatus.Registered)
                assertThat(original.exists()).isFalse()
                assertThat(preferences.getMemberId()).isEqualTo("member-b")
                assertThat(preferences.getPendingLogin()).isNull()
                assertThat(database.exerciseSyncDao().getByMember("member-a")).isEmpty()
                assertThat(database.exercisePointReceiptDao().getByMember("member-a")).isEmpty()
            } finally {
                database.close()
                context.deleteDatabase(name)
            }
        }

    private fun assertPreserved() {
        assertThat(preferences.getRetainedMemberId()).isEqualTo("member-a")
        assertThat(original.readBytes()).isEqualTo(byteArrayOf(1, 4, 9))
        coVerify(exactly = 0) { cleaner.clearAll() }
        coVerify(exactly = 0) { cleaner.clearForAccountAdoption(any(), any(), any()) }
    }

    private fun authorizedState(client: String = CLIENT): AuthState {
        val request = AuthorizationRequest.Builder(trusted, client, ResponseTypeValues.CODE, Uri.parse(config.redirectUri)).build()
        val response = AuthorizationResponse.Builder(request).setAuthorizationCode("synthetic-code").build()
        return AuthState().apply {
            update(response, null)
            update(tokens(response, "imported-refresh"), null)
        }
    }

    private fun tokens(
        response: AuthorizationResponse,
        refresh: String,
    ): TokenResponse {
        val payload = JSONObject().put("sub", "synthetic-subject").put("iss", ISSUER).put("aud", CLIENT)
        val token =
            "eyJhbGciOiJSUzI1NiJ9." +
                Base64.encodeToString(payload.toString().toByteArray(), Base64.NO_WRAP or Base64.NO_PADDING or Base64.URL_SAFE) +
                ".synthetic-signature"
        return TokenResponse.Builder(response.createTokenExchangeRequest())
            .setTokenType("Bearer")
            .setAccessToken("synthetic-access")
            .setIdToken(token)
            .setRefreshToken(refresh)
            .build()
    }

    private companion object {
        const val CLIENT = "synthetic-client"
        const val ISSUER = "https://auth.example.test/pool"
    }
}
