package com.project200.feature.matching.place

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.project200.domain.model.BaseResult
import com.project200.domain.model.ExercisePlace
import com.project200.domain.usecase.EditExercisePlaceUseCase
import com.project200.domain.usecase.RegisterExercisePlaceUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ExercisePlaceRegisterViewModel @Inject constructor(
    private val registerExercisePlaceUseCase: RegisterExercisePlaceUseCase,
    private val editExercisePlace: EditExercisePlaceUseCase
) : ViewModel() {
    // 원본 장소 정보를 저장 (Fragment에서 arguments로 전달받음)
    private val _originalPlaceInfo = MutableLiveData<ExercisePlace>()

    private val _customPlaceName = MutableLiveData<String>()
    val customPlaceName: LiveData<String> = _customPlaceName

    private val _registrationResult = MutableLiveData<BaseResult<Unit>>()
    val registrationResult: LiveData<BaseResult<Unit>> = _registrationResult

    private val _editResult = MutableLiveData<BaseResult<Unit>>()
    val editResult: LiveData<BaseResult<Unit>> = _editResult

    /**
     * Fragment에서 받은 arguments로 ViewModel 초기화
     */
    fun initializePlaceInfo(id: Long, placeName: String, placeAddress: String, latitude: Double, longitude: Double) {
        val placeInfo = ExercisePlace(
            id = id,
            name = placeName,
            address = placeAddress,
            latitude = latitude,
            longitude = longitude
        )
        _originalPlaceInfo.value = placeInfo
        _customPlaceName.value = placeInfo.name
    }

    /**
     * EditText의 텍스트가 변경될 때마다 호출
     */
    fun onPlaceNameChanged(newName: String) {
        _customPlaceName.value = newName
    }

    /**
     * 주소 등록
     */
    fun confirmExercisePlace() {
        viewModelScope.launch {
            val customName = _customPlaceName.value
            if (customName.isNullOrBlank()) return@launch

            val originalInfo = _originalPlaceInfo.value ?: return@launch

            if(originalInfo.id != DEFAULT_PLACE_ID) { // 기존 장소 수정
                val result = editExercisePlace(originalInfo.copy(name = customName))
                _editResult.value = result
            } else { // 신규 장소 등록
                val result = registerExercisePlaceUseCase(originalInfo.copy(name = customName))
                _registrationResult.value = result
            }
        }
    }

    companion object {
        const val DEFAULT_PLACE_ID = -1L
    }
}