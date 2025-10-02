package com.project200.domain.usecase

import com.project200.domain.model.BaseResult
import com.project200.domain.repository.MemberRepository
import javax.inject.Inject

class EditOpenUrlUseCase @Inject constructor(
    private val memberRepository: MemberRepository
) {
    suspend operator fun invoke(id: Long, url: String): BaseResult<Unit> {
        return memberRepository.editOpenUrl(id, url)
    }
}