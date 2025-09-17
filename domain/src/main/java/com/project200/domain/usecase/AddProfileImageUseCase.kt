package com.project200.domain.usecase

import com.project200.domain.model.BaseResult
import com.project200.domain.repository.MemberRepository
import javax.inject.Inject

class AddProfileImageUseCase @Inject constructor(
    private val memberRepository: MemberRepository
) {
    suspend operator fun invoke(image: String): BaseResult<Unit> {
        return memberRepository.addProfileImage(image)
    }
}