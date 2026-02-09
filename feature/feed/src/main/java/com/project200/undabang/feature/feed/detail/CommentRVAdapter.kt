package com.project200.undabang.feature.feed.detail

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.project200.presentation.utils.RelativeTimeUtil
import com.project200.undabang.feature.feed.R
import com.project200.undabang.feature.feed.databinding.ItemCommentBinding
import com.project200.undabang.feature.feed.databinding.ItemReplyBinding


class CommentRVAdapter(
    private val currentMemberId: String?,
    private val onLikeClick: (CommentItem) -> Unit,
    private val onReplyClick: (CommentItem) -> Unit,
    private val onMoreClick: (CommentItem) -> Unit,
) : ListAdapter<CommentItem, RecyclerView.ViewHolder>(CommentDiffCallback()) {

    companion object {
        private const val VIEW_TYPE_COMMENT = 0
        private const val VIEW_TYPE_REPLY = 1
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is CommentItem.CommentData -> VIEW_TYPE_COMMENT
            is CommentItem.ReplyData -> VIEW_TYPE_REPLY
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_COMMENT -> {
                val binding = ItemCommentBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
                CommentViewHolder(binding)
            }
            VIEW_TYPE_REPLY -> {
                val binding = ItemReplyBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
                ReplyViewHolder(binding)
            }
            else -> throw IllegalArgumentException("Unknown view type: $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is CommentItem.CommentData -> (holder as CommentViewHolder).bind(item)
            is CommentItem.ReplyData -> (holder as ReplyViewHolder).bind(item)
        }
    }

    inner class CommentViewHolder(
        private val binding: ItemCommentBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: CommentItem.CommentData) {
            with(binding) {
                nicknameTv.text = item.memberNickname
                timeTv.text = RelativeTimeUtil.getRelativeTime(item.createdAt)
                timeTv.visibility = View.VISIBLE
                contentTv.text = item.content
                likeCountTv.text = item.likesCount.toString()

                val profileUrl = item.memberThumbnailUrl ?: item.memberProfileImageUrl
                Glide.with(root.context)
                    .load(profileUrl)
                    .placeholder(com.project200.undabang.presentation.R.drawable.ic_profile_default)
                    .error(com.project200.undabang.presentation.R.drawable.ic_profile_default)
                    .circleCrop()
                    .into(profileIv)

                val likeIcon = if (item.isLiked) {
                    R.drawable.ic_like_fill
                } else {
                    R.drawable.ic_like
                }
                likeIv.setImageResource(likeIcon)

                val isMyComment = currentMemberId != null && item.memberId == currentMemberId
                moreIv.visibility = if (isMyComment) View.VISIBLE else View.GONE

                likeIv.setOnClickListener { onLikeClick(item) }
                replyBtn.setOnClickListener { onReplyClick(item) }
                moreIv.setOnClickListener { onMoreClick(item) }
            }
        }
    }

    inner class ReplyViewHolder(
        private val binding: ItemReplyBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: CommentItem.ReplyData) {
            with(binding) {
                nicknameTv.text = item.memberNickname
                timeTv.text = RelativeTimeUtil.getRelativeTime(item.createdAt)
                timeTv.visibility = View.VISIBLE
                contentTv.text = item.content
                likeCountTv.text = item.likesCount.toString()

                val profileUrl = item.memberThumbnailUrl ?: item.memberProfileImageUrl
                Glide.with(root.context)
                    .load(profileUrl)
                    .placeholder(com.project200.undabang.presentation.R.drawable.ic_profile_default)
                    .error(com.project200.undabang.presentation.R.drawable.ic_profile_default)
                    .circleCrop()
                    .into(profileIv)

                val likeIcon = if (item.isLiked) {
                    R.drawable.ic_like_fill
                } else {
                    R.drawable.ic_like
                }
                likeIv.setImageResource(likeIcon)

                val isMyComment = currentMemberId != null && item.memberId == currentMemberId
                moreIv.visibility = if (isMyComment) View.VISIBLE else View.GONE

                likeIv.setOnClickListener { onLikeClick(item) }
                replyBtn.setOnClickListener { onReplyClick(item) }
                moreIv.setOnClickListener { onMoreClick(item) }
            }
        }
    }

    class CommentDiffCallback : DiffUtil.ItemCallback<CommentItem>() {
        override fun areItemsTheSame(oldItem: CommentItem, newItem: CommentItem): Boolean {
            return oldItem.commentId == newItem.commentId &&
                oldItem::class == newItem::class
        }

        override fun areContentsTheSame(oldItem: CommentItem, newItem: CommentItem): Boolean {
            return oldItem == newItem
        }
    }
}
