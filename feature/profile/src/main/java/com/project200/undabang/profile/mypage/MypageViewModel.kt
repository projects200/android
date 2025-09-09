package com.project200.undabang.profile.mypage

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.project200.domain.model.BaseResult
import com.project200.domain.model.UserProfile
import com.project200.domain.usecase.GetUserProfileUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MypageViewModel
    @Inject
    constructor(
        private val getUserProfileUseCase: GetUserProfileUseCase,
    ) : ViewModel() {
        private val _profile = MutableLiveData<UserProfile>()
        val profile: LiveData<UserProfile> = _profile

        fun getProfile() {
            viewModelScope.launch {
                when (val result = getUserProfileUseCase()) {
                    is BaseResult.Success -> {
                        _profile.value = result.data
                    }
                    is BaseResult.Error -> {
                    }
                }
            }
        }
    }
