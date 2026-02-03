package com.project200.undabang.feature.feed.list

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.project200.domain.model.Feed
import com.project200.presentation.base.BindingFragment
import com.project200.undabang.feature.feed.R
import com.project200.undabang.feature.feed.databinding.FragmentFeedListBinding
import dagger.hilt.android.AndroidEntryPoint

import com.project200.presentation.view.MenuBottomSheetDialog
import com.project200.presentation.view.SelectionBottomSheetDialog

@AndroidEntryPoint
class FeedListFragment : BindingFragment<FragmentFeedListBinding>(R.layout.fragment_feed_list) {

    private val viewModel: FeedListViewModel by viewModels()
    private lateinit var feedAdapter: FeedListAdapter
    private var selectedFeed: Feed? = null

    override fun getViewBinding(view: View): FragmentFeedListBinding {
        return FragmentFeedListBinding.bind(view)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initAdapter()
        initToolbar()
        initView()
        initObserver()
    }

    private fun initAdapter() {
        feedAdapter = FeedListAdapter { feed ->
            selectedFeed = feed
            showMenuBottomSheet()
        }
    }

    private fun showMenuBottomSheet() {
        MenuBottomSheetDialog(
            onEditClicked = {
                // TODO: 피드 수정 기능 구현
            },
            onDeleteClicked = {
                selectedFeed?.let { viewModel.deleteFeed(it.feedId) }
            }
        ).show(parentFragmentManager, MenuBottomSheetDialog::class.java.simpleName)
    }

    private fun initToolbar() {
        binding.baseToolbar.apply {
            setTitle("피드")
            showBackButton(false)
            setSubButton(R.drawable.ic_feed_add) {
                findNavController().navigate(
                    FeedListFragmentDirections.actionFeedListFragmentToFeedFormFragment()
                )
            }
            setSubButton2(R.drawable.ic_category) {
                showCategoryBottomSheet()
            }
        }
    }

    private fun showCategoryBottomSheet() {
        viewModel.requestShowCategoryBottomSheet()
    }

    private fun displayCategoryBottomSheet(items: List<String>) {
        SelectionBottomSheetDialog(items) { selectedType ->
            viewModel.selectType(selectedType)
        }.show(parentFragmentManager, SelectionBottomSheetDialog::class.java.name)
        viewModel.onCategoryBottomSheetShown()
    }

    private fun initView() {
        binding.swipeRefreshLayout.setOnRefreshListener {
            viewModel.loadFeeds(isRefresh = true)
        }

        binding.feedListRv.apply {
            adapter = feedAdapter
            layoutManager = LinearLayoutManager(context)
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    super.onScrolled(recyclerView, dx, dy)
                    
                    val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                    val totalItemCount = layoutManager.itemCount
                    val lastVisibleItem = layoutManager.findLastVisibleItemPosition()

                    if (viewModel.canLoadMore() && totalItemCount <= (lastVisibleItem + 5)) {
                        viewModel.loadFeeds()
                    }
                }
            })
        }
    }

    private fun initObserver() {
        viewModel.feedList.observe(viewLifecycleOwner) { feeds ->
            feedAdapter.submitList(feeds)
        }

        viewModel.showEmptyView.observe(viewLifecycleOwner) { showEmpty ->
            binding.emptyTv.visibility = if (showEmpty) View.VISIBLE else View.GONE
            binding.swipeRefreshLayout.visibility = if (showEmpty) View.GONE else View.VISIBLE
        }

        viewModel.showCategoryBottomSheet.observe(viewLifecycleOwner) { items ->
            if (!items.isNullOrEmpty()) {
                displayCategoryBottomSheet(items)
            }
        }

        viewModel.selectedType.observe(viewLifecycleOwner) { type ->
            binding.baseToolbar.apply {
                if (type != null) {
                    setTitle(type)
                    setSubButton2(null)
                    showBackButton(true) { viewModel.clearType() }
                } else {
                    setTitle("피드")
                    setSubButton2(R.drawable.ic_category) {
                        showCategoryBottomSheet()
                    }
                    showBackButton(false)
                }
            }
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            val isInitialLoading = isLoading && feedAdapter.itemCount == 0
            if (isInitialLoading) {
                binding.shimmerLayout.visibility = View.VISIBLE
                binding.shimmerLayout.startShimmer()
                binding.swipeRefreshLayout.visibility = View.GONE
                binding.emptyTv.visibility = View.GONE
            } else {
                binding.shimmerLayout.stopShimmer()
                binding.shimmerLayout.visibility = View.GONE
                binding.swipeRefreshLayout.isRefreshing = false
            }
        }

        viewModel.error.observe(viewLifecycleOwner) { errorMessage ->
            if (errorMessage.isNotEmpty()) {
                Toast.makeText(context, errorMessage, Toast.LENGTH_SHORT).show()
            }
        }

        viewModel.currentMemberId.observe(viewLifecycleOwner) { memberId ->
            feedAdapter.setCurrentMemberId(memberId)
        }

        viewModel.deleteSuccess.observe(viewLifecycleOwner) { success ->
            if (success) {
                Toast.makeText(context, "피드가 삭제되었습니다.", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
