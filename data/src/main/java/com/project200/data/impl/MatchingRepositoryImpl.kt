package com.project200.data.impl

import android.content.SharedPreferences
import androidx.core.content.edit
import com.project200.common.constants.RuleConstants.ZOOM_LEVEL
import com.project200.common.utils.DefaultPrefs
import com.project200.domain.model.BaseResult
import com.project200.domain.model.Location
import com.project200.domain.model.MapPosition
import com.project200.domain.model.MatchingMember
import com.project200.domain.repository.MatchingRepository
import javax.inject.Inject

class MatchingRepositoryImpl @Inject constructor(
    @DefaultPrefs private val sharedPreferences: SharedPreferences
) : MatchingRepository {
    /**
    * 매칭 지도 상에서 회원들의 정보를 반환하는 함수
     */
    override suspend fun getMembers(): BaseResult<List<MatchingMember>> {
        // TODO: 실제 API 사용 시, 아래의 더미 데이터를 제거하고 API 연동 로직으로 대체할 예정입니다.
        return BaseResult.Success(
            listOf(
                MatchingMember(
                    memberId = "a1b2c3d4-e5f6-7890-1234-567890abcdef",
                    profileThumbnailUrl = "https://images.unsplash.com/photo-1571019613454-1cb2f99b2d8b",
                    nickname = "강철헬린이",
                    gender = "MALE",
                    birthDate = "1998-05-21",
                    locations = listOf(
                        Location(
                            exerciseLocationName = "강남 파워피트니스",
                            latitude = 37.4979,
                            longitude = 127.0276
                        ),
                        Location(
                            exerciseLocationName = "서울숲 런닝 트랙",
                            latitude = 37.5445,
                            longitude = 127.0370
                        )
                    )
                ),
                MatchingMember(
                    memberId = "b2c3d4e5-f6a7-8901-2345-67890abcdef1",
                    profileThumbnailUrl = "https://images.unsplash.com/photo-1541534741688-6078c6bfb5c5",
                    nickname = "요가사랑",
                    gender = "FEMALE",
                    birthDate = "1992-11-08",
                    locations = listOf(
                        Location(
                            exerciseLocationName = "홍대 요가스테이",
                            latitude = 37.5562,
                            longitude = 126.9224
                        )
                    )
                ),
                MatchingMember(
                    memberId = "c3d4e5f6-a7b8-9012-3456-7890abcdef12",
                    profileThumbnailUrl = "https://images.unsplash.com/photo-1517836357463-d25dfeac3438",
                    nickname = "주말엔축구왕",
                    gender = "MALE",
                    birthDate = "1989-01-30",
                    locations = listOf(
                        Location(
                            exerciseLocationName = "잠실 종합운동장",
                            latitude = 37.5152,
                            longitude = 127.0722
                        ),
                        Location(
                            exerciseLocationName = "효창운동장",
                            latitude = 37.5393,
                            longitude = 126.9602
                        )
                    )
                ),
                MatchingMember(
                    memberId = "d4e5f6a7-b8c9-0123-4567-890abcdef123",
                    profileThumbnailUrl = "https://images.unsplash.com/photo-1599058917212-d750089bc07e",
                    nickname = "클라이밍중독",
                    gender = "FEMALE",
                    birthDate = "2001-08-15",
                    locations = listOf(
                        Location(
                            exerciseLocationName = "더클라임 신림점",
                            latitude = 37.4842,
                            longitude = 126.9295
                        )
                    )
                )
            )
        )
    }

    override suspend fun saveLastMapPosition(mapPosition: MapPosition) {
        sharedPreferences.edit {
            putFloat(KEY_LAST_LAT, mapPosition.latitude.toFloat())
            putFloat(KEY_LAST_LON, mapPosition.longitude.toFloat())
            putInt(KEY_LAST_ZOOM, mapPosition.zoomLevel)
        }
    }

    override suspend fun getLastMapPosition(): MapPosition? {
        val lat = sharedPreferences.getFloat(KEY_LAST_LAT, 0f).toDouble()
        val lon = sharedPreferences.getFloat(KEY_LAST_LON, 0f).toDouble()

        return if (lat != 0.0 && lon != 0.0) {
            MapPosition(
                latitude = lat,
                longitude = lon,
                zoomLevel = sharedPreferences.getInt(KEY_LAST_ZOOM, ZOOM_LEVEL)
            )
        } else {
            null
        }
    }

    companion object {
        private const val KEY_LAST_LAT = "key_last_lat"
        private const val KEY_LAST_LON = "key_last_lon"
        private const val KEY_LAST_ZOOM = "key_last_zoom"
    }
}