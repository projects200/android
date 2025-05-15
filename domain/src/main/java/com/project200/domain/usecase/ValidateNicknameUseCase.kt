package com.project200.domain.usecase

import javax.inject.Inject

class ValidateNicknameUseCase @Inject constructor() {
    private val nicknameRegex = "^[가-힣a-zA-Z0-9]{1,30}$".toRegex()

    operator fun invoke(nickname: String): Boolean {
        if (nickname.isBlank()) return false
        return nicknameRegex.matches(nickname)
    }
}