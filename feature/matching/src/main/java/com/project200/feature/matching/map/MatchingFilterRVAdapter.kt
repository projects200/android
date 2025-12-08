package com.project200.feature.matching.map

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.project200.feature.matching.utils.FilterState
import com.project200.feature.matching.utils.MatchingFilterType
import com.project200.undabang.feature.matching.databinding.ItemFilterBinding

class MatchingFilterRVAdapter(
    private val onClick: (MatchingFilterType) -> Unit
) : ListAdapter<MatchingFilterType, MatchingFilterRVAdapter.ViewHolder>(DiffCallback) {

    // 현재 필터 상태를 저장 (어떤 필터가 활성화되었는지 확인용)
    private var currentFilterState: FilterState = FilterState()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemFilterBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    /**
     * 필터 상태가 변경될 때 호출하여 UI 갱신 (선택 여부 표시)
     */
    fun submitFilterState(newState: FilterState) {
        this.currentFilterState = newState
        notifyDataSetChanged()
    }

    inner class ViewHolder(private val binding: ItemFilterBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: MatchingFilterType) {
            val context = binding.root.context
            binding.filterTitleTv.text = context.getString(item.labelResId)

            // 현재 필터 타입이 활성화되었는지 확인
            if (isFilterSelected(item)) {
                binding.root.isSelected = true
                binding.filterTitleTv.setTextColor(context.getColor(com.project200.undabang.presentation.R.color.white300))
                binding.filterIv.imageTintList= ColorStateList.valueOf(context.getColor(com.project200.undabang.presentation.R.color.white300))
            } else {
                binding.root.isSelected = false
                binding.filterTitleTv.setTextColor(context.getColor(com.project200.undabang.presentation.R.color.black))
                binding.filterIv.imageTintList= ColorStateList.valueOf(context.getColor(com.project200.undabang.presentation.R.color.black))
            }

            binding.root.setOnClickListener { onClick(item) }
        }

        private fun isFilterSelected(type: MatchingFilterType): Boolean {
            return when (type) {
                MatchingFilterType.GENDER -> currentFilterState.gender != null
                MatchingFilterType.AGE -> currentFilterState.ageGroup != null
                MatchingFilterType.DAY -> currentFilterState.days != null
                MatchingFilterType.SKILL -> currentFilterState.skillLevel != null
                MatchingFilterType.SCORE -> currentFilterState.exerciseScore != null
            }
        }
    }

    companion object {
        private val DiffCallback = object : DiffUtil.ItemCallback<MatchingFilterType>() {
            override fun areItemsTheSame(oldItem: MatchingFilterType, newItem: MatchingFilterType): Boolean {
                return oldItem == newItem
            }

            override fun areContentsTheSame(oldItem: MatchingFilterType, newItem: MatchingFilterType): Boolean {
                return oldItem == newItem
            }
        }
    }
}