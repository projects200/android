package com.project200.undabang.feature.feed.list

import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.project200.domain.model.FeedPicture
import com.project200.undabang.feature.feed.R
import android.view.LayoutInflater
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.project200.presentation.utils.UiUtils.dpToPx
import com.project200.undabang.feature.feed.databinding.ItemFeedImageBinding

class ImageRVAdapter(private val pictures: List<FeedPicture>) :
    RecyclerView.Adapter<ImageRVAdapter.ImageViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageViewHolder {
        val binding = ItemFeedImageBinding.inflate(
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

    inner class ImageViewHolder(private val binding: ItemFeedImageBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(picture: FeedPicture) {
            Glide.with(binding.feedImageIv.context)
                .load(picture.feedPictureUrl)
                .placeholder(R.drawable.ic_feed_image_placeholder)
                .transform(RoundedCorners(dpToPx(binding.root.context, 12f)))
                .error(R.drawable.ic_feed_image_placeholder)
                .into(binding.feedImageIv)
        }
    }
}
