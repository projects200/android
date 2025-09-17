package com.project200.undabang.profile.mypage

import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.project200.domain.model.BaseResult
import com.project200.domain.model.UserProfile
import com.project200.domain.usecase.AddProfileImageUseCase
import com.project200.domain.usecase.CheckNicknameDuplicatedUseCase
import com.project200.domain.usecase.EditProfileUseCase
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
        private val editProfileUseCase: EditProfileUseCase,
        private val addProfileImageUseCase: AddProfileImageUseCase,
    ) : ViewModel() {
        private val _initProfile = MutableLiveData<UserProfile>()
        val initProfile: LiveData<UserProfile> = _initProfile

        private val nickname = MutableLiveData("")

        private val _gender = MutableLiveData<String>()
        val gender: LiveData<String> = _gender

        private val introduction = MutableLiveData<String>("")

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

        private val _editResult = MutableSharedFlow<Boolean>()
        val editResult: SharedFlow<Boolean> = _editResult

        init {
            getProfile()
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

        fun updateNickname(value: String) {
            if (nickname.value != value) {
                nickname.value = value
                _isNicknameChecked.value = false
                _nicknameValidationState.value = NicknameValidationState.INVISIBLE
            }
        }

        fun updateIntroduction(value: String) {
            introduction.value = value
        }

        fun updateProfileImageUri(uri: Uri?) {
            _newProfileImageUri.value = uri
        }

        fun selectGender(gender: String) {
            _gender.value = gender
        }

        fun completeEditProfile() {
            viewModelScope.launch {
                val currentNickname = nickname.value ?: return@launch
                val currentGender = _gender.value ?: return@launch
                val currentIntroduction = introduction.value.orEmpty()

                val isProfileChanged =
                    checkProfileChanged(currentNickname, currentGender, currentIntroduction)

                // 변경사항 있는지 체크
                if (_newProfileImageUri.value == null && !isProfileChanged) {
                    _errorType.emit(ProfileEditErrorType.NO_CHANGE)
                    return@launch
                }

                // 중복 확인이 됐는지 체크
                if (_initProfile.value?.nickname != currentNickname && _isNicknameChecked.value == false) {
                    _errorType.emit(ProfileEditErrorType.NO_DUPLICATE_CHECKED)
                    return@launch
                }

                // 새 프로필 사진이 있다면 호출
                // 수정된 프로필 정보가 있다면 호출
                val addProfileImageResult =
                    if (_newProfileImageUri.value != null) {
                        addProfileImageUseCase(_newProfileImageUri.value.toString())
                    } else {
                        BaseResult.Success(
                            Unit,
                        )
                    }
                val editProfileResult =
                    if (isProfileChanged) {
                        editProfileUseCase(
                            currentNickname,
                            currentGender,
                            currentIntroduction,
                        )
                    } else {
                        BaseResult.Success(Unit)
                    }

                handleEditResult(addProfileImageResult, editProfileResult)
            }
        }

        fun checkIsNicknameDuplicated() {
            val currentNickname = nickname.value.orEmpty()

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

        private fun checkProfileChanged(
            nickname: String,
            gender: String,
            introduction: String,
        ): Boolean {
            val initialProfile = _initProfile.value ?: return false
            return initialProfile.nickname != nickname ||
                initialProfile.gender != gender ||
                initialProfile.bio.orEmpty() != introduction
        }

        private fun handleEditResult(
            imageResult: BaseResult<Unit>,
            editResult: BaseResult<Unit>,
        ) {
            viewModelScope.launch {
                if (imageResult is BaseResult.Error || editResult is BaseResult.Error) {
                    _editResult.emit(false)
                } else {
                    _editResult.emit(true)
                }
            }
        }
    }
