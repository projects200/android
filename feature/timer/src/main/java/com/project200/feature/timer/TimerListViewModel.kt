package com.project200.feature.timer

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.project200.domain.model.BaseResult
import com.project200.domain.model.CustomTimer
import com.project200.domain.usecase.GetCustomTimerListUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TimerListViewModel @Inject constructor(
    private val getCustomTimerListUseCase: GetCustomTimerListUseCase
) : ViewModel() {

    private val _customTimerList = MutableLiveData<List<CustomTimer>>()
    val customTimerList: LiveData<List<CustomTimer>> = _customTimerList

    private val _errorToast = MutableSharedFlow<BaseResult.Error>()
    val errorToast: SharedFlow<BaseResult.Error> = _errorToast.asSharedFlow()

    init {
        loadCustomTimers()
    }

    fun loadCustomTimers() {
        viewModelScope.launch {
            when (val result = getCustomTimerListUseCase()) {
                is BaseResult.Success -> {
                    _customTimerList.value = result.data
                }
                is BaseResult.Error -> {
                    _errorToast.emit(result)
                }
            }
        }
    }
}