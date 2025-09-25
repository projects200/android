package com.project200.data.api

import com.project200.data.dto.KakaoAddressResponse
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query
import javax.inject.Named

interface KakaoApiService {
    // 카카오 좌표 -> 주소 변환
    @GET("/v2/local/geo/coord2address.json")
    @Named("kakao")
    suspend fun getAddressFromCoordinates(
        @Query("x") longitude: Double, // 경도
        @Query("y") latitude: Double   // 위도
    ): KakaoAddressResponse
}