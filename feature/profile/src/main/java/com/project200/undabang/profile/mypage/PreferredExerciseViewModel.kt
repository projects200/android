package com.project200.undabang.profile.mypage

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.map
import androidx.lifecycle.viewModelScope
import com.project200.domain.model.BaseResult
import com.project200.domain.model.PreferredExercise
import com.project200.domain.usecase.GetPreferredExerciseTypesUseCase
import com.project200.domain.usecase.GetPreferredExerciseUseCase
import com.project200.undabang.profile.utils.PreferredExerciseUiModel
import com.project200.undabang.profile.utils.SkillLevel // 추가
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

    val selectedExerciseUiModels: LiveData<List<PreferredExerciseUiModel>> = exerciseUiModels.map { list ->
        list.filter { it.isSelected }
    }

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
            // 기존에 생성된 UI 모델이 있다면 상세 정보를 유지, 없다면 새로 생성
            val existingUiModel = exerciseUiModels.value?.find { it.exercise.exerciseTypeId == exercise.exerciseTypeId }
            PreferredExerciseUiModel(
                exercise = exercise,
                isSelected = selectedTypeIds.contains(exercise.exerciseTypeId)
            ).apply {
                if (existingUiModel != null) {
                    this.selectedDays = existingUiModel.selectedDays
                    this.skillLevel = existingUiModel.skillLevel
                }
            }
        }
        exerciseUiModels.value = uiList
    }

    /**
     * 특정 운동의 요일 선택 상태를 업데이트합니다.
     */
    fun updateDaySelection(exerciseTypeId: Long, dayIndex: Int) {
        val currentModels = exerciseUiModels.value ?: return
        val newModels = currentModels.map { uiModel ->
            if (uiModel.exercise.exerciseTypeId == exerciseTypeId) {
                uiModel.copy(selectedDays = uiModel.selectedDays.toMutableList().apply {
                    this[dayIndex] = !this[dayIndex]
                })
            } else { uiModel }
        }
        exerciseUiModels.value = newModels
    }

    /**
     * 특정 운동의 숙련도를 업데이트합니다.
     */
    fun updateSkillLevel(exerciseTypeId: Long, skill: SkillLevel) {
        val currentModels = exerciseUiModels.value ?: return
        val newModels = currentModels.map { uiModel ->
            if (uiModel.exercise.exerciseTypeId == exerciseTypeId) {
                uiModel.copy(skillLevel = if (uiModel.skillLevel == skill) null else skill)
            } else { uiModel }
        }
        exerciseUiModels.value = newModels
    }

    /**
     * 운동 종류 선택/해제
     */
    fun updateSelectedExercise(exercise: PreferredExercise) {
        val list = _preferredExercise.value?.toMutableList() ?: mutableListOf()
        if (!list.removeAll { it.exerciseTypeId == exercise.exerciseTypeId }) {
            list.add(exercise)
        }

        _preferredExercise.value = list
    }
}