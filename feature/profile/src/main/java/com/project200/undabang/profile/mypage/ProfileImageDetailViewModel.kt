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
    private val _imageSaveResult = MutableSharedFlow<Boolean>()
    val imageSaveResult: SharedFlow<Boolean> = _imageSaveResult

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
    /**
     * URL로부터 이미지를 다운로드하여 갤러리에 저장합니다.
     * @param context ContentResolver를 사용하기 위해 필요합니다.
     * @param imageUrl 저장할 이미지의 URL
     */
    fun saveImageToGallery(imageUrl: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Glide를 사용해 URL로부터 이미지를 Bitmap으로 변환합니다.
                val bitmap = Glide.with(context)
                    .asBitmap()
                    .load(imageUrl)
                    .submit()
                    .get()

                // MediaStore에 저장할 이미지의 메타데이터를 설정합니다.
                val contentValues = ContentValues().apply {
                    // 파일명 (중복되지 않도록 현재 시간 사용)
                    put(MediaStore.Images.Media.DISPLAY_NAME, "IMG_${System.currentTimeMillis()}.jpg")
                    // 저장될 경로
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/운다방") // 앱 이름 폴더에 저장
                    // 파일 타입
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    // 파일을 쓰는 중에는 다른 곳에서 접근할 수 없도록 설정
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }

                val contentResolver = context.contentResolver
                // MediaStore에 새로운 이미지 항목을 생성하고 Uri를 받습니다.
                val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                    ?: throw IOException()

                // Uri를 통해 OutputStream을 열고, Bitmap 데이터를 압축하여 저장합니다.
                contentResolver.openOutputStream(uri)?.use { stream ->
                    if (!bitmap.compress(Bitmap.CompressFormat.JPEG, QUALITY, stream)) {
                        throw IOException()
                    }
                }

                // IS_PENDING 값을 0으로 업데이트하여 파일을 다른 앱에 노출시킵니다.
                contentValues.clear()
                contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                contentResolver.update(uri, contentValues, null, null)

                _imageSaveResult.emit(true)

            } catch (e: Exception) {
                e.printStackTrace()
                _imageSaveResult.emit(false)
            }
        }
    }


    companion object {
        const val EMPTY_ID = -1L
    }
}