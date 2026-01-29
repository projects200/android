package com.project200.feature.exercise.detail

import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.project200.common.utils.CommonDateTimeFormatters
import com.project200.domain.model.BaseResult
import com.project200.domain.model.ExerciseRecord

import com.project200.presentation.base.BaseAlertDialog
import com.project200.presentation.base.BindingFragment
import com.project200.presentation.utils.UiState
import com.project200.presentation.utils.mapFailureToString
import com.project200.undabang.feature.exercise.R
import com.project200.undabang.feature.exercise.databinding.FragmentExerciseDetailBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ExerciseDetailFragment : BindingFragment<FragmentExerciseDetailBinding>(R.layout.fragment_exercise_detail) {
    private val viewModel: ExerciseDetailViewModel by viewModels()
    private val args: ExerciseDetailFragmentArgs by navArgs()
    private var currentRecord: ExerciseRecord? = null

    override fun getViewBinding(view: View): FragmentExerciseDetailBinding {
        return FragmentExerciseDetailBinding.bind(view)
    }

    override fun setupViews() {
        viewModel.getExerciseRecord(args.recordId)
        binding.baseToolbar.apply {
            setTitle(getString(R.string.exercise_detail))
            showBackButton(true) { findNavController().navigateUp() }
            setSecondarySubButton(R.drawable.ic_share) {
                findNavController().navigate(
                    ExerciseDetailFragmentDirections.actionExerciseDetailFragmentToExerciseShareEditFragment(args.recordId)
                )
            }
            setSubButton(R.drawable.ic_menu) { showExerciseDetailMenu() }
        }
    }

    override fun setupObservers() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.exerciseRecord.collect { state ->
                    binding.shimmerLayout.visibility = if (state is UiState.Loading) View.VISIBLE else View.GONE
                    binding.scrollView.visibility = if (state is UiState.Success) View.VISIBLE else View.GONE

                    when (state) {
                        is UiState.Loading -> {
                            binding.shimmerLayout.startShimmer()
                        }
                        is UiState.Success -> {
                            binding.shimmerLayout.stopShimmer()
                            currentRecord = state.data
                            bindExerciseRecordData(state.data)
                        }
                        is UiState.Error -> {
                            binding.shimmerLayout.stopShimmer()
                            Toast.makeText(requireContext(), requireContext().mapFailureToString(state.failure), Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }

        viewModel.deleteResult.observe(viewLifecycleOwner) { result ->
            when (result) {
                is BaseResult.Success -> {
                    findNavController().popBackStack()
                }
                is BaseResult.Error -> {
                    Toast.makeText(requireContext(), result.message, Toast.LENGTH_SHORT).show()
                }
            }
        }

        // 이전 화면에서 새로고침 요청이 있을 경우에만 데이터를 새로고침합니다.
        val savedStateHandle = findNavController().currentBackStackEntry?.savedStateHandle
        savedStateHandle?.getLiveData<Boolean>(KEY_RECORD_UPDATED)?.observe(viewLifecycleOwner) { shouldRefresh ->
            if (shouldRefresh) {
                viewModel.getExerciseRecord(args.recordId)
                savedStateHandle.remove<Boolean>(KEY_RECORD_UPDATED)
            }
        }
    }

    private fun bindExerciseRecordData(record: ExerciseRecord) {
        with(binding) {
            // ViewPager2와 CircleIndicator3 설정
            val currentPictureUrls = record.pictures?.map { it.url }
            if (currentPictureUrls.isNullOrEmpty()) {
                recordImgContainerFl.visibility = View.GONE
            } else {
                recordImgContainerFl.visibility = View.VISIBLE
                recordImgVp.adapter = ImageSliderAdapter(currentPictureUrls)

                val indicator = recordImgIndicator
                indicator.setViewPager(recordImgVp)
            }

            recordTitleTv.text = record.title
            recordTypeTv.setTextOrHide(record.personalType, recordTypeTitleTv)

            // 시간 처리
            val dateTimeFormatter = CommonDateTimeFormatters.YY_MM_DD_HH_MM
            val startTimeString = record.startedAt.format(dateTimeFormatter)
            val endTimeString = record.endedAt.format(dateTimeFormatter)

            if (startTimeString.isNullOrBlank() && endTimeString.isNullOrBlank()) {
                recordTimeCl.visibility = View.GONE
            } else {
                recordTimeCl.visibility = View.VISIBLE
                startTimeTv.text = startTimeString
                endTimeTv.text = endTimeString
            }

            // 장소 처리
            recordLocationTv.setTextOrHide(record.location, recordLocationTitleTv)

            // 내용 처리
            recordDescTv.setTextOrHide(record.detail, recordDescTitleTv)
        }
    }

    private fun TextView.setTextOrHide(
        newText: String?,
        titleView: TextView? = null,
    ) {
        if (!newText.isNullOrBlank()) {
            this.text = newText
            this.visibility = View.VISIBLE
            titleView?.visibility = View.VISIBLE
        } else {
            this.visibility = View.GONE
            titleView?.visibility = View.GONE
        }
    }

    private fun showExerciseDetailMenu() {
        ExerciseMenuBottomSheetDialog(
            onEditClicked = {
                findNavController().navigate(
                    ExerciseDetailFragmentDirections
                        .actionExerciseDetailFragmentToExerciseFormFragment(args.recordId),
                )
            },
            onDeleteClicked = { showDeleteConfirmationDialog() },
        ).show(parentFragmentManager, ExerciseMenuBottomSheetDialog::class.java.simpleName)
    }

    private fun showDeleteConfirmationDialog() {
        BaseAlertDialog(
            title = getString(R.string.exercise_record_delete_alert),
            desc = null,
            onConfirmClicked = {
                viewModel.deleteExerciseRecord(args.recordId)
            },
        ).show(parentFragmentManager, BaseAlertDialog::class.java.simpleName)
    }

    companion object {
        const val KEY_RECORD_UPDATED = "record_updated"
    }
}
