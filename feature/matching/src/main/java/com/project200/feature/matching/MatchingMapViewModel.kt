package com.project200.feature.matching

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.project200.domain.model.BaseResult
import com.project200.domain.model.MapPosition
import com.project200.domain.model.MatchingMember
import com.project200.domain.usecase.GetLastMapPositionUseCase
import com.project200.domain.usecase.GetMatchingMembersUseCase
import com.project200.domain.usecase.SaveLastMapPositionUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MatchingMapViewModel
    @Inject
    constructor(
        private val getMatchingMembersUseCase: GetMatchingMembersUseCase,
        private val getLastMapPositionUseCase: GetLastMapPositionUseCase,
        private val saveLastMapPositionUseCase: SaveLastMapPositionUseCase,
    ) : ViewModel() {
        // 회원 목록
        private val _matchingMembers = MutableLiveData<BaseResult<List<MatchingMember>>>()
        val matchingMembers: LiveData<BaseResult<List<MatchingMember>>> = _matchingMembers

        // 지도 초기 위치
        private val _initialMapPosition = MutableLiveData<MapPosition?>()
        val initialMapPosition: LiveData<MapPosition?> = _initialMapPosition

        init {
            loadInitialMapPosition()
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
    }
