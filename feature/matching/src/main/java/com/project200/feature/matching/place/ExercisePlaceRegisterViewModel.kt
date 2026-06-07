package com.project200.feature.matching.place

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.project200.domain.model.BaseResult
import com.project200.domain.model.ExercisePlace
import com.project200.domain.usecase.EditExercisePlaceUseCase
import com.project200.domain.usecase.RegisterExercisePlaceUseCase
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
class ExercisePlaceRegisterViewModel
    @Inject
    constructor(
        private val registerExercisePlaceUseCase: RegisterExercisePlaceUseCase,
        private val editExercisePlace: EditExercisePlaceUseCase,
    ) : ViewModel() {
        // 원본 장소 정보 (Fragment에서 arguments로 전달받음)
        private var originalPlaceInfo: ExercisePlace? = null

        private val _customPlaceName = MutableStateFlow("")
        val customPlaceName: StateFlow<String> = _customPlaceName.asStateFlow()

        private val _placeAddress = MutableStateFlow("")
        val placeAddress: StateFlow<String> = _placeAddress.asStateFlow()

        private val _registrationResult = MutableSharedFlow<BaseResult<Unit>>()
        val registrationResult: SharedFlow<BaseResult<Unit>> = _registrationResult.asSharedFlow()

        private val _editResult = MutableSharedFlow<BaseResult<Unit>>()
        val editResult: SharedFlow<BaseResult<Unit>> = _editResult.asSharedFlow()

        /**
         * Fragment에서 받은 arguments로 ViewModel 초기화
         */
        fun initializePlaceInfo(
            id: Long,
            placeName: String,
            placeAddress: String,
            latitude: Double,
            longitude: Double,
        ) {
            originalPlaceInfo =
                ExercisePlace(
                    id = id,
                    name = placeName,
                    address = placeAddress,
                    latitude = latitude,
                    longitude = longitude,
                )
            _customPlaceName.value = placeName
            _placeAddress.value = placeAddress
        }

        /**
         * 입력 필드의 텍스트가 변경될 때마다 호출
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
                if (customName.isBlank()) return@launch

                val originalInfo = originalPlaceInfo ?: return@launch

                if (originalInfo.id != DEFAULT_PLACE_ID) { // 기존 장소 수정
                    val result = editExercisePlace(originalInfo.copy(name = customName))
                    _editResult.emit(result)
                } else { // 신규 장소 등록
                    val result = registerExercisePlaceUseCase(originalInfo.copy(name = customName))
                    _registrationResult.emit(result)
                }
            }
        }

        companion object {
            const val DEFAULT_PLACE_ID = -1L
        }
    }
