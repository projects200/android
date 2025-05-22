package com.project200.feature.exercise.form

import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.project200.common.constants.RuleConstants.MAX_IMAGE
import com.project200.common.utils.CommonDateTimeFormatters.MM_DD_DAY_HH_MM_KOREAN
import com.project200.domain.model.ExerciseRecord
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@HiltViewModel
class ExerciseFormViewModel @Inject constructor() : ViewModel() {

    private val _startTime = MutableLiveData<LocalDateTime?>()
    val startTime: LiveData<LocalDateTime?> = _startTime

    private val _endTime = MutableLiveData<LocalDateTime?>()
    val endTime: LiveData<LocalDateTime?> = _endTime

    private val _imageItems = MutableLiveData<MutableList<ExerciseImageListItem>>(
        mutableListOf(ExerciseImageListItem.AddButtonItem)
    )
    val imageItems: LiveData<MutableList<ExerciseImageListItem>> = _imageItems

    val dateTimeFormatter: DateTimeFormatter = MM_DD_DAY_HH_MM_KOREAN

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
            currentList.add(ExerciseImageListItem.ImageItem(uri))
        }
        _imageItems.value = currentList
    }

    fun removeImage(itemToRemove: ExerciseImageListItem.ImageItem) {
        val currentList = _imageItems.value ?: return
        currentList.remove(itemToRemove)
        _imageItems.value = currentList
    }

    fun getCurrentPermittedImageCount(): Int {
        val imageCount = _imageItems.value?.count { it is ExerciseImageListItem.ImageItem } ?: 0
        return MAX_IMAGE - imageCount
    }


    fun submitRecord(title: String, type: String, location: String, detail: String): Boolean {
        if (title.isBlank() ||
            _startTime.value == null ||
            _endTime.value == null
        )  return false // 유효성 검사 실패

        val pictureUris = _imageItems.value
            ?.filterIsInstance<ExerciseImageListItem.ImageItem>()
            ?.map { it.uri.toString() }

        val record = ExerciseRecord(
            title = title,
            detail = detail,
            personalType = type,
            startedAt = _startTime.value!!,
            endedAt = _endTime.value!!,
            location = location,
            pictureUrls = pictureUris
        )
        // TODO: 실제 저장 로직 구현

        return true
    }
}