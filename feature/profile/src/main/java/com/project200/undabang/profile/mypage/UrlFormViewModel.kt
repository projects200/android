package com.project200.undabang.profile.mypage

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
class UrlFormViewModel
    @Inject
    constructor(
        val addUrlUseCase: AddOpenUrlUseCase,
        val editUrlUseCase: EditOpenUrlUseCase,
    ) : ViewModel() {
        private var initialUrl: String = ""

        private var urlId: Long = -1L

        private val _confirmResult = MutableSharedFlow<BaseResult<Unit>>()
        val confirmResult: SharedFlow<BaseResult<Unit>> = _confirmResult

        fun confirmUrl(url: String) {
            viewModelScope.launch {
                _confirmResult.emit(
                    if (initialUrl.isEmpty()) {
                        addUrlUseCase(url)
                    } else {
                        editUrlUseCase(urlId, url)
                    },
                )
            }
        }

        fun setInitialUrl(
            id: Long,
            url: String,
        ) {
            urlId = id
            initialUrl = url
        }
    }
