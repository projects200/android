package com.project200.data.impl

import com.project200.common.utils.NetworkMonitor
import com.project200.data.api.ChatApiService
import com.project200.data.dto.SocketChatMessage
import com.project200.data.dto.SocketChatMessageDTO
import com.project200.data.dto.SocketChatRequest
import com.project200.data.mapper.toModel
import com.project200.domain.model.ChattingMessage
import com.project200.domain.model.SocketType
import com.project200.domain.repository.ChatSocketRepository
import com.project200.undabang.data.BuildConfig
import com.squareup.moshi.Moshi // Moshi import
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import okhttp3.*
import timber.log.Timber
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.pow

@Singleton
class ChatSocketRepositoryImpl @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val chatApi: ChatApiService,
    private val moshi: Moshi,
    private val networkMonitor: NetworkMonitor
) : ChatSocketRepository {
    private val requestAdapter = moshi.adapter(SocketChatRequest::class.java)
    private val responseAdapter = moshi.adapter(SocketChatMessage::class.java)

    private var webSocket: WebSocket? = null
    private val _incomingMessages = MutableSharedFlow<ChattingMessage>()
    override val incomingMessages = _incomingMessages.asSharedFlow()

    private var currentChatRoomId: Long = -1L
    private var isUserInChatRoom = false
    private var retryCount = 0
    private var heartbeatJob: Job? = null

    // 네트워크 복구 감지
    init {
        CoroutineScope(Dispatchers.IO).launch {
            networkMonitor.networkState.collect { isConnected ->
                if (isConnected && isUserInChatRoom) {
                    retryCount = 0
                    connectSocketInternal(currentChatRoomId)
                }
            }
        }
    }

    override fun connect(chatRoomId: Long) {
        currentChatRoomId = chatRoomId
        isUserInChatRoom = true
        connectSocketInternal(chatRoomId)
    }

    override fun disconnect() {
        isUserInChatRoom = false
        stopHeartbeat()
        webSocket?.close(1000, "User Exit")
        webSocket = null
    }

    override fun sendMessage(content: String) {
        val payload = SocketChatRequest(SocketType.TALK, content)
        val json = requestAdapter.toJson(payload)
        webSocket?.send(json)
        Timber.d("Sent message: $json")
    }

    private fun connectSocketInternal(chatRoomId: Long) {
        if (webSocket != null) return

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 티켓 발급
                val response = chatApi.getChatTicket(chatRoomId)
                val ticket = response.data?.chatTicket
                    ?: throw Exception("Ticket issuance failed")

                // 소켓 연결
                val wsUrl = if(BuildConfig.DEBUG) "wss://dev-chat.undabang.store/ws/chat?chatTicket=$ticket"
                else "wss://chat.undabang.store/ws/chat?chatTicket=$ticket"

                val request = Request.Builder().url(wsUrl).build()
                webSocket = okHttpClient.newWebSocket(request, socketListener)

            } catch (e: Exception) {
                handleConnectionFailure(e)
            }
        }
    }

    private val socketListener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            retryCount = 0
            startApplicationHeartbeat()
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            try {
                val wrapper = responseAdapter.fromJson(text) ?: return
                if (wrapper.type == SocketType.TALK && wrapper.data != null) {
                    CoroutineScope(Dispatchers.IO).launch {
                        Timber.d("Received TALK message: $text")
                        _incomingMessages.emit(wrapper.data.toModel())
                    }
                } else if (wrapper.type == SocketType.PONG) {
                    Timber.d("Received PONG from server")
                }
            } catch (e: Exception) {
                Timber.e(e, "Socket Message Parsing Error. Text: $text")
            }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            cleanupAndRetry(t)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
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
        if (!isUserInChatRoom) return
        if (!networkMonitor.isCurrentlyConnected()) return

        CoroutineScope(Dispatchers.IO).launch {
            val delayMs = (2.0.pow(retryCount) * 1000).toLong().coerceAtMost(10000L)
            delay(delayMs)
            retryCount++
            connectSocketInternal(currentChatRoomId)
        }
    }

    private fun startApplicationHeartbeat() {
        stopHeartbeat()
        heartbeatJob = CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                delay(30000) // 30초마다 PING
                val pingPayload = SocketChatRequest(
                    SocketType.PING,
                    content = null
                )
                webSocket?.send(requestAdapter.toJson(pingPayload))
            }
        }
    }

    private fun stopHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = null
    }
}