package com.project200.undabang.feature.feed.detail

import android.os.Bundle
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

    override fun getViewBinding(view: View): FragmentFeedDetailBinding {
        return FragmentFeedDetailBinding.bind(view)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initToolbar()
        initObserver()
    }

    private fun initToolbar() {
        binding.baseToolbar.apply {
            setTitle("")
            showBackButton(true) { findNavController().navigateUp() }
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
    }

    private fun bindFeedData(feed: Feed) {
        with(binding) {
            nicknameTv.text = feed.nickname
            timeTv.text = RelativeTimeUtil.getRelativeTime(feed.feedCreatedAt)
            contentTv.text = feed.feedContent
            likeCountTv.text = feed.feedLikesCount.toString()
            commentCountTv.text = feed.feedCommentsCount.toString()

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
