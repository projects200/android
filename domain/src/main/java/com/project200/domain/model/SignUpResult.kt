package com.project200.domain.model

sealed class SignUpResult {
    data object Success : SignUpResult()
    data class Failure(val errorCode: String, val errorMessage: String) : SignUpResult()
}