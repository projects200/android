package com.project200.undabang.profile.utils

enum class ProfileEditErrorType {
    LOAD_FAILED, // 프로필 조회 실패
    SAME_AS_ORIGINAL, // 기존 닉네임과 동일할 경우
    CHECK_DUPLICATE_FAILED, // 중복 확인 API 실패
}
