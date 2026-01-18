package com.project200.feature.exercise.form

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.project200.domain.model.BaseResult
import com.project200.domain.model.KakaoPlaceInfo
import com.project200.domain.usecase.GetAddressFromCoordinatesUseCase
import com.project200.domain.usecase.GetPlacesByKeywordUseCase
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

        private var isPlaceSelectedBySearch = false // 키워드 검색으로 선택한 장소인지 여부

        /**
         * 주어진 위도와 경도를 사용하여 주소 정보를 가져옵니다.
         */
        fun fetchAddressFromCoordinates(
            latitude: Double,
            longitude: Double,
        ) {
            viewModelScope.launch {
                val result = getAddressFromCoordinatesUseCase(latitude, longitude)
                if (result is BaseResult.Success) {
                    _place.value =
                        KakaoPlaceInfo(
                            placeName = result.data.placeName,
                            address = result.data.address,
                            latitude = latitude,
                            longitude = longitude,
                        )
                }
                _placeInfoResult.value = result
            }
        }

        /**
         * 키워드로 장소를 검색합니다.
         */
        fun searchPlacesByKeyword(
            query: String,
            latitude: Double,
            longitude: Double,
        ) {
            viewModelScope.launch {
                _searchResult.value = searchPlacesByKeywordUseCase(query, latitude, longitude)
            }
        }

        /**
         * 사용자가 검색 결과에서 장소를 선택했을 때 호출됩니다.
         */
        fun selectSearchedPlace(placeInfo: KakaoPlaceInfo) {
            isPlaceSelectedBySearch = true
            if (placeInfo.placeName.isEmpty() || placeInfo.placeName == placeInfo.address) {
                _place.value = placeInfo.copy(placeName = "")
            } else {
                _place.value = placeInfo
            }
        }

        fun onMapMoved(
            latitude: Double,
            longitude: Double,
        ) {
            if (isPlaceSelectedBySearch) {
                // 리스트 클릭으로 인한 이동이었으므로, 플래그만 리셋하고 아무것도 하지 않음
                isPlaceSelectedBySearch = false
                return
            }
            // 사용자가 직접 지도를 드래그한 경우에만 주소 변환 API 호출
            fetchAddressFromCoordinates(latitude, longitude)
        }
    }
