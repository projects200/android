package com.project200.feature.exercise.form

import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.project200.common.constants.RuleConstants.MAX_IMAGE
import com.project200.common.utils.CommonDateTimeFormatters.YY_MM_DD_HH_MM
import com.project200.domain.model.BaseResult
import com.project200.domain.model.ExerciseEditResult
import com.project200.domain.model.ExerciseRecord
import com.project200.domain.model.SubmissionResult
import com.project200.domain.usecase.CreateExerciseRecordUseCase
import com.project200.domain.usecase.EditExerciseRecordUseCase
import com.project200.domain.usecase.GetExerciseRecordDetailUseCase
import com.project200.domain.usecase.UploadExerciseRecordImagesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@HiltViewModel
class ExerciseFormViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getExerciseRecordDetailUseCase: GetExerciseRecordDetailUseCase,
    private val createExerciseRecordUseCase: CreateExerciseRecordUseCase,
    private val uploadExerciseRecordImagesUseCase: UploadExerciseRecordImagesUseCase,
    private val editExerciseRecordUseCase: EditExerciseRecordUseCase
) : ViewModel() {
    val recordId: Long? = savedStateHandle.get<Long>("recordId")

    private val _startTime = MutableLiveData<LocalDateTime?>()
    val startTime: LiveData<LocalDateTime?> = _startTime

    private val _endTime = MutableLiveData<LocalDateTime?>()
    val endTime: LiveData<LocalDateTime?> = _endTime

    private val _imageItems = MutableLiveData<MutableList<ExerciseImageListItem>>(
        mutableListOf(ExerciseImageListItem.AddButtonItem)
    )
    val imageItems: LiveData<MutableList<ExerciseImageListItem>> = _imageItems

    val dateTimeFormatter: DateTimeFormatter = YY_MM_DD_HH_MM

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

    private val _toastMessage = MutableLiveData<String>()
    val toastMessage: LiveData<String> = _toastMessage

    /** 초기 데이터 설정 */
    fun loadInitialRecord() {
        if (recordId == -1L || recordId == null) {
            // 생성 모드
            isEditMode = false
            initialRecord = null
            _startTime.value = null
            _endTime.value = null
            _imageItems.value = mutableListOf(ExerciseImageListItem.AddButtonItem)
            _initialDataLoaded.value = null
        } else {
            // 수정 모드
            viewModelScope.launch {
                when (val result = getExerciseRecordDetailUseCase(recordId)) {
                    is BaseResult.Success -> { setupEditMode(result.data) }
                    is BaseResult.Error -> { _toastMessage.value = LOAD_FAIL }
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
    }

    fun setStartTime(dateTime: LocalDateTime) {
        _startTime.value = dateTime
        // 시작 시간이 종료 시간 이후면, 종료 시간 초기화
        if (_endTime.value != null && dateTime.isAfter(_endTime.value)) {
            _endTime.value = null
        }
    }

    fun setEndTime(dateTime: LocalDateTime): Boolean {
        if (_startTime.value != null && dateTime.isBefore(_startTime.value)) {
            return false
        }
        _endTime.value = dateTime
        return true
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
    private fun hasChanges(record: ExerciseRecord): Boolean
        = hasContentChanges(record) || hasImageChanges()

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
    fun submitRecord(title: String, type: String, location: String, detail: String) {
        // 제출할 기록
        val recordToSubmit = ExerciseRecord(
            title = title,
            detail = detail,
            personalType = type,
            startedAt = _startTime.value!!,
            endedAt = _endTime.value!!,
            location = location,
            pictures = null
        )

        // 변경 사항 확인 (수정 모드일 때)
        if (isEditMode && !hasChanges(recordToSubmit)) {
            _toastMessage.value = NO_CHANGE
            return
        }

        // 유효성 검사
        if (title.isBlank() || _startTime.value == null || _endTime.value == null) {
            _toastMessage.value = INVALID_INPUT
            return
        }

        // 로딩 시작
        _isLoading.value = true

        // 새로 추가된 이미지 URI 목록 가져오기
        val newImageUris = _imageItems.value
            ?.filterIsInstance<ExerciseImageListItem.NewImageItem>()
            ?.map { it.uri.toString() } ?: emptyList()

        if (isEditMode) editExerciseRecord(recordToSubmit, newImageUris)
        else createExerciseRecord(recordToSubmit, newImageUris)

    }

    /** 기록 생성 */
    private fun createExerciseRecord(
        record: ExerciseRecord,
        newImageUris: List<String>
    ) {
        viewModelScope.launch {
            when (val createResult = createExerciseRecordUseCase(record)) {
                is BaseResult.Success -> {
                    val createdRecordId = createResult.data
                    // 기록 생성 성공 시 & 업로드할 이미지가 있을 경우 -> 이미지 업로드 요청
                    if (newImageUris.isNotEmpty()) {
                        when (uploadExerciseRecordImagesUseCase(createdRecordId, newImageUris)) {
                            is BaseResult.Success -> {
                                // 이미지 업로드 성공 -> 최종 성공
                                Timber.tag("ExerciseFormViewModel").d("성공")
                                _createResult.value = SubmissionResult.Success(createdRecordId)
                            }

                            is BaseResult.Error -> {
                                // 이미지 업로드 실패 -> 부분 성공 (오류 메시지와 함께)
                                _createResult.value =
                                    SubmissionResult.PartialSuccess(createdRecordId, UPLOAD_FAIL)
                            }
                        }
                    } else {
                        // 업로드할 이미지 없음 -> 최종 성공
                        _createResult.value = SubmissionResult.Success(createdRecordId)
                    }
                }

                is BaseResult.Error -> {
                    // 기록 생성 실패
                    _createResult.value = SubmissionResult.Failure(CREATE_FAIL, createResult.cause)
                    _toastMessage.value = CREATE_FAIL
                }
            }
            // 로딩 종료
            _isLoading.value = false
        }
    }

    /** 기록 수정 */
    private fun editExerciseRecord(
        record: ExerciseRecord,
        newImageUris: List<String>
    ) {
        viewModelScope.launch {
            _editResult.value = editExerciseRecordUseCase(
                recordId = recordId!!,
                recordToUpdate = record,
                isContentChanges = hasContentChanges(record),
                imagesToDelete = removedPictureIds,
                newImages = newImageUris
            )
            // 로딩 종료
            _isLoading.value = false
        }
    }


    companion object {
        const val LOAD_FAIL = "기록을 불러오는데 실패했습니다"
        const val NO_CHANGE = "변경 사항이 없습니다"
        const val INVALID_INPUT = "필수 항목을 입력해주세요."
        const val CREATE_FAIL = "기록 생성에 실패했습니다"
        const val UPLOAD_FAIL = "이미지 업로드에 실패했습니다"
        const val TAG = "ExerciseViewModel"
    }
}