package com.project200.undabang.feature.feed.form

import android.net.Uri
import androidx.annotation.StringRes
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
import com.project200.domain.usecase.GetPreferredExerciseTypesUseCase
import com.project200.domain.usecase.GetPreferredExerciseUseCase
import com.project200.domain.usecase.GetUserProfileUseCase
import com.project200.domain.usecase.UpdateFeedUseCase
import com.project200.undabang.feature.feed.R
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class FeedFormEvent {
    data class ShowToast(@StringRes val messageResId: Int) : FeedFormEvent()
}

@HiltViewModel
class FeedFormViewModel @Inject constructor(
    private val getUserProfileUseCase: GetUserProfileUseCase,
    private val getPreferredExerciseUseCase: GetPreferredExerciseUseCase,
    private val getPreferredExerciseTypesUseCase: GetPreferredExerciseTypesUseCase,
    private val createFeedUseCase: CreateFeedUseCase,
    private val updateFeedUseCase: UpdateFeedUseCase
) : ViewModel() {

    private var feedId: Long = -1L
    private var initialContent: String? = null
    private var initialTypeId: Long = -1L
    private var initialTypeName: String? = null

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

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> get() = _isLoading

    private val _createSuccess = MutableLiveData<Long>()
    val createSuccess: LiveData<Long> get() = _createSuccess

    private val _updateSuccess = MutableLiveData<Boolean>()
    val updateSuccess: LiveData<Boolean> get() = _updateSuccess

    private val _event = MutableLiveData<FeedFormEvent>()
    val event: LiveData<FeedFormEvent> get() = _event

    private val _showDabangSelection = MutableLiveData<List<PreferredExercise>?>()
    val showDabangSelection: LiveData<List<PreferredExercise>?> get() = _showDabangSelection

    private val _initialContentForEdit = MutableLiveData<String?>()
    val initialContentForEdit: LiveData<String?> get() = _initialContentForEdit

    fun initData(
        feedId: Long = -1L,
        feedContent: String? = null,
        feedTypeId: Long = -1L,
        feedTypeName: String? = null
    ) {
        this.feedId = feedId
        this.initialContent = feedContent
        this.initialTypeId = feedTypeId
        this.initialTypeName = feedTypeName
        _isEditMode.value = feedId != -1L
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            when (val result = getUserProfileUseCase()) {
                is BaseResult.Success -> _userProfile.value = result.data
                is BaseResult.Error -> _event.value = FeedFormEvent.ShowToast(R.string.profile_load_error)
            }

            val preferredResult = getPreferredExerciseUseCase()
            val allTypesResult = getPreferredExerciseTypesUseCase()

            val preferredList = if (preferredResult is BaseResult.Success) preferredResult.data else emptyList()
            val allList = if (allTypesResult is BaseResult.Success) allTypesResult.data else emptyList()

            val combined = (preferredList + allList).distinctBy { it.exerciseTypeId }
            _exerciseTypes.value = combined

            if (_isEditMode.value == true) {
                _initialContentForEdit.value = initialContent
                if (initialTypeId != -1L && initialTypeName != null) {
                    val matchedType = combined.find { it.exerciseTypeId == initialTypeId }
                        ?: PreferredExercise(
                            preferredExerciseId = -1L,
                            exerciseTypeId = initialTypeId,
                            name = initialTypeName!!,
                            skillLevel = "",
                            daysOfWeek = List(7) { false },
                            imageUrl = null
                        )
                    _selectedType.value = matchedType
                }
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

    fun submitFeed(content: String) {
        if (content.isBlank()) {
            _event.value = FeedFormEvent.ShowToast(R.string.feed_form_empty_content_warning)
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
                        _updateSuccess.value = true
                    }
                    is BaseResult.Error -> {
                        _event.value = FeedFormEvent.ShowToast(R.string.feed_form_update_error)
                    }
                }
            } else {
                val model = CreateFeedModel(
                    feedContent = content,
                    feedTypeId = _selectedType.value?.exerciseTypeId
                )
                when (val result = createFeedUseCase(model)) {
                    is BaseResult.Success -> {
                        _createSuccess.value = result.data.feedId
                    }
                    is BaseResult.Error -> {
                        _event.value = FeedFormEvent.ShowToast(R.string.feed_form_create_error)
                    }
                }
            }
            _isLoading.value = false
        }
    }
}
