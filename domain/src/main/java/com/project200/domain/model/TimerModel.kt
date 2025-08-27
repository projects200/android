package com.project200.domain.model

data class CustomTimer(
    val id: Long,
    val name: String
)

data class SimpleTimer(
    val id: Long,
    val time: Int
)

data class Step(
    val id: Long,
    val order: Int,
    val time: Long,
    val name: String
)