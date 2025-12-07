package com.project200.undabang.fcm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.SharedPreferences
import androidx.core.app.NotificationCompat
import androidx.core.content.edit
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.project200.common.constants.FcmConstants.KEY_FCM_TOKEN
import com.project200.common.utils.ChatRoomStateRepository
import com.project200.common.utils.EncryptedPrefs
import com.project200.presentation.utils.DeepLinkManager
import com.project200.undabang.fcm.FcmConstant.CHAT_NOTI_CHANNEL_ID
import com.project200.undabang.fcm.FcmConstant.CHAT_NOTI_CHANNEL_NAME
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class FcmService : FirebaseMessagingService() {
    @Inject
    @EncryptedPrefs
    lateinit var sharedPreferences: SharedPreferences

    @Inject
    lateinit var chatRoomStateRepository: ChatRoomStateRepository

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
            sendNotification(remoteMessage.data)
        }
    }

    private fun sendNotification(data: Map<String, String>) {
        val chatRoomId = data["chatroomId"]
        val nickname = data["nickname"]
        val memberId = data["memberId"]
        val content = data["content"]

        if (chatRoomId == null || nickname == null || memberId == null || content == null) return

        // 현재 활성화된 채팅방과 동일한 채팅방에서 온 알림이면 무시
        if (chatRoomId.toLong() == chatRoomStateRepository.activeChatRoomId.value) return

        // 알림 식별을 위한 고유 ID
        val uniqueId = chatRoomId.hashCode()

        // 채팅방으로 이동할 딥링크 URI 생성
        val deepLinkUri = DeepLinkManager.createChatRoomUri(chatRoomId, nickname, memberId)

        // 클릭 시 이동할 Activity 설정
        val intent =
            Intent(Intent.ACTION_VIEW, deepLinkUri).apply {
                `package` = this@FcmService.packageName
            }

        // PendingIntent 생성
        val pendingIntent =
            PendingIntent.getActivity(
                this,
                uniqueId,
                intent,
                PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE,
            )

        // 알림 생성
        val notificationBuilder =
            NotificationCompat.Builder(this, CHAT_NOTI_CHANNEL_ID)
                .setSmallIcon(com.project200.undabang.presentation.R.drawable.app_icon) // 알림 아이콘
                .setContentTitle(data["nickname"]) // 알림 제목 (닉네임)
                .setContentText(data["content"]) // 알림 본문 (내용)
                .setAutoCancel(true) // 클릭 시 알림 자동 삭제
                .setPriority(NotificationCompat.PRIORITY_HIGH) // 헤드업 알림을 위한 우선순위 설정
                .setContentIntent(pendingIntent) // 클릭 시 이동할 인텐트 설정

        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        // 채널 생성 및 알림 발송
        notificationManager.apply {
            createNotificationChannel(
                NotificationChannel(
                    CHAT_NOTI_CHANNEL_ID,
                    CHAT_NOTI_CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH, // 헤드업 알림을 위한 중요도 설정
                ),
            )
            notify(uniqueId, notificationBuilder.build())
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
