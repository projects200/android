package com.project200.undabang.auth.register

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class TermsViewModel @Inject constructor() : ViewModel() {

    private val _serviceChecked = MutableLiveData(false)
    val serviceChecked: LiveData<Boolean> = _serviceChecked

    private val _privacyChecked = MutableLiveData(false)
    val privacyChecked: LiveData<Boolean> = _privacyChecked

    /* private val _locationChecked = MutableLiveData(false)
    val locationChecked: LiveData<Boolean> = _locationChecked

    private val _notifyChecked = MutableLiveData(false)
    val notifyChecked: LiveData<Boolean> = _notifyChecked */

    val isAllRequiredChecked: LiveData<Boolean> = MediatorLiveData<Boolean>().apply {
        fun update() {
            value = (_serviceChecked.value == true
                    && _privacyChecked.value == true)
        }

        addSource(_serviceChecked) { update() }
        addSource(_privacyChecked) { update() }
    }

    fun toggleService() {
        _serviceChecked.value = !(_serviceChecked.value ?: false)
    }

    fun togglePrivacy() {
        _privacyChecked.value = !(_privacyChecked.value ?: false)
    }

    /*fun toggleLocation() {
        _locationChecked.value = !(_locationChecked.value ?: false)
    }

    fun toggleNotify() {
        _notifyChecked.value = !(_notifyChecked.value ?: false)
    }*/
}
