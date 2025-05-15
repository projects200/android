package com.project200.undabang.oauth

import android.content.Context
import androidx.core.content.edit
import com.project200.undabang.oauth.config.CognitoConfig.AUTH_PREFS_NAME
import com.project200.undabang.oauth.config.CognitoConfig.AUTH_STATE_PREF_KEY
import dagger.hilt.android.qualifiers.ApplicationContext
import net.openid.appauth.AuthState
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.TokenResponse
import org.json.JSONException
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthStateManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var currentAuthState: AuthState = AuthState()

    init {
        restoreAuthState()
        Timber.tag(TAG).d("AuthStateManager initialized. Current state needs refresh: ${currentAuthState.needsTokenRefresh}")
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
            val prefs = context.getSharedPreferences(AUTH_PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit {
                putString(AUTH_STATE_PREF_KEY, currentAuthState.jsonSerializeString())
            }
            Timber.tag(TAG).i("AuthState saved.")
        } catch (e: JSONException) {
            Timber.tag(TAG).e(e, "Failed to serialize auth state for saving")
        }
    }

    private fun restoreAuthState() {
        try {
            val prefs = context.getSharedPreferences(AUTH_PREFS_NAME, Context.MODE_PRIVATE)
            val storedAuthStateString = prefs.getString(AUTH_STATE_PREF_KEY, null)
            if (storedAuthStateString != null) {
                currentAuthState = AuthState.jsonDeserialize(storedAuthStateString)
                Timber.tag(TAG).i("AuthState restored.")
            } else {
                currentAuthState = AuthState()
                Timber.tag(TAG).i("No AuthState found, initialized a new one.")
            }
        } catch (e: JSONException) {
            Timber.tag(TAG).e(e, "Failed to deserialize auth state")
            currentAuthState = AuthState()
        }
    }

    fun clearAuthState() {
        currentAuthState = AuthState()
        saveAuthState()
        Timber.tag(TAG).i("AuthState cleared.")
    }

    companion object {
        private const val TAG = "AuthStateManager"
    }
}