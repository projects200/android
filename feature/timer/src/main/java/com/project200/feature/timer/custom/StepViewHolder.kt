package com.project200.feature.timer.custom

import android.view.View
import androidx.recyclerview.widget.RecyclerView
import com.project200.domain.model.Step
import com.project200.feature.timer.utils.TimerFormatter.toFormattedTime
import com.project200.undabang.feature.timer.databinding.ItemCustomTimerCreateStepBinding

class StepViewHolder(
    private val binding: ItemCustomTimerCreateStepBinding,
    private val listener: OnStepItemClickListener
) : RecyclerView.ViewHolder(binding.root) {
    private var currentStep: Step? = null

    init {
        binding.stepDeleteIv.setOnClickListener {
            currentStep?.id?.let { id -> listener.onDeleteClick(id) }
        }
        binding.stepTimeTv.setOnClickListener {
            currentStep?.id?.let { id -> listener.onTimeClick(id, currentStep!!.time) }
        }
        binding.stepNameEt.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                currentStep?.id?.let { id ->
                    listener.onStepNameChanged(id, binding.stepNameEt.text.toString())
                }
            }
        }
    }
    fun bind(step: Step) {
        this.currentStep = step // 현재 스텝 정보 업데이트

        if (binding.stepNameEt.text.toString() != step.name) {
            binding.stepNameEt.setText(step.name)
        }

        binding.stepTimeTv.text = step.time.toFormattedTime()
    }
}