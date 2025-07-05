package com.project200.feature.exercise.main

import android.graphics.Color
import android.view.View
import android.view.WindowManager
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

        dialog?.window?.let { window ->
            val screenWidth = resources.displayMetrics.widthPixels
            val desiredWidth = (screenWidth * 0.85).toInt()

            window.setLayout(desiredWidth, WindowManager.LayoutParams.WRAP_CONTENT)
            window.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
        }
    }

    override fun setupViews() {
        binding.backBtn.setOnClickListener { dismiss() }

        viewModel.policyData.observe(viewLifecycleOwner) { policies ->
            if (policies.isNotEmpty()) {
                displayPolicyData(policies)
            } else {
                binding.pointRangeDescTv.text = getString(R.string.data_error)
                binding.pointWinDescTv.text = getString(R.string.data_error)
                binding.pointReduceDescTv.text = getString(R.string.data_error)
            }
        }
    }

    private fun displayPolicyData(policies: List<ScorePolicy>) {
        val policyMap = policies.associateBy { PolicyType.fromKey(it.policyKey) }

        // 점수 범위
        val minScore = policyMap[PolicyType.EXERCISE_SCORE_MIN_POINTS]?.policyValue ?: 0
        val maxScore = policyMap[PolicyType.EXERCISE_SCORE_MAX_POINTS]?.policyValue ?: 100
        val pointRangeDesc = StringBuilder().apply {
            appendLine(getString(R.string.exercise_policy_min_point_format, minScore))
            appendLine(getString(R.string.exercise_policy_max_point_format, maxScore))
        }.toString().trim()
        binding.pointRangeDescTv.text = pointRangeDesc

        // 점수 획득
        val signupPoints = policyMap[PolicyType.SIGNUP_INITIAL_POINTS]?.policyValue ?: 35
        val pointsPerExercise = policyMap[PolicyType.POINTS_PER_EXERCISE]?.policyValue ?: 3
        val recordValidityValue = policyMap[PolicyType.EXERCISE_RECORD_VALIDITY_PERIOD]?.policyValue ?: 2
        val recordValidityUnit = policyMap[PolicyType.EXERCISE_RECORD_VALIDITY_PERIOD]?.policyUnit?.let {
            when (it.uppercase()) {
                "DAYS" -> "일"
                "HOURS" -> "시간"
                else -> it.lowercase()
            }
        } ?: "일"
        val pointWinDesc = StringBuilder().apply {
            appendLine(getString(R.string.exercise_policy_signup_points_format, signupPoints))
            appendLine(getString(R.string.exercise_policy_points_per_exercise_format, pointsPerExercise))
            appendLine(getString(R.string.exercise_policy_record_validity_format, recordValidityValue, recordValidityUnit))
        }.toString().trim()
        binding.pointWinDescTv.text = pointWinDesc

        // 점수 차감
        val penaltyThresholdValue = policyMap[PolicyType.PENALTY_INACTIVITY_THRESHOLD_DAYS]?.policyValue ?: 7
        val penaltyThresholdUnit = policyMap[PolicyType.PENALTY_INACTIVITY_THRESHOLD_DAYS]?.policyUnit?.let {
            when (it.uppercase()) {
                "DAYS" -> "일"
                "HOURS" -> "시간"
                else -> it.lowercase()
            }
        } ?: "일"
        val penaltyDecrementPoints = policyMap[PolicyType.PENALTY_SCORE_DECREMENT_POINTS]?.policyValue ?: 1
        val pointReduceDesc = StringBuilder().apply {
            appendLine(getString(R.string.exercise_policy_penalty_format, penaltyThresholdValue, penaltyThresholdUnit, penaltyThresholdValue, penaltyThresholdUnit, penaltyDecrementPoints))
        }.toString().trim()
        binding.pointReduceDescTv.text = pointReduceDesc
    }
}