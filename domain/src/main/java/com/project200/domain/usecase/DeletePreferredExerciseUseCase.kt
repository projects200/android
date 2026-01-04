package com.project200.domain.usecase

import com.project200.domain.model.BaseResult
import com.project200.domain.repository.MemberRepository
import javax.inject.Inject

class DeletePreferredExerciseUseCase @Inject constructor(
    val memberRepository: MemberRepository,
) {
    suspend operator fun invoke(preferredExerciseIds: List<Long>): BaseResult<Unit> {
        return memberRepository.deletePreferredExercise(preferredExerciseIds)
    }
}