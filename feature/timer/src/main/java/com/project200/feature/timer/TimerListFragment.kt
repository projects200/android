package com.project200.feature.timer

import android.view.View
import android.widget.Toast
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.project200.domain.model.CustomTimer
import com.project200.presentation.base.BindingFragment
import com.project200.undabang.feature.timer.R
import com.project200.undabang.feature.timer.databinding.FragmentTimerListBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class TimerListFragment: BindingFragment<FragmentTimerListBinding>(R.layout.fragment_timer_list) {
    private val viewModel: TimerListViewModel by viewModels()
    private lateinit var customTimerRVAdapter: CustomTimerRVAdapter

    override fun getViewBinding(view: View): FragmentTimerListBinding {
        return FragmentTimerListBinding.bind(view)
    }

    override fun setupViews() {
        initClickListeners()
        initRecyclerView()
    }

    private fun initClickListeners() {
        binding.simpleTimerBtn.setOnClickListener {
            findNavController().navigate(
                TimerListFragmentDirections.actionTimerListFragmentToSimpleTimerFragment()
            )
        }
        binding.addCustomTimerBtn.setOnClickListener {
            findNavController().navigate(
                TimerListFragmentDirections.actionTimerListFragmentToCustomTimerFormFragment()
            )
        }
    }

    private fun initRecyclerView() {
        customTimerRVAdapter = CustomTimerRVAdapter { customTimer ->
            findNavController().navigate(
                TimerListFragmentDirections.actionTimerListFragmentToCustomTimerFragment(customTimer.id)
            )
        }
        binding.customTimerRv.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = customTimerRVAdapter
        }
    }

    override fun setupObservers() {
        // 타이머 리스트 관찰
        viewModel.customTimerList.observe(viewLifecycleOwner) { timers ->
            // LiveData가 변경될 때마다 어댑터에 리스트를 제출합니다.
            customTimerRVAdapter.submitList(timers)
        }

        // 토스트 메시지 이벤트 관찰
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.errorToast.collect { error ->
                    Toast.makeText(requireContext(), getString(R.string.error_failed_to_load_list), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}