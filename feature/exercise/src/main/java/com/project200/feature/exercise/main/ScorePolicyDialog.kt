package com.project200.feature.exercise.main

import android.content.Context
import android.graphics.Color
import android.view.View
import android.view.WindowManager
import androidx.core.graphics.drawable.toDrawable
import androidx.fragment.app.viewModels
import com.project200.domain.model.Policy
import com.project200.presentation.base.BaseDialogFragment
import com.project200.undabang.feature.exercise.R
import com.project200.undabang.feature.exercise.databinding.DialogScorePolicyBinding

class ScorePolicyDialog : BaseDialogFragment<DialogScorePolicyBinding>(R.layout.dialog_score_policy) {
    private val viewModel: ExerciseMainViewModel by viewModels(ownerProducer = { requireParentFragment() })

    override fun getViewBinding(view: View): DialogScorePolicyBinding {
        return DialogScorePolicyBinding.bind(view)
    }

    override fun onStart() {
        super.onStart()
        setupDialogWindow(requireContext())
    }

    override fun setupViews() {
        binding.backBtn.setOnClickListener { dismiss() }

        viewModel.scorePolicy.observe(viewLifecycleOwner) { policyGroup ->
            policyGroup?.let { displayPolicyData(it.policies) }
        }
    }

    private fun displayPolicyData(policies: List<Policy>) {
        val policyMap = policies.associateBy { it.policyKey }

        // 점수 범위
        val minScore = policyMap["EXERCISE_SCORE_MIN_POINTS"]?.policyValue?.toIntOrNull()
        val maxScore = policyMap["EXERCISE_SCORE_MAX_POINTS"]?.policyValue?.toIntOrNull()
        binding.pointRangeDescTv.text =
            formatPolicyDescription(
                getString(R.string.exercise_policy_min_point_format, minScore),
                getString(R.string.exercise_policy_max_point_format, maxScore),
            )

        // 점수 획득
        val signupPoints = policyMap["SIGNUP_INITIAL_POINTS"]?.policyValue?.toIntOrNull()
        val pointsPerExercise = policyMap["POINTS_PER_EXERCISE"]?.policyValue?.toIntOrNull()
        val recordValidityValue = policyMap["EXERCISE_RECORD_VALIDITY_PERIOD"]?.policyValue?.toIntOrNull()
        val recordValidityUnit = policyMap["EXERCISE_RECORD_VALIDITY_PERIOD"]?.policyUnit?.toKoreanUnit()

        binding.pointWinDescTv.text =
            formatPolicyDescription(
                getString(R.string.exercise_policy_signup_points_format, signupPoints),
                getString(R.string.exercise_policy_points_per_exercise_format, pointsPerExercise),
                getString(R.string.exercise_policy_record_validity_format, recordValidityValue, recordValidityUnit),
            )

        // 점수 차감
        val penaltyThresholdValue = policyMap["PENALTY_INACTIVITY_THRESHOLD_DAYS"]?.policyValue?.toIntOrNull()
        val penaltyThresholdUnit = policyMap["PENALTY_INACTIVITY_THRESHOLD_DAYS"]?.policyUnit?.toKoreanUnit()
        val penaltyDecrementPoints = policyMap["PENALTY_SCORE_DECREMENT_POINTS"]?.policyValue?.toIntOrNull()
        binding.pointReduceDescTv.text =
            formatPolicyDescription(
                getString(
                    R.string.exercise_policy_penalty_format,
                    penaltyThresholdValue,
                    penaltyThresholdUnit,
                    penaltyThresholdValue,
                    penaltyThresholdUnit,
                    penaltyDecrementPoints,
                ),
            )
    }

    private fun setupDialogWindow(context: Context) {
        dialog?.window?.let { window ->
            val screenWidth = context.resources.displayMetrics.widthPixels
            val desiredWidth = (screenWidth * 0.85).toInt()
            window.setLayout(desiredWidth, WindowManager.LayoutParams.WRAP_CONTENT)
            window.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
        }
    }

    private fun String.toKoreanUnit(): String {
        return when (this.uppercase()) {
            "DAYS" -> getString(R.string.unit_days)
            "HOURS" -> getString(R.string.unit_hours)
            else -> this.lowercase()
        }
    }

    private fun formatPolicyDescription(vararg lines: String): String {
        return StringBuilder().apply {
            lines.forEach { appendLine(it) }
        }.toString().trim()
    }
}
