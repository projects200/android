package com.project200.undabang.feature.feed.form

import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.project200.domain.model.BaseResult
import com.project200.domain.model.CreateFeedModel
import com.project200.domain.model.PreferredExercise
import com.project200.domain.model.UpdateFeedModel
import com.project200.domain.model.UserProfile
import com.project200.domain.usecase.CreateFeedUseCase
import com.project200.domain.usecase.DeleteFeedImageUseCase
import com.project200.domain.usecase.GetFeedDetailUseCase
import com.project200.domain.usecase.GetPreferredExerciseTypesUseCase
import com.project200.domain.usecase.GetPreferredExerciseUseCase
import com.project200.domain.usecase.GetUserProfileUseCase
import com.project200.domain.usecase.UpdateFeedUseCase
import com.project200.domain.usecase.UploadFeedImagesUseCase
import com.project200.presentation.utils.UiText
import com.project200.undabang.feature.feed.R
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FeedFormViewModel @Inject constructor(
    private val getUserProfileUseCase: GetUserProfileUseCase,
    private val getPreferredExerciseUseCase: GetPreferredExerciseUseCase,
    private val getPreferredExerciseTypesUseCase: GetPreferredExerciseTypesUseCase,
    private val getFeedDetailUseCase: GetFeedDetailUseCase,
    private val createFeedUseCase: CreateFeedUseCase,
    private val updateFeedUseCase: UpdateFeedUseCase,
    private val uploadFeedImagesUseCase: UploadFeedImagesUseCase,
    private val deleteFeedImageUseCase: DeleteFeedImageUseCase,
) : ViewModel() {

    private var feedId: Long = -1L

    private val _isEditMode = MutableLiveData(false)
    val isEditMode: LiveData<Boolean> get() = _isEditMode

    private val _userProfile = MutableLiveData<UserProfile>()
    val userProfile: LiveData<UserProfile> get() = _userProfile

    private val _exerciseTypes = MutableLiveData<List<PreferredExercise>>()
    val exerciseTypes: LiveData<List<PreferredExercise>> get() = _exerciseTypes

    private val _selectedType = MutableLiveData<PreferredExercise?>()
    val selectedType: LiveData<PreferredExercise?> get() = _selectedType

    private val _selectedImages = MutableLiveData<List<Uri>>(emptyList())
    val selectedImages: LiveData<List<Uri>> get() = _selectedImages

    private val _registeredImages = MutableLiveData<List<RegisteredImage>>(emptyList())
    val registeredImages: LiveData<List<RegisteredImage>> get() = _registeredImages

    private val deletedImageIds = mutableListOf<Long>()

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> get() = _isLoading

    private val _createSuccess = MutableLiveData<Long>()
    val createSuccess: LiveData<Long> get() = _createSuccess

    private val _updateSuccess = MutableLiveData<Boolean>()
    val updateSuccess: LiveData<Boolean> get() = _updateSuccess

    private val _toastEvent = MutableSharedFlow<UiText>()
    val toastEvent: SharedFlow<UiText> = _toastEvent.asSharedFlow()

    private val _showDabangSelection = MutableLiveData<List<PreferredExercise>?>()
    val showDabangSelection: LiveData<List<PreferredExercise>?> get() = _showDabangSelection

    private val _initialContentForEdit = MutableLiveData<String?>()
    val initialContentForEdit: LiveData<String?> get() = _initialContentForEdit

    fun initData(feedId: Long = -1L) {
        this.feedId = feedId
        _isEditMode.value = feedId != -1L
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            when (val result = getUserProfileUseCase()) {
                is BaseResult.Success -> _userProfile.value = result.data
                is BaseResult.Error -> _toastEvent.emit(UiText.StringResource(R.string.profile_load_error))
            }

            val preferredResult = getPreferredExerciseUseCase()
            val allTypesResult = getPreferredExerciseTypesUseCase()

            val preferredList = if (preferredResult is BaseResult.Success) preferredResult.data else emptyList()
            val allList = if (allTypesResult is BaseResult.Success) allTypesResult.data else emptyList()

            val combined = (preferredList + allList).distinctBy { it.exerciseTypeId }
            _exerciseTypes.value = combined

            if (_isEditMode.value == true) {
                loadFeedForEdit(combined)
            }
        }
    }

    private suspend fun loadFeedForEdit(exerciseTypes: List<PreferredExercise>) {
        when (val result = getFeedDetailUseCase(feedId)) {
            is BaseResult.Success -> {
                val feed = result.data
                _initialContentForEdit.value = feed.feedContent
                
                val existingList = feed.feedPictures.map { picture ->
                    RegisteredImage(picture.feedPictureId, picture.feedPictureUrl)
                }
                _registeredImages.value = existingList
                
                val typeId = feed.feedTypeId
                val typeName = feed.feedTypeName
                if (typeId != null && typeName != null) {
                    val matchedType = exerciseTypes.find { it.exerciseTypeId == typeId }
                        ?: PreferredExercise(
                            preferredExerciseId = -1L,
                            exerciseTypeId = typeId,
                            name = typeName,
                            skillLevel = "",
                            daysOfWeek = List(7) { false },
                            imageUrl = null
                        )
                    _selectedType.value = matchedType
                }
            }
            is BaseResult.Error -> {
                _toastEvent.emit(UiText.StringResource(R.string.feed_load_error))
            }
        }
    }

    fun selectType(exercise: PreferredExercise?) {
        _selectedType.value = exercise
    }

    fun requestShowDabangSelection() {
        val types = _exerciseTypes.value
        if (!types.isNullOrEmpty()) {
            _showDabangSelection.value = types
        }
    }

    fun onDabangSelectionShown() {
        _showDabangSelection.value = null
    }

    fun addImages(uris: List<Uri>) {
        val current = _selectedImages.value ?: emptyList()
        _selectedImages.value = current + uris
    }

    fun removeImage(uri: Uri) {
        val current = _selectedImages.value ?: emptyList()
        _selectedImages.value = current.filter { it != uri }
    }

    fun removeExistingImage(imageId: Long) {
        deletedImageIds.add(imageId)
        val current = _registeredImages.value ?: emptyList()
        _registeredImages.value = current.filter { it.imageId != imageId }
    }

    fun submitFeed(content: String) {
        if (content.isBlank()) {
            viewModelScope.launch {
                _toastEvent.emit(UiText.StringResource(R.string.feed_form_empty_content_warning))
            }
            return
        }

        _isLoading.value = true
        viewModelScope.launch {
            if (_isEditMode.value == true) {
                val model = UpdateFeedModel(
                    feedId = feedId,
                    feedContent = content,
                    feedTypeId = _selectedType.value?.exerciseTypeId
                )
                when (updateFeedUseCase(model)) {
                    is BaseResult.Success -> {
                        var hasImageError = false
                        
                        deletedImageIds.forEach { imageId ->
                            when (deleteFeedImageUseCase(feedId, imageId)) {
                                is BaseResult.Error -> hasImageError = true
                                is BaseResult.Success -> {}
                            }
                        }
                        
                        val newImages = _selectedImages.value ?: emptyList()
                        if (newImages.isNotEmpty()) {
                            val imageUriStrings = newImages.map { it.toString() }
                            when (uploadFeedImagesUseCase(feedId, imageUriStrings)) {
                                is BaseResult.Error -> hasImageError = true
                                is BaseResult.Success -> {}
                            }
                        }
                        
                        if (hasImageError) {
                            _toastEvent.emit(UiText.StringResource(R.string.feed_form_image_upload_error))
                        }
                        _updateSuccess.value = true
                    }
                    is BaseResult.Error -> {
                        _toastEvent.emit(UiText.StringResource(R.string.feed_form_update_error))
                    }
                }
            } else {
                val model = CreateFeedModel(
                    feedContent = content,
                    feedTypeId = _selectedType.value?.exerciseTypeId
                )
                when (val result = createFeedUseCase(model)) {
                    is BaseResult.Success -> {
                        val createdFeedId = result.data.feedId
                        val images = _selectedImages.value ?: emptyList()
                        if (images.isNotEmpty()) {
                            val imageUriStrings = images.map { it.toString() }
                            when (uploadFeedImagesUseCase(createdFeedId, imageUriStrings)) {
                                is BaseResult.Success -> {
                                    _createSuccess.value = createdFeedId
                                }
                                is BaseResult.Error -> {
                                    _createSuccess.value = createdFeedId
                                    _toastEvent.emit(UiText.StringResource(R.string.feed_form_image_upload_error))
                                }
                            }
                        } else {
                            _createSuccess.value = createdFeedId
                        }
                    }
                    is BaseResult.Error -> {
                        _toastEvent.emit(UiText.StringResource(R.string.feed_form_create_error))
                    }
                }
            }
            _isLoading.value = false
        }
    }
}
