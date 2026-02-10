package com.project200.undabang.feature.feed.list

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.project200.presentation.base.BindingFragment
import com.project200.presentation.view.SelectionBottomSheetDialog
import com.project200.undabang.feature.feed.R
import com.project200.undabang.feature.feed.databinding.FragmentFeedListBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class FeedListFragment : BindingFragment<FragmentFeedListBinding>(R.layout.fragment_feed_list) {

    private val viewModel: FeedListViewModel by viewModels()
    private lateinit var feedAdapter: FeedListAdapter

    override fun getViewBinding(view: View): FragmentFeedListBinding {
        return FragmentFeedListBinding.bind(view)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initAdapter()
        initToolbar()
        initView()
        initObserver()
        observeRefreshSignal()
    }

    private fun observeRefreshSignal() {
        val savedStateHandle = findNavController().currentBackStackEntry?.savedStateHandle
        savedStateHandle?.getLiveData<Boolean>(REFRESH_KEY)?.observe(viewLifecycleOwner) { shouldRefresh ->
            if (shouldRefresh) {
                viewModel.loadFeeds(isRefresh = true)
                savedStateHandle.remove<Boolean>(REFRESH_KEY)
            }
        }
    }

    private fun initAdapter() {
        feedAdapter = FeedListAdapter(
            onItemClick = { feed ->
                findNavController().navigate(
                    FeedListFragmentDirections.actionFeedListFragmentToFeedDetailFragment(feed.feedId)
                )
            },
        )
    }

    private fun initToolbar() {
        binding.baseToolbar.apply {
            setTitle(getString(R.string.feed_title))
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
                    setTitle(getString(R.string.feed_title))
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
                binding.swipeRefreshLayout.visibility = View.VISIBLE
                binding.swipeRefreshLayout.isRefreshing = false
            }
        }

        viewModel.event.observe(viewLifecycleOwner) { event ->
            when (event) {
                is FeedListEvent.ShowToast -> {
                    Toast.makeText(context, event.messageResId, Toast.LENGTH_SHORT).show()
                }
                is FeedListEvent.FeedDeleted -> {
                    Toast.makeText(context, event.messageResId, Toast.LENGTH_SHORT).show()
                }
            }
        }

        viewModel.currentMemberId.observe(viewLifecycleOwner) { memberId ->
            feedAdapter.setCurrentMemberId(memberId)
        }
    }

    companion object {
        const val REFRESH_KEY = "feed_list_refresh"
    }
}
