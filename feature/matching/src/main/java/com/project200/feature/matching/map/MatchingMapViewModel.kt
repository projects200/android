package com.project200.feature.matching.map

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kakao.vectormap.LatLng
import com.project200.common.utils.ClockProvider
import com.project200.domain.model.AgeGroup
import com.project200.domain.model.BaseResult
import com.project200.domain.model.DayOfWeek
import com.project200.domain.model.ExercisePlace
import com.project200.domain.model.MapBounds
import com.project200.domain.model.MapPosition
import com.project200.domain.model.MatchingMember
import com.project200.domain.usecase.GetExercisePlaceUseCase
import com.project200.domain.usecase.GetLastMapPositionUseCase
import com.project200.domain.usecase.GetMatchingMembersUseCase
import com.project200.domain.usecase.SaveLastMapPositionUseCase
import com.project200.feature.matching.utils.FilterState
import com.project200.feature.matching.utils.MatchingFilterType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeParseException
import javax.inject.Inject
import kotlin.math.abs

@HiltViewModel
class MatchingMapViewModel
    @Inject
    constructor(
        private val getMatchingMembersUseCase: GetMatchingMembersUseCase,
        private val getLastMapPositionUseCase: GetLastMapPositionUseCase,
        private val saveLastMapPositionUseCase: SaveLastMapPositionUseCase,
        private val getExercisePlaceUseCase: GetExercisePlaceUseCase,
        private val clockProvider: ClockProvider,
    ) : ViewModel() {
        // 회원 목록
        private val matchingMembers =
            MutableStateFlow<BaseResult<List<MatchingMember>>>(BaseResult.Success(emptyList()))

        // 내 운동 장소 목록
        private val exercisePlaces =
            MutableStateFlow<BaseResult<List<ExercisePlace>>>(BaseResult.Success(emptyList()))

        private val _errorEvents = MutableSharedFlow<String>()
        val errorEvents: SharedFlow<String> = _errorEvents

        // 필터 상태
        private val _filterState = MutableStateFlow(FilterState())
        val filterState: StateFlow<FilterState> = _filterState.asStateFlow()

        // 현재 선택된 필터 타입
        private val _currentFilterType = MutableSharedFlow<MatchingFilterType>()
        val currentFilterType: SharedFlow<MatchingFilterType> = _currentFilterType

        // 마지막으로 가져온 지도 위치 정보
        private var lastFetchedCenter: LatLng? = null
        private var lastFetchedZoom: Int? = null

        val combinedMapData: StateFlow<Pair<List<MatchingMember>, List<ExercisePlace>>> =
            combine(
                matchingMembers,
                exercisePlaces,
                _filterState
            ) { membersResult, placesResult, filters ->
                // 성공 데이터만 추출, 실패 시 빈 리스트
                val members = (membersResult as? BaseResult.Success)?.data ?: emptyList()
                val places = (placesResult as? BaseResult.Success)?.data ?: emptyList()

                // 에러 결과는 별도의 이벤트 스트림으로 전달
                (membersResult as? BaseResult.Error)?.message?.let { _errorEvents.emit(it) }
                (placesResult as? BaseResult.Error)?.message?.let { _errorEvents.emit(it) }

                // 필터링 적용
                val filteredMembers = members.filter { member ->
                    checkMemberMatchesFilter(member, filters)
                }
                // 최종적으로 성공 데이터만 Pair로 묶어서 UI에 전달
                Pair(filteredMembers, places)
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = Pair(emptyList(), emptyList()),
            )

        // 지도 초기 위치
        private val _initialMapPosition = MutableLiveData<MapPosition?>()
        val initialMapPosition: LiveData<MapPosition?> = _initialMapPosition

        // 장소 안내 다이얼로그 표시 알림
        private val _shouldShowPlaceDialog = MutableSharedFlow<Unit>()
        val shouldShowPlaceDialog: SharedFlow<Unit> = _shouldShowPlaceDialog

        private var isPlaceCheckDone = false // 최초 장소 검사가 완료되었는가?

        init {
            checkExercisePlace()
            loadInitialMapPosition()
        }

        /**
         * 서버에서 매칭 회원 목록을 가져옵니다.
         */
        private fun fetchMatchingMembers(
            bounds: MapBounds,
            center: LatLng,
            zoom: Int
        ) {
            viewModelScope.launch {
                val result = getMatchingMembersUseCase(bounds)

                matchingMembers.value = result

                // 호출 성공 시 마지막 상태 업데이트
                lastFetchedCenter = center
                lastFetchedZoom = zoom
            }
        }

        /**
         * 마지막 위치를 불러와 LiveData를 업데이트합니다.
         */
        private fun loadInitialMapPosition() {
            viewModelScope.launch {
                val lastPosition = getLastMapPositionUseCase()
                _initialMapPosition.value = lastPosition
            }
        }

        /**
         * 지도의 마지막 위치를 저장합니다.
         */
        fun saveLastLocation(
            latitude: Double,
            longitude: Double,
            zoomLevel: Int,
        ) {
            viewModelScope.launch {
                val currentPosition = MapPosition(latitude, longitude, zoomLevel)
                saveLastMapPositionUseCase(currentPosition)
                _initialMapPosition.value = currentPosition
            }
        }

        /**
         * 운동 장소를 검사하고, 없다면 다이얼로그를 띄우는 공통 함수.
         * 이 함수가 호출되면 다이얼로그는 최대 한 번만 띄웁니다.
         */
        private fun checkExercisePlace() {
            if (isPlaceCheckDone) return
            isPlaceCheckDone = true // 검사가 시작되면 다시 호출되지 않도록 플래그를 먼저 올림

            viewModelScope.launch {
                val result = getExercisePlaceUseCase()
                exercisePlaces.value = result

                when (result) {
                    is BaseResult.Success -> {
                        if (result.data.isEmpty()) {
                            _shouldShowPlaceDialog.emit(Unit)
                        }
                    }

                    is BaseResult.Error -> {
                        // 에러 발생 시에도 없다고 간주하고 다이얼로그 표시
                        _shouldShowPlaceDialog.emit(Unit)
                    }
                }
            }
        }

        // 필터 버튼 클릭 시 호출
        fun onFilterTypeClicked(type: MatchingFilterType) {
            viewModelScope.launch {
                _currentFilterType.emit(type)
            }
        }

        /**
         * 필터 초기화
         */
        fun clearFilters() {
            _filterState.value = FilterState()
        }

        /**
         * 필터 옵션 선택 시 호출
         */
        fun onFilterOptionSelected(
            type: MatchingFilterType,
            option: Any?,
        ) {
            _filterState.update { current ->
                when (type) {
                    MatchingFilterType.GENDER -> current.copy(gender = toggle(current.gender, option))
                    MatchingFilterType.AGE -> current.copy(ageGroup = toggle(current.ageGroup, option))
                    MatchingFilterType.SKILL -> current.copy(skillLevel = toggle(current.skillLevel, option))
                    MatchingFilterType.SCORE -> current.copy(exerciseScore = toggle(current.exerciseScore, option))
                    MatchingFilterType.DAY -> {
                        val newDays =
                            if (option == null) {
                                // 전체 선택 시 모두 비움 (Empty == 전체)
                                emptySet()
                            } else {
                                val day = option as DayOfWeek
                                // 요일 토글
                                if (day in current.days) current.days - day else current.days + day
                            }
                        current.copy(days = newDays)
                    }
                }
            }
        }


        /**
         * 회원이 필터 조건을 만족하는지 검사합니다.
         */
        private fun checkMemberMatchesFilter(member: MatchingMember, filters: FilterState): Boolean {
            // 성별 필터 (선택 안됨(null)이면 통과, 선택되었으면 일치해야 함)
            if (filters.gender != null && member.gender != filters.gender.name) {
                return false
            }

            // 나이대 필터 (AgeGroup 로직에 따라 구현)
            if (filters.ageGroup != null) {
                if (!isAgeInGroup(member.birthDate, filters.ageGroup)) {
                    return false
                }
            }

            // 운동 실력 필터
            // TODO: 선호 운동 추가 시 활용
            /*if (filters.skillLevel != null && member.skillLevel != filters.skillLevel) {
                return false
            }*/

            // 운동 점수 필터
            // TODO: 운동 점수 데이터 추가 시 활용
            /*if (filters.exerciseScore != null && member.exerciseScore < filters.exerciseScore) {
                return false
            }*/

            // 요일 필터 (선택된 요일이 포함되면 통과)
            // TODO: 선호 운동 날짜 추가 시 활용
            /*if (filters.days.isNotEmpty()) {
                val hasMatchingDay = member.availableDays.any { it in filters.days }
                if (!hasMatchingDay) return false
            }*/

            return true
        }

        /**
         * 나이대 매칭 헬퍼 함수
         */
        private fun isAgeInGroup(birth: String, group: AgeGroup): Boolean {
            // 생년월일 데이터가 비어있으면 매칭에서 제외하거나 포함 (정책에 따라 결정)
            if (birth.isBlank()) return false

            return try {
                // 올해 - 생년
                val age = clockProvider.now().year - LocalDate.parse(birth).year

                when (group) {
                    AgeGroup.TEEN -> age in 10..19
                    AgeGroup.TWENTIES -> age in 20..29
                    AgeGroup.THIRTIES -> age in 30..39
                    AgeGroup.FORTIES -> age in 40..49
                    AgeGroup.FIFTIES -> age >= 50
                    else -> true
                }
            } catch (e: DateTimeParseException) {
                // 날짜 형식이 잘못되었을 경우 처리
                false
            }
        }

    /**
     * @param currentBounds 현재 지도 영역 (API 전송용)
     * @param currentCenter 현재 카메라 중심 (이동 거리 계산용)
     * @param currentZoom 현재 줌 레벨 (이동 거리 계산용)
     */
    fun fetchMatchingMembersIfMoved(
        currentBounds: MapBounds,
        currentCenter: LatLng,
        currentZoom: Int
    ) {
        if (shouldFetch(currentBounds, currentCenter, currentZoom)) {
            fetchMatchingMembers(currentBounds, currentCenter, currentZoom)
        }
    }

    private fun shouldFetch(
        currentBounds: MapBounds,
        currentCenter: LatLng,
        currentZoom: Int
    ): Boolean {
        val lastCenter = lastFetchedCenter ?: return true
        val lastZoom = lastFetchedZoom ?: return true

        // 줌이 바뀌면 다시 조회
        if (lastZoom != currentZoom) return true

        // THRESHOLD_RATE 이상 이동했는지 확인
        // 화면 가로/세로 길이의 THRESHOLD_RATE 이상 이동 시
        val latSpan = abs(currentBounds.topLeftLat - currentBounds.bottomRightLat)
        val lngSpan = abs(currentBounds.topLeftLng - currentBounds.bottomRightLng)

        // 이동 임계값 계산
        val latThreshold = latSpan * THRESHOLD_RATE
        val lngThreshold = lngSpan * THRESHOLD_RATE

        val latDiff = abs(lastCenter.latitude - currentCenter.latitude)
        val lngDiff = abs(lastCenter.longitude - currentCenter.longitude)

        return latDiff > latThreshold || lngDiff > lngThreshold
    }

        private fun <T> toggle(
            current: T?,
            selected: Any?,
        ): T? {
            val selectedCasted = selected as T
            return if (current == selectedCasted) null else selectedCasted
        }

        companion object {
            private const val THRESHOLD_RATE = 0.3
        }
    }
