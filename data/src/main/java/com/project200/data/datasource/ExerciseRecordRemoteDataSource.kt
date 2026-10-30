package com.project200.data.datasource

import com.project200.data.api.ApiService
import com.project200.data.dto.BaseResponse
import com.project200.data.dto.ExerciseIdDto
import com.project200.data.dto.ExercisePicturesDto
import com.project200.data.dto.GetExerciseCountByRangeDTO
import com.project200.data.dto.GetExerciseRecordData
import com.project200.data.dto.GetExerciseRecordListDto
import com.project200.data.dto.PatchExerciseRequestDto
import com.project200.data.dto.PostExerciseRequestDto
import com.project200.data.dto.PostExerciseResponseDTO
import com.project200.data.utils.ExerciseSyncAccount
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

        suspend fun getExerciseRecordDetail(
            recordId: Long,
            account: ExerciseSyncAccount? = null,
        ): BaseResponse<GetExerciseRecordData> = apiService.getExerciseRecordDetail(recordId, account)

        suspend fun getExerciseList(
            date: LocalDate,
            account: ExerciseSyncAccount? = null,
        ): BaseResponse<List<GetExerciseRecordListDto>> = apiService.getExerciseList(date, account)

        suspend fun postExerciseRecord(
            request: PostExerciseRequestDto,
            account: ExerciseSyncAccount? = null,
        ): BaseResponse<PostExerciseResponseDTO> = apiService.postExerciseRecord(request, account)

        suspend fun patchExerciseRecord(
            recordId: Long,
            request: PatchExerciseRequestDto,
            account: ExerciseSyncAccount? = null,
        ): BaseResponse<ExerciseIdDto> = apiService.patchExerciseRecord(recordId, request, account)

        suspend fun postExerciseImages(
            recordId: Long,
            pictures: List<MultipartBody.Part>,
            uploadRequestId: String? = null,
            clientImageIds: List<String>? = null,
            account: ExerciseSyncAccount? = null,
        ): BaseResponse<ExercisePicturesDto> =
            apiService.postExerciseImages(recordId, pictures, uploadRequestId, clientImageIds, account)

        suspend fun deleteExerciseImages(
            recordId: Long,
            pictureIds: List<Long>,
            editId: String? = null,
            account: ExerciseSyncAccount? = null,
        ): BaseResponse<ExerciseIdDto?> = apiService.deleteExerciseImages(recordId, pictureIds, editId, account)

        suspend fun deleteExerciseRecord(
            recordId: Long,
            editId: String? = null,
            account: ExerciseSyncAccount? = null,
        ): BaseResponse<ExerciseIdDto?> = apiService.deleteExerciseRecord(recordId, editId, account)
    }
