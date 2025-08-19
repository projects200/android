package com.project200.feature.timer.custom

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.project200.domain.model.Step
import com.project200.domain.model.ValidationResult
import com.project200.domain.usecase.ValidateCustomTimerUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

sealed interface TimerFormListItem {
    val id: Long

    data class StepItem(val step: Step) : TimerFormListItem {
        override val id: Long = step.id
    }

    data class FooterItem(
        val name: String,
        val time: Int
    ) : TimerFormListItem {
        override val id: Long = 0L
    }
}

data class CustomTimerFormUiState(
    val title: String = "",
    val listItems: List<TimerFormListItem> = emptyList()
)

@HiltViewModel
class CustomTimerFormViewModel @Inject constructor(
    private val validateCustomTimerUseCase: ValidateCustomTimerUseCase
) : ViewModel() {

    private val _uiState = MutableLiveData<CustomTimerFormUiState>()
    val uiState: LiveData<CustomTimerFormUiState> = _uiState

    private val _toast = MutableLiveData<ValidationResult>()
    val toast: LiveData<ValidationResult> = _toast

    private val _createResult = MutableLiveData<Long>()
    val createResult: LiveData<Long> = _createResult

    // 로컬에서만 사용하는 임시 ID. 음수 값으로 서버 ID와 충돌 방지
    private var localIdCounter = -1L

    init {
        // 초기 상태: 푸터 아이템만 있는 리스트
        _uiState.value = CustomTimerFormUiState(
            listItems = listOf(TimerFormListItem.FooterItem(name = "", time = 60))
        )
    }

    fun updateTimerTitle(title: String) {
        // 현재 상태를 가져와 title만 변경한 새 객체로 값을 업데이트
        _uiState.value = _uiState.value?.copy(title = title)
    }

    fun updateNewStepName(name: String) {
        _uiState.value = _uiState.value?.let { currentState ->
            val updatedList = currentState.listItems.map {
                if (it is TimerFormListItem.FooterItem) it.copy(name = name) else it
            }
            currentState.copy(listItems = updatedList)
        }
    }

    fun updateNewStepTime(time: Int) {
        _uiState.value = _uiState.value?.let { currentState ->
            val updatedList = currentState.listItems.map {
                if (it is TimerFormListItem.FooterItem) it.copy(time = time) else it
            }
            currentState.copy(listItems = updatedList)
        }
    }

    fun addStep() {
        val currentState = _uiState.value ?: return
        val footer = currentState.listItems.last() as? TimerFormListItem.FooterItem ?: return

        val newStep = Step(
            id = localIdCounter--, // 음수 ID 할당
            order = 0, // order는 마지막에 일괄 부여하므로 임시값 사용
            time = footer.time,
            name = if(footer.name.isBlank()) "Step" else footer.name
        )

        val newStepItem = TimerFormListItem.StepItem(newStep)
        val newList = currentState.listItems.dropLast(1) + newStepItem + footer.copy(name = "", time = 60)
        _uiState.value = currentState.copy(listItems = newList)
    }

    fun removeStep(id: Long) {
        _uiState.value = _uiState.value?.let { currentState ->
            val updatedList = currentState.listItems.filterNot { item ->
                item is TimerFormListItem.StepItem && item.step.id == id
            }
            currentState.copy(listItems = updatedList)
        }
    }

    fun updateStepName(id: Long, name: String) {
        _uiState.value = _uiState.value?.let { currentState ->
            val updatedList = currentState.listItems.map { item ->
                if (item is TimerFormListItem.StepItem && item.step.id == id) {
                    item.copy(step = item.step.copy(name = name))
                } else {
                    item
                }
            }
            currentState.copy(listItems = updatedList)
        }
    }

    fun updateStepTime(id: Long, time: Int) {
        _uiState.value = _uiState.value?.let { currentState ->
            val updatedList = currentState.listItems.map { item ->
                if (item is TimerFormListItem.StepItem && item.step.id == id) {
                    item.copy(step = item.step.copy(time = time))
                } else {
                    item
                }
            }
            currentState.copy(listItems = updatedList)
        }
    }

    fun getStepsWithFinalOrder(): List<Step> {
        val currentSteps = _uiState.value?.listItems?.mapNotNull { it as? TimerFormListItem.StepItem } ?: emptyList()
        return currentSteps.mapIndexed { index, stepItem ->
            stepItem.step.copy(order = index + 1)
        }
    }

    fun completeCustomTimerCreation() {
        val currentState = _uiState.value ?: return
        val currentSteps = currentState.listItems.mapNotNull { (it as? TimerFormListItem.StepItem)?.step }
        val validationResult = validateCustomTimerUseCase(currentState.title, currentSteps)

        if (validationResult is ValidationResult.Success) {
            val finalSteps = getStepsWithFinalOrder()
            // TODO: 서버에 전송
            _createResult.value = 1L // 임시로 1L 반환, 실제로는 서버 응답 ID 사용
        } else {
            _toast.value = validationResult
        }
    }
}