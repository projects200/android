package com.project200.feature.exercise.list

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.project200.domain.model.ExerciseListItem
import com.project200.presentation.utils.UiUtils.dpToPx
import com.project200.undabang.feature.exercise.R
import com.project200.undabang.feature.exercise.databinding.ItemExerciseListBinding
import timber.log.Timber
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

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
                // 시간 포맷팅 적용
                exerciseTimeTv.text = "${item.startTime.format(formatter)} ~ ${item.endTime.format(formatter)}"


                val imageUrl = item.imageUrl
                Timber.tag("asdasd").d("${item.imageUrl}")
                if (!imageUrl.isNullOrEmpty()) {
                    Glide.with(exerciseIv)
                        .load(imageUrl[0])
                        .transform(CenterCrop(), RoundedCorners(dpToPx(exerciseIv.context, 8f)))
                        .into(exerciseIv)
                    Timber.tag("asdasd").e("${imageUrl[0]}")
                } else {
                    exerciseIv.setImageResource(R.drawable.ic_empty_img) // 기본 이미지
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