package com.project200.undabang.feature.feed.form

import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.project200.domain.model.BaseResult
import com.project200.domain.model.CreateFeedModel
import com.project200.domain.model.PreferredExercise
import com.project200.domain.model.UserProfile
import com.project200.domain.usecase.CreateFeedUseCase
import com.project200.domain.usecase.GetPreferredExerciseTypesUseCase
import com.project200.domain.usecase.GetPreferredExerciseUseCase
import com.project200.domain.usecase.GetUserProfileUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FeedFormViewModel @Inject constructor(
    private val getUserProfileUseCase: GetUserProfileUseCase,
    private val getPreferredExerciseUseCase: GetPreferredExerciseUseCase,
    private val getPreferredExerciseTypesUseCase: GetPreferredExerciseTypesUseCase,
    private val createFeedUseCase: CreateFeedUseCase
) : ViewModel() {

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

    private val _error = MutableLiveData<String>()
    val error: LiveData<String> get() = _error

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            when (val result = getUserProfileUseCase()) {
                is BaseResult.Success -> _userProfile.value = result.data
                is BaseResult.Error -> _error.value = result.message
            }

            val preferredResult = getPreferredExerciseUseCase()
            val allTypesResult = getPreferredExerciseTypesUseCase()

            val preferredList = if (preferredResult is BaseResult.Success) preferredResult.data else emptyList()
            val allList = if (allTypesResult is BaseResult.Success) allTypesResult.data else emptyList()

            val combined = (preferredList + allList).distinctBy { it.exerciseTypeId }
            _exerciseTypes.value = combined
        }
    }

    fun selectType(exercise: PreferredExercise?) {
        _selectedType.value = exercise
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
            _error.value = "내용을 입력해주세요."
            return
        }

        _isLoading.value = true
        viewModelScope.launch {
            val model = CreateFeedModel(
                feedContent = content,
                feedTypeId = _selectedType.value?.exerciseTypeId
            )
            when (val result = createFeedUseCase(model)) {
                is BaseResult.Success -> {
                    _createSuccess.value = result.data.feedId
                }
                is BaseResult.Error -> {
                    _error.value = result.message
                }
            }
            _isLoading.value = false
        }
    }
}
