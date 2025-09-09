package com.project200.feature.timer

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.project200.domain.model.CustomTimer
import com.project200.undabang.feature.timer.databinding.ItemCustomTimerBinding

class CustomTimerRVAdapter(
    private val onItemClick: (CustomTimer) -> Unit,
) : RecyclerView.Adapter<CustomTimerRVAdapter.CustomTimerViewHolder>() {
    private var items: List<CustomTimer> = emptyList()

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): CustomTimerViewHolder {
        val binding =
            ItemCustomTimerBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false,
            )
        return CustomTimerViewHolder(binding)
    }

    override fun onBindViewHolder(
        holder: CustomTimerViewHolder,
        position: Int,
    ) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    fun submitList(list: List<CustomTimer>) {
        items = list
        notifyDataSetChanged()
    }

    inner class CustomTimerViewHolder(
        private val binding: ItemCustomTimerBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(timer: CustomTimer) {
            binding.customTimerTitle.text = timer.name
            binding.customTimerTitle.setOnClickListener {
                onItemClick(timer)
            }
        }
    }
}
