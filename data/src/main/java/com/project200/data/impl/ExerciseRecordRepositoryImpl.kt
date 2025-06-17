package com.project200.data.impl

import android.content.Context
import androidx.core.net.toUri
import com.project200.common.di.IoDispatcher
import com.project200.data.api.ApiService
import com.project200.data.dto.ExerciseIdDto
import com.project200.data.dto.GetExerciseCountByRangeDTO
import com.project200.data.dto.GetExerciseRecordData
import com.project200.data.dto.GetExerciseRecordListDto
import com.project200.data.mapper.toModel
import com.project200.data.mapper.toMultipartBodyPart
import com.project200.data.mapper.toPatchExerciseDTO
import com.project200.data.mapper.toPostExerciseDTO
import com.project200.data.utils.apiCallBuilder
import com.project200.domain.model.BaseResult
import com.project200.domain.model.ExerciseCount
import com.project200.domain.model.ExerciseListItem
import com.project200.domain.model.ExerciseRecord
import com.project200.domain.repository.ExerciseRecordRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import timber.log.Timber
import java.time.LocalDate
import java.util.NoSuchElementException
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

class ExerciseRecordRepositoryImpl @Inject constructor(
    private val apiService: ApiService,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    @ApplicationContext private val context: Context
) : ExerciseRecordRepository {

    override suspend fun getExerciseCountByRange(
        startDate: LocalDate,
        endDate: LocalDate
    ): BaseResult<List<ExerciseCount>> {
        return apiCallBuilder(
            ioDispatcher = ioDispatcher,
            apiCall = { apiService.getExerciseCountsByRange(startDate, endDate) },
            mapper = { dtoList: List<GetExerciseCountByRangeDTO>? ->
                dtoList?.map { it.toModel() } ?: throw NoSuchElementException("구간별 운동 횟수 조회 응답 데이터가 없습니다.")
            }
        )
    }

    override suspend fun getExerciseDetail(recordId: Long): BaseResult<ExerciseRecord> {
        return apiCallBuilder(
            ioDispatcher = ioDispatcher,
            apiCall = { apiService.getExerciseRecordDetail(recordId) },
            mapper = { dto: GetExerciseRecordData? ->
                dto?.toModel() ?: throw NoSuchElementException("운동 상세 정보 응답 데이터가 없습니다.")
            }
        )
    }

    override suspend fun getExerciseRecordList(date: LocalDate): BaseResult<List<ExerciseListItem>> {
        return apiCallBuilder(
            ioDispatcher = ioDispatcher,
            apiCall = { apiService.getExerciseList(date) },
            mapper = { dtoList: List<GetExerciseRecordListDto>? ->
                dtoList?.map { it.toModel() } ?: emptyList()
            }
        )
    }


    override suspend fun createExerciseRecord(record: ExerciseRecord): BaseResult<Long> {
        return apiCallBuilder(
            ioDispatcher = ioDispatcher,
            apiCall = { apiService.postExerciseRecord(record.toPostExerciseDTO()) },
            mapper = { exerciseIdDto: ExerciseIdDto? ->
                exerciseIdDto?.exerciseId ?: throw NoSuchElementException("운동 기록 생성 응답 데이터가 없습니다.")
            }
        )
    }

    override suspend fun updateExerciseRecord(record: ExerciseRecord, recordId: Long): BaseResult<Long> {
        return apiCallBuilder(
            ioDispatcher = ioDispatcher,
            apiCall = { apiService.patchExerciseRecord(recordId, record.toPatchExerciseDTO()) },
            mapper = { exerciseIdDto: ExerciseIdDto? ->
                exerciseIdDto?.exerciseId ?: throw NoSuchElementException("운동 기록 수정 응답 데이터가 없습니다.")
            }
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
                Timber.w(e, "CancellationException: $it")
                throw e
            } catch (e: Exception) {
                Timber.w(e, "Invalid URI string: $it")
                null
            }
        }

        val imageParts = imageUris.mapNotNull { uri ->
            uri.toMultipartBodyPart(context, "pictures")
        }

        if (imageParts.isEmpty() && imageUris.isNotEmpty()) {
            return BaseResult.Error("CONVERSION_FAILED", "이미지 파일 변환에 실패했습니다.")
        }

        return apiCallBuilder(
            ioDispatcher = ioDispatcher,
            apiCall = { apiService.postExerciseImages(recordId, imageParts) },
            mapper = { exerciseIdDto: ExerciseIdDto? ->
                exerciseIdDto?.exerciseId ?: throw NoSuchElementException("이미지 업로드 응답 데이터가 없습니다.")
            }
        )
    }

    override suspend fun deleteExerciseRecordImages(
        recordId: Long,
        imageIds: List<Long>
    ): BaseResult<Unit> {
        return apiCallBuilder(
            ioDispatcher = ioDispatcher,
            apiCall = { apiService.deleteExerciseImages(recordId, imageIds) },
            mapper = { Unit }
        )
    }
    override suspend fun deleteExerciseRecord(recordId: Long): BaseResult<Unit> {
        return apiCallBuilder(
            ioDispatcher = ioDispatcher,
            apiCall = { apiService.deleteExerciseRecord(recordId) },
            mapper = { Unit }
        )
    }
}