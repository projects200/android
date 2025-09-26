package com.project200.data.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

// 좌표로 주소 변환
@JsonClass(generateAdapter = true)
data class KakaoAddressResponse(
    @Json(name = "documents") val documents: List<AddressDocument>
)

@JsonClass(generateAdapter = true)
data class AddressDocument(
    @Json(name = "road_address") val roadAddress: RoadAddress?,
    @Json(name = "address") val address: Address?
)

@JsonClass(generateAdapter = true)
data class RoadAddress(
    @Json(name = "address_name") val addressName: String,
    @Json(name = "building_name") val buildingName: String
)

@JsonClass(generateAdapter = true)
data class Address(
    @Json(name = "address_name") val addressName: String
)

// 키워드로 장소 검색
@JsonClass(generateAdapter = true)
data class KakaoKeywordSearchResponse(
    @Json(name = "documents") val documents: List<PlaceDocument>
)

@JsonClass(generateAdapter = true)
data class PlaceDocument(
    @Json(name = "place_name") val placeName: String,
    @Json(name = "address_name") val addressName: String,
    @Json(name = "road_address_name") val roadAddressName: String
)

