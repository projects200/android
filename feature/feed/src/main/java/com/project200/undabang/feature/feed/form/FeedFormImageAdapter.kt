package com.project200.undabang.feature.feed.form

import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.project200.undabang.feature.feed.R
import com.project200.undabang.feature.feed.databinding.ItemFeedFormImageBinding


class FeedFormImageAdapter(
    private val onDeleteExistingClick: (Long) -> Unit,
    private val onDeleteNewClick: (Uri) -> Unit
) : RecyclerView.Adapter<FeedFormImageAdapter.ViewHolder>() {

    private val items = mutableListOf<FeedFormImageItem>()

    fun submitList(
        registeredImages: List<RegisteredImage>,
        newImages: List<Uri>
    ) {
        items.clear()
        items.addAll(registeredImages.map { FeedFormImageItem.Existing(it.imageId, it.imageUrl) })
        items.addAll(newImages.map { FeedFormImageItem.New(it) })
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemFeedFormImageBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class ViewHolder(private val binding: ItemFeedFormImageBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: FeedFormImageItem) {
            val imageSource: Any = when (item) {
                is FeedFormImageItem.Existing -> item.imageUrl
                is FeedFormImageItem.New -> item.uri
            }

            Glide.with(binding.formImageIv.context)
                .load(imageSource)
                .placeholder(R.drawable.ic_feed_image_placeholder)
                .fitCenter()
                .into(binding.formImageIv)

            binding.deleteIv.setOnClickListener {
                when (item) {
                    is FeedFormImageItem.Existing -> onDeleteExistingClick(item.imageId)
                    is FeedFormImageItem.New -> onDeleteNewClick(item.uri)
                }
            }
        }
    }
}
