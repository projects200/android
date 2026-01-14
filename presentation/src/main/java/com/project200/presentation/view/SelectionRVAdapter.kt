package com.project200.presentation.view

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.core.widget.TextViewCompat
import androidx.recyclerview.widget.RecyclerView
import com.project200.undabang.presentation.R
import com.project200.undabang.presentation.databinding.ItemSelectBinding


class SelectionRVAdapter(
    private val items: List<String>,
    private val selectedItem: String? = null,
    private val onItemClick: (String) -> Unit
) : RecyclerView.Adapter<SelectionRVAdapter.ViewHolder>() {

    inner class ViewHolder(private val binding: ItemSelectBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: String) {
            binding.nameTv.text = item

            // 현재 아이템이 선택된 아이템인지 확인하여 체크 표시 노출
            val isSelected = item == selectedItem
            binding.checkIv.isVisible = isSelected
            TextViewCompat.setTextAppearance(binding.nameTv, if (isSelected) R.style.content_bold else R.style.content_regular)
            binding.root.setOnClickListener {
                onItemClick(item)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemSelectBinding.inflate(
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
}