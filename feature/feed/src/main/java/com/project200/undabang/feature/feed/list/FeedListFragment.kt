package com.project200.undabang.feature.feed.list

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.fragment.app.viewModels
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

    private fun initToolbar() {
        binding.baseToolbar.apply {
            setTitle("피드")
            showBackButton(false)
            setSubButton(R.drawable.ic_feed_add) {
                // TODO: 피드 추가 화면으로 이동
                Toast.makeText(context, "피드 추가 클릭", Toast.LENGTH_SHORT).show()
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
            // TODO: 선택된 타입으로 필터링 로직 구현
            Toast.makeText(context, "$selectedType 필터링", Toast.LENGTH_SHORT).show()
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

                    if (!viewModel.isLoading.value!! && totalItemCount <= (lastVisibleItem + 5)) {
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

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.loadingPb.visibility = if (isLoading && feedAdapter.itemCount == 0) View.VISIBLE else View.GONE
        }

        viewModel.error.observe(viewLifecycleOwner) { errorMessage ->
            if (errorMessage.isNotEmpty()) {
                Toast.makeText(context, errorMessage, Toast.LENGTH_SHORT).show()
            }
        }
    }
}
