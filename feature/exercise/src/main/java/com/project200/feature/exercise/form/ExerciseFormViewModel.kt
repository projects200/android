package com.project200.feature.exercise.form

import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.project200.common.constants.RuleConstants.MAX_IMAGE
import com.project200.common.utils.ClockProvider
import com.project200.common.utils.CommonDateTimeFormatters.YY_MM_DD_HH_MM
import com.project200.domain.model.BaseResult
import com.project200.domain.model.ExerciseEditResult
import com.project200.domain.model.ExerciseRecord
import com.project200.domain.model.SubmissionResult
import com.project200.domain.usecase.CreateExerciseRecordUseCase
import com.project200.domain.usecase.EditExerciseRecordUseCase
import com.project200.domain.usecase.GetExerciseRecordDetailUseCase
import com.project200.domain.usecase.GetExpectedScoreInfoUseCase
import com.project200.domain.usecase.UploadExerciseRecordImagesUseCase
import com.project200.feature.exercise.utils.ScoreGuidanceState
import com.project200.feature.exercise.utils.TimeSelectionState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject


@HiltViewModel
class ExerciseFormViewModel
    @Inject
    constructor(
        private val getExerciseRecordDetailUseCase: GetExerciseRecordDetailUseCase,
        private val createExerciseRecordUseCase: CreateExerciseRecordUseCase,
        private val uploadExerciseRecordImagesUseCase: UploadExerciseRecordImagesUseCase,
        private val editExerciseRecordUseCase: EditExerciseRecordUseCase,
        private val getExpectedScoreInfoUseCase: GetExpectedScoreInfoUseCase,
        private val clockProvider: ClockProvider,
    ) : ViewModel() {
        private val _startTime = MutableLiveData<LocalDateTime?>()
        val startTime: LiveData<LocalDateTime?> = _startTime

        private val _endTime = MutableLiveData<LocalDateTime?>()
        val endTime: LiveData<LocalDateTime?> = _endTime

        private val _imageItems =
            MutableLiveData<MutableList<ExerciseImageListItem>>(
                mutableListOf(ExerciseImageListItem.AddButtonItem),
            )
        val imageItems: LiveData<MutableList<ExerciseImageListItem>> = _imageItems

        // 수정/생성 모드 관련
        private var initialRecord: ExerciseRecord? = null // 수정 시 초기 데이터 저장
        private var isEditMode = false
        private val removedPictureIds = mutableListOf<Long>() // 삭제할 기존 이미지 ID 목록

        private val _initialDataLoaded = MutableLiveData<ExerciseRecord?>()
        val initialDataLoaded: LiveData<ExerciseRecord?> = _initialDataLoaded

        private val _isLoading = MutableLiveData<Boolean>()
        val isLoading: LiveData<Boolean> = _isLoading

        private val _createResult = MutableLiveData<SubmissionResult>()
        val createResult: LiveData<SubmissionResult> = _createResult

        private val _editResult = MutableLiveData<ExerciseEditResult>()
        val editResult: LiveData<ExerciseEditResult> = _editResult

        private val _toastMessage = MutableLiveData<String?>()
        val toastMessage: LiveData<String?> = _toastMessage

        private val _scoreGuidanceState = MutableLiveData<ScoreGuidanceState>(ScoreGuidanceState.Hidden)
        val scoreGuidanceState: LiveData<ScoreGuidanceState> = _scoreGuidanceState

        private val _timeSelectionState = MutableLiveData(TimeSelectionState.NONE)
        val timeSelectionState: LiveData<TimeSelectionState> = _timeSelectionState

        /** 초기 데이터 설정 */
        fun loadInitialRecord(recordId: Long) {
            if (recordId == -1L) {
                // 생성 모드
                isEditMode = false
                initialRecord = null

                // 현재 시간 기준 정시로 초기화
                val now = clockProvider.localDateTimeNow().withMinute(0).withSecond(0).withNano(0)
                _startTime.value = now.minusHours(1)
                _endTime.value = now

                _imageItems.value = mutableListOf(ExerciseImageListItem.AddButtonItem)
                _initialDataLoaded.value = null
            } else {
                // 수정 모드
                viewModelScope.launch {
                    when (val result = getExerciseRecordDetailUseCase(recordId)) {
                        is BaseResult.Success -> {
                            setupEditMode(result.data)
                        }
                        is BaseResult.Error -> {
                            _toastMessage.value = LOAD_FAIL
                        }
                    }
                }
            }
        }

        /** 수정 모드 데이터 설정 */
        private fun setupEditMode(record: ExerciseRecord) {
            initialRecord = record
            isEditMode = true

            _startTime.value = record.startedAt
            _endTime.value = record.endedAt

            val imageListForEditMode = mutableListOf<ExerciseImageListItem>(ExerciseImageListItem.AddButtonItem)
            record.pictures?.forEach { picture ->
                imageListForEditMode.add(ExerciseImageListItem.ExistingImageItem(picture.url, picture.id))
            }

            _imageItems.value = imageListForEditMode

            _initialDataLoaded.value = record
            _scoreGuidanceState.value = ScoreGuidanceState.Hidden
        }

    fun setStartTime(dateTime: LocalDateTime) {
        val now = clockProvider.localDateTimeNow()
        var newStartTime = dateTime

        // 시작 시간이 현재 시간 이후로 설정되지 않도록 보정
        if (newStartTime.isAfter(now)) {
            newStartTime = now
            _toastMessage.value = "시작 시간은 현재 시간 이후로 설정할 수 없습니다."
        }

        _startTime.value = newStartTime

        // 시작 시간이 종료 시간을 넘어서면, 종료 시간 조정
        applyTimeCorrection(isStartChanged = true)

        updateScoreGuidance()
    }

    fun setEndTime(dateTime: LocalDateTime) {
        val now = clockProvider.localDateTimeNow()
        var newEndTime = dateTime

        // 종료 시간이 현재 시간 이후로 설정되지 않도록 보정
        if (newEndTime.isAfter(now)) {
            newEndTime = now
            _toastMessage.value = "종료 시간은 현재 시간 이후로 설정할 수 없습니다."
        }

        _endTime.value = newEndTime

        // 종료 시간이 시작 시간을 넘어서지 않으면, 시작 시간 조정
        applyTimeCorrection(isStartChanged = false)
    }

    /**
     * 시간 역전 보정 함수
     *
     */
    private fun applyTimeCorrection(isStartChanged: Boolean) {
        val now = clockProvider.localDateTimeNow()
        var start = _startTime.value ?: return
        var end = _endTime.value ?: return

        if (isStartChanged) {
            // 시작 시간이 종료 시간보다 같거나 늦어지면: 종료 = 시작 + 1시간
            if (!start.isBefore(end)) {
                end = start.plusHours(1)
                // 보정된 종료 시간이 현재를 초과하면 현재 시간으로 강제 고정
                if (end.isAfter(now)) {
                    end = now
                }
            }
        } else {
            // 종료 시간이 시작 시간보다 같거나 빨라지면: 시작 = 종료 - 1시간
            if (!end.isAfter(start)) {
                start = end.minusHours(1)
            }
        }

        _startTime.value = start
        _endTime.value = end
    }

    // 시간 선택 버튼 클릭 이벤트 처리
    fun onTimeSelectionClick(selection: TimeSelectionState) {
        // 이미 선택된 버튼을 다시 누르면 선택 해제
        if (_timeSelectionState.value == selection) {
            _timeSelectionState.value = TimeSelectionState.NONE
        } else {
            _timeSelectionState.value = selection
        }
    }

    // CalendarView에서 날짜가 선택되었을 때 호출
    fun updateDate(year: Int, month: Int, dayOfMonth: Int) {
        val selectedDate = LocalDate.of(year, month + 1, dayOfMonth)
        when (_timeSelectionState.value) {
            TimeSelectionState.START_DATE -> {
                val existingTime = _startTime.value?.toLocalTime() ?: clockProvider.nowTime()
                setStartTime(LocalDateTime.of(selectedDate, existingTime))
            }
            TimeSelectionState.END_DATE -> {
                val existingTime = _endTime.value?.toLocalTime() ?: clockProvider.nowTime()
                setEndTime(LocalDateTime.of(selectedDate, existingTime))
            }
            else -> return
        }
    }

    // 시간 선택 후 데이터를 업데이트
    fun updateTime(hour: Int, minute: Int) {
        val selectedTime = LocalTime.of(hour, minute)
        when (_timeSelectionState.value) {
            TimeSelectionState.START_TIME -> {
                val existingDate = _startTime.value?.toLocalDate() ?: LocalDate.now()
                setStartTime(LocalDateTime.of(existingDate, selectedTime))
            }
            TimeSelectionState.END_TIME -> {
                val existingDate = _endTime.value?.toLocalDate() ?: LocalDate.now()
                setEndTime(LocalDateTime.of(existingDate, selectedTime))
            }
            else -> return
        }
        _timeSelectionState.value = TimeSelectionState.NONE // 시간 설정 후 선택기 닫기
    }


        fun addImage(uris: List<Uri>) {
            val currentList = _imageItems.value ?: mutableListOf()
            for (uri in uris) {
                currentList.add(ExerciseImageListItem.NewImageItem(uri))
            }
            _imageItems.value = currentList
        }

        fun removeImage(itemToRemove: ExerciseImageListItem) {
            val currentList = _imageItems.value ?: return
            currentList.remove(itemToRemove)

            // 기존 이미지였다면, 삭제 목록에 ID 추가
            if (itemToRemove is ExerciseImageListItem.ExistingImageItem) {
                removedPictureIds.add(itemToRemove.pictureId)
            }
            _imageItems.value = currentList
        }

        fun getCurrentPermittedImageCount(): Int {
            val imageCount =
                _imageItems.value?.count { it !is ExerciseImageListItem.AddButtonItem } ?: 0
            return MAX_IMAGE - imageCount
        }

        /** 변경 사항이 있는지 확인 */
        private fun hasChanges(record: ExerciseRecord): Boolean = hasContentChanges(record) || hasImageChanges()

        private fun hasContentChanges(record: ExerciseRecord): Boolean {
            initialRecord?.let { initialRecord ->
                return initialRecord.title != record.title ||
                    initialRecord.personalType != record.personalType ||
                    initialRecord.location != record.location ||
                    initialRecord.detail != record.detail ||
                    initialRecord.startedAt != _startTime.value ||
                    initialRecord.endedAt != _endTime.value
            }
            return true
        }

        /** 이미지 변경 사항이 있는지 확인 */
        private fun hasImageChanges(): Boolean {
            return removedPictureIds.isNotEmpty() ||
                _imageItems.value?.any { it is ExerciseImageListItem.NewImageItem } == true
        }

        /** 기록 생성 또는 수정 */
        fun submitRecord(
            recordId: Long,
            title: String,
            type: String,
            location: String,
            detail: String,
        ) {
            // 유효성 검사
            if (title.isBlank() || _startTime.value == null || _endTime.value == null) {
                _toastMessage.value = INVALID_INPUT
                return
            }

            // 제출할 기록
            val recordToSubmit =
                ExerciseRecord(
                    title = title,
                    detail = detail,
                    personalType = type,
                    startedAt = _startTime.value!!,
                    endedAt = _endTime.value!!,
                    location = location,
                    pictures = null,
                )

            // 변경 사항 확인 (수정 모드일 때)
            if (isEditMode && !hasChanges(recordToSubmit)) {
                _toastMessage.value = NO_CHANGE
                return
            }

            // 로딩 시작
            _isLoading.value = true

            // 새로 추가된 이미지 URI 목록 가져오기
            val newImageUris =
                _imageItems.value
                    ?.filterIsInstance<ExerciseImageListItem.NewImageItem>()
                    ?.map { it.uri.toString() } ?: emptyList()

            if (isEditMode) {
                editExerciseRecord(recordId, recordToSubmit, newImageUris)
            } else {
                createExerciseRecord(recordToSubmit, newImageUris)
            }
        }

        /** 기록 생성 */
        private fun createExerciseRecord(
            record: ExerciseRecord,
            newImageUris: List<String>,
        ) {
            viewModelScope.launch {
                when (val createResult = createExerciseRecordUseCase(record)) {
                    is BaseResult.Success -> {
                        // 기록 생성 성공 시, 이미지 업로드 로직 호출
                        handleSuccessfulRecordCreation(createResult.data.recordId, newImageUris, createResult.data.earnedPoints)
                    }
                    is BaseResult.Error -> {
                        // 기록 생성 실패
                        _createResult.value = SubmissionResult.Failure(CREATE_FAIL, createResult.cause)
                        _toastMessage.value = CREATE_FAIL
                        _isLoading.value = false
                    }
                }
            }
        }

        /** 기록 생성 후 이미지 업로드 처리 */
        private suspend fun handleSuccessfulRecordCreation(
            recordId: Long,
            newImageUris: List<String>,
            earnedPoints: Int,
        ) {
            if (newImageUris.isNotEmpty()) {
                when (uploadExerciseRecordImagesUseCase(recordId, newImageUris)) {
                    is BaseResult.Success -> {
                        // 이미지 업로드 성공 -> 최종 성공
                        _createResult.value = SubmissionResult.Success(recordId, earnedPoints)
                    }
                    is BaseResult.Error -> {
                        // 이미지 업로드 실패 -> 부분 성공
                        _createResult.value = SubmissionResult.PartialSuccess(recordId, UPLOAD_FAIL)
                    }
                }
            } else {
                // 업로드할 이미지가 없음 -> 최종 성공
                _createResult.value = SubmissionResult.Success(recordId, earnedPoints)
            }
            _isLoading.value = false // 이미지 업로드까지 완료된 후 로딩 종료
        }

        /** 기록 수정 */
        private fun editExerciseRecord(
            recordId: Long,
            record: ExerciseRecord,
            newImageUris: List<String>,
        ) {
            viewModelScope.launch {
                _editResult.value =
                    editExerciseRecordUseCase(
                        recordId = recordId,
                        recordToUpdate = record,
                        isContentChanges = hasContentChanges(record),
                        imagesToDelete = removedPictureIds,
                        newImages = newImageUris,
                    )
                // 로딩 종료
                _isLoading.value = false
            }
        }

        /** 예상 획득 점수 정보 조회 및 UI 업데이트 */
        private fun updateScoreGuidance() {
            // 수정 모드일 경우 점수 안내 로직을 실행하지 않음
            if (isEditMode) {
                _scoreGuidanceState.value = ScoreGuidanceState.Hidden
                return
            }

            val startTime = _startTime.value
            // 시작 시간이 설정되지 않았으면 안내 숨김
            if (startTime == null) {
                _scoreGuidanceState.value = ScoreGuidanceState.Hidden
                return
            }

            viewModelScope.launch {
                when (val result = getExpectedScoreInfoUseCase()) { // UseCase 호출
                    is BaseResult.Success -> {
                        val expectedScoreInfo = result.data
                        Timber.tag(TAG).d("Expected Score Info: $expectedScoreInfo")

                        // 최대 점수 도달 여부 확인
                        val currentUserScore = expectedScoreInfo.currentUserScore
                        val maxScore = expectedScoreInfo.maxScore
                        val pointsPerExercise = expectedScoreInfo.pointsPerExercise

                        if (currentUserScore >= maxScore) {
                            _scoreGuidanceState.value = ScoreGuidanceState.Warning(MAX_SCORE_REACHED)
                            return@launch
                        }

                        // 획득 가능 기간 지남 여부 확인
                        val validStart = expectedScoreInfo.validWindow.startDateTime
                        val validEnd = expectedScoreInfo.validWindow.endDateTime
                        if (startTime.isBefore(validStart) || startTime.isAfter(validEnd)) {
                            _scoreGuidanceState.value =
                                ScoreGuidanceState.Warning(UPLOAD_PERIOD_EXPIRED)
                            return@launch
                        }

                        // 이미 점수 획득 여부 확인
                        val recordDate = startTime.toLocalDate()
                        if (!expectedScoreInfo.earnableScoreDays.contains(recordDate)) {
                            _scoreGuidanceState.value = ScoreGuidanceState.Warning(ALREADY_SCORED_TODAY)
                            return@launch
                        }

                        // 점수 획득 가능 & 예상 획득 점수 계산
                        val pointsToEarn = minOf(pointsPerExercise, maxScore - currentUserScore)
                        _scoreGuidanceState.value = ScoreGuidanceState.PointsAvailable(pointsToEarn)
                    }

                    is BaseResult.Error -> {
                        // API 호출 실패 시 에러 메시지 표시 및 안내 숨김
                        Timber.e("Failed to fetch expected score info: ${result.message}")
                        _toastMessage.value = FETCH_SCORE_INFO_FAIL // 새로운 상수 추가
                        _scoreGuidanceState.value = ScoreGuidanceState.Hidden
                    }
                }
            }
        }

        companion object {
            const val LOAD_FAIL = "기록을 불러오는데 실패했습니다"
            const val NO_CHANGE = "변경 사항이 없습니다"
            const val INVALID_INPUT = "필수 항목을 입력해주세요."
            const val CREATE_FAIL = "기록 생성에 실패했습니다"
            const val UPLOAD_FAIL = "이미지 업로드에 실패했습니다"
            const val FETCH_SCORE_INFO_FAIL = "점수 정보를 불러오는데 실패했습니다."
            const val TAG = "ExerciseViewModel"

            // 점수 안내 관련 상수
            const val ALREADY_SCORED_TODAY = "이 날은 이미 점수를 획득했어요"
            const val UPLOAD_PERIOD_EXPIRED = "점수를 획득할 수 있는 기간이 지났어요"
            const val MAX_SCORE_REACHED = "점수가 최대치에 도달했어요!"
        }
    }
