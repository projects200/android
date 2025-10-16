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

        fun fetchChattingRooms() {
            viewModelScope.launch {
                when (val result = getChattingRoomsUseCase()) {
                    is BaseResult.Success -> {
                        _chattingRooms.value = result.data
                    }
                    is BaseResult.Error -> {
                        // TODO: 에러 처리 (예: Toast 메시지 표시)
                    }
                }
            }
        }
    }
