package com.project200.feature.exercise.form


import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.project200.presentation.utils.UiUtils.dpToPx
import com.project200.undabang.feature.exercise.databinding.ItemExerciseAddImageBinding
import com.project200.undabang.feature.exercise.databinding.ItemExerciseImageBinding

class ExerciseImageAdapter(
    private var itemSize: Int,
    private val onAddItemClick: () -> Unit,
    private val onDeleteItemClick: (ExerciseImageListItem) -> Unit
) : ListAdapter<ExerciseImageListItem, RecyclerView.ViewHolder>(ExerciseImageDiffCallback()) {

    class ExerciseImageDiffCallback : DiffUtil.ItemCallback<ExerciseImageListItem>() {
        override fun areItemsTheSame(oldItem: ExerciseImageListItem, newItem: ExerciseImageListItem): Boolean {
            return oldItem.key == newItem.key
        }

        override fun areContentsTheSame(oldItem: ExerciseImageListItem, newItem: ExerciseImageListItem): Boolean {
            // 데이터 클래스의 동등성 비교
            return oldItem == newItem
        }
    }

    companion object {
        private const val VIEW_TYPE_ADD = 1
        private const val VIEW_TYPE_IMAGE = 2
        private const val ROUND_CORNER = 8f
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is ExerciseImageListItem.AddButtonItem -> VIEW_TYPE_ADD
            is ExerciseImageListItem.NewImageItem,
            is ExerciseImageListItem.ExistingImageItem -> VIEW_TYPE_IMAGE
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val holder = when (viewType) {
            VIEW_TYPE_ADD -> {
                val binding = ItemExerciseAddImageBinding.inflate(inflater, parent, false)
                AddImageViewHolder(binding, onAddItemClick)
            }
            VIEW_TYPE_IMAGE -> {
                val binding = ItemExerciseImageBinding.inflate(inflater, parent, false)
                ExerciseImageViewHolder(binding, onDeleteItemClick)
            }
            else -> throw IllegalArgumentException("Invalid view type: $viewType")
        }

        // 아이템 뷰 크기 설정
        val layoutParams = holder.itemView.layoutParams
        layoutParams.width = itemSize
        layoutParams.height = itemSize
        holder.itemView.layoutParams = layoutParams

        return holder
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = getItem(position)
        when (holder) {
            is AddImageViewHolder -> holder.bind()
            is ExerciseImageViewHolder -> holder.bind(item)
        }
    }

    inner class AddImageViewHolder(
        private val binding: ItemExerciseAddImageBinding,
        private val onAddItemClick: () -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind() {
            binding.root.setOnClickListener {
                onAddItemClick()
            }
        }
    }

    inner class ExerciseImageViewHolder(
        private val binding: ItemExerciseImageBinding,
        private val onDeleteItemClick: (ExerciseImageListItem) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: ExerciseImageListItem) {
            val imageSource: Any? = when (item) {
                is ExerciseImageListItem.NewImageItem -> item.uri
                is ExerciseImageListItem.ExistingImageItem -> item.url
                else -> null
            }

            imageSource?.let {
                Glide.with(binding.imageIv.context)
                    .load(it)
                    .transform(CenterCrop(), RoundedCorners(dpToPx(binding.root.context, ROUND_CORNER)))
                    .into(binding.imageIv)
            }

            binding.deleteBtn.setOnClickListener {
                val currentPosition = bindingAdapterPosition
                if (currentPosition != RecyclerView.NO_POSITION) {
                    onDeleteItemClick(getItem(currentPosition))
                }
            }
        }
    }
}