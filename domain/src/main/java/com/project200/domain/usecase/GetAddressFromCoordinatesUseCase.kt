package com.project200.domain.usecase

import com.project200.domain.model.BaseResult
import com.project200.domain.model.KakaoPlaceInfo
import com.project200.domain.repository.AddressRepository
import javax.inject.Inject

class GetAddressFromCoordinatesUseCase @Inject constructor(
    private val addressRepository: AddressRepository
) {
    suspend operator fun invoke(latitude: Double, longitude: Double): BaseResult<KakaoPlaceInfo> {
        return addressRepository.getAddressFromCoordinates(latitude, longitude)
    }
}