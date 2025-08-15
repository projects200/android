package com.project200.feature.timer.simple

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.project200.domain.model.SimpleTimer
import com.project200.feature.timer.utils.TimerFormatter.toFormattedTime
import com.project200.undabang.feature.timer.databinding.ItemSimpleTimerBinding

class SimpleTimerRVAdapter(
    private val items: List<SimpleTimer>,
    private val itemHeight: Int,
    private val onItemClick: (SimpleTimer) -> Unit
) : RecyclerView.Adapter<SimpleTimerRVAdapter.SimpleTimerViewHolder>() {

    inner class SimpleTimerViewHolder(
        private val binding: ItemSimpleTimerBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(timer: SimpleTimer) {
            binding.simpleTimerBtn.apply{
                text = timer.time.toFormattedTime()
                setOnClickListener { onItemClick(timer) }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SimpleTimerViewHolder {
        val binding = ItemSimpleTimerBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )

        // 아이템 높이 설정
        val params = binding.root.layoutParams
        params.height = itemHeight
        binding.root.layoutParams = params

        return SimpleTimerViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SimpleTimerViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount() = items.size
}
