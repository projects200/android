package com.project200.feature.timer.custom

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.project200.domain.model.BaseResult
import com.project200.domain.model.CustomTimerValidationResult
import com.project200.domain.model.Step
import com.project200.domain.usecase.CreateCustomTimerUseCase
import com.project200.domain.usecase.EditCustomTimerUseCase
import com.project200.domain.usecase.GetCustomTimerUseCase
import com.project200.domain.usecase.ValidateCustomTimerUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.Collections
import javax.inject.Inject

@HiltViewModel
class CustomTimerFormViewModel
    @Inject
    constructor(
        private val validateCustomTimerUseCase: ValidateCustomTimerUseCase,
        private val getCustomTimerUseCase: GetCustomTimerUseCase,
        private val createCustomTimerUseCase: CreateCustomTimerUseCase,
        private val editCustomTimerUseCase: EditCustomTimerUseCase,
    ) : ViewModel() {
        private val _uiState =
            MutableStateFlow(
                CustomTimerFormUiState(
                    listItems = listOf(TimerFormListItem.FooterItem(name = "", time = DEFAULT_TIME)),
                ),
            )
        val uiState: StateFlow<CustomTimerFormUiState> = _uiState.asStateFlow()

        private val _toast = MutableSharedFlow<ToastMessageType>()
        val toast: SharedFlow<ToastMessageType> = _toast.asSharedFlow()

        private val _submitResult = MutableSharedFlow<String>()
        val submitResult: SharedFlow<String> = _submitResult.asSharedFlow()

        // 원본 데이터 저장 (수정 모드에서 변경 사항 취소 시 사용)
        private var originalTitle: String = ""
        private var originalSteps: List<Step> = emptyList()

        // 수정 대상 타이머의 localId. 생성 모드는 이 값이 없는 경우이다
        private var customTimerLocalId: String? = null
        val isEditMode: Boolean
            get() = customTimerLocalId != null

        fun loadData(localId: String?) {
            customTimerLocalId = localId
            if (localId != null) {
                viewModelScope.launch {
                    when (val result = getCustomTimerUseCase(localId)) {
                        is BaseResult.Success -> {
                            originalTitle = result.data.name
                            originalSteps = result.data.steps

                            _uiState.value =
                                CustomTimerFormUiState(
                                    title = result.data.name,
                                    listItems =
                                        result.data.steps.map { TimerFormListItem.StepItem(it) } +
                                            TimerFormListItem.FooterItem(name = "", time = DEFAULT_TIME),
                                )
                        }
                        is BaseResult.Error -> {
                            _toast.emit(ToastMessageType.GET_ERROR)
                        }
                    }
                }
            } else {
                _uiState.value =
                    CustomTimerFormUiState(
                        listItems = listOf(TimerFormListItem.FooterItem(name = "", time = DEFAULT_TIME)),
                    )
            }
        }

        fun updateTimerTitle(title: String) {
            _uiState.update { it.copy(title = title) }
        }

        fun updateNewStepName(name: String) {
            _uiState.update { current ->
                current.copy(
                    listItems =
                        current.listItems.map {
                            if (it is TimerFormListItem.FooterItem) it.copy(name = name) else it
                        },
                )
            }
        }

        fun updateNewStepTime(time: Int) {
            _uiState.update { current ->
                current.copy(
                    listItems =
                        current.listItems.map {
                            if (it is TimerFormListItem.FooterItem) it.copy(time = time) else it
                        },
                )
            }
        }

        fun addStep() {
            val current = _uiState.value
            if (current.listItems.size >= MAX_STEP_SIZE) {
                viewModelScope.launch { _toast.emit(ToastMessageType.MAX_STEPS) }
                return
            }
            val footer = current.listItems.last() as? TimerFormListItem.FooterItem ?: return

            // order는 서버 스텝 id가 사라진 자리를 대신하는 화면 내 식별자다. 현재 목록의 최댓값 + 1로
            // 매겨 삭제 후 재추가에도 값이 겹치지 않게 한다
            val existingOrders = current.listItems.mapNotNull { (it as? TimerFormListItem.StepItem)?.step?.order }
            val newOrder = (existingOrders.maxOrNull() ?: -1) + 1

            val newStep =
                Step(
                    order = newOrder,
                    time = footer.time,
                    name = if (footer.name.isBlank()) "Step" else footer.name,
                )

            val newList =
                current.listItems.dropLast(1) +
                    TimerFormListItem.StepItem(newStep) +
                    footer.copy(name = "", time = DEFAULT_TIME)
            _uiState.value = current.copy(listItems = newList)
        }

        fun removeStep(order: Int) {
            _uiState.update { current ->
                current.copy(
                    listItems =
                        current.listItems.filterNot { item ->
                            item is TimerFormListItem.StepItem && item.step.order == order
                        },
                )
            }
        }

        fun updateStepName(
            order: Int,
            name: String,
        ) {
            _uiState.update { current ->
                current.copy(
                    listItems =
                        current.listItems.map { item ->
                            if (item is TimerFormListItem.StepItem && item.step.order == order) {
                                item.copy(step = item.step.copy(name = name))
                            } else {
                                item
                            }
                        },
                )
            }
        }

        fun updateStepTime(
            order: Int,
            time: Int,
        ) {
            _uiState.update { current ->
                current.copy(
                    listItems =
                        current.listItems.map { item ->
                            if (item is TimerFormListItem.StepItem && item.step.order == order) {
                                item.copy(step = item.step.copy(time = time))
                            } else {
                                item
                            }
                        },
                )
            }
        }

        fun moveStep(
            fromPosition: Int,
            toPosition: Int,
        ) {
            _uiState.update { current ->
                val mutableList = current.listItems.toMutableList()
                // Footer는 항상 마지막에 있어야 하므로, Footer가 아닌 아이템만 스왑 대상
                if (fromPosition < mutableList.size - 1 && toPosition < mutableList.size - 1) {
                    Collections.swap(mutableList, fromPosition, toPosition)
                }
                current.copy(listItems = mutableList)
            }
        }

        fun getStepsWithFinalOrder(): List<Step> {
            val currentSteps = _uiState.value.listItems.mapNotNull { it as? TimerFormListItem.StepItem }
            return currentSteps.mapIndexed { index, stepItem ->
                stepItem.step.copy(order = index)
            }
        }

        fun submitCustomTimer() {
            val current = _uiState.value
            val currentSteps = current.listItems.mapNotNull { (it as? TimerFormListItem.StepItem)?.step }
            val validationResult = validateCustomTimerUseCase(current.title, currentSteps)

            if (validationResult is CustomTimerValidationResult.Success) {
                Timber.d("Validation passed, creating timer")
                if (isEditMode) {
                    editCustomTimer(current.title, getStepsWithFinalOrder())
                } else {
                    createCustomTimer(current.title, getStepsWithFinalOrder())
                }
            } else {
                val errorType =
                    when (validationResult) {
                        is CustomTimerValidationResult.EmptyTitle -> ToastMessageType.EMPTY_TITLE
                        is CustomTimerValidationResult.NoSteps -> ToastMessageType.NO_STEPS
                        is CustomTimerValidationResult.InvalidStepTime -> ToastMessageType.INVALID_STEP_TIME
                        is CustomTimerValidationResult.EmptyStepName -> ToastMessageType.EMPTY_STEP_NAME
                        else -> null
                    }
                if (errorType != null) {
                    viewModelScope.launch { _toast.emit(errorType) }
                }
            }
        }

        private fun createCustomTimer(
            title: String,
            steps: List<Step>,
        ) {
            viewModelScope.launch {
                when (val result = createCustomTimerUseCase(title, steps)) {
                    is BaseResult.Success -> _submitResult.emit(result.data)
                    is BaseResult.Error -> _toast.emit(ToastMessageType.CREATE_ERROR)
                }
            }
        }

        // 로컬이 원본이라 이름만 바뀐 경우와 스텝이 바뀐 경우를 나누지 않고 하나로 저장한다
        private fun editCustomTimer(
            title: String,
            steps: List<Step>,
        ) {
            val localId = customTimerLocalId ?: return
            if (originalTitle == title && originalSteps == steps) {
                viewModelScope.launch { _toast.emit(ToastMessageType.NO_CHANGES) }
                return
            }
            viewModelScope.launch {
                when (val result = editCustomTimerUseCase(localId, title, steps)) {
                    is BaseResult.Success -> _submitResult.emit(localId)
                    is BaseResult.Error -> _toast.emit(ToastMessageType.EDIT_ERROR)
                }
            }
        }

        companion object {
            const val DEFAULT_TIME = 60 // 기본 시간 60초
            const val MAX_STEP_SIZE = 51 // 최대 스텝 개수 (50 + Footer)
        }
    }
