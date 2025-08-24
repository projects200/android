package com.project200.feature.exercise.detail

import android.content.Context
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.project200.domain.model.BaseResult
import com.project200.domain.model.ExerciseRecord
import com.project200.presentation.base.BindingFragment
import com.project200.undabang.feature.exercise.R
import com.project200.undabang.feature.exercise.databinding.FragmentExerciseDetailBinding
import dagger.hilt.android.AndroidEntryPoint
import com.project200.common.utils.CommonDateTimeFormatters
import com.project200.feature.exercise.form.ExerciseMenuBottomSheet
import com.project200.feature.exercise.list.ExerciseListFragmentDirections
import com.project200.presentation.base.BaseAlertDialog
import com.project200.presentation.navigator.BottomNavigationController
import com.project200.presentation.navigator.FragmentNavigator

@AndroidEntryPoint
class ExerciseDetailFragment: BindingFragment<FragmentExerciseDetailBinding>(R.layout.fragment_exercise_detail) {
    private val viewModel: ExerciseDetailViewModel by viewModels()
    private var bottomNavController: BottomNavigationController? = null

    override fun getViewBinding(view: View): FragmentExerciseDetailBinding {
        return FragmentExerciseDetailBinding.bind(view)
    }

    override fun setupViews() {
        binding.baseToolbar.apply {
            setTitle(getString(R.string.exercise_detail))
            showBackButton(true) { findNavController().navigateUp() }
            setSubButton(R.drawable.ic_menu) { showExerciseDetailMenu() }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.getExerciseRecord()
    }

    override fun setupObservers() {
        viewModel.exerciseRecord.observe(viewLifecycleOwner) { result ->
            when (result) {
                is BaseResult.Success -> {
                    bindExerciseRecordData(result.data)
                }
                is BaseResult.Error -> {
                    Toast.makeText(requireContext(), result.message, Toast.LENGTH_SHORT).show()
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


    private fun TextView.setTextOrHide(newText: String?, titleView: TextView? = null) {
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
        ExerciseMenuBottomSheet(
            onEditClicked = {
                findNavController().navigate(
                    ExerciseDetailFragmentDirections
                        .actionExerciseDetailFragmentToExerciseFormFragment(viewModel.recordId)
                )
            },
            onDeleteClicked = { showDeleteConfirmationDialog() }
        ).show(parentFragmentManager, ExerciseMenuBottomSheet::class.java.simpleName)
    }

    private fun showDeleteConfirmationDialog() {
        BaseAlertDialog(
            title = getString(R.string.exercise_record_delete_alert),
            desc = null,
            onConfirmClicked = {
                viewModel.deleteExerciseRecord()
            }
        ).show(parentFragmentManager, BaseAlertDialog::class.java.simpleName)
    }

    companion object {
        const val TAG = "ExerciseDetailFragment"
    }
}