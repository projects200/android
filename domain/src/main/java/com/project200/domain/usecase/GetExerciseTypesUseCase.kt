package com.project200.domain.usecase

import com.project200.domain.model.BaseResult
import com.project200.domain.model.ExerciseType
import com.project200.domain.repository.MemberRepository
import javax.inject.Inject

class GetExerciseTypesUseCase @Inject constructor(
    private val repository: MemberRepository,
) {
    suspend operator fun invoke(): BaseResult<List<ExerciseType>> {
        return repository.getPreferredExerciseTypes()
    }
}
