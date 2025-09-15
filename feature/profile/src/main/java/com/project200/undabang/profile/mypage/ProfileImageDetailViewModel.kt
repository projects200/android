package com.project200.undabang.profile.mypage

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.project200.domain.model.BaseResult
import com.project200.domain.model.ProfileImage
import com.project200.domain.model.ProfileImageList
import com.project200.domain.usecase.GetProfileImagesUseCase
import com.project200.undabang.profile.utils.ProfileImageErrorType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfileImageDetailViewModel @Inject constructor(
    private val getProfileImagesUseCase: GetProfileImagesUseCase
): ViewModel() {

    private val _profileImages = MutableLiveData<List<ProfileImage>>()
    val profileImages: LiveData<List<ProfileImage>> = _profileImages

    private val _errorType = MutableSharedFlow<ProfileImageErrorType>()
    val errorType: SharedFlow<ProfileImageErrorType> = _errorType

    init {
        getProfileImageList()
    }

    fun getProfileImageList() {
        viewModelScope.launch {
            when(val result = getProfileImagesUseCase()) {
                is BaseResult.Success -> {
                    val imageList = result.data
                    val displayList = mutableListOf<ProfileImage>()

                    // 썸네일과 이미지 리스트가 모두 비어있는지 확인
                    if (imageList.thumbnail == null && imageList.images.isEmpty()) {
                        // 더미 이미지 아이템을 리스트에 추가
                        displayList.add(ProfileImage(id = EMPTY_ID, url = ""))
                    } else {
                        // 이미지가 있는 경우, 썸네일을 맨 앞에 추가
                        imageList.thumbnail?.let { thumbnail ->
                            displayList.add(thumbnail)
                            displayList.addAll(imageList.images.filter { it.id != thumbnail.id })
                        } ?: run {
                            // 썸네일은 없지만 일반 이미지는 있는 경우
                            displayList.addAll(imageList.images)
                        }
                    }
                    _profileImages.value = displayList
                }
                is BaseResult.Error -> {
                    _errorType.emit(ProfileImageErrorType.LOAD_FAILED)
                }
            }
        }
    }


    companion object {
        const val EMPTY_ID = -1L
    }
}