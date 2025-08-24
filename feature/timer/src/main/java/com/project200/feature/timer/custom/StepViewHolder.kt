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

    fun bind(step: Step) {
        if (binding.stepNameEt.text.toString() != step.name) {
            binding.stepNameEt.setText(step.name)
        }
        binding.stepTimeTv.apply{
            text = step.time.toFormattedTime()
            setOnClickListener {
                listener.onTimeClick(step.id, step.time)
            }
        }

        binding.stepDeleteIv.setOnClickListener {
            listener.onDeleteClick(step.id)
        }

        binding.stepNameEt.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                listener.onStepNameChanged(step.id, binding.stepNameEt.text.toString())
            }
        }
    }
}