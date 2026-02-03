package com.project200.undabang.feature.feed.detail

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.project200.domain.model.FeedPicture
import com.project200.undabang.feature.feed.R
import com.project200.undabang.feature.feed.databinding.ItemFeedDetailImageBinding

class FeedDetailImageAdapter(
    private val pictures: List<FeedPicture>
) : RecyclerView.Adapter<FeedDetailImageAdapter.ImageViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageViewHolder {
        val binding = ItemFeedDetailImageBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ImageViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ImageViewHolder, position: Int) {
        holder.bind(pictures[position])
    }

    override fun getItemCount(): Int = pictures.size

    inner class ImageViewHolder(
        private val binding: ItemFeedDetailImageBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(picture: FeedPicture) {
            Glide.with(binding.feedImageIv.context)
                .load(picture.feedPictureUrl)
                .placeholder(R.drawable.ic_feed_image_placeholder)
                .centerCrop()
                .error(R.drawable.ic_feed_image_placeholder)
                .into(binding.feedImageIv)
        }
    }
}
