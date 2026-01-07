package com.project200.data.impl

import com.project200.common.utils.NetworkMonitor
import com.project200.data.api.ChatApiService
import com.project200.data.dto.SocketChatMessage
import com.project200.data.dto.SocketChatRequest
import com.project200.data.local.PreferenceManager
import com.project200.data.mapper.toModel
import com.project200.domain.model.ChattingMessage
import com.project200.domain.model.SocketType
import com.project200.domain.repository.ChatSocketRepository
import com.project200.undabang.data.BuildConfig
import com.squareup.moshi.Moshi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import timber.log.Timber
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.pow

@Singleton
class ChatSocketRepositoryImpl
    @Inject
    constructor(
        private val okHttpClient: OkHttpClient,
        private val chatApi: ChatApiService,
        private val moshi: Moshi,
        private val networkMonitor: NetworkMonitor,
        private val spManager: PreferenceManager,
    ) : ChatSocketRepository {
        private val requestAdapter = moshi.adapter(SocketChatRequest::class.java)
        private val responseAdapter = moshi.adapter(SocketChatMessage::class.java)

        private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val connectionMutex = Mutex()

        private var webSocket: WebSocket? = null

        private val _incomingMessages = MutableSharedFlow<ChattingMessage>()
        override val incomingMessages = _incomingMessages.asSharedFlow()

        private val memberId = spManager.getMemberId().toString()
        private var currentChatRoomId: Long = -1L
        private var isUserInChatRoom = false
        private var retryCount = AtomicInteger(0)

        private var heartbeatJob: Job? = null
        private var retryJob: Job? = null

        // 네트워크 복구 감지
        init {
            CoroutineScope(Dispatchers.IO).launch {
                networkMonitor.networkState.collect { isConnected ->
                    if (isActive && isConnected && isUserInChatRoom) {
                        retryCount.set(0)
                        connectSocketInternal(currentChatRoomId)
                    }
                }
            }
        }

        /**
         * 채팅방에 소켓 연결
         */
        override fun connect(chatRoomId: Long) {
            currentChatRoomId = chatRoomId
            isUserInChatRoom = true
            connectSocketInternal(chatRoomId)
        }

        /**
         * 채팅방 소켓 연결 해제
         */
        override fun disconnect() {
            isUserInChatRoom = false
            stopHeartbeat()
            retryJob?.cancel()
            retryJob = null
            webSocket?.close(1000, "User Exit")
            webSocket = null
        }

        /**
         * 메시지 전송
         */
        override fun sendMessage(content: String) {
            val payload = SocketChatRequest(SocketType.TALK, content)
            val json = requestAdapter.toJson(payload)
            webSocket?.send(json)
            Timber.d("Sent message: $json")
        }

        /**
         * 소켓 연결 처리
         */
        private fun connectSocketInternal(chatRoomId: Long) {
            repositoryScope.launch {
                connectionMutex.withLock {
                    if (webSocket != null) { // 이미 연결된 경우
                        Timber.d("WebSocket already connected.")
                        return@withLock
                    }
                    try {
                        // 티켓 발급
                        val response = chatApi.getChatTicket(chatRoomId)
                        val ticket =
                            response.data?.chatTicket
                                ?: throw Exception("Ticket issuance failed")

                        // 소켓 연결
                        val wsUrl =
                            if (BuildConfig.DEBUG) {
                                "$BASE_URL_DEBUG$ticket"
                            } else {
                                "$BASE_URL_RELEASE$ticket"
                            }

                        val request = Request.Builder().url(wsUrl).build()
                        webSocket = okHttpClient.newWebSocket(request, socketListener)
                    } catch (e: Exception) {
                        handleConnectionFailure(e)
                    }
                }
            }
        }

        /**
         * 소켓 리스너
         */
        private val socketListener =
            object : WebSocketListener() {
                override fun onOpen(
                    webSocket: WebSocket,
                    response: Response,
                ) {
                    retryCount.set(0)
                    startApplicationHeartbeat()
                }

                // 메시지 수신
                override fun onMessage(
                    webSocket: WebSocket,
                    text: String,
                ) {
                    repositoryScope.launch {
                        try {
                            val wrapper = responseAdapter.fromJson(text) ?: return@launch
                            // TALK 타입 메시지 처리
                            // PING/PONG은 별도 처리 없음
                            if (wrapper.type == SocketType.TALK && wrapper.data != null) {
                                val message = wrapper.data.toModel().copy(isMine = wrapper.data.senderId == memberId)
                                Timber.d("Received TALK message: $text \n $memberId")
                                _incomingMessages.emit(message)
                            } else if (wrapper.type == SocketType.PONG) {
                                Timber.d("Received PONG from server")
                            }
                        } catch (e: Exception) {
                            Timber.e(e, "Socket Message Parsing Error. Text: $text")
                        }
                    }
                }

                override fun onFailure(
                    webSocket: WebSocket,
                    t: Throwable,
                    response: Response?,
                ) {
                    cleanupAndRetry(t)
                }

                override fun onClosed(
                    webSocket: WebSocket,
                    code: Int,
                    reason: String,
                ) {
                    if (code != 1000) cleanupAndRetry(Exception("Closed: $reason"))
                }
            }

        private fun cleanupAndRetry(t: Throwable) {
            stopHeartbeat()
            webSocket = null
            handleConnectionFailure(t)
        }

        // 지수 백오프 재연결
        private fun handleConnectionFailure(t: Throwable) {
            if (!isUserInChatRoom || !networkMonitor.isCurrentlyConnected()) return

            // 이전 재시도 작업 취소 후 재연결 시도
            retryJob?.cancel()
            retryJob =
                repositoryScope.launch {
                    val currentRetry = retryCount.getAndIncrement()
                    val delayMs = (2.0.pow(currentRetry) * 1000).toLong().coerceAtMost(MAX_RETRY_DELAY_MS)

                    delay(delayMs)
                    // 지연 시간 이후에도 여전히 방에 있는지 확인
                    if (isActive && isUserInChatRoom) {
                        connectSocketInternal(currentChatRoomId)
                    }
                }
        }

        // 애플리케이션 하트비트 시작
        private fun startApplicationHeartbeat() {
            stopHeartbeat()
            heartbeatJob =
                CoroutineScope(Dispatchers.IO).launch {
                    while (isActive) {
                        delay(PING_INTERVAL_MS) // 30초마다 PING
                        val pingPayload =
                            SocketChatRequest(
                                SocketType.PING,
                                content = null,
                            )
                        webSocket?.send(requestAdapter.toJson(pingPayload))
                    }
                }
        }

        private fun stopHeartbeat() {
            heartbeatJob?.cancel()
            heartbeatJob = null
        }

        companion object {
            private const val PING_INTERVAL_MS = 30_000L
            private const val MAX_RETRY_DELAY_MS = 10_000L
            private const val BASE_URL_DEBUG = "wss://dev-chat.undabang.store/ws/chat?chatTicket="
            private const val BASE_URL_RELEASE = "wss://chat.undabang.store/ws/chat?chatTicket="
        }
    }
