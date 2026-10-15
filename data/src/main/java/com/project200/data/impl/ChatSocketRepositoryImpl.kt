package com.project200.data.impl

import com.project200.common.utils.NetworkMonitor
import com.project200.data.api.ChatApiService
import com.project200.data.dto.SocketChatMessage
import com.project200.data.dto.SocketChatRequest
import com.project200.data.local.PreferenceManager
import com.project200.data.mapper.toModel
import com.project200.domain.model.ChatSocketConnection
import com.project200.domain.model.ChatSocketStatus
import com.project200.domain.model.ChattingMessage
import com.project200.domain.model.OpponentStatus
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
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
        private val socketLock = Any()
        private var connectionGeneration = 0L
        private var closingSocket: WebSocket? = null
        private val _connection = MutableStateFlow(ChatSocketConnection())
        override val connection = _connection.asStateFlow()

        @Volatile
        private var webSocket: WebSocket? = null

        private val _incomingMessages = MutableSharedFlow<ChattingMessage>()
        override val incomingMessages = _incomingMessages.asSharedFlow()

        private val _socketErrors = MutableSharedFlow<String>()
        override val socketErrors = _socketErrors.asSharedFlow()

        private val _opponentStatusChanges = MutableSharedFlow<OpponentStatus>()
        override val opponentStatusChanges = _opponentStatusChanges.asSharedFlow()

        private val memberId: String? = spManager.getMemberId()
        @Volatile
        private var currentChatRoomId: Long = -1L
        @Volatile
        private var isUserInChatRoom = false
        private var retryCount = AtomicInteger(0)

        private var heartbeatJob: Job? = null
        private var retryJob: Job? = null

        // [MEASURE] 측정용 변수
        private val connectionAttemptCount = AtomicInteger(0)
        private val connectionSuccessCount = AtomicInteger(0)
        private val connectionFailCount = AtomicInteger(0)
        private val reconnectCount = AtomicInteger(0)
        private val reconnectSuccessCount = AtomicInteger(0)
        private var lastDisconnectTime: Long = 0L
        private var totalDowntimeMs: Long = 0L

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
            synchronized(socketLock) {
                currentChatRoomId = chatRoomId
                isUserInChatRoom = true
            }
            connectSocketInternal(chatRoomId)
        }

        /**
         * 채팅방 소켓 연결 해제
         */
        override fun disconnect() {
            // [MEASURE] 세션 통계 출력
            Timber.w("[MEASURE] === 세션 통계 ===")
            Timber.w("[MEASURE] 연결 시도: ${connectionAttemptCount.get()}")
            Timber.w("[MEASURE] 연결 성공: ${connectionSuccessCount.get()}")
            Timber.w("[MEASURE] 연결 실패: ${connectionFailCount.get()}")
            Timber.w("[MEASURE] 연결 끊김(재연결 시도): ${reconnectCount.get()}")
            Timber.w("[MEASURE] 재연결 성공: ${reconnectSuccessCount.get()}")
            Timber.w("[MEASURE] 총 다운타임: ${totalDowntimeMs}ms")
            if (connectionAttemptCount.get() > 0) {
                Timber.w("[MEASURE] 연결 성공률: ${connectionSuccessCount.get() * 100 / connectionAttemptCount.get()}%")
            }
            if (reconnectCount.get() > 0) {
                Timber.w("[MEASURE] 재연결 성공률: ${reconnectSuccessCount.get() * 100 / reconnectCount.get()}%")
                Timber.w(
                    "[MEASURE] 평균 복구 시간: ${if (reconnectSuccessCount.get() > 0) totalDowntimeMs / reconnectSuccessCount.get() else 0}ms",
                )
            }
            Timber.w("[MEASURE] ================")

            synchronized(socketLock) {
                isUserInChatRoom = false
                connectionGeneration++
                stopHeartbeat()
                retryJob?.cancel()
                retryJob = null
                val socket = webSocket ?: closingSocket
                webSocket = null
                closingSocket = socket
                // close 요청이 아니라 실제 onClosed/onFailure 콜백으로 종료를 확인합니다.
                _connection.value =
                    ChatSocketConnection(
                        currentChatRoomId,
                        if (socket == null) ChatSocketStatus.Disconnected else ChatSocketStatus.Closing,
                    )
                if (socket != null && !socket.close(1000, "User Exit")) socket.cancel()
            }
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
                    val generation =
                        synchronized(socketLock) {
                            if (!isUserInChatRoom || currentChatRoomId != chatRoomId || webSocket != null) {
                                return@launch
                            }
                            _connection.value = ChatSocketConnection(chatRoomId, ChatSocketStatus.Connecting)
                            connectionGeneration
                        }
                    connectionAttemptCount.incrementAndGet()
                    try {
                        val response = chatApi.getChatTicket(chatRoomId)
                        val ticket = response.data?.chatTicket ?: throw Exception("Ticket issuance failed")
                        val wsUrl = if (BuildConfig.DEBUG) "$BASE_URL_DEBUG$ticket" else "$BASE_URL_RELEASE$ticket"
                        val request = Request.Builder().url(wsUrl).build()
                        synchronized(socketLock) {
                            // 티켓 조회 중 방을 나갔으면 늦은 응답으로 소켓을 다시 열지 않습니다.
                            if (!isUserInChatRoom || generation != connectionGeneration || currentChatRoomId != chatRoomId) {
                                return@launch
                            }
                            webSocket = okHttpClient.newWebSocket(request, socketListener(chatRoomId, generation))
                        }
                    } catch (e: Exception) {
                        synchronized(socketLock) {
                            if (generation != connectionGeneration || !isUserInChatRoom) return@launch
                            connectionFailCount.incrementAndGet()
                            _connection.value = ChatSocketConnection(chatRoomId, ChatSocketStatus.Failed)
                            handleConnectionFailure(e)
                        }
                    }
                }
            }
        }

        /**
         * 소켓 리스너
         */
        private fun socketListener(
            roomId: Long,
            generation: Long,
        ): WebSocketListener =
            object : WebSocketListener() {
                override fun onOpen(
                    webSocket: WebSocket,
                    response: Response,
                ) {
                    synchronized(socketLock) {
                        if (
                            generation != connectionGeneration || !isUserInChatRoom ||
                            this@ChatSocketRepositoryImpl.webSocket !== webSocket
                        ) {
                            webSocket.cancel()
                            return
                        }
                        _connection.value = ChatSocketConnection(roomId, ChatSocketStatus.Connected)
                        connectionSuccessCount.incrementAndGet()
                        if (lastDisconnectTime > 0L) {
                            val downtimeMs = System.currentTimeMillis() - lastDisconnectTime
                            totalDowntimeMs += downtimeMs
                            reconnectSuccessCount.incrementAndGet()
                            Timber.w(
                                "[MEASURE] 재연결 성공! 복구 소요시간: ${downtimeMs}ms, " +
                                    "재연결 성공: ${reconnectSuccessCount.get()}/${reconnectCount.get()}",
                            )
                            lastDisconnectTime = 0L
                        }
                        Timber.w("[MEASURE] 연결 성공 (${connectionSuccessCount.get()}/${connectionAttemptCount.get()})")
                        retryCount.set(0)
                        startApplicationHeartbeat()
                    }
                }

                // 메시지 수신
                override fun onMessage(
                    webSocket: WebSocket,
                    text: String,
                ) {
                    if (!isCurrentSocket(webSocket, generation)) return
                    repositoryScope.launch {
                        try {
                            if (!isCurrentSocket(webSocket, generation)) return@launch
                            val wrapper = responseAdapter.fromJson(text) ?: return@launch
                            when (wrapper.type) {
                                SocketType.TALK -> {
                                    val chatDto = wrapper.getChatData(moshi)

                                    if (chatDto != null) {
                                        val message =
                                            chatDto.toModel().copy(
                                                isMine = chatDto.senderId == memberId,
                                            )
                                        Timber.d("Received TALK message: $text")
                                        _incomingMessages.emit(message)
                                    }
                                }

                                SocketType.PONG -> {
                                    Timber.d("Received PONG from server")
                                }

                                SocketType.ERROR -> {
                                    val errorMessage = wrapper.getErrorDetail() ?: "오류가 발생했습니다."
                                    Timber.e("Socket Error: ${wrapper.message} - $errorMessage")

                                    _socketErrors.emit(errorMessage)
                                }

                                SocketType.SYSTEM_LEAVE -> {
                                    val message = wrapper.message ?: "상대방이 채팅방을 나갔습니다."
                                    Timber.d("Opponent left: $message")
                                    _socketErrors.emit(message)
                                    _opponentStatusChanges.emit(OpponentStatus.Left)
                                }

                                SocketType.SYSTEM_BANNED -> {
                                    val message = wrapper.message ?: "차단된 회원과는 대화할 수 없습니다."
                                    Timber.d("Opponent blocked: $message")
                                    _socketErrors.emit(message)
                                    _opponentStatusChanges.emit(OpponentStatus.Blocked)
                                }

                                else -> {
                                    Timber.d("Received Other type (${wrapper.type}): $text")
                                }
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
                    finishSocket(webSocket, generation, t)
                }

                override fun onClosed(
                    webSocket: WebSocket,
                    code: Int,
                    reason: String,
                ) {
                    finishSocket(webSocket, generation, if (code == 1000) null else Exception("Socket closed"))
                }
            }

        private fun isCurrentSocket(
            socket: WebSocket,
            generation: Long,
        ): Boolean =
            synchronized(socketLock) {
                generation == connectionGeneration && isUserInChatRoom && webSocket === socket
            }

        private fun finishSocket(
            socket: WebSocket,
            generation: Long,
            failure: Throwable?,
        ) {
            synchronized(socketLock) {
                if (closingSocket === socket) {
                    closingSocket = null
                    if (!isUserInChatRoom && _connection.value.status == ChatSocketStatus.Closing) {
                        _connection.value = ChatSocketConnection(currentChatRoomId, ChatSocketStatus.Disconnected)
                    }
                    return
                }
                if (generation != connectionGeneration || webSocket !== socket) return
                stopHeartbeat()
                webSocket = null
                _connection.value =
                    ChatSocketConnection(
                        currentChatRoomId,
                        if (failure == null) ChatSocketStatus.Disconnected else ChatSocketStatus.Failed,
                    )
                if (failure != null) {
                    reconnectCount.incrementAndGet()
                    lastDisconnectTime = System.currentTimeMillis()
                    handleConnectionFailure(failure)
                }
            }
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
            private const val BASE_URL_DEBUG = "wss://dev-chat.undabang.site/ws/chat?chatTicket="
            private const val BASE_URL_RELEASE = "wss://chat.undabang.site/ws/chat?chatTicket="
        }
    }
