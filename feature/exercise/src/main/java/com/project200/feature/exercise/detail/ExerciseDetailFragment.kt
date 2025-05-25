package com.project200.feature.exercise.detail

import android.content.Context
import android.view.View
import android.widget.PopupMenu
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
import com.project200.feature.exercise.ImageSliderAdapter
import com.project200.presentation.base.BaseAlertDialog
import com.project200.presentation.navigator.FragmentNavigator

@AndroidEntryPoint
class ExerciseDetailFragment: BindingFragment<FragmentExerciseDetailBinding>(R.layout.fragment_exercise_detail) {
    private val viewModel: ExerciseDetailViewModel by viewModels()
    private var fragmentNavigator: FragmentNavigator? = null

    override fun getViewBinding(view: View): FragmentExerciseDetailBinding {
        return FragmentExerciseDetailBinding.bind(view)
    }

    override fun setupViews() {
        binding.baseToolbar.apply {
            setTitle(getString(R.string.exercise_record))
            showBackButton(true) { findNavController().navigateUp() }
            setSubButton(R.drawable.ic_menu) { view -> showExerciseDetailMenu(view) }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.getExerciseRecord()
    }

    override fun setupObservers() {
        super.setupObservers()

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
    }

    private fun bindExerciseRecordData(record: ExerciseRecord) {
        with(binding) {
            // 이미지 ViewPager 설정
            val currentPictureUrls = record.pictures?.map { it.url }
            if (currentPictureUrls.isNullOrEmpty()) {
                recordImgVp.visibility = View.GONE
            } else {
                recordImgVp.adapter = ImageSliderAdapter(currentPictureUrls)
                recordImgVp.visibility = View.VISIBLE
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

    private fun showExerciseDetailMenu(anchorView: View) {
        val popup = PopupMenu(requireContext(), anchorView)
        popup.menuInflater.inflate(R.menu.menu_exercise_detail, popup.menu)
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.item_edit_record -> {
                    fragmentNavigator?.navigateFromExerciseDetailToExerciseForm(viewModel.recordId!!)
                    true
                }
                R.id.item_delete_record -> {
                    showDeleteConfirmationDialog()
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    private fun showDeleteConfirmationDialog() {
        BaseAlertDialog(
            title = getString(R.string.exercise_record_delete_alert),
            desc = null,
            onConfirmClicked = {
                // TODO: 삭제
            }
        ).show(parentFragmentManager, BaseAlertDialog::class.java.simpleName)
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is FragmentNavigator) {
            fragmentNavigator = context
        } else {
            throw ClassCastException("$context must implement FragmentNavigator")
        }
    }

    override fun onDetach() {
        fragmentNavigator = null
        super.onDetach()
    }

    companion object {
        const val TAG = "ExerciseDetailFragment"
    }
}