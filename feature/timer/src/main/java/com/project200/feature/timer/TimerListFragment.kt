package com.project200.feature.timer

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.fragment.findNavController
import com.project200.presentation.compose.applyAppTheme
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber

@AndroidEntryPoint
class TimerListFragment : Fragment() {
    private val viewModel: TimerListViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View =
        ComposeView(requireContext()).apply {
            applyAppTheme {
                val customTimers by viewModel.customTimerList.collectAsStateWithLifecycle()
                TimerListScreen(
                    customTimers = customTimers,
                    onBackClick = { findNavController().navigateUp() },
                    onSimpleTimerClick = {
                        findNavController().navigate(
                            TimerListFragmentDirections.actionTimerListFragmentToSimpleTimerFragment(),
                        )
                    },
                    onCustomTimerClick = { timer ->
                        findNavController().navigate(
                            TimerListFragmentDirections.actionTimerListFragmentToCustomTimerFragment(timer.localId),
                        )
                    },
                    onAddCustomTimerClick = {
                        findNavController().navigate(
                            TimerListFragmentDirections.actionTimerListFragmentToCustomTimerFormFragment(),
                        )
                    },
                )
            }
        }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)

        // 이전 화면으로부터 REFRESH_LIST_KEY로 전달되는 결과를 관찰합니다
        val savedStateHandle = findNavController().currentBackStackEntry?.savedStateHandle
        savedStateHandle?.getLiveData<Boolean>(REFRESH_KEY)?.observe(viewLifecycleOwner) { shouldRefresh ->
            if (shouldRefresh) {
                Timber.tag("TimerListFragment").d("커스텀 타이머 리프레시")
                viewModel.refreshLocalCustomTimers()
                savedStateHandle.remove<Boolean>(REFRESH_KEY)
            }
        }
    }

    companion object {
        const val REFRESH_KEY = "refresh_list_key"
    }
}
