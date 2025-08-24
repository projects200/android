package com.project200.feature.timer.custom

import com.project200.domain.model.Step

sealed interface TimerFormListItem {
    val id: Long

    data class StepItem(val step: Step) : TimerFormListItem {
        override val id: Long = step.id
    }

    data class FooterItem(
        val name: String,
        val time: Int
    ) : TimerFormListItem {
        override val id: Long = 0L
    }
}

data class CustomTimerFormUiState(
    val title: String = "",
    val listItems: List<TimerFormListItem> = emptyList()
)