package com.project200.data.impl

import android.content.ContentResolver
import com.project200.common.di.IoDispatcher
import com.project200.data.api.ApiService
import com.project200.data.dto.GetExerciseRecordData
import com.project200.data.mapper.toModel
import com.project200.data.mapper.toPostExerciseDTO
import com.project200.data.utils.apiCallBuilder
import com.project200.domain.model.BaseResult
import com.project200.domain.model.ExerciseRecord
import com.project200.domain.repository.ExerciseRecordRepository
import kotlinx.coroutines.CoroutineDispatcher
import timber.log.Timber
import javax.inject.Inject
import androidx.core.net.toUri
import com.project200.data.mapper.toMultipartBodyPart
import kotlin.coroutines.cancellation.CancellationException

class ExerciseRecordRepositoryImpl @Inject constructor(
    private val apiService: ApiService,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val contentResolver: ContentResolver
) : ExerciseRecordRepository {
    override suspend fun getExerciseDetail(recordId: Long): BaseResult<ExerciseRecord> {
        return apiCallBuilder(
            ioDispatcher = ioDispatcher,
            apiCall = { apiService.getExerciseRecordDetail(recordId) },
            mapper = { dto: GetExerciseRecordData -> dto.toModel() }
        )
    }

    override suspend fun createExerciseRecord(record: ExerciseRecord): BaseResult<Long> {
        return apiCallBuilder(
            ioDispatcher = ioDispatcher,
            apiCall = { apiService.postExerciseRecord(record.toPostExerciseDTO())},
            mapper = { exerciseId: Long  -> exerciseId }
        )
    }

    override suspend fun uploadExerciseRecordImages(
        recordId: Long,
        images: List<String>,
    ): BaseResult<Long> {

        val imageUris = images.mapNotNull {
            try {
                it.toUri()
            } catch (e: CancellationException) {
                Timber.w(e, "Coroutine CancellationException: $it")
                null
            } catch (e: Exception) {
                Timber.w(e, "Invalid URI string: $it")
                null
            }
        }

        val imageParts = imageUris.mapNotNull { uri ->
            uri.toMultipartBodyPart(contentResolver, "pictures")
        }

        if (imageParts.isEmpty() && imageUris.isNotEmpty()) {
            return BaseResult.Error("CONVERSION_FAILED", "이미지 파일 변환에 실패했습니다.")
        }

        return apiCallBuilder(
            ioDispatcher = ioDispatcher,
            apiCall = { apiService.postExerciseImages(recordId, imageParts) },
            mapper = { responseId: Long -> responseId }
        )
    }
}