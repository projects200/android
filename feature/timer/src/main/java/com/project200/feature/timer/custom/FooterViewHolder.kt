package com.project200.feature.timer.custom

import androidx.recyclerview.widget.RecyclerView
import com.project200.feature.timer.utils.TimerFormatter.toFormattedTime
import com.project200.undabang.feature.timer.databinding.ItemCustomTimerCreateFooterBinding


class FooterViewHolder(
    private val binding: ItemCustomTimerCreateFooterBinding,
    private val listener: OnStepItemClickListener
) : RecyclerView.ViewHolder(binding.root) {
    // 현재 바인딩된 FooterItem
    private var currentFooterItem: TimerFormListItem.FooterItem? = null

    init {
        binding.stepNameEt.setOnFocusChangeListener { _, hasFocus ->
            // 포커스를 잃었을 때만 ViewModel에 변경사항 전달
            if (!hasFocus) {
                listener.onNewStepNameChanged(binding.stepNameEt.text.toString())
            }
        }
        binding.stepTimeTv.setOnClickListener {
            currentFooterItem?.let { footer ->
                listener.onNewStepTimeClick(footer.time)
            }
        }
        binding.addStepIv.setOnClickListener {
            listener.onAddStepClick()
        }
    }

    fun bind(item: TimerFormListItem.FooterItem) {
        if (binding.stepNameEt.text.toString() != item.name) {
            binding.stepNameEt.setText(item.name)
        }
        binding.stepTimeTv.text = item.time.toFormattedTime()
    }
}