package com.project200.feature.matching.map

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.project200.domain.model.BaseResult
import com.project200.domain.model.MapPosition
import com.project200.domain.model.MatchingMember
import com.project200.domain.usecase.GetExercisePlaceUseCase
import com.project200.domain.usecase.GetLastMapPositionUseCase
import com.project200.domain.usecase.GetMatchingMembersUseCase
import com.project200.domain.usecase.GetOpenUrlUseCase
import com.project200.domain.usecase.SaveLastMapPositionUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class MatchingMapViewModel
    @Inject
    constructor(
        private val getMatchingMembersUseCase: GetMatchingMembersUseCase,
        private val getLastMapPositionUseCase: GetLastMapPositionUseCase,
        private val saveLastMapPositionUseCase: SaveLastMapPositionUseCase,
        private val getOpenUrlUseCase: GetOpenUrlUseCase,
        private val getExercisePlaceUseCase: GetExercisePlaceUseCase,
    ) : ViewModel() {
        // 회원 목록
        private val _matchingMembers = MutableLiveData<BaseResult<List<MatchingMember>>>()
        val matchingMembers: LiveData<BaseResult<List<MatchingMember>>> = _matchingMembers

        // 지도 초기 위치
        private val _initialMapPosition = MutableLiveData<MapPosition?>()
        val initialMapPosition: LiveData<MapPosition?> = _initialMapPosition

        // URL 안내 화면으로 이동 알림
        private val _shouldNavigateToGuide = MutableSharedFlow<Unit>()
        val shouldNavigateToGuide: SharedFlow<Unit> = _shouldNavigateToGuide

        // 장소 안내 다이얼로그 표시 알림
        private val _shouldShowPlaceDialog = MutableSharedFlow<Unit>()
        val shouldShowPlaceDialog: SharedFlow<Unit> = _shouldShowPlaceDialog

        private var isUrlCheckDone = false // 최초 URL 검사가 완료되었는가?
        private var wasUrlMissed = false // 최초 검사 시 URL이 없었는가?
        private var isPlaceCheckDone = false // 최초 장소 검사가 완료되었는가?

        init {
            loadInitialMapPosition()
            checkIsGuideNeed()
        }

        /**
         * 서버에서 매칭 회원 목록을 가져옵니다.
         */
        fun fetchMatchingMembers() {
            viewModelScope.launch {
                val result = getMatchingMembersUseCase()
                _matchingMembers.value = result
            }
        }

        /**
         * 마지막 위치를 불러와 LiveData를 업데이트합니다.
         */
        private fun loadInitialMapPosition() {
            viewModelScope.launch {
                // UseCase를 함수처럼 호출
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
                saveLastMapPositionUseCase(MapPosition(latitude, longitude, zoomLevel))
            }
        }

        /**
         * ViewModel 생성 시 단 한 번만 호출되는 최초 설정 검사 함수.
         */
        private fun checkIsGuideNeed() {
            Timber.tag(
                "MatchingMapViewModel",
            ).d("isUrlCheckDone: $isUrlCheckDone / wasUrlMissed: $wasUrlMissed / isPlaceCheckDone: $isPlaceCheckDone")
            if (isUrlCheckDone) return
            isUrlCheckDone = true // 중복 실행 방지를 위해 플래그를 먼저 올림

            viewModelScope.launch {
                when (val urlResult = getOpenUrlUseCase()) {
                    is BaseResult.Success -> {
                        if (urlResult.data.url.isNotEmpty()) {
                            // URL 존재
                            wasUrlMissed = false
                            // 즉시 운동 장소 검사를 진행
                            checkExercisePlace()
                        } else {
                            // 빈 URL
                            wasUrlMissed = true
                            _shouldNavigateToGuide.emit(Unit)
                        }
                    }
                    is BaseResult.Error -> {
                        if (urlResult.errorCode == NO_URL) {
                            // URL이 생성되지 않은 경우
                            wasUrlMissed = true
                            _shouldNavigateToGuide.emit(Unit)
                        } else {
                            // 그 외의 에러는 무시하고, 운동 장소 검사만 진행
                            wasUrlMissed = false
                            checkExercisePlace()
                        }
                    }
                }
            }
        }

        /**
         * Fragment가 onResume 될 때 호출될 함수.
         * url 안내 페이지에서 돌아온 경우를 처리합니다.
         */
        fun checkHasPlaceGuideBeenShown() {
            // 최초에 URL이 없어서 안내 페이지로 갔었고, 아직 장소 다이얼로그가 뜬 적이 없다면
            if (wasUrlMissed && !isPlaceCheckDone) {
                // 이제 돌아왔으니 운동 장소 검사를 진행
                checkExercisePlace()
            }
        }

        /**
         * 운동 장소를 검사하고, 없다면 다이얼로그를 띄우는 공통 함수.
         * 이 함수가 호출되면 다이얼로그는 최대 한 번만 띄웁니다.
         */
        private fun checkExercisePlace() {
            // 이미 다이얼로그를 보여줬다면 더 이상 검사하지 않음
            if (isPlaceCheckDone) return

            viewModelScope.launch {
                when (val result = getExercisePlaceUseCase()) {
                    is BaseResult.Success -> {
                        if (result.data.isEmpty()) {
                            // 장소가 없으면 다이얼로그 표시 요청
                            _shouldShowPlaceDialog.emit(Unit)
                            // 다이얼로그를 띄웠다고 플래그를 설정하여 다시는 띄우지 않도록 함
                            isPlaceCheckDone = true
                        }
                    }
                    is BaseResult.Error -> {
                        // 에러 발생 시에도 없다고 간주하고 다이얼로그 표시
                        _shouldShowPlaceDialog.emit(Unit)
                        isPlaceCheckDone = true
                    }
                }
            }
        }

        companion object {
            const val NO_URL = "404"
        }
    }
