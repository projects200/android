package com.project200.feature.matching.place

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.project200.domain.model.BaseResult
import com.project200.domain.model.ExercisePlace
import com.project200.domain.usecase.DeleteExercisePlaceUseCase
import com.project200.domain.usecase.GetExercisePlaceUseCase
import com.project200.feature.matching.MatchingFixtureEvidence
import com.project200.feature.matching.utils.ExercisePlaceErrorType
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class ExercisePlaceLoadState {
    LOADING,
    READY,
    ERROR,
}

@HiltViewModel
class ExercisePlaceViewModel
    @Inject
    constructor(
        private val getExercisePlaceUseCase: GetExercisePlaceUseCase,
        private val deleteExercisePlaceUseCase: DeleteExercisePlaceUseCase,
        @ApplicationContext private val context: Context,
    ) : ViewModel() {
        private val _places = MutableStateFlow<List<ExercisePlace>>(emptyList())
        val places: StateFlow<List<ExercisePlace>> = _places.asStateFlow()

        private val _loadState = MutableStateFlow(ExercisePlaceLoadState.LOADING)
        val loadState: StateFlow<ExercisePlaceLoadState> = _loadState.asStateFlow()

        private val _errorToast = MutableSharedFlow<ExercisePlaceErrorType>()
        val errorToast: SharedFlow<ExercisePlaceErrorType> = _errorToast.asSharedFlow()

        fun getExercisePlaces() {
            viewModelScope.launch {
                _loadState.value = ExercisePlaceLoadState.LOADING
                val request = MatchingFixtureEvidence.placeLoadStarted(context)
                val result =
                    try {
                        getExercisePlaceUseCase()
                    } catch (failure: Throwable) {
                        MatchingFixtureEvidence.placeLoadThrown(context, request, failure)
                        throw failure
                    }
                MatchingFixtureEvidence.placeLoadFinished(context, request, result)
                when (result) {
                    is BaseResult.Success -> {
                        _places.value = result.data
                        _loadState.value = ExercisePlaceLoadState.READY
                    }
                    is BaseResult.Error -> {
                        _loadState.value = ExercisePlaceLoadState.ERROR
                        _errorToast.emit(ExercisePlaceErrorType.LOAD_FAILED)
                    }
                }
            }
        }

        fun deleteExercisePlace(placeId: Long) {
            viewModelScope.launch {
                _loadState.value = ExercisePlaceLoadState.LOADING
                when (deleteExercisePlaceUseCase(placeId)) {
                    is BaseResult.Success -> {
                        _places.value = _places.value.filterNot { it.id == placeId }
                        _loadState.value = ExercisePlaceLoadState.READY
                    }
                    is BaseResult.Error -> {
                        _loadState.value = ExercisePlaceLoadState.ERROR
                        _errorToast.emit(ExercisePlaceErrorType.DELETE_FAILED)
                    }
                }
            }
        }
    }
