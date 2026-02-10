package com.project200.undabang.feature.feed.list

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.project200.domain.model.Feed
import com.project200.undabang.feature.feed.R
import com.project200.undabang.feature.feed.databinding.ItemFeedBinding
import com.project200.presentation.utils.RelativeTimeUtil

class FeedListAdapter(
    private val onItemClick: (Feed) -> Unit,
) : RecyclerView.Adapter<FeedListAdapter.FeedViewHolder>() {

    private val items = mutableListOf<Feed>()
    private var currentMemberId: String? = null

    fun submitList(newItems: List<Feed>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    fun setCurrentMemberId(memberId: String) {
        currentMemberId = memberId
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FeedViewHolder {
        val binding = ItemFeedBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return FeedViewHolder(binding)
    }

    override fun onBindViewHolder(holder: FeedViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class FeedViewHolder(private val binding: ItemFeedBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(feed: Feed) {
            with(binding) {
                nicknameTv.text = feed.nickname
                timeTv.text = RelativeTimeUtil.getRelativeTime(feed.feedCreatedAt)
                
                val hasType = !feed.feedTypeName.isNullOrBlank()
                arrowIv.visibility = if (hasType) View.VISIBLE else View.GONE
                feedTypeTv.visibility = if (hasType) View.VISIBLE else View.GONE
                if (hasType) {
                    feedTypeTv.text = feed.feedTypeName
                }

                Glide.with(root.context)
                    .load(feed.profileUrl)
                    .placeholder(com.project200.undabang.presentation.R.drawable.ic_profile_default)
                    .error(com.project200.undabang.presentation.R.drawable.ic_profile_default)
                    .circleCrop()
                    .into(profileIv)

                contentTv.text = feed.feedContent

                if (feed.feedPictures.isNotEmpty()) {
                    imagesRv.visibility = View.VISIBLE
                    
                    val imageAdapter = ImageRVAdapter(feed.feedPictures)
                    imagesRv.adapter = imageAdapter
                } else {
                    imagesRv.visibility = View.GONE
                }

                likeCountTv.text = feed.feedLikesCount.toString()
                commentCountTv.text = feed.feedCommentsCount.toString()

                root.setOnClickListener { onItemClick(feed) }
            }
        }
    }
}
