package com.project200.domain.model

sealed class SignUpResult {
    data class Success(val memberId: String) : SignUpResult()
    data class Failure(val errorCode: String) : SignUpResult()
}