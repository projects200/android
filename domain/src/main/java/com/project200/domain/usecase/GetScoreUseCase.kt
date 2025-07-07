package com.project200.domain.usecase

import com.project200.domain.model.BaseResult
import com.project200.domain.model.Score
import com.project200.domain.repository.MemberRepository
import java.time.LocalDate
import javax.inject.Inject

class GetScoreUseCase @Inject constructor(
    private val memberRepository: MemberRepository
) {
    suspend operator fun invoke(): BaseResult<Score> {
        return memberRepository.getScore()
    }
}