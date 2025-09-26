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
import com.project200.domain.usecase.GetPlacesByKeywordUseCase
import com.project200.feature.matching.utils.ExercisePlaceErrorType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ExercisePlaceSearchViewModel
@Inject
constructor(
    private val getAddressFromCoordinatesUseCase: GetAddressFromCoordinatesUseCase,
    private val searchPlacesByKeywordUseCase: GetPlacesByKeywordUseCase,
) : ViewModel() {

    private val _place = MutableLiveData<KakaoPlaceInfo>()
    val place: LiveData<KakaoPlaceInfo> = _place

    private val _placeInfoResult = MutableLiveData<BaseResult<KakaoPlaceInfo>>()
    val placeInfoResult: LiveData<BaseResult<KakaoPlaceInfo>> = _placeInfoResult

    private val _searchResult = MutableLiveData<BaseResult<List<KakaoPlaceInfo>>>()
    val searchResult: LiveData<BaseResult<List<KakaoPlaceInfo>>> = _searchResult

    /**
     * 주어진 위도와 경도를 사용하여 주소 정보를 가져옵니다.
     */
    fun fetchAddressFromCoordinates(latitude: Double, longitude: Double) {
        viewModelScope.launch {
            _placeInfoResult.value = getAddressFromCoordinatesUseCase(latitude, longitude)
        }
    }

    /**
     * 키워드로 장소를 검색합니다.
     */
    fun searchPlacesByKeyword(query: String, latitude: Double, longitude: Double) {
        viewModelScope.launch {
            _searchResult.value = searchPlacesByKeywordUseCase(query, latitude, longitude)
        }
    }

    fun setPlace(place: KakaoPlaceInfo) {
        _place.value = place
    }
}
