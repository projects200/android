package com.project200.undabang.auth.register

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.project200.common.utils.toLocalDate
import com.project200.domain.model.BaseResult
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

    private val _signUpResult = MutableLiveData<BaseResult<Unit>>()
    val signUpResult: LiveData<BaseResult<Unit>> = _signUpResult


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
            _signUpResult.value = BaseResult.Error(
                errorCode = ERROR_CODE_INVALID_NICKNAME,
                message = ERROR_MESSAGE_INVALID_NICKNAME,
                cause = null
            )
            return
        }

        if (currentGender == null || currentBirthStr == null) {
            _signUpResult.value = BaseResult.Error(
                errorCode = FORM_INCOMPLETE,
                message = ERROR_MESSAGE_FORM_INCOMPLETE,
                cause = null
            )
            return
        }

        viewModelScope.launch {
            val birthDate = currentBirthStr.toLocalDate() ?: LocalDate.now()

            when(val result = signUpUseCase(currentGender, currentNickname, birthDate)) {
                is BaseResult.Success -> {
                    _signUpResult.value = BaseResult.Success(Unit)
                }
                is BaseResult.Error -> {
                    _signUpResult.value = BaseResult.Error(
                        errorCode = result.errorCode,
                        message = result.message,
                        cause = result.cause
                    )
                }
            }
        }
    }

    companion object {
        const val ERROR_CODE_INVALID_NICKNAME = "INVALID_NICKNAME"
        const val ERROR_MESSAGE_INVALID_NICKNAME = "닉네임 조건을 확인해주세요."
        const val ERROR_MESSAGE_FORM_INCOMPLETE = "생일과 성별을 모두 선택해주세요."
        const val ERROR_MESSAGE_NICKNAME_DUPLICATED = "이미 사용 중인 닉네임입니다."
        const val FORM_INCOMPLETE = "FORM_INCOMPLETE"
    }
}