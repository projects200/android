package com.project200.domain.usecase

import com.project200.domain.model.BaseResult
import com.project200.domain.model.PreferredExercise
import com.project200.domain.repository.MemberRepository
import javax.inject.Inject

class GetPreferredExerciseUseCase @Inject constructor(
    private val repository: MemberRepository,
) {
    suspend operator fun invoke(): BaseResult<List<PreferredExercise>> {
        return repository.getPreferredExercises()
    }
}