package com.project200.domain.usecase

import com.project200.domain.model.BaseResult
import com.project200.domain.model.PreferredExercise
import com.project200.domain.repository.MemberRepository
import javax.inject.Inject

class EditPreferredExerciseUseCase @Inject constructor(
    val memberRepository: MemberRepository,
) {
    suspend operator fun invoke(preferredExercises: List<PreferredExercise>): BaseResult<Unit> {
        return memberRepository.editPreferredExercise(preferredExercises)
    }
}