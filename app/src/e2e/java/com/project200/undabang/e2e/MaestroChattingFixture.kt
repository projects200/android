package com.project200.undabang.e2e

import com.project200.domain.model.BaseResult
import com.project200.domain.model.ChattingRoom
import com.project200.domain.repository.ChattingRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoMap
import dagger.multibindings.StringKey
import javax.inject.Inject

internal class MaestroChattingFixture
    @Inject
    constructor(
        private val chattingRepository: ChattingRepository,
    ) : MaestroFeatureFixture {
        override suspend fun prepare(request: MaestroFeatureRequest): Map<String, String> {
            val room = requireDedicatedRoom(request)
            val history = readOrFail(chattingRepository.getChattingMessages(room.id, null, 30))
            if (!history.opponentActive || history.blockActive) {
                throw MaestroSetupFailure("chat_fixture_inactive")
            }
            return mapOf(
                "CHAT_ROOM_ID" to room.id.toString(),
                "PEER_MEMBER_ID" to room.opponentMemberId,
                "PEER_NICKNAME" to room.nickname,
            )
        }

        override suspend fun cleanup(request: MaestroFeatureRequest) {
            // 기존 전용 방과 메시지는 소유물이 아닙니다. 탈퇴·삭제하지 않고 멤버십만 재확인합니다.
            requireDedicatedRoom(request)
        }

        private suspend fun requireDedicatedRoom(request: MaestroFeatureRequest): ChattingRoom {
            val roomId = request.parameters.optString("MAESTRO_CHAT_ROOM_ID").toLongOrNull()
            val peerId = request.parameters.optString("MAESTRO_PEER_MEMBER_ID")
            if (
                request.feature != "chatting" || request.flow !in FLOWS ||
                !RUN_ID.matches(request.runId) || roomId == null || roomId <= 0 ||
                peerId.isBlank() || peerId == request.memberId
            ) {
                throw MaestroSetupFailure("chat_fixture_invalid")
            }
            val rooms = readOrFail(chattingRepository.getChattingRooms())
            val room =
                rooms.singleOrNull { it.id == roomId }
                    ?: throw MaestroSetupFailure("chat_fixture_room_missing")
            if (room.opponentMemberId != peerId || room.nickname.isBlank()) {
                throw MaestroSetupFailure("chat_fixture_peer_mismatch")
            }
            return room
        }

        private fun <T> readOrFail(result: BaseResult<T>): T =
            when (result) {
                is BaseResult.Success -> result.data
                is BaseResult.Error -> throw MaestroSetupFailure("chat_fixture_read_failed")
            }

        companion object {
            private val RUN_ID = Regex("[a-f0-9]{16,32}")
            private val FLOWS = setOf("chatting-send", "chatting-receive", "chatting-list-refresh", "chatting-history")
        }
    }

@Module
@InstallIn(SingletonComponent::class)
internal abstract class MaestroChattingFixtureModule {
    @Binds
    @IntoMap
    @StringKey("chatting")
    abstract fun bindChattingFixture(fixture: MaestroChattingFixture): MaestroFeatureFixture
}
