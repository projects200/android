package com.project200.feature.timer.custom

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.project200.domain.model.BaseResult
import com.project200.domain.model.Step
import com.project200.domain.model.CustomTimerValidationResult
import com.project200.domain.usecase.CreateCustomTimerUseCase
import com.project200.domain.usecase.EditCustomTimerNameUseCase
import com.project200.domain.usecase.EditCustomTimerUseCase
import com.project200.domain.usecase.GetCustomTimerUseCase
import com.project200.domain.usecase.ValidateCustomTimerUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.Collections
import javax.inject.Inject


@HiltViewModel
class CustomTimerFormViewModel @Inject constructor(
    private val validateCustomTimerUseCase: ValidateCustomTimerUseCase,
    private val getCustomTimerUseCase: GetCustomTimerUseCase,
    private val createCustomTimerUseCase: CreateCustomTimerUseCase,
    private val editCustomTimerUseCase: EditCustomTimerUseCase,
) : ViewModel() {

    private val _uiState = MutableLiveData<CustomTimerFormUiState>()
    val uiState: LiveData<CustomTimerFormUiState> = _uiState

    private val _toast = MutableLiveData<ToastMessageType>()
    val toast: LiveData<ToastMessageType> = _toast

    private val _submitResult = MutableLiveData<Long>()
    val submitResult: LiveData<Long> = _submitResult

    // 원본 데이터 저장 (수정 모드에서 변경 사항 취소 시 사용)
    private var originalTitle: String = ""
    private var originalSteps: List<Step> = emptyList()

    // 타이머 id 저장
    private var customTimerId: Long = DEFAULT_DUMMY_ID
    val isEditMode: Boolean
        get() = customTimerId != DEFAULT_DUMMY_ID

    // 로컬에서만 사용하는 임시 ID. 음수 값으로 서버 ID와 충돌 방지
    private var localIdCounter = DEFAULT_DUMMY_ID

    init {
        // 초기 상태: 푸터 아이템만 있는 리스트
        _uiState.value = CustomTimerFormUiState(
            listItems = listOf(TimerFormListItem.FooterItem(name = "", time = DEFAULT_TIME))
        )
    }

    fun loadData(timerId: Long) {
        if (timerId != DEFAULT_DUMMY_ID) {
            customTimerId = timerId
            // 수정 모드: 조회 api
            viewModelScope.launch {
                when ( val result = getCustomTimerUseCase(timerId)) {
                    is BaseResult.Success -> {
                        originalTitle = result.data.name
                        originalSteps = result.data.steps

                        // 리스트에 푸터 아이템 추가하고 아이템화합니다.
                        _uiState.value = CustomTimerFormUiState(
                            title = result.data.name,
                            listItems = result.data.steps.map { TimerFormListItem.StepItem(it) }
                                    + TimerFormListItem.FooterItem(name = "", time = DEFAULT_TIME)
                        )
                    }
                    is BaseResult.Error -> {
                        _toast.value = ToastMessageType.GET_ERROR
                    }
                }
            }
        } else {
            // 생성 모드: 기존 초기 상태 설정
            _uiState.value = CustomTimerFormUiState(
                listItems = listOf(TimerFormListItem.FooterItem(name = "", time = DEFAULT_TIME))
            )
        }
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
        if(currentState.listItems.size >= MAX_STEP_SIZE) {
            _toast.value = ToastMessageType.MAX_STEPS
            return
        }
        val footer = currentState.listItems.last() as? TimerFormListItem.FooterItem ?: return

        val newStep = Step(
            id = localIdCounter--, // 음수 ID 할당
            order = 0, // order는 마지막에 일괄 부여하므로 임시값 사용
            time = footer.time,
            name = if(footer.name.isBlank()) "Step" else footer.name
        )

        val newStepItem = TimerFormListItem.StepItem(newStep)
        val newList = currentState.listItems.dropLast(1) + newStepItem + footer.copy(name = "", time = DEFAULT_TIME)
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

    fun moveStep(fromPosition: Int, toPosition: Int) {
        _uiState.value = _uiState.value?.let { currentState ->
            val mutableList = currentState.listItems.toMutableList()
            // Footer는 항상 마지막에 있어야 하므로, Footer가 아닌 아이템만 스왑 대상으로 간주
            if (fromPosition < mutableList.size - 1 && toPosition < mutableList.size - 1) {
                Collections.swap(mutableList, fromPosition, toPosition)
            }
            currentState.copy(listItems = mutableList)
        }
    }

    fun getStepsWithFinalOrder(): List<Step> {
        val currentSteps = _uiState.value?.listItems?.mapNotNull { it as? TimerFormListItem.StepItem } ?: emptyList()
        return currentSteps.mapIndexed { index, stepItem ->
            stepItem.step.copy(order = index)
        }
    }

    fun submitCustomTimer() {
        val currentState = _uiState.value ?: return
        val currentSteps = currentState.listItems.mapNotNull { (it as? TimerFormListItem.StepItem)?.step }
        val validationResult = validateCustomTimerUseCase(currentState.title, currentSteps)

        if (validationResult is CustomTimerValidationResult.Success) {
            Timber.d("Validation passed, creating timer")
            if (isEditMode) editCustomTimer(currentState.title, getStepsWithFinalOrder())
            else createCustomTimer(currentState.title, getStepsWithFinalOrder())
        } else {
            _toast.value = when (validationResult) {
                is CustomTimerValidationResult.EmptyTitle -> ToastMessageType.EMPTY_TITLE
                is CustomTimerValidationResult.NoSteps -> ToastMessageType.NO_STEPS
                is CustomTimerValidationResult.InvalidStepTime -> ToastMessageType.INVALID_STEP_TIME
                is CustomTimerValidationResult.EmptyStepName -> ToastMessageType.EMPTY_STEP_NAME
                else -> null
            }
        }
    }

    private fun createCustomTimer(title: String, steps: List<Step>) {
        viewModelScope.launch {
            when (val result = createCustomTimerUseCase(title, steps)) {
                is BaseResult.Success -> {
                    _submitResult.value = result.data
                }
                is BaseResult.Error -> {
                    _toast.value = ToastMessageType.CREATE_ERROR
                }
            }
        }
    }

    private fun editCustomTimer(title: String, steps: List<Step>) {
        val timerId = if(customTimerId != DEFAULT_DUMMY_ID) customTimerId else return
        val hasTitleChanged = originalTitle != title
        val hasStepsChanged = originalSteps != steps
        if (!hasTitleChanged && !hasStepsChanged) {
            // 변경 사항이 없으면 api 호출 없이 토스트 메세지 출력
            _toast.value = ToastMessageType.NO_CHANGES
            return
        }
        viewModelScope.launch {
            when (editCustomTimerUseCase(hasTitleChanged, hasStepsChanged, timerId, title, steps)) {
                is BaseResult.Success -> {
                    _submitResult.value = customTimerId
                }
                is BaseResult.Error -> {
                    _toast.value = ToastMessageType.EDIT_ERROR
                }
            }
        }
    }

    companion object {
        const val DEFAULT_TIME = 60 // 기본 시간 60초
        const val DEFAULT_DUMMY_ID = -1L // 임시 ID
        const val MAX_STEP_SIZE = 51 // 최대 스텝 개수 (50 + Footer)
    }
}