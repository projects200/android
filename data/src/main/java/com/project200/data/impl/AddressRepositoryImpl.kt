package com.project200.data.impl

import com.project200.common.di.IoDispatcher
import com.project200.data.api.KakaoApiService
import com.project200.data.utils.externalApiCallBuilder
import com.project200.domain.model.BaseResult
import com.project200.domain.model.KakaoPlaceInfo
import com.project200.domain.repository.AddressRepository
import kotlinx.coroutines.CoroutineDispatcher
import javax.inject.Inject

class AddressRepositoryImpl
    @Inject
    constructor(
        private val kakaoApiService: KakaoApiService,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) : AddressRepository {
        /**
         * 좌표(위도, 경도)를 주소 정보로 변환합니다.
         * 1. 먼저, '키워드로 주변 장소 검색' API를 호출하여 가장 가까운 장소를 찾습니다.
         * 2. 가까운 장소가 있다면, 그 장소의 이름과 주소를 사용합니다.
         * 3. 가까운 장소가 없다면, '좌표->주소 변환' API를 호출하여 도로명 주소 또는 지번 주소를 사용합니다.
         * @return API 호출 결과를 BaseResult<PlaceInfo> 형태로 반환
         */
        override suspend fun getAddressFromCoordinates(
            latitude: Double,
            longitude: Double,
        ): BaseResult<KakaoPlaceInfo> {
            return externalApiCallBuilder(
                ioDispatcher = ioDispatcher,
                apiCall = {
                    // 키워드로 주변 장소 검색 api를 호출합니다.
                    val nearestPlace =
                        kakaoApiService.searchNearbyPlaces(
                            "",
                            latitude,
                            longitude,
                            20,
                        ).documents.firstOrNull()

                    if (nearestPlace != null) {
                        // 가까운 장소를 찾았다면, 이 정보를 사용합니다.
                        val finalAddress =
                            if (nearestPlace.roadAddressName.isNotBlank()) {
                                nearestPlace.roadAddressName
                            } else {
                                nearestPlace.addressName
                            }
                        KakaoPlaceInfo(
                            placeName = nearestPlace.placeName,
                            address = finalAddress,
                            latitude = latitude,
                            longitude = longitude,
                        )
                    } else {
                        // 가까운 장소가 없다면, '좌표->주소 변환' API를 호출합니다.
                        val addressResponse =
                            kakaoApiService.getAddressFromCoordinates(longitude, latitude)
                        val addressDocument =
                            addressResponse.documents.firstOrNull()
                                ?: throw NoSuchElementException()

                        val roadAddress = addressDocument.roadAddress
                        if (roadAddress != null) {
                            // 도로명 주소가 있으면 사용합니다.
                            KakaoPlaceInfo(
                                placeName = roadAddress.buildingName,
                                address = roadAddress.addressName,
                                latitude = latitude,
                                longitude = longitude,
                            )
                        } else {
                            // 도로명 주소가 없으면 지번 주소를 사용합니다.
                            val jibunAddress = addressDocument.address ?: throw NoSuchElementException()
                            KakaoPlaceInfo(
                                placeName = jibunAddress.addressName,
                                address = jibunAddress.addressName,
                                latitude = latitude,
                                longitude = longitude,
                            )
                        }
                    }
                },
                mapper = { placeInfo: KakaoPlaceInfo -> placeInfo },
            )
        }

        override suspend fun getPlacesByKeyword(
            query: String,
            latitude: Double,
            longitude: Double,
        ): BaseResult<List<KakaoPlaceInfo>> {
            return externalApiCallBuilder(
                ioDispatcher = ioDispatcher,
                apiCall = {
                    val response =
                        kakaoApiService.searchPlacesByKeyword(
                            query = query,
                            latitude = latitude,
                            longitude = longitude,
                        )
                    response.documents.map { document ->
                        val finalAddress =
                            if (document.roadAddressName.isNotBlank()) {
                                document.roadAddressName
                            } else {
                                document.addressName
                            }
                        KakaoPlaceInfo(
                            placeName = document.placeName,
                            address = finalAddress,
                            latitude = document.latitude.toDouble(),
                            longitude = document.longitude.toDouble(),
                        )
                    }
                },
                mapper = { places: List<KakaoPlaceInfo> -> places },
            )
        }
    }
