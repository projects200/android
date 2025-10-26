package com.project200.feature.timer.custom.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.project200.feature.timer.custom.TimerFormListItem
import com.project200.undabang.feature.timer.databinding.ItemCustomTimerCreateFooterBinding
import com.project200.undabang.feature.timer.databinding.ItemCustomTimerCreateStepBinding

class AddedStepRVAdapter(
    private val listener: OnStepItemClickListener,
) : ListAdapter<TimerFormListItem, RecyclerView.ViewHolder>(DiffCallback) { // 제네릭 타입 변경

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is TimerFormListItem.StepItem -> VIEW_TYPE_STEP
            is TimerFormListItem.FooterItem -> VIEW_TYPE_FOOTER
        }
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_STEP -> {
                val binding = ItemCustomTimerCreateStepBinding.inflate(LayoutInflater.from(parent.context), parent, false)
                StepViewHolder(binding, listener)
            }
            VIEW_TYPE_FOOTER -> {
                val binding = ItemCustomTimerCreateFooterBinding.inflate(LayoutInflater.from(parent.context), parent, false)
                FooterViewHolder(binding, listener)
            }
            else -> throw IllegalArgumentException("Invalid view type")
        }
    }

    override fun onBindViewHolder(
        holder: RecyclerView.ViewHolder,
        position: Int,
    ) {
        when (val item = getItem(position)) {
            is TimerFormListItem.StepItem -> (holder as StepViewHolder).bind(item.step)
            is TimerFormListItem.FooterItem -> (holder as FooterViewHolder).bind(item)
        }
    }

    companion object {
        private const val VIEW_TYPE_STEP = 0
        private const val VIEW_TYPE_FOOTER = 1

        private val DiffCallback =
            object : DiffUtil.ItemCallback<TimerFormListItem>() {
                override fun areItemsTheSame(
                    oldItem: TimerFormListItem,
                    newItem: TimerFormListItem,
                ): Boolean {
                    return oldItem.id == newItem.id
                }

                override fun areContentsTheSame(
                    oldItem: TimerFormListItem,
                    newItem: TimerFormListItem,
                ): Boolean {
                    return oldItem == newItem
                }
            }
    }
}
