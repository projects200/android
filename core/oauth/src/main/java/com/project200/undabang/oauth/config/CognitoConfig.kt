package com.project200.undabang.oauth.config

import com.project200.undabang.core.oauth.BuildConfig
import javax.inject.Inject
import javax.inject.Singleton


@Singleton
class CognitoConfig @Inject constructor() {
    val redirectUri: String = "com.project200.undabang://callback"
    val logoutRedirectUri: String = "com.project200.undabang://logout"
    val scopes: String = "com.undabang.user/user email openid phone"

    val cognitoRegion: String = BuildConfig.COGNITO_REGION
    val cognitoUserPoolId: String = BuildConfig.COGNITO_USER_POOL_ID
    val cognitoAppClientId: String = BuildConfig.COGNITO_APP_CLIENT_ID

    val issuerUri: String = "https://cognito-idp.$cognitoRegion.amazonaws.com/$cognitoUserPoolId"

    val authStatePrefKey: String = "authState"
    val authPrefsName: String = "auth"
}
