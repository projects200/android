package com.project200.undabang.auth.register

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class TermsViewModel
    @Inject
    constructor() : ViewModel() {
        private val _serviceChecked = MutableStateFlow(false)
        val serviceChecked: StateFlow<Boolean> = _serviceChecked.asStateFlow()

        private val _privacyChecked = MutableStateFlow(false)
        val privacyChecked: StateFlow<Boolean> = _privacyChecked.asStateFlow()

        val isAllRequiredChecked: StateFlow<Boolean> =
            combine(_serviceChecked, _privacyChecked) { service, privacy ->
                service && privacy
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

        fun toggleService() {
            _serviceChecked.value = !_serviceChecked.value
        }

        fun togglePrivacy() {
            _privacyChecked.value = !_privacyChecked.value
        }
    }
