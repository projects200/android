package com.project200.undabang.profile.setting

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.project200.domain.model.BaseResult
import com.project200.domain.model.BlockedMember
import com.project200.domain.usecase.GetBlockedMembersUseCase
import com.project200.domain.usecase.UnblockMemberUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BlockMembersViewModel
    @Inject
    constructor(
        private val getBlockedMembersUseCase: GetBlockedMembersUseCase,
        private val unblockMemberUseCase: UnblockMemberUseCase,
    ) : ViewModel() {
        private val _blockedMembers = MutableStateFlow<List<BlockedMember>>(emptyList())
        val blockedMembers: StateFlow<List<BlockedMember>> = _blockedMembers.asStateFlow()

        private val _errorEvent = MutableSharedFlow<String?>()
        val errorEvent: SharedFlow<String?> = _errorEvent

        init {
            fetchBlockedMembers()
        }

        /**
         * 차단된 사용자 목록을 가져옵니다.
         */
        fun fetchBlockedMembers() {
            viewModelScope.launch {
                when (val result = getBlockedMembersUseCase()) {
                    is BaseResult.Success -> {
                        _blockedMembers.value = result.data
                    }
                    is BaseResult.Error -> {
                        _errorEvent.emit(result.message)
                    }
                }
            }
        }

        /**
         * 특정 사용자의 차단을 해제합니다.
         * 성공했을 경우에만 목록을 갱신하여 UI에서 제거된 것처럼 보이게 합니다.
         * @param member 차단 해제할 사용자 객체
         */
        fun unblockMember(member: BlockedMember) {
            viewModelScope.launch {
                when (val result = unblockMemberUseCase(member.memberId)) {
                    is BaseResult.Success -> {
                        fetchBlockedMembers()
                    }
                    is BaseResult.Error -> {
                        _errorEvent.emit(result.message)
                    }
                }
            }
        }
    }
