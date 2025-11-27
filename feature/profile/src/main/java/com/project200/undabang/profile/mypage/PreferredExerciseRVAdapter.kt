package com.project200.undabang.profile.mypage

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.project200.domain.model.PreferredExercise
import com.project200.undabang.feature.profile.databinding.ItemPreferredExerciseBinding
import com.project200.undabang.profile.utils.PreferredExerciseDayFormatter.formatDaysOfWeek

class PreferredExerciseRVAdapter(
    private var items: List<PreferredExercise> = emptyList()
) : RecyclerView.Adapter<PreferredExerciseRVAdapter.ViewHolder>() {
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemPreferredExerciseBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    fun setItems(newItems: List<PreferredExercise>) {
        items = newItems
        notifyDataSetChanged()
    }

    inner class ViewHolder(private val binding: ItemPreferredExerciseBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(exercise: PreferredExercise) {
            binding.exerciseNameTv.text = exercise.name
            binding.skillTv.text = exercise.skillLevel
            binding.exerciseDaysTv.text = formatDaysOfWeek(exercise.daysOfWeek)
            Glide.with(binding.exerciseIv.context)
                .load(exercise.imageUrl)
                .into(binding.exerciseIv)
        }
    }
}