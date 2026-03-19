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

    override fun onBindViewHolder(
        holder: RecyclerView.ViewHolder,
        position: Int,
        payloads: MutableList<Any>,
    ) {
        if (payloads.isEmpty()) {
            super.onBindViewHolder(holder, position, payloads)
            return
        }

        val payload = payloads.firstOrNull() as? Set<*> ?: run {
            super.onBindViewHolder(holder, position, payloads)
            return
        }

        if (PAYLOAD_LIKE in payload) {
            val item = getItem(position)
            when (holder) {
                is CommentViewHolder -> holder.bindLike(item)
                is ReplyViewHolder -> holder.bindLike(item)
            }
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

        fun bindLike(item: CommentItem) {
            with(binding) {
                likeCountTv.text = item.likesCount.toString()
                val likeIcon = if (item.isLiked) {
                    R.drawable.ic_like_fill
                } else {
                    R.drawable.ic_like
                }
                likeIv.setImageResource(likeIcon)
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

                if (item.taggedMember != null) {
                    taggedMemberTv.text = "@${item.taggedMember.memberNickname}"
                    taggedMemberTv.visibility = View.VISIBLE
                } else {
                    taggedMemberTv.visibility = View.GONE
                }

                val profileUrl = item.memberThumbnailUrl ?: item.memberProfileImageUrl
                Glide.with(root.context)
                    .load(profileUrl)
                    .placeholder(com.project200.undabang.presentation.R.drawable.ic_profile_default)
                    .error(com.project200.undabang.presentation.R.drawable.ic_profile_default)
                    .circleCrop()
                    .into(profileIv)

                likeIv.setImageResource(if (item.isLiked) {
                    R.drawable.ic_like_fill
                } else {
                    R.drawable.ic_like
                })

                val isMyComment = currentMemberId != null && item.memberId == currentMemberId
                moreIv.visibility = if (isMyComment) View.VISIBLE else View.GONE

                likeIv.setOnClickListener { onLikeClick(item) }
                replyBtn.setOnClickListener { onReplyClick(item) }
                moreIv.setOnClickListener { onMoreClick(item) }
            }
        }

        fun bindLike(item: CommentItem) {
            with(binding) {
                likeCountTv.text = item.likesCount.toString()
                val likeIcon = if (item.isLiked) {
                    R.drawable.ic_like_fill
                } else {
                    R.drawable.ic_like
                }
                likeIv.setImageResource(likeIcon)
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

        override fun getChangePayload(oldItem: CommentItem, newItem: CommentItem): Any? {
            val payloads = mutableSetOf<String>()
            if (oldItem.isLiked != newItem.isLiked || oldItem.likesCount != newItem.likesCount) {
                payloads.add(PAYLOAD_LIKE)
            }
            return payloads.ifEmpty { null }
        }
    }

    companion object {
        private const val VIEW_TYPE_COMMENT = 0
        private const val VIEW_TYPE_REPLY = 1
        const val PAYLOAD_LIKE = "payload_like"
    }
}