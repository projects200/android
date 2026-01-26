package com.project200.feature.exercise.form

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.project200.domain.model.KakaoPlaceInfo
import com.project200.undabang.feature.exercise.databinding.ItemSearchedPlaceBinding

class SearchedPlaceRVAdapter(
    private val onItemClick: (KakaoPlaceInfo) -> Unit,
) : RecyclerView.Adapter<SearchedPlaceRVAdapter.SearchedPlaceViewHolder>() {
    private var items: List<KakaoPlaceInfo> = emptyList()

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): SearchedPlaceViewHolder {
        val binding = ItemSearchedPlaceBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return SearchedPlaceViewHolder(binding, onItemClick)
    }

    override fun onBindViewHolder(
        holder: SearchedPlaceViewHolder,
        position: Int,
    ) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    fun submitList(newItems: List<KakaoPlaceInfo>) {
        items = newItems
        notifyDataSetChanged()
    }

    class SearchedPlaceViewHolder(
        private val binding: ItemSearchedPlaceBinding,
        private val onItemClick: (KakaoPlaceInfo) -> Unit,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: KakaoPlaceInfo) {
            binding.placeNameTv.text = item.placeName
            binding.placeAddressTv.text = item.address
            itemView.setOnClickListener { onItemClick(item) }
        }
    }
}
