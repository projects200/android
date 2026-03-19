package com.project200.undabang.profile.mypage

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.project200.domain.model.ProfileImage
import com.project200.undabang.feature.profile.R
import com.project200.undabang.feature.profile.databinding.ItemProfileImageBinding

class ProfileImageAdapter : ListAdapter<ProfileImage, ProfileImageAdapter.ImageViewHolder>(ImageDiffCallback) {
    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): ImageViewHolder {
        val binding = ItemProfileImageBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ImageViewHolder(binding)
    }

    override fun onBindViewHolder(
        holder: ImageViewHolder,
        position: Int,
    ) {
        holder.bind(getItem(position))
    }

    class ImageViewHolder(private val binding: ItemProfileImageBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(profileImage: ProfileImage) {
            Glide.with(binding.root.context)
                .load(profileImage.url)
                .error(com.project200.undabang.presentation.R.drawable.ic_profile_default)
                .into(binding.profileImageIv)
        }
    }

    companion object {
        private val ImageDiffCallback =
            object : DiffUtil.ItemCallback<ProfileImage>() {
                override fun areItemsTheSame(
                    oldItem: ProfileImage,
                    newItem: ProfileImage,
                ): Boolean {
                    // 각 아이템의 고유 ID로 비교
                    return oldItem.id == newItem.id
                }

                override fun areContentsTheSame(
                    oldItem: ProfileImage,
                    newItem: ProfileImage,
                ): Boolean {
                    // 아이템의 내용(데이터)이 같은지 비교
                    return oldItem == newItem
                }
            }
    }
}
