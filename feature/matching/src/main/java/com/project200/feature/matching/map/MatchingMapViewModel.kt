package com.project200.feature.matching.map

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.project200.domain.model.BaseResult
import com.project200.domain.model.ExercisePlace
import com.project200.domain.model.MapPosition
import com.project200.domain.model.MatchingMember
import com.project200.domain.usecase.GetExercisePlaceUseCase
import com.project200.domain.usecase.GetLastMapPositionUseCase
import com.project200.domain.usecase.GetMatchingMembersUseCase
import com.project200.domain.usecase.SaveLastMapPositionUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MatchingMapViewModel
    @Inject
    constructor(
        private val getMatchingMembersUseCase: GetMatchingMembersUseCase,
        private val getLastMapPositionUseCase: GetLastMapPositionUseCase,
        private val saveLastMapPositionUseCase: SaveLastMapPositionUseCase,
        private val getExercisePlaceUseCase: GetExercisePlaceUseCase,
    ) : ViewModel() {
        // 회원 목록
        private val matchingMembers =
            MutableStateFlow<BaseResult<List<MatchingMember>>>(BaseResult.Success(emptyList()))

        // 내 운동 장소 목록
        private val exercisePlaces =
            MutableStateFlow<BaseResult<List<ExercisePlace>>>(BaseResult.Success(emptyList()))

        private val _errorEvents = MutableSharedFlow<String>()
        val errorEvents: SharedFlow<String> = _errorEvents

        val combinedMapData: StateFlow<Pair<List<MatchingMember>, List<ExercisePlace>>> =
            combine(
                matchingMembers,
                exercisePlaces,
            ) { membersResult, placesResult ->
                // 성공 데이터만 추출, 실패 시 빈 리스트
                val members = (membersResult as? BaseResult.Success)?.data ?: emptyList()
                val places = (placesResult as? BaseResult.Success)?.data ?: emptyList()

                // 에러 결과는 별도의 이벤트 스트림으로 전달
                (membersResult as? BaseResult.Error)?.message?.let { _errorEvents.emit(it) }
                (placesResult as? BaseResult.Error)?.message?.let { _errorEvents.emit(it) }

                // 최종적으로 성공 데이터만 Pair로 묶어서 UI에 전달
                Pair(members, places)
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
        fun fetchMatchingMembers() {
            viewModelScope.launch {
                matchingMembers.value = getMatchingMembersUseCase()
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

        companion object {
            const val NO_URL = "404"
        }
    }
