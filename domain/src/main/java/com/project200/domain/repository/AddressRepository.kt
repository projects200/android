package com.project200.domain.repository

import com.project200.domain.model.BaseResult
import com.project200.domain.model.KakaoPlaceInfo

interface AddressRepository {
    /**
     * 좌표(위도, 경도)를 주소 정보로 변환합니다.
     * @return API 호출 결과를 BaseResult<PlaceInfo> 형태로 반환
     */
    suspend fun getAddressFromCoordinates(latitude: Double, longitude: Double): BaseResult<KakaoPlaceInfo>

    /**
     * 키워드로 장소를 검색합니다.
     * @return API 호출 결과를 BaseResult<List<PlaceInfo>> 형태로 반환
     */
    suspend fun getPlacesByKeyword(query: String, latitude: Double, longitude: Double): BaseResult<List<KakaoPlaceInfo>>
}