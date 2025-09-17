package com.project200.domain.usecase

import com.project200.domain.model.BaseResult
import com.project200.domain.model.ExpectedScoreInfo
import com.project200.domain.repository.ScoreRepository
import javax.inject.Inject

class GetExpectedScoreInfoUseCase @Inject constructor(
    private val scoreRepository: ScoreRepository
) {
    suspend operator fun invoke(): BaseResult<ExpectedScoreInfo> {
        return scoreRepository.getExpectedScoreInfo()
    }
}