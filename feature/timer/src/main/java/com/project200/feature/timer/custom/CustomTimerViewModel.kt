package com.project200.feature.timer.custom

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.project200.domain.model.Step
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class CustomTimerViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
): ViewModel() {
    private val customTimerId: Long = savedStateHandle.get<Long>("customTimerId")
        ?: throw IllegalStateException("customTimerId is required for CustomTimerViewModel")

    private val _steps = MutableLiveData<List<Step>>(listOf<Step>(
        Step(1, 1, 30 ,"준비 운동"),
        Step(2, 2, 20, "고강도 운동"),
        Step(3, 3, 10, "휴식"),
        Step(4, 4, 30, "마무리 운동")
    ))
    val steps: MutableLiveData<List<Step>> = _steps
}