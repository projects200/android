package com.project200.undabang.profile.utils

enum class ProfileEditErrorType {
    LOAD_FAILED, // 프로필 조회 실패
    SAME_AS_ORIGINAL, // 기존 닉네임과 동일할 경우
    CHECK_DUPLICATE_FAILED, // 중복 확인 API 실패
    NO_CHANGE, // 변경사항 없음
    NO_DUPLICATE_CHECKED, // 중복 확인 안됨
    IMAGE_INVALID_TYPE, // 이미지 타입 오류
    IMAGE_READ_FAILED, // 이미지 읽기 또는 압축 실패
}
