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
class ExercisePlaceSearchViewModel
@Inject
constructor(
) : ViewModel() {

}
