package com.project200.feature.matching.place

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.project200.domain.model.BaseResult
import com.project200.domain.model.ExercisePlace
import com.project200.domain.usecase.DeleteExercisePlaceUseCase
import com.project200.domain.usecase.GetExercisePlaceUseCase
import com.project200.feature.matching.utils.ExercisePlaceErrorType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ExercisePlaceViewModel
    @Inject
    constructor(
        private val getExercisePlaceUseCase: GetExercisePlaceUseCase,
        private val deleteExercisePlaceUseCase: DeleteExercisePlaceUseCase,
    ) : ViewModel() {
        private val _places = MutableStateFlow<List<ExercisePlace>>(emptyList())
        val places: StateFlow<List<ExercisePlace>> = _places.asStateFlow()

        private val _errorToast = MutableSharedFlow<ExercisePlaceErrorType>()
        val errorToast: SharedFlow<ExercisePlaceErrorType> = _errorToast.asSharedFlow()

        fun getExercisePlaces() {
            viewModelScope.launch {
                when (val result = getExercisePlaceUseCase()) {
                    is BaseResult.Success -> {
                        _places.value = result.data
                    }
                    is BaseResult.Error -> {
                        _errorToast.emit(ExercisePlaceErrorType.LOAD_FAILED)
                    }
                }
            }
        }

        fun deleteExercisePlace(placeId: Long) {
            viewModelScope.launch {
                when (deleteExercisePlaceUseCase(placeId)) {
                    is BaseResult.Success -> {
                        _places.value = _places.value.filterNot { it.id == placeId }
                    }
                    is BaseResult.Error -> {
                        _errorToast.emit(ExercisePlaceErrorType.DELETE_FAILED)
                    }
                }
            }
        }
    }
