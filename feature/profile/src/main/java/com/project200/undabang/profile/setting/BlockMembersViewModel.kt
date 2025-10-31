package com.project200.undabang.profile.setting

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.project200.domain.model.BlockedMember
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BlockMembersViewModel
    @Inject
    constructor() : ViewModel() {
        private val _blockedMembers = MutableLiveData<List<BlockedMember>>()
        val blockedMembers: LiveData<List<BlockedMember>> = _blockedMembers

        init {
            fetchBlockedMembers()
        }

        fun fetchBlockedMembers() {
            viewModelScope.launch {
                // TODO: API 호출

                // 임시 더미 데이터
                _blockedMembers.value =
                    listOf(
                        BlockedMember(1, "uuid-1", "차단된사용자닉네임1", "url_to_image1", "2023-10-27T14:30:00"),
                        BlockedMember(2, "uuid-2", "차단된사용자닉네임2", "url_to_image2", "2023-10-26T11:00:00"),
                    )
            }
        }

        fun unblockMember(member: BlockedMember) {
            viewModelScope.launch {
                // TODO: API 호출

                // 임시로 리스트에서 해당 멤버를 제거하고 UI 업데이트
                val currentList = _blockedMembers.value?.toMutableList() ?: mutableListOf()
                currentList.remove(member)
                _blockedMembers.value = currentList
            }
        }
    }
