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
) : ViewModel() {

    private val _nickname = MutableLiveData("")
    val nickname: LiveData<String> = _nickname

    private val _birth = MutableLiveData<String?>(null)
    val birth: LiveData<String?> = _birth

    private val _gender = MutableLiveData<String?>(null)
    val gender: LiveData<String?> = _gender


    val isFormValid: LiveData<Boolean> = MediatorLiveData<Boolean>().apply {
        fun validateFormInputs() {
            val isNicknameEntered = _nickname.value.orEmpty().isNotEmpty()
            value = isNicknameEntered && !_birth.value.isNullOrEmpty() && !_gender.value.isNullOrEmpty()
        }
        addSource(_nickname) { validateFormInputs() }
        addSource(_birth) { validateFormInputs() }
        addSource(_gender) { validateFormInputs() }
    }

    private val _signUpResult = MutableLiveData<SignUpResult?>()
    val signUpResult: LiveData<SignUpResult?> = _signUpResult


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
        val currentNickname = _nickname.value.orEmpty()
        val currentGender = _gender.value
        val currentBirthStr = _birth.value


        if (!validateNicknameUseCase(currentNickname)) {
            _signUpResult.value = SignUpResult.Failure(
                errorCode = ERROR_CODE_INVALID_NICKNAME,
                errorMessage = "닉네임 조건을 확인해주세요."
            )
            return
        }

        if (currentGender == null || currentBirthStr == null) {
            _signUpResult.value = SignUpResult.Failure(
                errorCode = FORM_INCOMPLETE,
                errorMessage = "생일과 성별을 모두 선택해주세요."
            )
            return
        }

        viewModelScope.launch {
            val birthDate = currentBirthStr.toLocalDate() ?: LocalDate.now()

            _signUpResult.value = signUpUseCase(
                currentGender,
                currentNickname,
                birthDate
            )
        }
    }

    companion object {
        const val ERROR_CODE_INVALID_NICKNAME = "INVALID_NICKNAME"
        const val FORM_INCOMPLETE = "FORM_INCOMPLETE"
    }
}