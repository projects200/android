package com.project200.feature.timer

import android.view.View
import android.widget.Toast
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.project200.presentation.base.BindingFragment
import com.project200.undabang.feature.timer.R
import com.project200.undabang.feature.timer.databinding.FragmentTimerListBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import timber.log.Timber

@AndroidEntryPoint
class TimerListFragment : BindingFragment<FragmentTimerListBinding>(R.layout.fragment_timer_list) {
    private val viewModel: TimerListViewModel by viewModels()
    private lateinit var customTimerRVAdapter: CustomTimerRVAdapter

    override fun getViewBinding(view: View): FragmentTimerListBinding {
        return FragmentTimerListBinding.bind(view)
    }

    override fun setupViews() {
        super.setupViews()
        initClickListeners()
        initRecyclerView()
    }

    private fun initClickListeners() {
        binding.simpleTimerBtn.setOnClickListener {
            findNavController().navigate(
                TimerListFragmentDirections.actionTimerListFragmentToSimpleTimerFragment(),
            )
        }
        binding.addCustomTimerBtn.setOnClickListener {
            findNavController().navigate(
                TimerListFragmentDirections.actionTimerListFragmentToCustomTimerFormFragment(),
            )
        }
    }

    private fun initRecyclerView() {
        customTimerRVAdapter =
            CustomTimerRVAdapter { customTimer ->
                findNavController().navigate(
                    TimerListFragmentDirections.actionTimerListFragmentToCustomTimerFragment(customTimer.id),
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
            // 타이머가 없을 때와 있을 때의 뷰 상태를 변경합니다.
            if (timers.isNullOrEmpty()) {
                binding.addTimerText.visibility = View.VISIBLE
                binding.addTimerIconRight.visibility = View.VISIBLE
                binding.addTimerIconCenter.visibility = View.GONE
            } else {
                binding.addTimerText.visibility = View.GONE
                binding.addTimerIconRight.visibility = View.GONE
                binding.addTimerIconCenter.visibility = View.VISIBLE
            }
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

        // 이전 화면으로부터 'REFRESH_LIST_KEY'로 전달되는 결과를 관찰합니다.
        // 이전 화면에서 새로고침 요청이 있을 경우에만 데이터를 새로고침합니다.
        val savedStateHandle = findNavController().currentBackStackEntry?.savedStateHandle
        savedStateHandle?.getLiveData<Boolean>(REFRESH_KEY)?.observe(viewLifecycleOwner) { shouldRefresh ->
            if (shouldRefresh) {
                Timber.tag("TimerListFragment").d("커스텀 타이머 리프레시")
                viewModel.loadCustomTimers()
                savedStateHandle.remove<Boolean>(REFRESH_KEY)
            }
        }
    }

    companion object {
        const val REFRESH_KEY = "refresh_list_key"
    }
}
