package com.project200.feature.exercise.list

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.project200.undabang.feature.exercise.R
import com.project200.undabang.feature.exercise.databinding.ItemExerciseListBinding
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

// 임시 모델
data class ExerciseListItem(
    val recordId: Long,
    val title: String,
    val type: String,
    val startTime: LocalDateTime,
    val endTime: LocalDateTime,
    val imageUrl: String?
)

class ExerciseListAdapter(
    private val onItemClicked: (Long) -> Unit
) : ListAdapter<ExerciseListItem, ExerciseListAdapter.ExerciseViewHolder>(ExerciseDiffCallback()) {

    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ExerciseViewHolder {
        val binding = ItemExerciseListBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ExerciseViewHolder(binding, onItemClicked, timeFormatter)
    }

    override fun onBindViewHolder(holder: ExerciseViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ExerciseViewHolder(
        private val binding: ItemExerciseListBinding,
        private val onItemClicked: (Long) -> Unit,
        private val formatter: DateTimeFormatter
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: ExerciseListItem) {
            with(binding) {
                exerciseTitleTv.text = item.title
                exerciseTypeTv.text = item.type
                // 시간 포맷팅 적용
                exerciseTimeTv.text = "${item.startTime.format(formatter)} ~ ${item.endTime.format(formatter)}"


                if (item.imageUrl != null) {
                    Glide.with(exerciseIv)
                        .load(item.imageUrl)
                        .into(exerciseIv)
                } else {
                    exerciseIv.setImageResource(R.drawable.ic_record_edit) // 기본 이미지
                }

                exerciseListCl.setOnClickListener {
                    onItemClicked(item.recordId)
                }
            }
        }
    }

    // DiffUtil 콜백 (효율적인 업데이트를 위함)
    class ExerciseDiffCallback : DiffUtil.ItemCallback<ExerciseListItem>() {
        override fun areItemsTheSame(
            oldItem: ExerciseListItem,
            newItem: ExerciseListItem
        ): Boolean {
            return oldItem.recordId == newItem.recordId
        }

        override fun areContentsTheSame(
            oldItem: ExerciseListItem,
            newItem: ExerciseListItem
        ): Boolean {
            return oldItem == newItem
        }
    }
}