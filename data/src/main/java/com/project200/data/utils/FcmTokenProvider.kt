package com.project200.data.utils

import android.content.SharedPreferences
import com.project200.common.constants.FcmConstants.KEY_FCM_TOKEN
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FcmTokenProvider
    @Inject
    constructor(
        private val prefs: SharedPreferences,
    ) {
        private var fcmToken: String? = null

        init {
            loadTokenFromPrefs()
        }

        private fun loadTokenFromPrefs() {
            fcmToken = prefs.getString(KEY_FCM_TOKEN, null)
            Timber.tag("FcmTokenProvider").d("FCM 토큰 : $fcmToken")
        }

        fun getFcmToken(): String? {
            if (fcmToken == null) {
                loadTokenFromPrefs()
            }
            return fcmToken
        }

        fun refreshToken() {
            loadTokenFromPrefs()
        }
    }
