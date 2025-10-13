package com.project200.feature.chatting.chattingRoom

import android.view.ContextThemeWrapper
import android.view.View
import android.widget.PopupMenu
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.project200.domain.model.ExercisePlace
import com.project200.feature.chatting.chattingRoom.adapter.ChatRVAdapter
import com.project200.presentation.base.BindingFragment
import com.project200.presentation.utils.MenuStyler
import com.project200.undabang.feature.chatting.R
import com.project200.undabang.feature.chatting.databinding.FragmentChattingRoomBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ChattingRoomFragment : BindingFragment<FragmentChattingRoomBinding>(R.layout.fragment_chatting_room) {
    private val viewModel: ChattingRoomViewModel by viewModels()
    private lateinit var chatAdapter: ChatRVAdapter
    private val args: ChattingRoomFragmentArgs by navArgs()

    // 이전 메시지 로드 상태를 추적하는 플래그
    private var isPaging = false
    // 스크롤 위치 복원을 위해 저장할 변수
    private var firstVisibleItemPositionBeforeLoad = 0
    private var firstVisibleItemOffsetBeforeLoad = 0

    override fun getViewBinding(view: View): FragmentChattingRoomBinding {
        return FragmentChattingRoomBinding.bind(view)
    }

    override fun setupViews() {
        setupRecyclerView()
        setupListeners()
        viewModel.setChatRoomId(args.roomId)
    }

    private fun setupListeners() {
        binding.baseToolbar.apply {
            setTitle(args.nickname)
            showBackButton(true) { findNavController().navigateUp() }
        }

        binding.menuBtn.setOnClickListener { showPopupMenu(binding.menuBtn) }

        binding.sendBtn.setOnClickListener {
            val messageText = binding.chattingMessageEt.text.toString()
            if (messageText.isNotBlank()) {
                viewModel.sendMessage(messageText)
                binding.chattingMessageEt.text.clear() // EditText 초기화
            }
        }
    }

    private fun setupRecyclerView() {
        chatAdapter = ChatRVAdapter()
        val layoutManager = LinearLayoutManager(requireContext()).apply {
            stackFromEnd = true // 기본적으로 하단 정렬
        }

        binding.chattingMessageRv.apply {
            adapter = chatAdapter
            this.layoutManager = layoutManager
            itemAnimator = null

            addOnScrollListener(
                object : RecyclerView.OnScrollListener() {
                    override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                        super.onScrollStateChanged(recyclerView, newState)
                        // 스크롤이 멈췄고, 최상단에 도달했을 때
                        if (newState == RecyclerView.SCROLL_STATE_IDLE && !recyclerView.canScrollVertically(-1)) {
                            // 더 불러올 메시지가 있고, 현재 로딩 중이 아닐 때만 호출
                            if (viewModel.hasNextMessages && !viewModel.isLoadingPreviousMessages.value) {
                                saveScrollState(layoutManager)
                                viewModel.loadPreviousMessages()
                            }
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
                    while (isActive) {
                        viewModel.getNewMessages()
                        delay(POLLING_PERIOD)
                    }
                }

                viewModel.messages.collect { messageList ->
                    // 리스트가 업데이트 되기 전, 현재 스크롤 상태를 확인
                    val isScrolledToBottom = !binding.chattingMessageRv.canScrollVertically(1)
                    val isInitialLoad = chatAdapter.currentList.isEmpty() && messageList.isNotEmpty()
                    val oldListSize = chatAdapter.currentList.size

                    chatAdapter.submitList(messageList) {
                        // 리스트 업데이트가 완료된 후 실행되는 콜백
                        val layoutManager = binding.chattingMessageRv.layoutManager as LinearLayoutManager

                        // 데이터 업데이트 후, 저장된 스크롤 상태로 복원
                        if (isPaging) {
                            val itemsAdded = messageList.size - oldListSize
                            val newPosition = firstVisibleItemPositionBeforeLoad + itemsAdded
                            layoutManager.scrollToPositionWithOffset(newPosition, firstVisibleItemOffsetBeforeLoad)
                            isPaging = false // 페이징 상태 초기화
                        } else if (isInitialLoad) {
                            binding.chattingMessageRv.post {
                                val totalHeight = (0 until layoutManager.itemCount).sumOf {
                                    layoutManager.findViewByPosition(it)?.height ?: 0
                                }

                                if (totalHeight < binding.chattingMessageRv.height) {
                                    layoutManager.stackFromEnd = false
                                } else {
                                    binding.chattingMessageRv.scrollToPosition(chatAdapter.itemCount - 1)
                                }
                            }
                        } else if (isScrolledToBottom) {
                            binding.chattingMessageRv.post {
                                binding.chattingMessageRv.scrollToPosition(chatAdapter.itemCount - 1)
                            }
                        }
                    }
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

    private fun showPopupMenu(
        view: View,
    ) {
        val contextWrapper = ContextThemeWrapper(requireContext(), com.project200.undabang.presentation.R.style.PopupItemStyle)

        PopupMenu(contextWrapper, view).apply {
            menuInflater.inflate(R.menu.chatting_room_item_menu, this.menu)

            menu.findItem(R.id.action_exit)?.let {
                MenuStyler.applyTextColor(requireContext(), it, com.project200.undabang.presentation.R.color.error_red)
            }

            setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.action_exit -> {
                        //TODO: 채팅방 나가기
                    }
                }
                true
            }

            MenuStyler.showIcons(this)
        }.show()
    }

    companion object {
        const val POLLING_PERIOD = 2000L
    }
}
