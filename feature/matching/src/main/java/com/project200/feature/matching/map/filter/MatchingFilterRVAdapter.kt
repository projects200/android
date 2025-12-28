package com.project200.feature.matching.map.filter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.project200.feature.matching.utils.FilterListItem
import com.project200.feature.matching.utils.FilterState
import com.project200.feature.matching.utils.MatchingFilterType
import com.project200.undabang.feature.matching.databinding.ItemFilterBinding
import com.project200.undabang.feature.matching.databinding.ItemFilterClearBinding

class MatchingFilterRVAdapter(
    private val onFilterClick: (MatchingFilterType) -> Unit,
    private val onClearClick: () -> Unit,
) : ListAdapter<FilterListItem, RecyclerView.ViewHolder>(DiffCallback) {
    // 현재 필터 상태를 저장 (어떤 필터가 활성화되었는지 확인용)
    private var currentFilterState: FilterState = FilterState()

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is FilterListItem.ClearButton -> TYPE_CLEAR
            is FilterListItem.FilterItem -> TYPE_FILTER
        }
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): RecyclerView.ViewHolder {
        return if (viewType == TYPE_CLEAR) {
            val binding = ItemFilterClearBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            ClearViewHolder(binding, onClearClick)
        } else {
            val binding = ItemFilterBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            FilterViewHolder(binding, onFilterClick)
        }
    }

    override fun onBindViewHolder(
        holder: RecyclerView.ViewHolder,
        position: Int,
    ) {
        when (val item = getItem(position)) {
            is FilterListItem.ClearButton -> (holder as ClearViewHolder).bind()
            is FilterListItem.FilterItem -> (holder as FilterViewHolder).bind(item.type, currentFilterState)
        }
    }

    /**
     * 필터 상태가 변경될 때 호출하여 UI 갱신 (선택 여부 표시)
     */
    fun submitFilterState(newState: FilterState) {
        this.currentFilterState = newState
        notifyDataSetChanged()
    }

    fun submitFilterList(filters: List<MatchingFilterType>) {
        val list = mutableListOf<FilterListItem>()
        list.add(FilterListItem.ClearButton) // 맨 앞에 초기화 버튼 추가
        list.addAll(filters.map { FilterListItem.FilterItem(it) })
        submitList(list)
    }

    companion object {
        private const val TYPE_CLEAR = 0
        private const val TYPE_FILTER = 1

        private val DiffCallback =
            object : DiffUtil.ItemCallback<FilterListItem>() {
                override fun areItemsTheSame(
                    oldItem: FilterListItem,
                    newItem: FilterListItem,
                ): Boolean {
                    return oldItem == newItem
                }

                override fun areContentsTheSame(
                    oldItem: FilterListItem,
                    newItem: FilterListItem,
                ): Boolean {
                    return oldItem == newItem
                }
            }
    }
}
