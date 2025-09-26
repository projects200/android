package com.project200.domain.usecase

import com.project200.domain.model.BaseResult
import com.project200.domain.model.KakaoPlaceInfo
import com.project200.domain.repository.AddressRepository
import javax.inject.Inject

class GetPlacesByKeywordUseCase @Inject constructor(
    private val addressRepository: AddressRepository
) {
    suspend operator fun invoke(query: String, latitude: Double, longitude: Double): BaseResult<List<KakaoPlaceInfo>> {
        return addressRepository.getPlacesByKeyword(query, latitude, longitude)
    }
}