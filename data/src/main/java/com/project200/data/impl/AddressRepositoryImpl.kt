package com.project200.data.impl

import com.project200.common.di.IoDispatcher
import com.project200.data.api.KakaoApiService
import com.project200.data.dto.KakaoAddressResponse
import com.project200.data.utils.externalApiCallBuilder
import com.project200.domain.model.BaseResult
import com.project200.domain.model.KakaoPlaceInfo
import com.project200.domain.repository.AddressRepository
import kotlinx.coroutines.CoroutineDispatcher
import javax.inject.Inject

class AddressRepositoryImpl @Inject constructor(
    private val kakaoApiService: KakaoApiService,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
): AddressRepository {
    override suspend fun getAddressFromCoordinates(latitude: Double, longitude: Double): BaseResult<KakaoPlaceInfo> {
        return externalApiCallBuilder(
            ioDispatcher = ioDispatcher,
            apiCall = { kakaoApiService.getAddressFromCoordinates(longitude, latitude) },
            mapper = { response: KakaoAddressResponse ->
                val document = response.documents.firstOrNull()
                    ?: throw NoSuchElementException()

                val roadAddress = document.roadAddress
                if (roadAddress != null) {
                    KakaoPlaceInfo(
                        placeName = roadAddress.buildingName.ifBlank { roadAddress.addressName },
                        address = roadAddress.addressName
                    )
                } else {
                    val jibunAddress = document.address
                        ?: throw NoSuchElementException()
                    KakaoPlaceInfo(
                        placeName = jibunAddress.addressName,
                        address = jibunAddress.addressName
                    )
                }
            }
        )
    }
}