package com.project200.feature.exercise.main

import android.content.Context
import android.graphics.Color
import android.view.View
import android.view.WindowManager
import androidx.annotation.StringRes
import androidx.core.graphics.drawable.toDrawable
import androidx.fragment.app.viewModels
import com.project200.domain.model.PolicyType
import com.project200.domain.model.ScorePolicy
import com.project200.presentation.base.BaseDialogFragment
import com.project200.undabang.feature.exercise.R
import com.project200.undabang.feature.exercise.databinding.DialogScorePolicyBinding

class ScorePolicyDialog() : BaseDialogFragment<DialogScorePolicyBinding>(R.layout.dialog_score_policy) {
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

        viewModel.policyData.observe(viewLifecycleOwner) { policies ->
            if (policies.isNotEmpty()) {
                displayPolicyData(policies)
            } else {
                val errorMessage = getString(R.string.data_error)
                binding.pointRangeDescTv.text = errorMessage
                binding.pointWinDescTv.text = errorMessage
                binding.pointReduceDescTv.text = errorMessage
            }
        }
    }

    private fun displayPolicyData(policies: List<ScorePolicy>) {
        val policyMap = policies.associateBy { PolicyType.fromKey(it.policyKey) }

        // 점수 범위
        val minScore = policyMap[PolicyType.EXERCISE_SCORE_MIN_POINTS]?.policyValue ?: 0
        val maxScore = policyMap[PolicyType.EXERCISE_SCORE_MAX_POINTS]?.policyValue ?: 100
        binding.pointRangeDescTv.text = formatPolicyDescription(
            getString(R.string.exercise_policy_min_point_format, minScore),
            getString(R.string.exercise_policy_max_point_format, maxScore)
        )

        // 점수 획득
        val signupPoints = policyMap[PolicyType.SIGNUP_INITIAL_POINTS]?.policyValue ?: 35
        val pointsPerExercise = policyMap[PolicyType.POINTS_PER_EXERCISE]?.policyValue ?: 3
        val recordValidityValue = policyMap[PolicyType.EXERCISE_RECORD_VALIDITY_PERIOD]?.policyValue ?: 2
        val recordValidityUnit = policyMap[PolicyType.EXERCISE_RECORD_VALIDITY_PERIOD]?.policyUnit?.toKoreanUnit() ?: getString(R.string.unit_days)

        binding.pointWinDescTv.text = formatPolicyDescription(
            getString(R.string.exercise_policy_signup_points_format, signupPoints),
            getString(R.string.exercise_policy_points_per_exercise_format, pointsPerExercise),
            getString(R.string.exercise_policy_record_validity_format, recordValidityValue, recordValidityUnit)
        )

        // 점수 차감
        val penaltyThresholdValue = policyMap[PolicyType.PENALTY_INACTIVITY_THRESHOLD_DAYS]?.policyValue ?: 7
        val penaltyThresholdUnit = policyMap[PolicyType.PENALTY_INACTIVITY_THRESHOLD_DAYS]?.policyUnit?.toKoreanUnit() ?: getString(R.string.unit_days)
        val penaltyDecrementPoints = policyMap[PolicyType.PENALTY_SCORE_DECREMENT_POINTS]?.policyValue ?: 1
        binding.pointReduceDescTv.text = formatPolicyDescription(
            getString(R.string.exercise_policy_penalty_format, penaltyThresholdValue, penaltyThresholdUnit, penaltyThresholdValue, penaltyThresholdUnit, penaltyDecrementPoints)
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