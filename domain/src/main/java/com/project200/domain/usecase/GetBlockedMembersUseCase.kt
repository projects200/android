package com.project200.domain.usecase

import com.project200.domain.model.BaseResult
import com.project200.domain.model.BlockedMember
import com.project200.domain.repository.MemberRepository
import javax.inject.Inject

class GetBlockedMembersUseCase @Inject constructor(
    private val memberRepository: MemberRepository
) {
    suspend operator fun invoke(): BaseResult<List<BlockedMember>> {
        return memberRepository.getBlockedMembers()
    }
}