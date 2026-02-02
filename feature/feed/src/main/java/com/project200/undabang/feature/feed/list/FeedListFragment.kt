package com.project200.undabang.feature.feed.list

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.project200.presentation.base.BindingFragment
import com.project200.undabang.feature.feed.R
import com.project200.undabang.feature.feed.databinding.FragmentFeedListBinding
import dagger.hilt.android.AndroidEntryPoint

import com.project200.presentation.view.SelectionBottomSheetDialog

@AndroidEntryPoint
class FeedListFragment : BindingFragment<FragmentFeedListBinding>(R.layout.fragment_feed_list) {

    private val viewModel: FeedListViewModel by viewModels()
    private val feedAdapter = FeedListAdapter()

    override fun getViewBinding(view: View): FragmentFeedListBinding {
        return FragmentFeedListBinding.bind(view)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initToolbar()
        initView()
        initObserver()
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadFeeds(isRefresh = true)
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
        val items = viewModel.exerciseTypeList.value
        if (items.isNullOrEmpty()) {
            viewModel.loadExerciseTypes()
            return
        }

        SelectionBottomSheetDialog(items) { selectedType ->
            viewModel.selectType(selectedType)
        }.show(parentFragmentManager, SelectionBottomSheetDialog::class.java.name)
    }

    private fun initView() {
        binding.feedListRv.apply {
            adapter = feedAdapter
            layoutManager = LinearLayoutManager(context)
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    super.onScrolled(recyclerView, dx, dy)
                    
                    val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                    val totalItemCount = layoutManager.itemCount
                    val lastVisibleItem = layoutManager.findLastVisibleItemPosition()

                    // 필터링 상태가 아닐 때만 다음 페이지 로드
                    if (viewModel.selectedType.value == null && 
                        !viewModel.isLoading.value!! && 
                        totalItemCount <= (lastVisibleItem + 5)) {
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

        viewModel.isEmpty.observe(viewLifecycleOwner) { isEmpty ->
            val isLoading = viewModel.isLoading.value ?: false
            val isInitialLoading = isLoading && feedAdapter.itemCount == 0
            binding.emptyTv.visibility = if (isEmpty && !isLoading) View.VISIBLE else View.GONE
            if (!isInitialLoading) {
                binding.feedListRv.visibility = if (isEmpty) View.GONE else View.VISIBLE
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
                binding.feedListRv.visibility = View.GONE
            } else {
                binding.shimmerLayout.stopShimmer()
                binding.shimmerLayout.visibility = View.GONE
            }
        }

        viewModel.error.observe(viewLifecycleOwner) { errorMessage ->
            if (errorMessage.isNotEmpty()) {
                Toast.makeText(context, errorMessage, Toast.LENGTH_SHORT).show()
            }
        }
    }
}
