package com.project200.undabang.profile.mypage

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.project200.domain.model.BaseResult
import com.project200.domain.model.PreferredExercise
import com.project200.domain.usecase.GetPreferredExerciseTypesUseCase
import com.project200.domain.usecase.GetPreferredExerciseUseCase
import com.project200.undabang.profile.utils.PreferredExerciseUiModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PreferredExerciseViewModel @Inject constructor(
    private val getPreferredExerciseUseCase: GetPreferredExerciseUseCase,
    private val getPreferredExerciseTypesUseCase: GetPreferredExerciseTypesUseCase,
) : ViewModel() {

    var nickname: String = ""

    // 전체 운동 종류 목록
    private val _exerciseTypes = MutableLiveData<List<PreferredExercise>>()
    // 선택한 운동 종류
    private val _preferredExercise = MutableLiveData<List<PreferredExercise>>()

    val exerciseUiModels = MediatorLiveData<List<PreferredExerciseUiModel>>()

    init {
        exerciseUiModels.addSource(_exerciseTypes) { updateUiModels() }
        exerciseUiModels.addSource(_preferredExercise) { updateUiModels() }
        fetchInitialData()
    }

    fun initNickname(nickName: String) {
        this.nickname = nickName
    }

    /**
     * 전체 운동 종류와 이미 선택된 선호 운동을 모두 조회합니다.
     */
    private fun fetchInitialData() {
        viewModelScope.launch {
            val allTypes = async { getPreferredExerciseTypesUseCase() }
            val preferredExercises = async { getPreferredExerciseUseCase() }

            val allTypesResult = allTypes.await()
            val preferredExerciseResult = preferredExercises.await()

            // 전체 운동 종류 목록 설정
            if (allTypesResult is BaseResult.Success) {
                _exerciseTypes.value = allTypesResult.data
            } else {
                // TODO: 전체 목록 로드 실패 시 에러 처리
            }

            // 기존에 선택된 선호 운동 목록 설정
            if (preferredExerciseResult is BaseResult.Success) {
                _preferredExercise.value = preferredExerciseResult.data
            } else {
                // TODO: 선호 운동 목록 로드 실패 시 에러 처리
            }
        }
    }

    /**
     * _exerciseTypes 또는 _preferredExercise가 변경될 때마다 호출되어 UiModel을 설정합니다.
     */
    private fun updateUiModels() {
        val allTypes = _exerciseTypes.value ?: return
        val selected = _preferredExercise.value ?: emptyList()
        val selectedTypeIds = selected.map { it.exerciseTypeId }.toSet()

        val uiList = allTypes.map { exercise ->
            PreferredExerciseUiModel(
                exercise = exercise,
                isSelected = selectedTypeIds.contains(exercise.exerciseTypeId)
            )
        }
        exerciseUiModels.value = uiList
    }

    /**
     * 선택된 운동 목록을 업데이트합니다.
     */
    fun updateSelectedExercise(exercise: PreferredExercise) {
        val currentSelected = _preferredExercise.value?.toMutableList() ?: mutableListOf()
        val isSelected = currentSelected.any { it.exerciseTypeId == exercise.exerciseTypeId }

        if (isSelected) {
            currentSelected.removeAll { it.exerciseTypeId == exercise.exerciseTypeId }
        } else {
            currentSelected.add(exercise)
        }
        _preferredExercise.value = currentSelected
    }
}