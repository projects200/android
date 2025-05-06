package com.project200.presentation

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.project200.domain.usecase.CheckForUpdateUseCase
import com.project200.domain.usecase.UpdateCheckResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val checkForUpdateUseCase: CheckForUpdateUseCase
) : ViewModel() {

    private val _updateCheckResult = MutableLiveData<UpdateCheckResult>()
    val updateCheckResult: LiveData<UpdateCheckResult> = _updateCheckResult

    // 업데이트 확인
    fun checkForUpdate() {
        if (_updateCheckResult.value != null) { return } // 이미 체크했다면 스킵

        viewModelScope.launch {
            checkForUpdateUseCase()
                .onSuccess { result ->
                    _updateCheckResult.value = result
                    when (result) {
                        is UpdateCheckResult.UpdateAvailable -> Timber.d("업데이트 가능 isForce: ${result.isForceUpdate}")
                        is UpdateCheckResult.NoUpdateNeeded -> Timber.d("업데이트 불가능")
                    }
                }
                .onFailure { error ->
                    Timber.e(error, "ViewModel: 업데이트 확인 실패")
                }
        }
    }
}