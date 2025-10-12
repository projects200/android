package com.project200.feature.chatting.chattingRoom

import android.view.View
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.project200.feature.chatting.chattingRoom.adapter.ChatRVAdapter
import com.project200.presentation.base.BindingFragment
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

    override fun getViewBinding(view: View): FragmentChattingRoomBinding {
        return FragmentChattingRoomBinding.bind(view)
    }

    override fun setupViews() {
        setupRecyclerView()
        setupListeners()
    }

    private fun setupListeners() {
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

        binding.chattingMessageRv.apply {
            adapter = chatAdapter
            layoutManager =
                LinearLayoutManager(requireContext()).apply {
                    stackFromEnd = true // 기본적으로 하단 정렬
                }
            itemAnimator = null

            addOnScrollListener(
                object : RecyclerView.OnScrollListener() {
                    override fun onScrolled(
                        recyclerView: RecyclerView,
                        dx: Int,
                        dy: Int,
                    ) {
                        super.onScrolled(recyclerView, dx, dy)
                        if (!recyclerView.canScrollVertically(-1)) {
                            viewModel.loadPreviousMessages()
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
                        viewModel.startPolling()
                        delay(POLLING_PERIOD)
                    }
                }

                viewModel.messages.collect { messageList ->
                    // 리스트가 업데이트 되기 전, 현재 스크롤 상태를 확인
                    val isScrolledToBottom = !binding.chattingMessageRv.canScrollVertically(1)
                    val isInitialLoad = chatAdapter.currentList.isEmpty() && messageList.isNotEmpty()
                    val isNewMessageAdded = chatAdapter.currentList.isNotEmpty() && chatAdapter.currentList.size < messageList.size

                    chatAdapter.submitList(messageList) {
                        // 리스트 업데이트가 완료된 후 실행되는 콜백
                        if (isInitialLoad) {
                            // 최초 로드 시, 메시지 양에 따라 상단/하단 정렬을 결정하는 로직
                            binding.chattingMessageRv.post {
                                val layoutManager = binding.chattingMessageRv.layoutManager as LinearLayoutManager

                                val totalHeight =
                                    (0 until layoutManager.itemCount).sumOf {
                                        layoutManager.findViewByPosition(it)?.height ?: 0
                                    }

                                if (totalHeight < binding.chattingMessageRv.height) {
                                    layoutManager.stackFromEnd = false
                                } else {
                                    binding.chattingMessageRv.post {
                                        binding.chattingMessageRv.scrollToPosition(chatAdapter.itemCount - 1)
                                    }
                                }
                            }
                        } else if (isNewMessageAdded && isScrolledToBottom) {
                            // 새 메시지가 추가되었고, 사용자가 스크롤을 맨 아래에 두고 있었을 때만 자동 스크롤
                            binding.chattingMessageRv.post {
                                binding.chattingMessageRv.scrollToPosition(chatAdapter.itemCount - 1)
                            }
                        }
                    }
                }
            }
        }
    }

    companion object {
        const val POLLING_PERIOD = 2000L
    }
}
