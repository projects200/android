package com.project200.data.impl

import android.content.SharedPreferences
import androidx.core.content.edit
import com.project200.common.constants.RuleConstants.ZOOM_LEVEL
import com.project200.common.di.IoDispatcher
import com.project200.common.utils.DefaultPrefs
import com.project200.data.api.ApiService
import com.project200.data.dto.EditExercisePlaceDTO
import com.project200.data.dto.GetExerciseCountByRangeDTO
import com.project200.data.dto.GetMatchingMembersDto
import com.project200.data.dto.GetMatchingProfileDTO
import com.project200.data.mapper.toDTO
import com.project200.data.mapper.toModel
import com.project200.data.utils.apiCallBuilder
import com.project200.domain.model.BaseResult
import com.project200.domain.model.ExerciseCount
import com.project200.domain.model.ExercisePlace
import com.project200.domain.model.Location
import com.project200.domain.model.MapPosition
import com.project200.domain.model.MatchingMember
import com.project200.domain.model.MatchingMemberProfile
import com.project200.domain.repository.MatchingRepository
import kotlinx.coroutines.CoroutineDispatcher
import java.time.LocalDate
import javax.inject.Inject
import kotlin.collections.map

class MatchingRepositoryImpl
    @Inject
    constructor(
        private val apiService: ApiService,
        @DefaultPrefs private val sharedPreferences: SharedPreferences,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) : MatchingRepository {
        /**
         * 매칭 지도 상에서 회원들의 정보를 반환하는 함수
         */
        override suspend fun getMembers(): BaseResult<List<MatchingMember>> {
            return apiCallBuilder(
                ioDispatcher = ioDispatcher,
                apiCall = { apiService.getMatchingMembers() },
                mapper = { dtoList: List<GetMatchingMembersDto>? ->
                    dtoList?.map { it.toModel() } ?: throw NoSuchElementException()
                },
            )
        }

        /** 마지막 지도 위치를 SharedPreferences에 저장
         * @param mapPosition 저장할 지도 위치 정보 (위도, 경도, 줌 레벨)
         */
        override suspend fun saveLastMapPosition(mapPosition: MapPosition) {
            sharedPreferences.edit {
                putFloat(KEY_LAST_LAT, mapPosition.latitude.toFloat())
                putFloat(KEY_LAST_LON, mapPosition.longitude.toFloat())
                putInt(KEY_LAST_ZOOM, mapPosition.zoomLevel)
            }
        }

        /** SharedPreferences에 저장된 마지막 지도 위치를 불러옴
         * @return 저장된 지도 위치 정보 (위도, 경도, 줌 레벨) 또는 저장된 정보가 없으면 null 반환
         */
        override suspend fun getLastMapPosition(): MapPosition? {
            val lat = sharedPreferences.getFloat(KEY_LAST_LAT, 0f).toDouble()
            val lon = sharedPreferences.getFloat(KEY_LAST_LON, 0f).toDouble()

            return if (lat != 0.0 && lon != 0.0) {
                MapPosition(
                    latitude = lat,
                    longitude = lon,
                    zoomLevel = sharedPreferences.getInt(KEY_LAST_ZOOM, ZOOM_LEVEL),
                )
            } else {
                null
            }
        }

        /** 특정 회원의 프로필과 운동 기록을 반환하는 함수
         * @param memberId 조회할 회원의 ID
         * @return 회원의 프로필 및 운동 기록 정보
         */
        override suspend fun getMatchingProfile(memberId: String): BaseResult<MatchingMemberProfile> {
            return apiCallBuilder(
                ioDispatcher = ioDispatcher,
                apiCall = { apiService.getMatchingProfile(memberId) },
                mapper = { dto: GetMatchingProfileDTO? ->
                    dto?.toModel() ?: throw NoSuchElementException()
                }
            )
        }

        /** 특정 회원의 운동 날짜 데이터를 반환하는 함수
         * @param memberId 조회할 회원의 ID
         * @param startDate 조회 시작 날짜
         * @param endDate 조회 종료 날짜
         * @return 회원의 운동 날짜 데이터 리스트
         */
        override suspend fun getMemberExerciseDates(
            memberId: String,
            startDate: LocalDate,
            endDate: LocalDate,
        ): BaseResult<List<ExerciseCount>> {
            return apiCallBuilder(
                ioDispatcher = ioDispatcher,
                apiCall = { apiService.getExerciseCountsByRange(startDate, endDate) },
                mapper = { dtoList: List<GetExerciseCountByRangeDTO>? ->
                    dtoList?.map { it.toModel() } ?: throw NoSuchElementException()
                },
            )

            // TODO: API 완성되면 사용 필요 (현재는 내 프로필에서 사용하는 api 재사용)
/*            return apiCallBuilder(
                ioDispatcher = ioDispatcher,
                apiCall = { apiService.getMatchingMemberCalendar(startDate, endDate) },
                mapper = { dtoList: List<GetExerciseCountByRangeDTO>? ->
                    dtoList?.map { it.toModel() } ?: throw NoSuchElementException()
                },
            )*/
        }

        /** 운동 장소 리스트를 반환하는 함수
         * @return 운동 장소 리스트
         */
        override suspend fun getExercisePlaces(): BaseResult<List<ExercisePlace>> {
            return BaseResult.Success(
                listOf(
                    ExercisePlace(
                        id = 101,
                        name = "스포애니 강남역 1호점",
                        address = "서울특별시 강남구 강남대로 390",
                        latitude = 37.49794,
                        longitude = 127.02762,
                    ),
                    ExercisePlace(
                        id = 102,
                        name = "애플짐 대치점",
                        address = "서울특별시 강남구 남부순환로 2933",
                        latitude = 37.49443,
                        longitude = 127.06259,
                    ),
                    ExercisePlace(
                        id = 103,
                        name = "양재시민의숲",
                        address = "서울특별시 서초구 매헌로 99",
                        latitude = 37.47071,
                        longitude = 127.03613,
                    ),
                    // --- 강북/도심 지역 ---
                    ExercisePlace(
                        id = 201,
                        name = "서울숲공원",
                        address = "서울특별시 성동구 뚝섬로 273",
                        latitude = 37.54442,
                        longitude = 127.03650,
                    ),
                    ExercisePlace(
                        id = 202,
                        name = "남산공원 북측순환로 입구",
                        address = "서울특별시 중구 삼일대로 231",
                        latitude = 37.55949,
                        longitude = 126.98567,
                    ),
                ),
            )
            /** TODO: 실제 api 완성되면 연결 필요
             * return apiCallBuilder(
             ioDispatcher = ioDispatcher,
             apiCall = { apiService.getExercisePlaces() },
             mapper = { dtoList: List<GetExercisePlaceDTO>? ->
             dtoList?.map { it.toModel() } ?: throw NoSuchElementException()
             },
             )*/
        }

        /** 운동 장소를 추가하는 함수
         * @param placeId 운동 장소 ID
         */
        override suspend fun deleteExercisePlace(placeId: Long): BaseResult<Unit> {
            return BaseResult.Success(Unit)
            /** TODO: 실제 api 완성되면 연결 필요
             * return apiCallBuilder(
             ioDispatcher = ioDispatcher,
             apiCall = { apiService.deleteExercisePlace(placeId) },
             mapper = { Unit }
             )*/
        }

    override suspend fun addExercisePlace(placeInfo: ExercisePlace): BaseResult<Unit> {
        return apiCallBuilder(
            ioDispatcher = ioDispatcher,
            apiCall = { apiService.postExercisePlace(placeInfo.toDTO())},
            mapper = { Unit }
        )
    }

    override suspend fun editExercisePlace(placeInfo: ExercisePlace): BaseResult<Unit> {
        return apiCallBuilder(
            ioDispatcher = ioDispatcher,
            apiCall = { apiService.putExercisePlace(placeInfo.id, EditExercisePlaceDTO(placeInfo.name))},
            mapper = { Unit }
        )
    }

    companion object {
            private const val KEY_LAST_LAT = "key_last_lat"
            private const val KEY_LAST_LON = "key_last_lon"
            private const val KEY_LAST_ZOOM = "key_last_zoom"
        }
    }
