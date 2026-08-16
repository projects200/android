package com.project200.undabang.feature.feed.detail

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.project200.presentation.compose.applyAppTheme
import com.project200.presentation.compose.components.feedback.UndabangBottomSheet
import com.project200.presentation.utils.collectFlow
import com.project200.presentation.utils.collectToast
import com.project200.undabang.feature.feed.form.FeedFormFragment
import com.project200.undabang.feature.feed.list.FeedListFragment
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class FeedDetailFragment : Fragment() {
    private val viewModel: FeedDetailViewModel by viewModels()
    private val args: FeedDetailFragmentArgs by navArgs()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel.setFeedId(args.feedId)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View =
        ComposeView(requireContext()).apply {
            applyAppTheme {
                val feed by viewModel.feed.collectAsStateWithLifecycle()
                val comments by viewModel.comments.collectAsStateWithLifecycle()
                val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
                val isMyFeed by viewModel.isMyFeed.collectAsStateWithLifecycle()
                val currentMemberId by viewModel.currentMemberId.collectAsStateWithLifecycle()
                val replyTarget by viewModel.replyTarget.collectAsStateWithLifecycle()

                // 댓글 입력 텍스트는 화면 회전 시에도 유지되도록 rememberSaveable로 저장
                var commentInput by rememberSaveable { mutableStateOf("") }
                // 첫 로드 실패를 한 번 감지하면 토스트 + 에러 UI 표시
                val isLoadError = remember { mutableStateOf(false) }
                LaunchedFeedLoadErrorCollector(viewModel) { isLoadError.value = true }

                FeedDetailScreen(
                    feed = feed,
                    commentItems = comments.toCommentItems(),
                    isLoading = isLoading,
                    isLoadError = isLoadError.value,
                    isMyFeed = isMyFeed,
                    currentMemberId = currentMemberId,
                    replyTarget = replyTarget,
                    commentInput = commentInput,
                    onCommentInputChange = { commentInput = it },
                    onSendComment = {
                        if (commentInput.isNotBlank()) {
                            viewModel.createComment(commentInput)
                            commentInput = ""
                        }
                    },
                    onCancelReply = { viewModel.setReplyTarget(null) },
                    onReplyClick = viewModel::setReplyTarget,
                    onCommentLikeClick = viewModel::toggleCommentLike,
                    onCommentMoreClick = ::showCommentMenuBottomSheet,
                    onFeedLikeClick = viewModel::toggleFeedLike,
                    onFeedMoreClick = ::showFeedMenuBottomSheet,
                    onBackClick = { findNavController().navigateUp() },
                )
            }
        }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        observeFeedUpdated()
        collectToast(viewModel.toastEvent)
        collectFlow(viewModel.feedDeleted) {
            findNavController().previousBackStackEntry?.savedStateHandle?.set(FeedListFragment.REFRESH_KEY, true)
            findNavController().navigateUp()
        }
    }

    private fun observeFeedUpdated() {
        // 작성/수정 화면에서 돌아왔을 때 갱신 시그널 처리
        findNavController().currentBackStackEntry?.savedStateHandle
            ?.getLiveData<Boolean>(FeedFormFragment.FEED_UPDATED_KEY)
            ?.observe(viewLifecycleOwner) { updated ->
                if (updated) {
                    viewModel.refreshFeed()
                    findNavController().previousBackStackEntry?.savedStateHandle?.set(FeedListFragment.REFRESH_KEY, true)
                    findNavController().currentBackStackEntry?.savedStateHandle?.remove<Boolean>(FeedFormFragment.FEED_UPDATED_KEY)
                }
            }
    }

    private fun showCommentMenuBottomSheet(item: CommentItem) {
        UndabangBottomSheet.showMenu(
            fragmentManager = parentFragmentManager,
            showEdit = false,
            onDeleteClick = { viewModel.deleteComment(item.commentId) },
        )
    }

    private fun showFeedMenuBottomSheet() {
        UndabangBottomSheet.showMenu(
            fragmentManager = parentFragmentManager,
            onEditClick = { navigateToEditFeed() },
            onDeleteClick = { viewModel.deleteFeed() },
        )
    }

    private fun navigateToEditFeed() {
        val feed = viewModel.feed.value ?: return
        val action =
            FeedDetailFragmentDirections.actionFeedDetailFragmentToFeedFormFragment(
                feedId = feed.feedId,
            )
        findNavController().navigate(action)
    }
}

@androidx.compose.runtime.Composable
private fun LaunchedFeedLoadErrorCollector(
    viewModel: FeedDetailViewModel,
    onError: () -> Unit,
) {
    androidx.compose.runtime.LaunchedEffect(viewModel) {
        viewModel.feedLoadError.collect { onError() }
    }
}
