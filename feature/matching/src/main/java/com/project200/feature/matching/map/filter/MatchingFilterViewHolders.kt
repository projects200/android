package com.project200.feature.matching.map.filter

import android.content.res.ColorStateList
import androidx.recyclerview.widget.RecyclerView
import com.project200.feature.matching.utils.FilterState
import com.project200.feature.matching.utils.MatchingFilterType
import com.project200.presentation.utils.labelResId
import com.project200.undabang.feature.matching.databinding.ItemFilterBinding
import com.project200.undabang.feature.matching.databinding.ItemFilterClearBinding
import com.project200.undabang.presentation.R

/**
 * 초기화 뷰홀더
 */
class ClearViewHolder(
    private val binding: ItemFilterClearBinding,
    private val onClearClick: () -> Unit
) : RecyclerView.ViewHolder(binding.root) {

    fun bind() {
        binding.root.setOnClickListener { onClearClick() }
    }
}

/**
 * 필터 항목 뷰홀더
 */
class FilterViewHolder(
    private val binding: ItemFilterBinding,
    private val onFilterClick: (MatchingFilterType) -> Unit
) : RecyclerView.ViewHolder(binding.root) {
    fun bind(item: MatchingFilterType, currentState: FilterState) {
        val context = binding.root.context

        // 라벨 설정
        val labelResId = if (item.isMultiSelect) {
            item.labelResId
        } else {
            getSelectedOptionLabelResId(item, currentState) ?: item.labelResId
        }
        binding.filterTitleTv.text = context.getString(labelResId)

        // 선택 상태 디자인
        val isSelected = isFilterSelected(item, currentState)
        binding.root.isSelected = isSelected


        binding.filterTitleTv.setTextColor(
            context.getColor(if (isSelected) R.color.white300 else R.color.black)
        )
        binding.filterIv.imageTintList = ColorStateList.valueOf(
            context.getColor(if (isSelected) R.color.white300 else R.color.black)
        )

        binding.root.setOnClickListener { onFilterClick(item) }
    }

    private fun isFilterSelected(type: MatchingFilterType, state: FilterState): Boolean {
        return when (type) {
            MatchingFilterType.GENDER -> state.gender != null
            MatchingFilterType.AGE -> state.ageGroup != null
            MatchingFilterType.DAY -> state.days.isNotEmpty()
            MatchingFilterType.SKILL -> state.skillLevel != null
            MatchingFilterType.SCORE -> state.exerciseScore != null
        }
    }

    // 선택된 옵션의 라벨 리소스 ID를 반환
    private fun getSelectedOptionLabelResId(type: MatchingFilterType, state: FilterState): Int? {
        return when (type) {
            MatchingFilterType.GENDER -> state.gender?.labelResId
            MatchingFilterType.AGE -> state.ageGroup?.labelResId
            MatchingFilterType.SKILL -> state.skillLevel?.labelResId
            MatchingFilterType.SCORE -> state.exerciseScore?.labelResId
            else -> null
        }
    }
}