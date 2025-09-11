package com.project200.undabang.profile.mypage

import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.project200.common.utils.toLocalDate
import com.project200.domain.model.BaseResult
import com.project200.domain.model.UserProfile
import com.project200.domain.usecase.GetUserProfileUseCase
import com.project200.domain.usecase.ValidateNicknameUseCase
import com.project200.undabang.profile.utils.ProfileEditErrorType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class ProfileEditViewModel @Inject constructor(
    private val validateNicknameUseCase: ValidateNicknameUseCase,
    private val getUserProfileUseCase: GetUserProfileUseCase,
) : ViewModel() {
    private val _initProfile = MutableLiveData<UserProfile>()
    val initProfile: LiveData<UserProfile> = _initProfile

    private val _nickname = MutableLiveData("")
    val nickname: LiveData<String> = _nickname

    private val _gender = MutableLiveData<String>()
    val gender: LiveData<String?> = _gender

    private val _introduction = MutableLiveData<String>("")
    val introduction: LiveData<String> = _introduction

    private val _signUpResult = MutableLiveData<BaseResult<Unit>>()
    val signUpResult: LiveData<BaseResult<Unit>> = _signUpResult

    private val _newProfileImageUri = MutableLiveData<Uri?>()
    val newProfileImageUri: LiveData<Uri?> get() = _newProfileImageUri

    private val _errorType = MutableSharedFlow<ProfileEditErrorType>()
    val errorType: SharedFlow<ProfileEditErrorType> = _errorType

    init {
        getProfile()
    }

    fun updateNickname(value: String) {
        _nickname.value = value
    }

    fun updateIntroduction(value: String) {
        _introduction.value = value
    }

    fun selectGender(gender: String) {
        _gender.value = gender
    }

    fun getProfile() {
        viewModelScope.launch {
            when (val result = getUserProfileUseCase()) {
                is BaseResult.Success -> {
                    _initProfile.value = result.data
                }

                is BaseResult.Error -> {
                    _errorType.emit(ProfileEditErrorType.LOAD_FAILED)
                }
            }
        }
    }

    fun completeEditProfile() {
        val currentNickname = _nickname.value.orEmpty()
        val currentGender = _gender.value

        if (!validateNicknameUseCase(currentNickname)) return

        // TODO: 닉네임 중복 확인 api
    }

    fun updateProfileImageUri(uri: Uri?) {
        _newProfileImageUri.value = uri
    }
}
