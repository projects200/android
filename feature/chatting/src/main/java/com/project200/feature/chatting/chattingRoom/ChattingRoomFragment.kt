package com.project200.feature.chatting.chattingRoom

import android.graphics.Rect
import android.os.Bundle
import android.view.ContextThemeWrapper
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat.getDrawable
import androidx.core.net.toUri
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.analytics.FirebaseAnalytics
import com.project200.common.utils.ChatRoomStateRepository
import com.project200.common.utils.CommonDateTimeFormatters.YYYY_MM_DD_KR
import com.project200.feature.chatting.chattingRoom.adapter.ChatRVAdapter
import com.project200.presentation.base.BindingFragment
import com.project200.presentation.utils.KeyboardControlInterface
import com.project200.presentation.utils.KeyboardUtils.hideKeyboard
import com.project200.presentation.utils.MenuStyler
import com.project200.presentation.utils.UiUtils.dpToPx
import com.project200.undabang.feature.chatting.R
import com.project200.undabang.feature.chatting.databinding.FragmentChattingRoomBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@AndroidEntryPoint
class ChattingRoomFragment : BindingFragment<FragmentChattingRoomBinding>(R.layout.fragment_chatting_room), KeyboardControlInterface {
    private val viewModel: ChattingRoomViewModel by viewModels()
    private lateinit var chatAdapter: ChatRVAdapter
    private val args: ChattingRoomFragmentArgs by navArgs()

    @Inject
    lateinit var firebaseAnalytics: FirebaseAnalytics

    @Inject
    lateinit var chatRoomStateRepository: ChatRoomStateRepository

    // 이전 메시지 로드 상태를 추적하는 플래그
    private var isPaging = false

    // 스크롤 위치 복원을 위해 저장할 변수
    private var firstVisibleItemPositionBeforeLoad = 0
    private var firstVisibleItemOffsetBeforeLoad = 0

    private lateinit var gestureDetector: GestureDetector

    private var lastDisplayedDate: LocalDate? = null

    override fun getViewBinding(view: View): FragmentChattingRoomBinding {
        return FragmentChattingRoomBinding.bind(view)
    }

    override fun setupViews() {
        setupRecyclerView()
        setupListeners()
        viewModel.setId(args.roomId, args.memberId)
        updateSendButtonState(false)
    }

    private fun setupListeners() {
        binding.baseToolbar.apply {
            setTitle(args.nickname)
            showBackButton(true) { findNavController().navigateUp() }
        }

        binding.menuBtn.setOnClickListener { showPopupMenu(binding.menuBtn) }

        binding.chattingMessageEt.addTextChangedListener { s ->
            updateSendButtonState(s.toString().isNotBlank())
        }

        binding.sendBtn.setOnClickListener {
            val messageText = binding.chattingMessageEt.text.toString()
            if (messageText.isNotBlank()) {
                // Firebase Analytics 이벤트 로깅
                val bundle = Bundle().apply {
                    putLong("timestamp", System.currentTimeMillis())
                }
                firebaseAnalytics.logEvent("chat_send_message", bundle)

                viewModel.sendMessage(messageText)
                binding.chattingMessageEt.text.clear() // EditText 초기화
            }
        }

        gestureDetector =
            GestureDetector(
                requireContext(),
                object : GestureDetector.SimpleOnGestureListener() {
                    override fun onSingleTapUp(e: MotionEvent): Boolean {
                        // 탭 동작이 감지되면 키보드 숨김
                        requireActivity().hideKeyboard(binding.chattingMessageEt)
                        return true
                    }
                },
            )

        binding.chattingMessageRv.addOnItemTouchListener(
            object : RecyclerView.OnItemTouchListener {
                override fun onInterceptTouchEvent(
                    rv: RecyclerView,
                    e: MotionEvent,
                ): Boolean {
                    gestureDetector.onTouchEvent(e)
                    return false
                }

                override fun onTouchEvent(
                    rv: RecyclerView,
                    e: MotionEvent,
                ) { }

                override fun onRequestDisallowInterceptTouchEvent(disallowIntercept: Boolean) { }
            },
        )
    }

    private fun setupRecyclerView() {
        chatAdapter =
            ChatRVAdapter(onProfileClicked = {
                findNavController().navigate(
                    "app://matching/map/${args.memberId}/${true}".toUri(),
                )
            })
        val layoutManager =
            LinearLayoutManager(requireContext()).apply {
                stackFromEnd = true // 기본적으로 하단 정렬
            }

        binding.chattingMessageRv.apply {
            adapter = chatAdapter
            this.layoutManager = layoutManager
            itemAnimator = null

            addOnScrollListener(
                object : RecyclerView.OnScrollListener() {
                    override fun onScrollStateChanged(
                        recyclerView: RecyclerView,
                        newState: Int,
                    ) {
                        super.onScrollStateChanged(recyclerView, newState)
                        // 스크롤이 멈췄고, 최상단에 도달했을 때
                        if (newState == RecyclerView.SCROLL_STATE_IDLE && !recyclerView.canScrollVertically(-1)) {
                            // 더 불러올 메시지가 있을 때
                            if (viewModel.hasNextMessages) {
                                saveScrollState(layoutManager)
                                viewModel.loadPreviousMessages()
                            }
                        }
                    }

                    override fun onScrolled(
                        recyclerView: RecyclerView,
                        dx: Int,
                        dy: Int,
                    ) {
                        super.onScrolled(recyclerView, dx, dy)

                        // 현재 화면 상단에 보이는 아이템의 위치를 찾습니다.
                        val firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition()

                        // 유효한 위치인지 확인
                        if (firstVisibleItemPosition == RecyclerView.NO_POSITION) return

                        // 해당 위치의 메시지 데이터를 가져옵니다.
                        val message = chatAdapter.currentList.getOrNull(firstVisibleItemPosition) ?: return

                        val currentDate = message.sentAt.toLocalDate()

                        // 마지막으로 표시된 날짜와 현재 날짜가 다를 경우에만 스낵바를 표시합니다.
                        if (currentDate != lastDisplayedDate) {
                            showCustomSnackBar(currentDate.format(YYYY_MM_DD_KR))
                            lastDisplayedDate = currentDate // 마지막 표시 날짜를 업데이트
                        }
                    }
                },
            )
        }
    }

    override fun setupObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    // Fragment가 STARTED 상태가 되면 폴링 시작
                    // STOPPED 상태가 되면 자동으로 코루틴 취소
                    while (isActive && viewModel.chatState.value != ChatInputState.OpponentBlocked) {
                        viewModel.getNewMessages()
                        delay(POLLING_PERIOD)
                    }
                }

                launch {
                    viewModel.chatState.collect { state ->
                        val isEnabled: Boolean
                        val messageResId: Int?

                        when (state) {
                            is ChatInputState.Active -> {
                                isEnabled = true
                                messageResId = R.string.chatting_message_hint
                            }
                            is ChatInputState.OpponentBlocked -> {
                                isEnabled = false
                                messageResId = R.string.chatting_opponent_blocked
                            }
                            is ChatInputState.OpponentLeft -> {
                                isEnabled = false
                                messageResId = R.string.chatting_opponent_exit
                            }
                        }

                        // 결정된 상태에 따라 UI를 업데이트
                        binding.chattingMessageEt.isEnabled = isEnabled
                        updateSendButtonState(isEnabled && binding.chattingMessageEt.text.isNotBlank())
                        binding.chattingMessageEt.hint = getString(messageResId)
                        if (!isEnabled) {
                            binding.chattingMessageEt.clearFocus()
                            binding.chattingMessageEt.text.clear()
                        }
                    }
                }

                launch {
                    viewModel.toast.collect { message ->
                        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
                    }
                }

                launch {
                    viewModel.exitResult.collect {
                        findNavController().navigateUp()
                    }
                }

                viewModel.messages.collect { messageList ->
                    // 리스트가 업데이트 되기 전, 현재 스크롤 상태를 확인
                    val isInitialLoad = chatAdapter.currentList.isEmpty() && messageList.isNotEmpty()
                    val oldListSize = chatAdapter.currentList.size

                    chatAdapter.submitList(messageList) {
                        when {
                            isPaging -> restoreScrollStateAfterPaging(messageList.size, oldListSize)
                            isInitialLoad -> handleInitialLoad()
                            else -> scrollToBottomIfNeeded(messageList.size, oldListSize)
                        }
                    }
                }
            }
        }
    }

    private fun restoreScrollStateAfterPaging(
        newSize: Int,
        oldSize: Int,
    ) {
        val itemsAdded = newSize - oldSize
        val newPosition = firstVisibleItemPositionBeforeLoad + itemsAdded
        (binding.chattingMessageRv.layoutManager as LinearLayoutManager)
            .scrollToPositionWithOffset(newPosition, firstVisibleItemOffsetBeforeLoad)
        isPaging = false
    }

    private fun handleInitialLoad() {
        binding.chattingMessageRv.post {
            val layoutManager = binding.chattingMessageRv.layoutManager as LinearLayoutManager
            val totalHeight =
                (0 until layoutManager.itemCount).sumOf {
                    layoutManager.findViewByPosition(it)?.height ?: 0
                }
            if (totalHeight < binding.chattingMessageRv.height) {
                layoutManager.stackFromEnd = false
            } else {
                binding.chattingMessageRv.scrollToPosition(chatAdapter.itemCount - 1)
            }
        }
    }

    private fun scrollToBottomIfNeeded(
        newSize: Int,
        oldSize: Int,
    ) {
        val isScrolledToBottom = !binding.chattingMessageRv.canScrollVertically(1)
        if (isScrolledToBottom && newSize > oldSize) {
            binding.chattingMessageRv.post {
                binding.chattingMessageRv.scrollToPosition(chatAdapter.itemCount - 1)

                val latestMessageDate = chatAdapter.currentList.lastOrNull()?.sentAt?.toLocalDate()
                if (latestMessageDate != null) {
                    lastDisplayedDate = latestMessageDate
                }
            }
        }
    }

    private fun saveScrollState(layoutManager: LinearLayoutManager) {
        isPaging = true // 페이징 시작을 알림
        firstVisibleItemPositionBeforeLoad = layoutManager.findFirstVisibleItemPosition()
        val firstVisibleItemView = layoutManager.findViewByPosition(firstVisibleItemPositionBeforeLoad)
        // 뷰의 top 좌표를 offset으로 저장
        firstVisibleItemOffsetBeforeLoad = firstVisibleItemView?.top ?: 0
    }

    private fun updateSendButtonState(isEnabled: Boolean) {
        binding.sendBtn.isEnabled = isEnabled
        binding.sendBtn.setImageDrawable(
            if (isEnabled) {
                getDrawable(requireContext(), R.drawable.ic_message_send)
            } else {
                getDrawable(requireContext(), R.drawable.ic_message_send_unable)
            },
        )
    }

    private fun showCustomSnackBar(message: String) {
        val snackBar = Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT)
        val snackBarView = snackBar.view

        val params = snackBarView.layoutParams as FrameLayout.LayoutParams

        params.apply {
            width = FrameLayout.LayoutParams.WRAP_CONTENT
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            topMargin = binding.baseToolbar.bottom + dpToPx(requireContext(), 40f)
        }

        snackBarView.layoutParams = params

        val snackBarText = snackBarView.findViewById<TextView>(com.google.android.material.R.id.snackbar_text)
        snackBarText.textAlignment = View.TEXT_ALIGNMENT_CENTER

        snackBarView.background = getDrawable(requireContext(), R.drawable.bg_snack_bar)

        snackBar.show()
    }

    private fun showPopupMenu(view: View) {
        val contextWrapper = ContextThemeWrapper(requireContext(), com.project200.undabang.presentation.R.style.PopupItemStyle)

        PopupMenu(contextWrapper, view).apply {
            menuInflater.inflate(R.menu.chatting_room_item_menu, this.menu)

            setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.action_exit -> {
                        viewModel.exitChatRoom()
                    }
                }
                true
            }

            MenuStyler.showIcons(this)
        }.show()
    }

    override fun shouldHideKeyboardOnTouch(ev: MotionEvent): Boolean {
        val x = ev.x.toInt()
        val y = ev.y.toInt()

        // RecyclerView 영역 확인
        val recyclerViewRect = Rect()
        binding.chattingMessageRv.getGlobalVisibleRect(recyclerViewRect)
        if (recyclerViewRect.contains(x, y)) {
            return false
        }

        // 전송 버튼 영역 확인
        val sendButtonRect = Rect()
        binding.sendBtn.getGlobalVisibleRect(sendButtonRect)
        return !sendButtonRect.contains(x, y)
    }

    override fun onResume() {
        super.onResume()
        // 현재 채팅방을 활성 채팅방으로 설정
        chatRoomStateRepository.setActiveChatRoomId(args.roomId)
    }

    override fun onPause() {
        super.onPause()
        // 채팅방을 나갈 때 활성 채팅방 ID를 null로 설정
        if (chatRoomStateRepository.activeChatRoomId.value == args.roomId) {
            chatRoomStateRepository.setActiveChatRoomId(null)
        }
    }

    companion object {
        const val POLLING_PERIOD = 2000L
    }
}
