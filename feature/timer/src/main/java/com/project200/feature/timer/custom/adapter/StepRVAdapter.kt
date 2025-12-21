package com.project200.feature.timer.custom.adapter

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat.getColor
import androidx.recyclerview.widget.RecyclerView
import com.project200.domain.model.Step
import com.project200.feature.timer.utils.TimerFormatter.toFormattedTime
import com.project200.undabang.feature.timer.databinding.ItemCustomTimerStepBinding
import com.project200.undabang.presentation.R

class StepRVAdapter(
    private val onItemClicked: (Int) -> Unit,
) : RecyclerView.Adapter<StepRVAdapter.StepViewHolder>() {
    private var items: List<Step> = emptyList()
    private var highlightedPosition = -1

    fun submitList(newItems: List<Step>) {
        items = newItems
        notifyDataSetChanged()
    }

    fun highlightItem(position: Int) {
        val oldPosition = highlightedPosition
        highlightedPosition = position
        if (oldPosition != -1) {
            notifyItemChanged(oldPosition)
        }
        notifyItemChanged(highlightedPosition)
    }

    inner class StepViewHolder(
        private val binding: ItemCustomTimerStepBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(
            timer: Step,
            isHighlighted: Boolean,
        ) {
            binding.stepNameTv.text = timer.name
            binding.stepTimeTv.text = timer.time.toInt().toFormattedTime()

            // 하이라이팅 여부에 따라 뷰 변경
            if (isHighlighted) {
                binding.customTimerTitle.backgroundTintList =
                    ColorStateList.valueOf(
                        getColor(binding.customTimerTitle.context, R.color.main),
                    )
                binding.clockIv.imageTintList = ColorStateList.valueOf(getColor(binding.customTimerTitle.context, R.color.white300))
                binding.stepNameTv.setTextColor(getColor(binding.customTimerTitle.context, R.color.white300))
                binding.stepTimeTv.setTextColor(getColor(binding.customTimerTitle.context, R.color.white300))
            } else {
                binding.customTimerTitle.backgroundTintList =
                    ColorStateList.valueOf(
                        getColor(binding.customTimerTitle.context, R.color.white300),
                    )
                binding.clockIv.imageTintList = ColorStateList.valueOf(getColor(binding.customTimerTitle.context, R.color.black))
                binding.stepNameTv.setTextColor(getColor(binding.customTimerTitle.context, R.color.black))
                binding.stepTimeTv.setTextColor(getColor(binding.customTimerTitle.context, R.color.black))
            }

            binding.customTimerTitle.setOnClickListener {
                onItemClicked(bindingAdapterPosition)
            }
        }
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): StepViewHolder {
        val binding =
            ItemCustomTimerStepBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false,
            )
        return StepViewHolder(binding)
    }

    override fun onBindViewHolder(
        holder: StepViewHolder,
        position: Int,
    ) {
        val step = items[position]
        holder.bind(step, position == highlightedPosition)
    }

    override fun getItemCount() = items.size
}
