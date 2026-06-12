package com.project200.undabang.auth.register

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.project200.common.utils.toLocalDate
import com.project200.domain.model.BaseResult
import com.project200.domain.usecase.SignUpUseCase
import com.project200.domain.usecase.ValidateNicknameUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class RegisterViewModel
    @Inject
    constructor(
        private val signUpUseCase: SignUpUseCase,
        private val validateNicknameUseCase: ValidateNicknameUseCase,
    ) : ViewModel() {
        private val _nickname = MutableStateFlow("")
        val nickname: StateFlow<String> = _nickname.asStateFlow()

        private val _birth = MutableStateFlow<String?>(null)
        val birth: StateFlow<String?> = _birth.asStateFlow()

        private val _gender = MutableStateFlow<String?>(null)
        val gender: StateFlow<String?> = _gender.asStateFlow()

        val isFormValid: StateFlow<Boolean> =
            combine(_nickname, _birth, _gender) { nickname, birth, gender ->
                nickname.isNotEmpty() && !birth.isNullOrEmpty() && !gender.isNullOrEmpty()
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = false,
            )

        private val _signUpResult = MutableSharedFlow<BaseResult<Unit>>()
        val signUpResult: SharedFlow<BaseResult<Unit>> = _signUpResult.asSharedFlow()

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
                val currentNickname = _nickname.value
                val currentGender = _gender.value
                val currentBirthStr = _birth.value

                if (!validateNicknameUseCase(currentNickname)) {
                    _signUpResult.emit(
                        BaseResult.Error(
                            errorCode = ERROR_CODE_INVALID_NICKNAME,
                            message = "",
                            cause = null,
                        ),
                    )
                    return@launch
                }

                if (currentGender == null || currentBirthStr == null) {
                    _signUpResult.emit(
                        BaseResult.Error(
                            errorCode = FORM_INCOMPLETE,
                            message = "",
                            cause = null,
                        ),
                    )
                    return@launch
                }

                val birthDate = currentBirthStr.toLocalDate() ?: LocalDate.now()

                when (val result = signUpUseCase(currentGender, currentNickname, birthDate)) {
                    is BaseResult.Success -> {
                        _signUpResult.emit(BaseResult.Success(Unit))
                    }
                    is BaseResult.Error -> {
                        _signUpResult.emit(
                            BaseResult.Error(
                                errorCode = result.errorCode,
                                message = result.message,
                                cause = result.cause,
                            ),
                        )
                    }
                }
            }
        }

        companion object {
            const val ERROR_CODE_INVALID_NICKNAME = "INVALID_NICKNAME"
            const val FORM_INCOMPLETE = "FORM_INCOMPLETE"
        }
    }
