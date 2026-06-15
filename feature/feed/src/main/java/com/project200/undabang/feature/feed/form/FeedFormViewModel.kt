package com.project200.undabang.feature.feed.form

import android.net.Uri
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FeedFormViewModel
    @Inject
    constructor(
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

        private val _isEditMode = MutableStateFlow(false)
        val isEditMode: StateFlow<Boolean> = _isEditMode.asStateFlow()

        private val _userProfile = MutableStateFlow<UserProfile?>(null)
        val userProfile: StateFlow<UserProfile?> = _userProfile.asStateFlow()

        private val exerciseTypesFlow = MutableStateFlow<List<PreferredExercise>>(emptyList())

        private val _selectedType = MutableStateFlow<PreferredExercise?>(null)
        val selectedType: StateFlow<PreferredExercise?> = _selectedType.asStateFlow()

        private val _selectedImages = MutableStateFlow<List<Uri>>(emptyList())
        val selectedImages: StateFlow<List<Uri>> = _selectedImages.asStateFlow()

        private val _registeredImages = MutableStateFlow<List<RegisteredImage>>(emptyList())
        val registeredImages: StateFlow<List<RegisteredImage>> = _registeredImages.asStateFlow()

        private val _content = MutableStateFlow("")
        val content: StateFlow<String> = _content.asStateFlow()

        private val deletedImageIds = mutableListOf<Long>()

        private val _isLoading = MutableStateFlow(false)
        val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

        private val _createSuccess = MutableSharedFlow<Long>()
        val createSuccess: SharedFlow<Long> = _createSuccess.asSharedFlow()

        private val _updateSuccess = MutableSharedFlow<Unit>()
        val updateSuccess: SharedFlow<Unit> = _updateSuccess.asSharedFlow()

        private val _toastEvent = MutableSharedFlow<UiText>()
        val toastEvent: SharedFlow<UiText> = _toastEvent.asSharedFlow()

        private val _showDabangSelection = MutableSharedFlow<List<PreferredExercise>>()
        val showDabangSelection: SharedFlow<List<PreferredExercise>> = _showDabangSelection.asSharedFlow()

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
                exerciseTypesFlow.value = combined

                if (_isEditMode.value) {
                    loadFeedForEdit(combined)
                }
            }
        }

        private suspend fun loadFeedForEdit(exerciseTypes: List<PreferredExercise>) {
            when (val result = getFeedDetailUseCase(feedId)) {
                is BaseResult.Success -> {
                    val feed = result.data
                    _content.value = feed.feedContent.orEmpty()

                    val existingList =
                        feed.feedPictures.map { picture ->
                            RegisteredImage(picture.feedPictureId, picture.feedPictureUrl)
                        }
                    _registeredImages.value = existingList

                    val typeId = feed.feedTypeId
                    val typeName = feed.feedTypeName
                    if (typeId != null && typeName != null) {
                        val matchedType =
                            exerciseTypes.find { it.exerciseTypeId == typeId }
                                ?: PreferredExercise(
                                    preferredExerciseId = -1L,
                                    exerciseTypeId = typeId,
                                    name = typeName,
                                    skillLevel = "",
                                    daysOfWeek = List(7) { false },
                                    imageUrl = null,
                                )
                        _selectedType.value = matchedType
                    }
                }
                is BaseResult.Error -> {
                    _toastEvent.emit(UiText.StringResource(R.string.feed_load_error))
                }
            }
        }

        fun updateContent(value: String) {
            _content.value = value
        }

        fun selectType(exercise: PreferredExercise?) {
            _selectedType.value = exercise
        }

        fun requestShowDabangSelection() {
            val types = exerciseTypesFlow.value
            if (types.isNotEmpty()) {
                viewModelScope.launch {
                    _showDabangSelection.emit(types)
                }
            }
        }

        fun addImages(uris: List<Uri>) {
            _selectedImages.value = _selectedImages.value + uris
        }

        fun removeImage(uri: Uri) {
            _selectedImages.value = _selectedImages.value.filter { it != uri }
        }

        fun removeExistingImage(imageId: Long) {
            deletedImageIds.add(imageId)
            _registeredImages.value = _registeredImages.value.filter { it.imageId != imageId }
        }

        fun submitFeed() {
            val text = _content.value
            if (text.isBlank()) {
                viewModelScope.launch {
                    _toastEvent.emit(UiText.StringResource(R.string.feed_form_empty_content_warning))
                }
                return
            }

            _isLoading.value = true
            viewModelScope.launch {
                if (_isEditMode.value) {
                    submitEdit(text)
                } else {
                    submitCreate(text)
                }
                _isLoading.value = false
            }
        }

        private suspend fun submitEdit(text: String) {
            val model =
                UpdateFeedModel(
                    feedId = feedId,
                    feedContent = text,
                    feedTypeId = _selectedType.value?.exerciseTypeId,
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

                    val newImages = _selectedImages.value
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
                    _updateSuccess.emit(Unit)
                }
                is BaseResult.Error -> {
                    _toastEvent.emit(UiText.StringResource(R.string.feed_form_update_error))
                }
            }
        }

        private suspend fun submitCreate(text: String) {
            val model =
                CreateFeedModel(
                    feedContent = text,
                    feedTypeId = _selectedType.value?.exerciseTypeId,
                )
            when (val result = createFeedUseCase(model)) {
                is BaseResult.Success -> {
                    val createdFeedId = result.data.feedId
                    val images = _selectedImages.value
                    if (images.isNotEmpty()) {
                        val imageUriStrings = images.map { it.toString() }
                        when (uploadFeedImagesUseCase(createdFeedId, imageUriStrings)) {
                            is BaseResult.Success -> _createSuccess.emit(createdFeedId)
                            is BaseResult.Error -> {
                                _createSuccess.emit(createdFeedId)
                                _toastEvent.emit(UiText.StringResource(R.string.feed_form_image_upload_error))
                            }
                        }
                    } else {
                        _createSuccess.emit(createdFeedId)
                    }
                }
                is BaseResult.Error -> {
                    _toastEvent.emit(UiText.StringResource(R.string.feed_form_create_error))
                }
            }
        }
    }
