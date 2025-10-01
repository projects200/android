package com.project200.feature.matching.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.project200.domain.model.BaseResult
import com.project200.domain.usecase.AddOpenUrlUseCase
import com.project200.domain.usecase.EditOpenUrlUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MatchingUrlFormViewModel @Inject constructor(
    val addUrlUseCase: AddOpenUrlUseCase,
): ViewModel() {
    private val _confirmResult = MutableSharedFlow<BaseResult<Unit>>()
    val confirmResult: SharedFlow<BaseResult<Unit>> = _confirmResult

    fun confirmUrl(url: String) {
        viewModelScope.launch {
            _confirmResult.emit(addUrlUseCase(url))
        }
    }
}