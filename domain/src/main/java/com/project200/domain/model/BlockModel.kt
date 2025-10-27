package com.project200.domain.model

data class BlockedMember(
    val memberBlockId: Long,
    val memberId: String,
    val nickname: String,
    val profileImageUrl: String,
    val thumbnailImageUrl: String
)