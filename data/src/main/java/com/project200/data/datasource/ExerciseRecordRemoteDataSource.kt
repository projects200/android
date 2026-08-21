package com.project200.data.datasource

import com.project200.data.api.ApiService
import com.project200.data.dto.BaseResponse
import com.project200.data.dto.ExerciseIdDto
import com.project200.data.dto.GetExerciseCountByRangeDTO
import com.project200.data.dto.GetExerciseRecordData
import com.project200.data.dto.GetExerciseRecordListDto
import com.project200.data.dto.PatchExerciseRequestDto
import com.project200.data.dto.PostExerciseRequestDto
import com.project200.data.dto.PostExerciseResponseDTO
import okhttp3.MultipartBody
import java.time.LocalDate
import javax.inject.Inject

/** 운동 기록의 서버 호출을 모읍니다. 응답 래핑과 오류 변환은 호출부의 apiCallBuilder가 맡습니다 */
class ExerciseRecordRemoteDataSource
    @Inject
    constructor(
        private val apiService: ApiService,
    ) {
        suspend fun getExerciseCountsByRange(
            startDate: LocalDate,
            endDate: LocalDate,
        ): BaseResponse<List<GetExerciseCountByRangeDTO>> = apiService.getExerciseCountsByRange(startDate, endDate)

        suspend fun getExerciseRecordDetail(recordId: Long): BaseResponse<GetExerciseRecordData> =
            apiService.getExerciseRecordDetail(recordId)

        suspend fun getExerciseList(date: LocalDate): BaseResponse<List<GetExerciseRecordListDto>> = apiService.getExerciseList(date)

        suspend fun postExerciseRecord(request: PostExerciseRequestDto): BaseResponse<PostExerciseResponseDTO> =
            apiService.postExerciseRecord(request)

        suspend fun patchExerciseRecord(
            recordId: Long,
            request: PatchExerciseRequestDto,
        ): BaseResponse<ExerciseIdDto> = apiService.patchExerciseRecord(recordId, request)

        suspend fun postExerciseImages(
            recordId: Long,
            pictures: List<MultipartBody.Part>,
        ): BaseResponse<ExerciseIdDto> = apiService.postExerciseImages(recordId, pictures)

        suspend fun deleteExerciseImages(
            recordId: Long,
            pictureIds: List<Long>,
        ): BaseResponse<Unit?> = apiService.deleteExerciseImages(recordId, pictureIds)

        suspend fun deleteExerciseRecord(recordId: Long): BaseResponse<Unit?> = apiService.deleteExerciseRecord(recordId)
    }
