package com.project200.feature.exercise.form

import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.project200.common.constants.RuleConstants.MAX_IMAGE
import com.project200.common.utils.CommonDateTimeFormatters.MM_DD_DAY_HH_MM_KOREAN
import com.project200.domain.model.BaseResult
import com.project200.domain.model.ExerciseRecord
import com.project200.domain.usecase.GetExerciseRecordDetailUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@HiltViewModel
class ExerciseFormViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getExerciseRecordDetailUseCase: GetExerciseRecordDetailUseCase
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

    val dateTimeFormatter: DateTimeFormatter = MM_DD_DAY_HH_MM_KOREAN

    // 수정/생성 모드 관련
    private var initialRecord: ExerciseRecord? = null // 수정 시 초기 데이터 저장
    private var isEditMode = false
    private val removedPictureIds = mutableListOf<Long>() // 삭제할 기존 이미지 ID 목록

    private val _initialDataLoaded = MutableLiveData<ExerciseRecord?>()
    val initialDataLoaded: LiveData<ExerciseRecord?> = _initialDataLoaded

    private val _submissionResult = MutableLiveData<Boolean>()
    val submissionResult: LiveData<Boolean> = _submissionResult

    private val _toastMessage = MutableLiveData<String>()
    val toastMessage: LiveData<String> = _toastMessage

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
                when (val result = getExerciseRecordDetailUseCase.invoke(recordId)) {
                    is BaseResult.Success -> {
                        setupEditMode(result.data)
                    }
                    is BaseResult.Error -> {
                        _toastMessage.value = "기록을 불러오는데 실패했습니다"
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

        record.pictures?.forEach { picture ->
            _imageItems.value?.add(ExerciseImageListItem.ExistingImageItem(picture.url, picture.id))
        }

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
        val imageCount = _imageItems.value?.count { it !is ExerciseImageListItem.AddButtonItem } ?: 0
        return MAX_IMAGE - imageCount
    }

    /** 변경 사항이 있는지 확인 */
    private fun hasChanges(title: String, type: String, location: String, detail: String): Boolean {
        initialRecord?.let { record ->
            val imagesChanged = removedPictureIds.isNotEmpty() ||
                    _imageItems.value?.any { it is ExerciseImageListItem.NewImageItem } == true

            return record.title != title ||
                    record.personalType != type ||
                    record.location != location ||
                    record.detail != detail ||
                    record.startedAt != _startTime.value ||
                    record.endedAt != _endTime.value ||
                    imagesChanged
        }
        return true
    }

    /** 기록 제출 또는 업데이트 */
    fun submitRecord(title: String, type: String, location: String, detail: String) {
        // 변경 사항이 없으면 토스트 알림
        if (isEditMode && !hasChanges(title, type, location, detail)) {
            return
        }

        // 유효성 검사
        if (title.isBlank() || _startTime.value == null || _endTime.value == null) {
            _submissionResult.value = false
            return
        }

        val newImageUris = _imageItems.value
            ?.filterIsInstance<ExerciseImageListItem.NewImageItem>()
            ?.map { it.uri } ?: emptyList()

        // TODO: 실제 서버 통신 로직 구현 (Repository/UseCase 호출)

        _submissionResult.value = true
    }
}