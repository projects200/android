package com.project200.domain.usecase

import com.project200.domain.repository.ExerciseRecordRepository
import javax.inject.Inject

class SyncExercisesUseCase @Inject constructor(
    private val repository: ExerciseRecordRepository,
) {
    suspend operator fun invoke(memberId: String) = repository.syncPendingExercises(memberId)
}
