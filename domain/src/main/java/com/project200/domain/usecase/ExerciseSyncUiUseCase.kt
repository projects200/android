package com.project200.domain.usecase

import com.project200.domain.repository.ExerciseRecordRepository
import javax.inject.Inject

class ExerciseSyncUiUseCase
    @Inject
    constructor(
        private val repository: ExerciseRecordRepository,
    ) {
        fun observe() = repository.observeSyncStatus()

        suspend fun retry(recordId: String) = repository.retryBody(recordId)

        suspend fun discardRemoteDeleted(recordId: String, confirmationToken: String) =
            repository.discardRemoteDeleted(recordId, confirmationToken)

        suspend fun consumePointNotice(recordIds: Set<String>) = repository.consumePointNotice(recordIds)
    }
