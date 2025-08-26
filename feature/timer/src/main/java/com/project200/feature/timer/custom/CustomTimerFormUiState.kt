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

enum class ToastMessageType {
    EMPTY_TITLE, // 제목이 비어있음
    NO_STEPS, // 스텝이 하나도 없음
    INVALID_STEP_TIME, // 스텝 시간이 0 이하임
    GET_ERROR, // 조회 에러
    CREATE_ERROR, // 생성 에러
    EDIT_ERROR, // 수정 에러
    UNKNOWN_ERROR
}