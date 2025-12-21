package com.project200.feature.timer.custom.adapter

import androidx.recyclerview.widget.RecyclerView
import com.project200.feature.timer.custom.TimerFormListItem
import com.project200.feature.timer.utils.TimerFormatter.toFormattedTime
import com.project200.undabang.feature.timer.databinding.ItemCustomTimerCreateFooterBinding

class FooterViewHolder(
    private val binding: ItemCustomTimerCreateFooterBinding,
    private val listener: OnStepItemClickListener,
) : RecyclerView.ViewHolder(binding.root) {
    fun bind(item: TimerFormListItem.FooterItem) {
        if (binding.stepNameEt.text.toString() != item.name) {
            binding.stepNameEt.setText(item.name)
        }
        binding.stepTimeTv.apply {
            text = item.time.toFormattedTime()
            setOnClickListener {
                listener.onNewStepTimeClick(item.time)
            }
        }

        binding.stepNameEt.setOnFocusChangeListener { _, hasFocus ->
            // 포커스를 잃었을 때만 ViewModel에 변경사항 전달
            if (!hasFocus) {
                listener.onNewStepNameChanged(binding.stepNameEt.text.toString())
            }
        }

        binding.addStepIv.setOnClickListener {
            listener.onAddStepClick()
        }
    }
}
