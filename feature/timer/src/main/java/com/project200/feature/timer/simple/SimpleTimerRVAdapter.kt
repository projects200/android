package com.project200.feature.timer.simple

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.project200.domain.model.SimpleTimer
import com.project200.feature.timer.utils.TimerFormatter.toFormattedTime
import com.project200.undabang.feature.timer.databinding.ItemSimpleTimerBinding

class SimpleTimerRVAdapter(
    var items: List<SimpleTimer> = emptyList(),
    var itemHeight: Int = 0,
    private val onItemClick: (SimpleTimer) -> Unit,
    private val onMenuClick: (SimpleTimer, View) -> Unit
) : RecyclerView.Adapter<SimpleTimerRVAdapter.SimpleTimerViewHolder>() {

    inner class SimpleTimerViewHolder(
        private val binding: ItemSimpleTimerBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(timer: SimpleTimer) {
            binding.root.layoutParams.height = itemHeight

            binding.simpleTimerBtn.apply{
                text = timer.time.toFormattedTime()
                setOnClickListener { onItemClick(timer) }
            }

            binding.menuBtn.setOnClickListener {
                onMenuClick(timer, binding.menuBtn)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SimpleTimerViewHolder {
        val binding = ItemSimpleTimerBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return SimpleTimerViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SimpleTimerViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount() = items.size
}