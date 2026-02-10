package com.project200.undabang.feature.feed.detail

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Toast
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.project200.domain.model.Feed
import com.project200.presentation.base.BindingFragment
import com.project200.presentation.utils.RelativeTimeUtil
import com.project200.presentation.view.MenuBottomSheetDialog
import com.project200.undabang.feature.feed.R
import com.project200.undabang.feature.feed.databinding.FragmentFeedDetailBinding
import com.project200.undabang.feature.feed.list.FeedListFragment
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class FeedDetailFragment : BindingFragment<FragmentFeedDetailBinding>(R.layout.fragment_feed_detail) {

    private val viewModel: FeedDetailViewModel by viewModels()
    private var commentRVAdapter: CommentRVAdapter? = null

    override fun getViewBinding(view: View): FragmentFeedDetailBinding {
        return FragmentFeedDetailBinding.bind(view)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initToolbar()
        initCommentInput()
        initObserver()
    }

    private fun initToolbar() {
        binding.baseToolbar.apply {
            setTitle("")
            showBackButton(true) { findNavController().navigateUp() }
        }
    }

    private fun initCommentInput() {
        with(binding.commentInputLayout) {
            commentInputEt.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    val hasText = !s.isNullOrBlank()
                    val sendIcon = if (hasText) {
                        R.drawable.ic_send
                    } else {
                        R.drawable.ic_send_unable
                    }
                    sendBtn.setImageResource(sendIcon)
                }
            })

            sendBtn.setOnClickListener {
                val content = commentInputEt.text.toString()
                if (content.isNotBlank()) {
                    viewModel.createComment(content)
                    commentInputEt.text.clear()
                }
            }

            cancelReplyIv.setOnClickListener {
                viewModel.setReplyTarget(null)
            }
        }
    }

    private fun initObserver() {
        viewModel.feed.observe(viewLifecycleOwner) { feed ->
            bindFeedData(feed)
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            if (isLoading) {
                binding.shimmerLayout.visibility = View.VISIBLE
                binding.shimmerLayout.startShimmer()
                binding.scrollView.visibility = View.GONE
            } else {
                binding.shimmerLayout.stopShimmer()
                binding.shimmerLayout.visibility = View.GONE
                binding.scrollView.visibility = View.VISIBLE
            }
        }

        viewModel.error.observe(viewLifecycleOwner) { errorMessage ->
            if (errorMessage.isNotEmpty()) {
                Toast.makeText(context, errorMessage, Toast.LENGTH_SHORT).show()
                binding.errorTv.visibility = View.VISIBLE
                binding.scrollView.visibility = View.GONE
            }
        }

        viewModel.isMyFeed.observe(viewLifecycleOwner) { isMyFeed ->
            binding.moreIv.visibility = if (isMyFeed) View.VISIBLE else View.GONE
        }

        viewModel.deleteSuccess.observe(viewLifecycleOwner) { success ->
            if (success) {
                Toast.makeText(context, "피드가 삭제되었습니다.", Toast.LENGTH_SHORT).show()
                findNavController().previousBackStackEntry?.savedStateHandle?.set(FeedListFragment.REFRESH_KEY, true)
                findNavController().navigateUp()
            }
        }

        viewModel.comments.observe(viewLifecycleOwner) { comments ->
            setupCommentAdapter()
            val items = comments.toCommentItems()
            commentRVAdapter?.submitList(items)

            binding.noCommentsTv.visibility = if (comments.isEmpty()) View.VISIBLE else View.GONE
            binding.commentsRv.visibility = if (comments.isEmpty()) View.GONE else View.VISIBLE
        }

        viewModel.replyTarget.observe(viewLifecycleOwner) { target ->
            with(binding.commentInputLayout) {
                if (target != null) {
                    replyTargetTv.text = "@${target.memberNickname} 님에게 답글 작성 중"
                    replyTargetTv.visibility = View.VISIBLE
                    cancelReplyIv.visibility = View.VISIBLE
                } else {
                    replyTargetTv.visibility = View.GONE
                    cancelReplyIv.visibility = View.GONE
                }
            }
        }

        viewModel.commentCreated.observe(viewLifecycleOwner) { success ->
            if (success) {
                Toast.makeText(context, "댓글이 작성되었습니다.", Toast.LENGTH_SHORT).show()
            }
        }

        viewModel.commentDeleted.observe(viewLifecycleOwner) { success ->
            if (success) {
                Toast.makeText(context, "댓글이 삭제되었습니다.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupCommentAdapter() {
        if (commentRVAdapter != null) return

        val currentMemberId = viewModel.currentMemberId.value
        commentRVAdapter = CommentRVAdapter(
            currentMemberId = currentMemberId,
            onLikeClick = { item -> viewModel.toggleCommentLike(item) },
            onReplyClick = { item -> viewModel.setReplyTarget(item) },
            onMoreClick = { item -> showCommentMenuBottomSheet(item) }
        )
        binding.commentsRv.adapter = commentRVAdapter
    }

    private fun showCommentMenuBottomSheet(item: CommentItem) {
        MenuBottomSheetDialog(
            onDeleteClicked = {
                viewModel.deleteComment(item.commentId)
            },
            showEditButton = false
        ).show(parentFragmentManager, "CommentMenu")
    }

    private fun bindFeedData(feed: Feed) {
        with(binding) {
            nicknameTv.text = feed.nickname
            timeTv.text = RelativeTimeUtil.getRelativeTime(feed.feedCreatedAt)
            contentTv.text = feed.feedContent
            likeCountTv.text = feed.feedLikesCount.toString()
            commentCountTv.text = feed.feedCommentsCount.toString()

            val likeIcon = if (feed.feedIsLiked) {
                R.drawable.ic_like_fill
            } else {
                R.drawable.ic_like
            }
            likeIv.setImageResource(likeIcon)

            likeIv.setOnClickListener {
                viewModel.toggleFeedLike()
            }

            val hasType = !feed.feedTypeName.isNullOrBlank()
            arrowIv.visibility = if (hasType) View.VISIBLE else View.GONE
            feedTypeTv.visibility = if (hasType) View.VISIBLE else View.GONE
            if (hasType) {
                feedTypeTv.text = feed.feedTypeName
            }

            Glide.with(profileIv.context)
                .load(feed.profileUrl)
                .placeholder(com.project200.undabang.presentation.R.drawable.ic_profile_default)
                .error(com.project200.undabang.presentation.R.drawable.ic_profile_default)
                .circleCrop()
                .into(profileIv)

            if (feed.feedPictures.isNotEmpty()) {
                imagesRv.visibility = View.VISIBLE
                imagesRv.layoutManager = LinearLayoutManager(
                    context,
                    LinearLayoutManager.HORIZONTAL,
                    false
                )
                imagesRv.adapter = FeedDetailImageAdapter(feed.feedPictures)
            } else {
                imagesRv.visibility = View.GONE
            }

            moreIv.setOnClickListener {
                showMenuBottomSheet()
            }
        }
    }

    private fun showMenuBottomSheet() {
        MenuBottomSheetDialog(
            onEditClicked = {
                // TODO: 피드 수정 기능 구현
            },
            onDeleteClicked = {
                viewModel.deleteFeed()
            }
        ).show(parentFragmentManager, MenuBottomSheetDialog::class.java.simpleName)
    }
}
