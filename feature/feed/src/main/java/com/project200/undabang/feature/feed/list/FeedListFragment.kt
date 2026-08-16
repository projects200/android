package com.project200.undabang.feature.feed.list

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.project200.presentation.compose.applyAppTheme
import com.project200.presentation.compose.components.feedback.UndabangBottomSheet
import com.project200.presentation.utils.collectToast
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class FeedListFragment : Fragment() {
    private val viewModel: FeedListViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View =
        ComposeView(requireContext()).apply {
            applyAppTheme {
                val feeds by viewModel.feedList.collectAsStateWithLifecycle()
                val selectedType by viewModel.selectedType.collectAsStateWithLifecycle()
                val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
                val isEmpty by viewModel.isEmpty.collectAsStateWithLifecycle()

                FeedListScreen(
                    feeds = feeds,
                    selectedTypeName = selectedType?.name,
                    isLoading = isLoading,
                    isEmpty = isEmpty,
                    onFeedClick = { feedId ->
                        findNavController().navigate(
                            FeedListFragmentDirections.actionFeedListFragmentToFeedDetailFragment(feedId),
                        )
                    },
                    onAddClick = {
                        findNavController().navigate(
                            FeedListFragmentDirections.actionFeedListFragmentToFeedFormFragment(),
                        )
                    },
                    onCategoryClick = viewModel::requestShowCategoryBottomSheet,
                    onClearCategory = viewModel::clearType,
                    onLoadMore = {
                        if (viewModel.canLoadMore()) {
                            viewModel.loadFeeds()
                        }
                    },
                )
            }
        }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        observeRefreshSignal()
        collectToast(viewModel.toastEvent)

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.showCategoryBottomSheet.collect { types ->
                    UndabangBottomSheet.showSelection(
                        fragmentManager = parentFragmentManager,
                        items = types.map { it.name },
                    ) { selectedName ->
                        val selected = types.find { it.name == selectedName }
                        viewModel.selectType(selected)
                    }
                }
            }
        }
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

    companion object {
        const val REFRESH_KEY = "feed_list_refresh"
    }
}
