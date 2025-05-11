package com.project200.undabang.oauth.config

import com.project200.undabang.core.oauth.BuildConfig


internal object CognitoConfig {
    const val REDIRECT_URI = "com.project200.undabang://callback"
    const val LOGOUT_REDIRECT_URI = "com.project200.undabang://logout"
    const val SCOPES = "email openid phone com.undabang.user/user"

    const val ISSUER_URI = "https://cognito-idp.${BuildConfig.COGNITO_REGION}.amazonaws.com/${BuildConfig.COGNITO_USER_POOL_ID}"

    const val AUTH_STATE_PREF_KEY = "authState"
    const val AUTH_PREFS_NAME = "auth"
}