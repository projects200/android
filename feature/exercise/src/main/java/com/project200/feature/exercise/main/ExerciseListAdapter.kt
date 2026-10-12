package com.project200.feature.exercise.main

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
import java.time.format.DateTimeFormatter

class ExerciseListAdapter(
    private val onItemClicked: (Long) -> Unit,
) : ListAdapter<ExerciseListItem, ExerciseListAdapter.ExerciseViewHolder>(ExerciseDiffCallback()) {
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): ExerciseViewHolder {
        val binding =
            ItemExerciseListBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false,
            )
        return ExerciseViewHolder(binding, onItemClicked, timeFormatter)
    }

    override fun onBindViewHolder(
        holder: ExerciseViewHolder,
        position: Int,
    ) {
        holder.bind(getItem(position), position == itemCount - 1)
    }

    override fun onCurrentListChanged(
        previousList: List<ExerciseListItem>,
        currentList: List<ExerciseListItem>,
    ) {
        super.onCurrentListChanged(previousList, currentList)
        // Diff에서 내용이 같아도 마지막 행이 바뀌면 기존/새 경계 레이블을 다시 바인딩합니다.
        val previousLastId = previousList.lastOrNull()?.recordId
        if (previousLastId == currentList.lastOrNull()?.recordId) return
        if (previousLastId != null) {
            val previousLastPosition = currentList.indexOfFirst { it.recordId == previousLastId }
            if (previousLastPosition >= 0) {
                notifyItemChanged(previousLastPosition)
            }
        }
        if (currentList.isNotEmpty()) {
            notifyItemChanged(currentList.lastIndex)
        }
    }

    class ExerciseViewHolder(
        private val binding: ItemExerciseListBinding,
        private val onItemClicked: (Long) -> Unit,
        private val formatter: DateTimeFormatter,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(
            item: ExerciseListItem,
            isLastItem: Boolean,
        ) {
            with(binding) {
                exerciseTitleTv.text = item.title
                // 시간 포맷팅 적용
                exerciseTimeTv.text = "${item.startTime.format(formatter)} ~ ${item.endTime.format(formatter)}"
                // 실제 마지막 행의 시간 텍스트를 목록 끝의 접근성 경계로 사용합니다.
                exerciseTimeTv.contentDescription =
                    if (isLastItem) {
                        root.context.getString(R.string.exercise_list_last_item, exerciseTimeTv.text)
                    } else {
                        null
                    }

                val imageUrl = item.imageUrl
                if (!imageUrl.isNullOrEmpty()) {
                    Glide.with(exerciseIv)
                        .load(imageUrl[0])
                        .transform(CenterCrop(), RoundedCorners(dpToPx(exerciseIv.context, 8f)))
                        .into(exerciseIv)
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
            newItem: ExerciseListItem,
        ): Boolean {
            return oldItem.recordId == newItem.recordId
        }

        override fun areContentsTheSame(
            oldItem: ExerciseListItem,
            newItem: ExerciseListItem,
        ): Boolean {
            return oldItem == newItem
        }
    }
}
