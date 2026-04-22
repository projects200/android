package com.project200.undabang.oauth.config

import com.project200.undabang.core.oauth.BuildConfig
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CognitoConfig
    @Inject
    constructor() {
        val redirectUri: String = "com.project200.undabang://callback"
        val logoutRedirectUri: String = "com.project200.undabang://logout"
        // DEBUG 에서도 Resource Server 커스텀 스코프를 요청해야 V1 access token 이 발급되어
        // API Gateway Cognito User Pool Authorizer 를 통과한다.
        val scopes: String = "com.undabang.user/user email openid phone profile"

        val cognitoRegion: String = BuildConfig.COGNITO_REGION
        val cognitoUserPoolId: String = BuildConfig.COGNITO_USER_POOL_ID
        val cognitoAppClientId: String = BuildConfig.COGNITO_APP_CLIENT_ID

        val issuerUri: String = "https://cognito-idp.$cognitoRegion.amazonaws.com/$cognitoUserPoolId"

        val authStatePrefKey: String = "authState"
        val authPrefsName: String = "auth"
    }
