package com.project200.undabang.fcm

import android.content.SharedPreferences
import androidx.core.content.edit
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.project200.common.constants.FcmConstants.KEY_FCM_TOKEN
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class FcmService : FirebaseMessagingService() {
    @Inject
    lateinit var sharedPreferences: SharedPreferences

    /**
     * 새로운 FCM 토큰이 발급되거나 갱신될 때 호출됩니다.
     * 이 토큰은 백엔드 서버로 전송되어 특정 기기에 알림을 보내는 데 사용됩니다.
     */
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Timber.tag(TAG).d("Refreshed FCM token: $token")

        saveFcmToken(token)
    }

    /**
     * 포그라운드 상태일 때 푸시 알림 메시지를 수신하면 호출됩니다.
     * 백그라운드 상태에서는 시스템 트레이에 자동으로 알림이 표시됩니다.
     */
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Timber.tag(TAG).d("Message received from: ${remoteMessage.from}")

        // 백엔드에서 보낸 알림 제목과 내용을 로그로 확인합니다.
        remoteMessage.notification?.let {
            Timber.tag(TAG).d("Notification Title: ${it.title}")
            Timber.tag(TAG).d("Notification Body: ${it.body}")
        }

        // 백엔드에서 보낸 데이터 페이로드(Key-Value)를 확인합니다.
        if (remoteMessage.data.isNotEmpty()) {
            Timber.tag(TAG).d("Data Payload: ${remoteMessage.data}")
        }
    }

    /**
     * FCM 토큰을 암호화된 SharedPreferences에 저장하는 메서드입니다.
     * 이 토큰은 백엔드 서버로 전송되어 특정 기기에 알림을 보내는 데 사용됩니다.
     */
    private fun saveFcmToken(token: String) {
        sharedPreferences.edit { putString(KEY_FCM_TOKEN, token) }
        Timber.tag(TAG).d("FCM token saved to encrypted preferences.")
    }

    companion object {
        private const val TAG = "FcmService"
    }
}
