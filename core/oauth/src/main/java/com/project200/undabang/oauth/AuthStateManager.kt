package com.project200.undabang.oauth

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.project200.undabang.oauth.config.CognitoConfig.AUTH_PREFS_NAME
import com.project200.undabang.oauth.config.CognitoConfig.AUTH_STATE_PREF_KEY
import dagger.hilt.android.qualifiers.ApplicationContext
import net.openid.appauth.AuthState
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.TokenResponse
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthStateManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var currentAuthState: AuthState = AuthState()
    private val prefs: SharedPreferences

    init {
        val masterKeyAlias = try {
            MasterKey.Builder(context, MasterKey.DEFAULT_MASTER_KEY_ALIAS)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
        } catch (e: Exception) {
            Timber.e(e, "MasterKey creation failed for AuthState.")
            throw RuntimeException("Failed to create MasterKey for AuthState", e) // 또는 다른 강력한 에러 처리
        }

        prefs = try {
            EncryptedSharedPreferences.create(
                context,
                AUTH_PREFS_NAME, // "auth"
                masterKeyAlias,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Timber.e(e, "AuthState 암호화 sp 생성 실패")
            throw RuntimeException("AuthState 암호화 sp 생성 실패", e)
        }

        restoreAuthState()
        Timber.tag(TAG).d("AuthStateManager 초기화. refresh 필요: ${currentAuthState.needsTokenRefresh}")
    }

    fun getCurrent(): AuthState {
        return currentAuthState
    }

    fun update(response: AuthorizationResponse?, ex: AuthorizationException?) {
        currentAuthState.update(response, ex)
        saveAuthState()
    }

    fun update(response: TokenResponse?, ex: AuthorizationException?) {
        currentAuthState.update(response, ex)
        saveAuthState()
    }

    private fun saveAuthState() {
        try {
            prefs.edit {
                putString(AUTH_STATE_PREF_KEY, currentAuthState.jsonSerializeString())
            }
            Timber.tag(TAG).i("AuthState saved (encrypted) to $AUTH_PREFS_NAME.")
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to serialize/save auth state to $AUTH_PREFS_NAME")
        }
    }

    private fun restoreAuthState() {
        try {
            val storedAuthStateString = prefs.getString(AUTH_STATE_PREF_KEY, null)
            if (storedAuthStateString != null) {
                currentAuthState = AuthState.jsonDeserialize(storedAuthStateString)
                Timber.tag(TAG).i("AuthState restored (decrypted) from $AUTH_PREFS_NAME.")
            } else {
                currentAuthState = AuthState()
                Timber.tag(TAG).i("No AuthState found in $AUTH_PREFS_NAME, initialized a new one.")
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to deserialize/restore auth state from $AUTH_PREFS_NAME")
            currentAuthState = AuthState()
        }
    }

    fun clearAuthState() {
        currentAuthState = AuthState()
        try {
            prefs.edit {
                remove(AUTH_STATE_PREF_KEY)
            }
            Timber.tag(TAG).i("AuthState cleared from $AUTH_PREFS_NAME.")
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to clear auth state from $AUTH_PREFS_NAME")
        }
    }

    companion object {
        private const val TAG = "AuthStateManager"
    }
}