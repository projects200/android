package com.project200.undabang.profile.setting

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.project200.domain.model.BlockedMember
import com.project200.undabang.feature.profile.R
import com.project200.undabang.feature.profile.databinding.ItemBlockMemberBinding

class BlockMemberRVAdapter(
    private val onUnblockClicked: (BlockedMember) -> Unit,
) : ListAdapter<BlockedMember, BlockMemberRVAdapter.BlockMemberViewHolder>(BlockMemberDiffCallback()) {
    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): BlockMemberViewHolder {
        val binding = ItemBlockMemberBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return BlockMemberViewHolder(binding, onUnblockClicked)
    }

    override fun onBindViewHolder(
        holder: BlockMemberViewHolder,
        position: Int,
    ) {
        holder.bind(getItem(position))
    }

    class BlockMemberViewHolder(
        private val binding: ItemBlockMemberBinding,
        private val onUnblockClicked: (BlockedMember) -> Unit,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: BlockedMember) {
            binding.nicknameTv.text = item.nickname

            val imgRes = if (!item.thumbnailImageUrl.isNullOrEmpty()) {
                item.thumbnailImageUrl
            } else {
                item.profileImageUrl
            }

            Glide.with(binding.profileImgIv)
                .load(imgRes)
                .placeholder(R.drawable.ic_profile_default)
                .error(R.drawable.ic_profile_default)
                .circleCrop()
                .into(binding.profileImgIv)

            binding.unblockBtn.setOnClickListener {
                onUnblockClicked(item)
            }
        }
    }

    companion object {
        class BlockMemberDiffCallback : DiffUtil.ItemCallback<BlockedMember>() {
            override fun areItemsTheSame(
                oldItem: BlockedMember,
                newItem: BlockedMember,
            ): Boolean {
                return oldItem.memberBlockId == newItem.memberBlockId
            }

            override fun areContentsTheSame(
                oldItem: BlockedMember,
                newItem: BlockedMember,
            ): Boolean {
                return oldItem == newItem
            }
        }
    }
}
