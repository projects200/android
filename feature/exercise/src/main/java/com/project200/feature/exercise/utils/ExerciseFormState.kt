package com.project200.feature.exercise.utils

sealed class ScoreGuidanceState {
    data object Hidden : ScoreGuidanceState() // 기본 상태 (경고 숨김, 버튼 텍스트 "기록 완료")

    data class Warning(val messageId: Int) : ScoreGuidanceState() // 경고 메시지 표시, 버튼 텍스트 "기록 완료"

    data class PointsAvailable(val points: Int) : ScoreGuidanceState() // 점수 획득 가능, 버튼 텍스트 "기록 완료하고 N점 획득!"
}

enum class TimeSelectionState {
    NONE,
    START_DATE,
    START_TIME,
    END_DATE,
    END_TIME,
}
