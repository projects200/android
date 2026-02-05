package com.project200.feature.matching.map.filter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.core.widget.TextViewCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.project200.feature.matching.utils.FilterOptionUiModel
import com.project200.undabang.feature.matching.databinding.ItemFilterOptionBinding
import com.project200.undabang.presentation.R

class FilterOptionRVAdapter(
    private val onClick: (Any?) -> Unit,
) : ListAdapter<FilterOptionUiModel, FilterOptionRVAdapter.ViewHolder>(DiffCallback) {
    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): ViewHolder {
        val binding = ItemFilterOptionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(
        holder: ViewHolder,
        position: Int,
    ) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemFilterOptionBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: FilterOptionUiModel) {
            val context = binding.root.context
            binding.optionTv.text = item.labelText ?: item.labelResId?.let { context.getString(it) } ?: ""
            binding.checkIv.isVisible = item.isSelected
            TextViewCompat.setTextAppearance(binding.optionTv, if (item.isSelected) R.style.content_bold else R.style.content_regular)
            binding.filterOptionLl.setOnClickListener { onClick(item.originalData) }
        }
    }

    companion object {
        private val DiffCallback =
            object : DiffUtil.ItemCallback<FilterOptionUiModel>() {
                override fun areItemsTheSame(
                    oldItem: FilterOptionUiModel,
                    newItem: FilterOptionUiModel,
                ): Boolean {
                    return oldItem.labelResId == newItem.labelResId && oldItem.labelText == newItem.labelText
                }

                override fun areContentsTheSame(
                    oldItem: FilterOptionUiModel,
                    newItem: FilterOptionUiModel,
                ): Boolean {
                    return oldItem == newItem
                }
            }
    }
}
