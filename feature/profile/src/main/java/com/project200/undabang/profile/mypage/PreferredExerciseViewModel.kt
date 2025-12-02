package com.project200.undabang.profile.mypage

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.map
import androidx.lifecycle.viewModelScope
import com.project200.domain.model.BaseResult
import com.project200.domain.model.PreferredExercise
import com.project200.domain.usecase.CreatePreferredExerciseUseCase
import com.project200.domain.usecase.DeletePreferredExerciseUseCase
import com.project200.domain.usecase.EditPreferredExerciseUseCase
import com.project200.domain.usecase.GetPreferredExerciseTypesUseCase
import com.project200.domain.usecase.GetPreferredExerciseUseCase
import com.project200.undabang.profile.utils.CompletionState
import com.project200.undabang.profile.utils.PreferredExerciseUiModel
import com.project200.undabang.profile.utils.SkillLevel // 추가
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PreferredExerciseViewModel @Inject constructor(
    private val getPreferredExerciseUseCase: GetPreferredExerciseUseCase,
    private val getPreferredExerciseTypesUseCase: GetPreferredExerciseTypesUseCase,
    private val createPreferredExerciseUseCase: CreatePreferredExerciseUseCase,
    private val editPreferredExerciseUseCase: EditPreferredExerciseUseCase,
    private val deletePreferredExerciseUseCase: DeletePreferredExerciseUseCase,
) : ViewModel() {

    var nickname: String = ""

    // 전체 운동 종류 목록
    private val _exerciseTypes = MutableLiveData<List<PreferredExercise>>()
    // 선택한 운동 종류
    private val _preferredExercise = MutableLiveData<List<PreferredExercise>>()

    private var initialPreferredExercises: List<PreferredExercise> = emptyList()

    private val _completionState = MutableLiveData<CompletionState>(CompletionState.Idle)
    val completionState: LiveData<CompletionState> = _completionState

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
                initialPreferredExercises = preferredExerciseResult.data
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
            val serverData = selected.find { it.exerciseTypeId == exercise.exerciseTypeId }
            PreferredExerciseUiModel(
                exercise = exercise,
                isSelected = selectedTypeIds.contains(exercise.exerciseTypeId)
            ).apply {
                if (existingUiModel != null) {
                    this.selectedDays = existingUiModel.selectedDays
                    this.skillLevel = existingUiModel.skillLevel
                } else if (serverData != null) {
                    this.selectedDays = serverData.daysOfWeek.toMutableList()
                    this.skillLevel = SkillLevel.from(serverData.skillLevel)
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

    /**
     * 선호 운동 설정 완료
     */
    fun completePreferredExerciseChanges() {
        _completionState.value = CompletionState.Loading

        val selectedUiModels = exerciseUiModels.value?.filter { it.isSelected } ?: emptyList()
        val currentPreferredExercises = selectedUiModels.map { it.toModel() }

        if (validateComplete(selectedUiModels)) return

        // 변경사항을 Create, Edit, Delete로 분류
        // 빠른 조회를 위해 맵으로 변환
        val initialMap = initialPreferredExercises.associateBy { it.exerciseTypeId }
        val currentMap = currentPreferredExercises.associateBy { it.exerciseTypeId }

        val toDelete = initialPreferredExercises.filter { !currentMap.containsKey(it.exerciseTypeId) }
        val toCreate = mutableListOf<PreferredExercise>()
        val toEdit = mutableListOf<PreferredExercise>()

        currentPreferredExercises.forEach { current ->
            val initial = initialMap[current.exerciseTypeId]
            if (initial == null) {
                // 생성 목록
                toCreate.add(current)
            } else if (initial != current) {
                // 수정 목록
                toEdit.add(current)
            }
        }

        viewModelScope.launch {
            val tasks = mutableListOf<Deferred<BaseResult<Any>>>()

            if (toDelete.isNotEmpty()) {
                val deleteIds = toDelete.map { it.preferredExerciseId }
                tasks.add(async { deletePreferredExerciseUseCase(deleteIds) })
            }

            if (toCreate.isNotEmpty()) {
                tasks.add(async { createPreferredExerciseUseCase(toCreate) })
            }

            if (toEdit.isNotEmpty()) {
                tasks.add(async { editPreferredExerciseUseCase(toEdit) })
            }

            val results = tasks.awaitAll()
            if (results.all { it is BaseResult.Success }) {
                _completionState.value = CompletionState.Success
            } else {
                val firstError = results.filterIsInstance<BaseResult.Error>().firstOrNull()
                _completionState.value = CompletionState.Error(firstError?.message.toString())
            }
        }
    }

    /**
     * 변경사항이 없는지 확인하는 함수
     */
    private fun validateComplete(selectedUiModels: List<PreferredExerciseUiModel>): Boolean {
        // 선택된 운동이 하나도 없는 경우
        if (selectedUiModels.isEmpty()) {
            _completionState.value = CompletionState.NoneSelected
            return true
        }
        // 선택된 운동 중 요일이나 숙련도가 누락된 경우
        val hasIncompleteSelection = selectedUiModels.any { uiModel ->
            !uiModel.selectedDays.contains(true) || uiModel.skillLevel == null
        }
        if (hasIncompleteSelection) {
            _completionState.value = CompletionState.IncompleteSelection
            return true
        }
        // 실제 내용 변경이 없는 경우
        val currentExercises = selectedUiModels.map { it.toModel() }
        if (areListsEqual(initialPreferredExercises, currentExercises)) {
            _completionState.value = CompletionState.NoChanges
            return true
        }
        return false
    }

    fun consumeCompletionState() {
        _completionState.value = CompletionState.Idle
    }

    /**
     * 두 PreferredExercise 리스트의 내용이 완전히 동일한지 비교하는 함수
     */
    private fun areListsEqual(initial: List<PreferredExercise>, current: List<PreferredExercise>): Boolean {
        if (initial.size != current.size) return false
        return initial.toSet() == current.toSet()
    }
}