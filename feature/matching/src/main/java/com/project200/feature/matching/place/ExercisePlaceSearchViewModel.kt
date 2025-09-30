package com.project200.feature.matching.place

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.project200.domain.model.BaseResult
import com.project200.domain.model.ExercisePlace
import com.project200.domain.model.KakaoPlaceInfo
import com.project200.domain.usecase.DeleteExercisePlaceUseCase
import com.project200.domain.usecase.GetAddressFromCoordinatesUseCase
import com.project200.domain.usecase.GetExercisePlaceUseCase
import com.project200.feature.matching.utils.ExercisePlaceErrorType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ExercisePlaceSearchViewModel
@Inject
constructor(
    private val getAddressFromCoordinatesUseCase: GetAddressFromCoordinatesUseCase
) : ViewModel() {

    private val _placeInfoResult = MutableLiveData<BaseResult<KakaoPlaceInfo>>()
    val placeInfoResult: LiveData<BaseResult<KakaoPlaceInfo>> = _placeInfoResult

    /**
     * 주어진 위도와 경도를 사용하여 주소 정보를 가져옵니다.
     */
    fun fetchAddressFromCoordinates(latitude: Double, longitude: Double) {
        viewModelScope.launch {
            _placeInfoResult.value = getAddressFromCoordinatesUseCase(latitude, longitude)
        }
    }
}
