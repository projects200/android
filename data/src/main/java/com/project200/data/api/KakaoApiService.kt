package com.project200.data.api

import com.project200.data.dto.KakaoAddressResponse
import com.project200.data.dto.KakaoKeywordSearchResponse
import retrofit2.http.GET
import retrofit2.http.Query
import javax.inject.Named

interface KakaoApiService {
    // 카카오 좌표 -> 주소 변환
    @GET("/v2/local/geo/coord2address.json")
    @Named("kakao")
    suspend fun getAddressFromCoordinates(
        @Query("x") longitude: Double,
        @Query("y") latitude: Double,
    ): KakaoAddressResponse

    // 카카오 좌표로 가까운 장소명 검색
    @GET("/v2/local/search/keyword.json")
    @Named("kakao")
    suspend fun searchNearbyPlaces(
        @Query("query") query: String,
        @Query("y") latitude: Double,
        @Query("x") longitude: Double,
        @Query("radius") radius: Int,
        @Query("sort") sort: String = "distance",
    ): KakaoKeywordSearchResponse

    // 카카오 키워드로 검색
    @GET("/v2/local/search/keyword.json")
    @Named("kakao")
    suspend fun searchPlacesByKeyword(
        @Query("query") query: String,
        @Query("y") latitude: Double,
        @Query("x") longitude: Double,
        @Query("sort") sort: String = "distance",
    ): KakaoKeywordSearchResponse
}
