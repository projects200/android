package com.project200.domain.usecase

import com.project200.domain.model.BaseResult
import com.project200.domain.repository.MemberRepository
import javax.inject.Inject

class EditProfileUseCase @Inject constructor(
    private val memberRepository: MemberRepository
) {
    suspend operator fun invoke(
        nickname: String,
        gender: String,
        introduction: String
    ): BaseResult<Unit> {
        return memberRepository.editUserProfile(nickname, gender, introduction)
    }
}