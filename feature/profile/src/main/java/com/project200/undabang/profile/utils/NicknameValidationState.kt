package com.project200.undabang.profile.utils

enum class NicknameValidationState {
    INVISIBLE, // 초기 상태 (메시지 숨김)
    INVALID, // 형식 오류
    DUPLICATED, // 중복됨
    AVAILABLE, // 사용 가능
}
