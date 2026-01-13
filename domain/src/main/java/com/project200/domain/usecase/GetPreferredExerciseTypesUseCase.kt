package com.project200.domain.usecase

import com.project200.domain.model.BaseResult
import com.project200.domain.model.ExerciseType
import com.project200.domain.model.PreferredExercise
import com.project200.domain.repository.MemberRepository
import javax.inject.Inject

class GetPreferredExerciseTypesUseCase @Inject constructor(
    private val repository: MemberRepository,
) {
    suspend operator fun invoke(): BaseResult<List<PreferredExercise>> {
        when(val result = repository.getPreferredExerciseTypes()) {
            is BaseResult.Success -> {
                // 운동 타입을 PreferredExercise 형태로 변환
                val exerciseTypes = result.data.map { exerciseType ->
                    exerciseType.toEmptyPreferredExercise()
                }
                return BaseResult.Success(exerciseTypes)
            }
            is BaseResult.Error -> return result
        }
    }
}