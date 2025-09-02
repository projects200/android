package com.project200.feature.timer.simple

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.project200.domain.model.SimpleTimer
import com.project200.feature.timer.utils.TimerFormatter.toFormattedTime
import com.project200.undabang.feature.timer.databinding.ItemSimpleTimerAddBinding
import com.project200.undabang.feature.timer.databinding.ItemSimpleTimerBinding

class SimpleTimerRVAdapter(
    var itemHeight: Int = 0,
    private val onItemClick: (SimpleTimer) -> Unit,
    private val onMenuClick: (SimpleTimer, View) -> Unit,
    private val onAddClick: () -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private var items: List<SimpleTimer> = emptyList()
    private var showAddButton: Boolean = false

    inner class SimpleTimerViewHolder(private val binding: ItemSimpleTimerBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(timer: SimpleTimer) {
            binding.root.layoutParams.height = itemHeight
            binding.simpleTimerBtn.apply {
                text = timer.time.toFormattedTime()
                setOnClickListener { onItemClick(timer) }
            }
            binding.menuBtn.setOnClickListener { onMenuClick(timer, it) }
        }
    }

    inner class AddButtonViewHolder(private val binding: ItemSimpleTimerAddBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind() {
            binding.root.layoutParams.height = itemHeight
            binding.simpleTimerAddBtn.setOnClickListener { onAddClick() }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_TIMER -> SimpleTimerViewHolder(ItemSimpleTimerBinding.inflate(inflater, parent, false))
            VIEW_TYPE_ADD -> AddButtonViewHolder(ItemSimpleTimerAddBinding.inflate(inflater, parent, false))
            else -> throw IllegalArgumentException("Invalid view type")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is SimpleTimerViewHolder -> holder.bind(items[position])
            is AddButtonViewHolder -> holder.bind()
        }
    }

    fun submitList(newItems: List<SimpleTimer>) {
        items = newItems
        // '추가' 버튼 표시 여부를 어댑터 내부에서 계산
        showAddButton = items.size < MAX_TIMER_COUNT
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int {
        // 추가 버튼을 보여줘야 한다면, 아이템 개수에 1을 더해서 반환
        return if (showAddButton) items.size + 1 else items.size
    }

    override fun getItemViewType(position: Int) = if (showAddButton && position == items.size) VIEW_TYPE_ADD else VIEW_TYPE_TIMER

    companion object {
        private const val VIEW_TYPE_TIMER = 0
        private const val VIEW_TYPE_ADD = 1
        private const val MAX_TIMER_COUNT = 6
    }
}