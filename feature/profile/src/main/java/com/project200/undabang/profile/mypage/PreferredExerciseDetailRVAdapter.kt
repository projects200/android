package com.project200.undabang.profile.mypage

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.project200.undabang.feature.profile.databinding.ItemPreferredExerciseDetailBinding
import com.project200.undabang.profile.utils.PreferredExerciseUiModel
import com.project200.undabang.profile.utils.SkillLevel

class PreferredExerciseDetailRVAdapter(
    private val viewModel: PreferredExerciseViewModel
) : ListAdapter<PreferredExerciseUiModel, PreferredExerciseDetailRVAdapter.DetailViewHolder>(ExerciseDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DetailViewHolder {
        val binding = ItemPreferredExerciseDetailBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return DetailViewHolder(binding)
    }

    override fun onBindViewHolder(holder: DetailViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class DetailViewHolder(private val binding: ItemPreferredExerciseDetailBinding) : RecyclerView.ViewHolder(binding.root) {
        private val dayButtons: List<TextView> =
            with(binding) { listOf(btnMon, btnTue, btnWed, btnThu, btnFri, btnSat, btnSun) }
        private val skillButtons: Map<SkillLevel, TextView> = mapOf(
            SkillLevel.NOVICE to binding.btnNovice,
            SkillLevel.BEGINNER to binding.btnBeginner,
            SkillLevel.INTERMEDIATE to binding.btnIntermediate,
            SkillLevel.ADVANCED to binding.btnAdvanced,
            SkillLevel.EXPERT to binding.btnExpert,
            SkillLevel.PROFESSIONAL to binding.btnProfessional
        )

        fun bind(uiModel: PreferredExerciseUiModel) {
            binding.exerciseTypeTv.text = uiModel.exercise.name
            Glide.with(itemView.context)
                .load(uiModel.exercise.imageUrl)
                .into(binding.exerciseTypeIv)

            updateUi(uiModel)

            dayButtons.forEachIndexed { index, button ->
                button.setOnClickListener {
                    viewModel.updateDaySelection(uiModel.exercise.exerciseTypeId, index)
                }
            }

            skillButtons.forEach { (skill, button) ->
                button.setOnClickListener {
                    viewModel.updateSkillLevel(uiModel.exercise.exerciseTypeId, skill)
                }
            }
        }

        private fun updateUi(uiModel: PreferredExerciseUiModel) {
            binding.exerciseInfoTv.text = uiModel.getExerciseInfo()

            // 요일 버튼 상태 업데이트
            dayButtons.forEachIndexed { index, button ->
                button.isSelected = uiModel.selectedDays[index]
            }

            // 숙련도 버튼 상태 업데이트
            skillButtons.forEach { (skill, button) ->
                button.isSelected = (uiModel.skillLevel == skill)
            }
        }
    }

    class ExerciseDiffCallback : DiffUtil.ItemCallback<PreferredExerciseUiModel>() {
        override fun areItemsTheSame(oldItem: PreferredExerciseUiModel, newItem: PreferredExerciseUiModel): Boolean {
            return oldItem.exercise.exerciseTypeId == newItem.exercise.exerciseTypeId
        }

        override fun areContentsTheSame(oldItem: PreferredExerciseUiModel, newItem: PreferredExerciseUiModel): Boolean {
            return oldItem == newItem
        }
    }
}