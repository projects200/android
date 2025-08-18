package com.project200.feature.timer.custom

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.project200.domain.model.Step
import com.project200.feature.timer.utils.TimerFormatter.toFormattedTime
import com.project200.undabang.feature.timer.databinding.ItemCustomTimerCreateFooterBinding
import com.project200.undabang.feature.timer.databinding.ItemCustomTimerCreateStepBinding
import timber.log.Timber

class AddedStepRVAdapter(
    private val listener: OnStepItemClickListener
) : ListAdapter<TimerFormListItem, RecyclerView.ViewHolder>(DiffCallback) { // 제네릭 타입 변경

    interface OnStepItemClickListener {
        fun onDeleteClick(id: Long)
        fun onTimeClick(id: Long, time: Int)
        fun onStepNameChanged(id: Long, name: String)
        // 입력 필드용 메서드
        fun onNewStepNameChanged(name: String)
        fun onNewStepTimeClick(time: Int)
        fun onAddStepClick()
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is TimerFormListItem.StepItem -> VIEW_TYPE_STEP
            is TimerFormListItem.FooterItem -> VIEW_TYPE_FOOTER
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
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

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is TimerFormListItem.StepItem -> (holder as StepViewHolder).bind(item.step)
            is TimerFormListItem.FooterItem -> (holder as FooterViewHolder).bind(item)
        }
    }

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
                    Timber.tag("ddd").d("Step name changed: ${binding.stepNameEt.text}")
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

    companion object {
        private const val VIEW_TYPE_STEP = 0
        private const val VIEW_TYPE_FOOTER = 1

        private val DiffCallback = object : DiffUtil.ItemCallback<TimerFormListItem>() {
            override fun areItemsTheSame(oldItem: TimerFormListItem, newItem: TimerFormListItem): Boolean {
                return oldItem.id == newItem.id
            }

            override fun areContentsTheSame(oldItem: TimerFormListItem, newItem: TimerFormListItem): Boolean {
                return oldItem == newItem
            }
        }
    }
}