package com.project200.undabang.profile.mypage

import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.project200.domain.model.BaseResult
import com.project200.domain.model.UserProfile
import com.project200.domain.usecase.CheckIsRegisteredUseCase
import com.project200.domain.usecase.CheckNicknameDuplicatedUseCase
import com.project200.domain.usecase.GetUserProfileUseCase
import com.project200.domain.usecase.ValidateNicknameUseCase
import com.project200.undabang.profile.utils.NicknameValidationState
import com.project200.undabang.profile.utils.ProfileEditErrorType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfileEditViewModel
@Inject
constructor(
    private val validateNicknameUseCase: ValidateNicknameUseCase,
    private val getUserProfileUseCase: GetUserProfileUseCase,
    private val checkNicknameDuplicatedUseCase: CheckNicknameDuplicatedUseCase,
) : ViewModel() {
    private val _initProfile = MutableLiveData<UserProfile>()
    val initProfile: LiveData<UserProfile> = _initProfile

    private val _nickname = MutableLiveData("")
    val nickname: LiveData<String> = _nickname

    private val _gender = MutableLiveData<String>()
    val gender: LiveData<String?> = _gender

    private val _introduction = MutableLiveData<String>("")
    val introduction: LiveData<String> = _introduction

    private val _newProfileImageUri = MutableLiveData<Uri?>()
    val newProfileImageUri: LiveData<Uri?> get() = _newProfileImageUri

    // 닉네임 유효성 검사 결과를 UI에 전달하기 위한 LiveData
    private val _nicknameValidationState =
        MutableLiveData<NicknameValidationState>(NicknameValidationState.INVISIBLE)
    val nicknameValidationState: LiveData<NicknameValidationState> = _nicknameValidationState

    // 중복 확인 버튼 활성화 여부 및 중복 체크 완료 상태를 관리
    private val _isNicknameChecked = MutableLiveData(false)
    val isNicknameChecked: LiveData<Boolean> = _isNicknameChecked

    private val _errorType = MutableSharedFlow<ProfileEditErrorType>()
    val errorType: SharedFlow<ProfileEditErrorType> = _errorType

    init {
        getProfile()
    }

    fun updateNickname(value: String) {
        if (_nickname.value != value) {
            _nickname.value = value
            _isNicknameChecked.value = false
            _nicknameValidationState.value = NicknameValidationState.INVISIBLE
        }
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

    fun checkIsNicknameDuplicated() {
        val currentNickname = _nickname.value.orEmpty()

        // 기존 닉네임과 동일한지 체크
        if (currentNickname == _initProfile.value?.nickname) {
            viewModelScope.launch {
                _errorType.emit(ProfileEditErrorType.SAME_AS_ORIGINAL)
            }
            return
        }

        // 닉네임 유효성 검사
        if (!validateNicknameUseCase(currentNickname)) {
            _nicknameValidationState.value = NicknameValidationState.INVALID
            return
        }

        // 유효성 검사를 통과하면 API 호출
        viewModelScope.launch {
            when (val result = checkNicknameDuplicatedUseCase(currentNickname)) {
                is BaseResult.Success -> {
                    if (!result.data) { // true = 사용가능
                        _nicknameValidationState.value = NicknameValidationState.DUPLICATED
                    } else { // false = 중복
                        _nicknameValidationState.value = NicknameValidationState.AVAILABLE
                        _isNicknameChecked.value = true // 중복 체크 완료 플래그 활성화
                    }
                }

                is BaseResult.Error -> {
                    _errorType.emit(ProfileEditErrorType.CHECK_DUPLICATE_FAILED)
                }
            }
        }
    }
}
