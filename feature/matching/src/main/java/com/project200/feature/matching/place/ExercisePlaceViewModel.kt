package com.project200.feature.matching.place

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.project200.domain.model.BaseResult
import com.project200.domain.model.ExercisePlace
import com.project200.domain.usecase.DeleteExercisePlaceUseCase
import com.project200.domain.usecase.GetExercisePlaceUseCase
import com.project200.feature.matching.utils.ExercisePlaceErrorType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ExercisePlaceViewModel @Inject constructor(
    private val getExercisePlaceUseCase: GetExercisePlaceUseCase,
    private val deleteExercisePlaceUseCase: DeleteExercisePlaceUseCase
): ViewModel() {
    private val _places = MutableLiveData<List<ExercisePlace>>()
    val places: LiveData<List<ExercisePlace>> = _places

    private val _errorToast = MutableLiveData<ExercisePlaceErrorType>()
    val errorToast: LiveData<ExercisePlaceErrorType> = _errorToast

    init {
        getExercisePlaces()
    }

    private fun getExercisePlaces() {
        viewModelScope.launch {
            when(val result = getExercisePlaceUseCase()) {
                is BaseResult.Success -> {
                    _places.value = result.data
                }
                is BaseResult.Error -> {
                    _errorToast.value = ExercisePlaceErrorType.LOAD_FAILED
                }
            }
        }
    }

    fun deleteExercisePlace(placeId: Long) {
        viewModelScope.launch {
            when(deleteExercisePlaceUseCase(placeId)) {
                is BaseResult.Success -> {
                    _places.value = _places.value?.filterNot { it.id == placeId }
                }
                is BaseResult.Error -> {
                    _errorToast.value = ExercisePlaceErrorType.DELETE_FAILED
                }
            }
        }
    }
}