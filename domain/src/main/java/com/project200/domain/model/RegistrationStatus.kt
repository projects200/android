package com.project200.domain.model

sealed class RegistrationStatus {
    object Registered : RegistrationStatus()
    object Unregistered : RegistrationStatus()
    object Indeterminate : RegistrationStatus() // 네트워크 오류 등으로 확인 불가
}
