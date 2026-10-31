package com.project200.data.local

/** 자격 증명은 저장하지 않습니다. 회원 경계 승인 전에는 일반 계정 조회를 차단합니다. */
data class PendingLogin(
    val attemptId: String,
    val retainedMemberId: String?,
    val retainedSubject: String?,
    val sessionRevision: Long,
    val fileSessionRevision: Long,
    val subject: String? = null,
    val targetMemberId: String? = null,
    val confirmationId: String? = null,
    val signupAllowed: Boolean = false,
)
