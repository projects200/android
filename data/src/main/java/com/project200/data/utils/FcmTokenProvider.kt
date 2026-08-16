package com.project200.data.utils

import android.content.SharedPreferences
import com.project200.common.constants.FcmConstants.KEY_FCM_TOKEN
import com.project200.common.utils.EncryptedPrefs
import javax.inject.Inject
import javax.inject.Singleton

/**
 * FCM 토큰을 읽는 단일 창구입니다
 * 토큰은 FcmService.onNewToken()이 저장소에 직접 씁니다
 * 메모리에 캐시하면 갱신 이후에도 옛 토큰이 헤더에 실리므로 호출할 때마다 저장소를 읽습니다
 */
@Singleton
class FcmTokenProvider
    @Inject
    constructor(
        @EncryptedPrefs private val prefs: SharedPreferences,
    ) {
        fun getFcmToken(): String? = prefs.getString(KEY_FCM_TOKEN, null)
    }
