package com.project200.undabang.auth.register

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.project200.common.utils.toLocalDate
import com.project200.domain.model.SignUpResult
import com.project200.domain.usecase.SignUpUseCase
import com.project200.domain.usecase.ValidateNicknameUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class RegisterViewModel @Inject constructor(
    private val signUpUseCase: SignUpUseCase,
    private val validateNicknameUseCase: ValidateNicknameUseCase
): ViewModel() {

    private val _nickname = MutableLiveData("")
    val nickname: LiveData<String> = _nickname

    private val _birth = MutableLiveData<String?>(null)
    val birth: LiveData<String?> = _birth

    private val _gender = MutableLiveData<String?>(null)
    val gender: LiveData<String?> = _gender

    val isFormValid: LiveData<Boolean> = MediatorLiveData<Boolean>().apply {
        fun validate() {
            val nameValid = validateNicknameUseCase.invoke(_nickname.value.orEmpty())
            value = nameValid && !(_birth.value.isNullOrEmpty()) && !(_gender.value.isNullOrEmpty())
        }
        addSource(_nickname) { validate() }
        addSource(_birth) { validate() }
        addSource(_gender) { validate() }
    }

    private val _signUpResult = MutableLiveData<SignUpResult>()
    val signUpResult: LiveData<SignUpResult> = _signUpResult

    fun updateNickname(value: String) {
        _nickname.value = value
    }

    fun updateBirth(value: String) {
        _birth.value = value
    }

    fun selectGender(gender: String) {
        _gender.value = gender
    }

    fun signUp() {
        viewModelScope.launch {
            _signUpResult.value = signUpUseCase.invoke(
                _gender.value ?: "U",
                _nickname.value ?: "",
                _birth.value.toLocalDate() ?: LocalDate.now()
            ) ?: SignUpResult.Failure("UNEXPECTED_NULL_ERROR")
        }
    }
}
