package com.project200.undabang.profile.mypage

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.project200.domain.model.PreferredExercise
import com.project200.undabang.feature.profile.databinding.ItemExerciseTypeBinding
import com.project200.undabang.profile.utils.PreferredExerciseUiModel


class PreferredExerciseTypeRVAdapter(
    private val onItemClicked: (PreferredExercise) -> Unit,
    private val onLimitReached: () -> Unit
) : ListAdapter<PreferredExerciseUiModel, PreferredExerciseTypeRVAdapter.ViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemExerciseTypeBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemExerciseTypeBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(uiModel: PreferredExerciseUiModel) {

            binding.exerciseTypeLl.setOnClickListener {
                // 이미 5개가 선택되어 있다면 개수 제한 콜백 호출
                if (!uiModel.isSelected && currentList.count { it.isSelected } >= MAX_SELECTION) {
                    onLimitReached()
                } else {
                    onItemClicked(uiModel.exercise)
                }
            }

            binding.exerciseNameTv.text = uiModel.exercise.name
            binding.checkIv.isVisible = uiModel.isSelected
            binding.exerciseTypeLl.isSelected = uiModel.isSelected

            if (uiModel.isSelected) {
                binding.exerciseNameTv.setTextColor(ContextCompat.getColor(binding.root.context, com.project200.undabang.presentation.R.color.white300))
                binding.root.backgroundTintList = ColorStateList.valueOf(
                    ContextCompat.getColor(binding.root.context, com.project200.undabang.presentation.R.color.main)
                )
            } else {
                binding.exerciseNameTv.setTextColor(ContextCompat.getColor(binding.root.context, com.project200.undabang.presentation.R.color.black))
                binding.root.backgroundTintList = ColorStateList.valueOf(
                    ContextCompat.getColor(binding.root.context, com.project200.undabang.presentation.R.color.gray300)
                )
            }
        }
    }

    companion object {
        private val DiffCallback = object : DiffUtil.ItemCallback<PreferredExerciseUiModel>() {
            override fun areItemsTheSame(oldItem: PreferredExerciseUiModel, newItem: PreferredExerciseUiModel): Boolean {
                return oldItem.exercise.exerciseTypeId == newItem.exercise.exerciseTypeId
            }

            override fun areContentsTheSame(oldItem: PreferredExerciseUiModel, newItem: PreferredExerciseUiModel): Boolean {
                return oldItem == newItem
            }
        }
        private const val MAX_SELECTION = 5
    }
}