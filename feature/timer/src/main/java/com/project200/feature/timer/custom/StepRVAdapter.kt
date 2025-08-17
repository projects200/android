package com.project200.feature.timer.custom

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.project200.domain.model.Step
import com.project200.undabang.feature.timer.databinding.ItemCustomTimerStepBinding

class StepRVAdapter(
    private val items: List<Step>,
) : RecyclerView.Adapter<StepRVAdapter.StepViewHolder>() {

    inner class StepViewHolder(
        private val binding: ItemCustomTimerStepBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(timer: Step) {

        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StepViewHolder {
        val binding = ItemCustomTimerStepBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return StepViewHolder(binding)
    }

    override fun onBindViewHolder(holder: StepViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount() = items.size
}