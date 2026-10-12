package com.project200.feature.chatting

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.project200.common.utils.ClockProvider
import com.project200.domain.model.BaseResult
import com.project200.domain.model.ChattingRoom
import com.project200.domain.usecase.GetChattingRoomsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChattingListViewModel
    @Inject
    constructor(
        private val getChattingRoomsUseCase: GetChattingRoomsUseCase,
        private val clockProvider: ClockProvider,
    ) : ViewModel() {
        private val _chattingRooms = MutableStateFlow<List<ChattingRoom>>(emptyList())
        val chattingRooms: StateFlow<List<ChattingRoom>> = _chattingRooms

        // 최초 조회 전 빈 기본값은 서버가 확인한 빈 상태와 구분합니다.
        private val _hasLoadedChattingRooms = MutableStateFlow(false)
        val hasLoadedChattingRooms: StateFlow<Boolean> = _hasLoadedChattingRooms
        private var chattingRoomsRequestId = 0L

        fun fetchChattingRooms() {
            val requestId = ++chattingRoomsRequestId
            _hasLoadedChattingRooms.value = false
            viewModelScope.launch {
                val result = getChattingRoomsUseCase()
                _hasLoadedChattingRooms.value = false
                when (result) {
                    is BaseResult.Success -> {
                        _chattingRooms.value = result.data
                        _hasLoadedChattingRooms.value = requestId == chattingRoomsRequestId
                    }
                    is BaseResult.Error -> {
                        // TODO: 에러 처리 (예: Toast 메시지 표시)
                    }
                }
            }
        }
    }
