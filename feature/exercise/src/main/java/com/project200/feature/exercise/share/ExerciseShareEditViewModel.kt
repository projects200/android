package com.project200.feature.exercise.share

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.project200.domain.model.BaseResult
import com.project200.domain.model.ExerciseRecord
import com.project200.domain.usecase.GetExerciseRecordDetailUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ExerciseShareEditViewModel @Inject constructor(
    private val getExerciseRecordDetailUseCase: GetExerciseRecordDetailUseCase
) : ViewModel() {

    private val _selectedTheme = MutableStateFlow(StickerTheme.DARK)
    val selectedTheme: StateFlow<StickerTheme> = _selectedTheme.asStateFlow()

    private val _exerciseRecord = MutableStateFlow<ExerciseRecord?>(null)
    val exerciseRecord: StateFlow<ExerciseRecord?> = _exerciseRecord.asStateFlow()

    private val _backgroundImageUrl = MutableStateFlow<String?>(null)
    val backgroundImageUrl: StateFlow<String?> = _backgroundImageUrl.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    fun loadExerciseRecord(recordId: Long) {
        viewModelScope.launch {
            _isLoading.value = true
            when (val result = getExerciseRecordDetailUseCase(recordId)) {
                is BaseResult.Success -> {
                    _exerciseRecord.value = result.data
                    _backgroundImageUrl.value = result.data.pictures?.firstOrNull()?.url
                }
                is BaseResult.Error -> {
                }
            }
            _isLoading.value = false
        }
    }

    fun selectTheme(theme: StickerTheme) {
        _selectedTheme.value = theme
    }
}
