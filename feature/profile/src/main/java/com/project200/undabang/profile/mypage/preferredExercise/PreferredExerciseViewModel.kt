package com.project200.undabang.profile.mypage.preferredExercise

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.project200.domain.model.BaseResult
import com.project200.domain.model.PreferredExercise
import com.project200.domain.usecase.CreatePreferredExerciseUseCase
import com.project200.domain.usecase.DeletePreferredExerciseUseCase
import com.project200.domain.usecase.EditPreferredExerciseUseCase
import com.project200.domain.usecase.GetPreferredExerciseTypesUseCase
import com.project200.domain.usecase.GetPreferredExerciseUseCase
import com.project200.presentation.utils.SkillLevel
import com.project200.undabang.profile.utils.CompletionState
import com.project200.undabang.profile.utils.PreferredExerciseUiModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PreferredExerciseViewModel
    @Inject
    constructor(
        private val getPreferredExerciseUseCase: GetPreferredExerciseUseCase,
        private val getPreferredExerciseTypesUseCase: GetPreferredExerciseTypesUseCase,
        private val createPreferredExerciseUseCase: CreatePreferredExerciseUseCase,
        private val editPreferredExerciseUseCase: EditPreferredExerciseUseCase,
        private val deletePreferredExerciseUseCase: DeletePreferredExerciseUseCase,
    ) : ViewModel() {
        var nickname: String = ""

        // 전체 운동 종류 목록
        private val exerciseTypes = MutableStateFlow<List<PreferredExercise>>(emptyList())

        // 선택한 운동 종류
        private val preferredExercise = MutableStateFlow<List<PreferredExercise>>(emptyList())

        private var initialPreferredExercises: List<PreferredExercise> = emptyList()

        private val _completionState = MutableStateFlow<CompletionState>(CompletionState.Idle)
        val completionState: StateFlow<CompletionState> = _completionState.asStateFlow()

        private val _exerciseUiModels = MutableStateFlow<List<PreferredExerciseUiModel>>(emptyList())
        val exerciseUiModels: StateFlow<List<PreferredExerciseUiModel>> = _exerciseUiModels.asStateFlow()

        val selectedExerciseUiModels: StateFlow<List<PreferredExerciseUiModel>> =
            _exerciseUiModels
                .map { list -> list.filter { it.isSelected } }
                .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

        init {
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
                val exerciseResult = preferredExercises.await()

                if (allTypesResult is BaseResult.Success && exerciseResult is BaseResult.Success) {
                    initialPreferredExercises = exerciseResult.data
                    exerciseTypes.value = allTypesResult.data
                    preferredExercise.value = exerciseResult.data
                    updateUiModels()
                }
            }
        }

        /**
         * exerciseTypes 또는 preferredExercise가 변경될 때 호출되어 UiModel을 갱신합니다.
         */
        private fun updateUiModels() {
            val allTypes = exerciseTypes.value
            val selected = preferredExercise.value
            val selectedTypeIds = selected.map { it.exerciseTypeId }.toSet()
            val currentUiModels = _exerciseUiModels.value

            val uiList =
                allTypes.map { exercise ->
                    val existingUiModel = currentUiModels.find { it.exercise.exerciseTypeId == exercise.exerciseTypeId }
                    val serverData = initialPreferredExercises.find { it.exerciseTypeId == exercise.exerciseTypeId }

                    PreferredExerciseUiModel(
                        exercise = exercise,
                        isSelected = selectedTypeIds.contains(exercise.exerciseTypeId),
                    ).apply {
                        // 서버 데이터가 있으면 먼저 채우고,
                        // 사용자가 수정한 기록이 있을 때만 덮어씌움
                        if (serverData != null) {
                            this.selectedDays = serverData.daysOfWeek.toMutableList()
                            this.skillLevel = SkillLevel.from(serverData.skillLevel)
                        }

                        // 만약 이미 사용자가 화면에서 조작 중이었다면 그 상태를 유지
                        if (existingUiModel != null && (
                                existingUiModel.selectedDays.contains(
                                    true,
                                ) || existingUiModel.skillLevel != null
                            )
                        ) {
                            this.selectedDays = existingUiModel.selectedDays
                            this.skillLevel = existingUiModel.skillLevel
                        }
                    }
                }
            _exerciseUiModels.value = uiList
        }

        /**
         * 특정 운동의 요일 선택 상태를 업데이트합니다.
         */
        fun updateDaySelection(
            exerciseTypeId: Long,
            dayIndex: Int,
        ) {
            val newModels =
                _exerciseUiModels.value.map { uiModel ->
                    if (uiModel.exercise.exerciseTypeId == exerciseTypeId) {
                        uiModel.copy(
                            selectedDays =
                                uiModel.selectedDays.toMutableList().apply {
                                    this[dayIndex] = !this[dayIndex]
                                },
                        )
                    } else {
                        uiModel
                    }
                }
            _exerciseUiModels.value = newModels
        }

        /**
         * 특정 운동의 숙련도를 업데이트합니다.
         */
        fun updateSkillLevel(
            exerciseTypeId: Long,
            skill: SkillLevel,
        ) {
            val newModels =
                _exerciseUiModels.value.map { uiModel ->
                    if (uiModel.exercise.exerciseTypeId == exerciseTypeId) {
                        uiModel.copy(skillLevel = if (uiModel.skillLevel == skill) null else skill)
                    } else {
                        uiModel
                    }
                }
            _exerciseUiModels.value = newModels
        }

        /**
         * 운동 종류 선택/해제
         */
        fun updateSelectedExercise(exercise: PreferredExercise) {
            val list = preferredExercise.value.toMutableList()
            if (!list.removeAll { it.exerciseTypeId == exercise.exerciseTypeId }) {
                list.add(exercise)
            }
            preferredExercise.value = list
            updateUiModels()
        }

        /**
         * 선호 운동 설정 완료
         */
        fun completePreferredExerciseChanges() {
            _completionState.value = CompletionState.Loading

            val selectedUiModels = _exerciseUiModels.value.filter { it.isSelected }
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
                } else if (initial.daysOfWeek != current.daysOfWeek || initial.skillLevel != current.skillLevel) {
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
            val hasIncompleteSelection =
                selectedUiModels.any { uiModel ->
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
        private fun areListsEqual(
            initial: List<PreferredExercise>,
            current: List<PreferredExercise>,
        ): Boolean {
            if (initial.size != current.size) return false
            return initial.toSet() == current.toSet()
        }
    }
