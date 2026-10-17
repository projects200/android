package com.project200.undabang.e2e

import android.content.Context
import android.system.Os
import android.util.AtomicFile
import com.project200.data.api.ApiService
import com.project200.data.api.KakaoApiService
import com.project200.data.dto.BaseResponse
import com.project200.data.dto.GetExercisePlaceDTO
import com.project200.data.dto.PlaceDocument
import com.project200.feature.matching.MatchingFixtureEvidence
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import javax.inject.Inject

internal class MatchingFeatureFixture
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val api: ApiService,
        private val catalog: KakaoApiService,
    ) : MaestroFeatureFixture {
        override suspend fun prepare(request: MaestroFeatureRequest): Map<String, String> {
            requireRequest(request)
            if (request.parameters.optString("phase") == "verify") {
                verify(request, readState(request))
                return emptyMap()
            }
            if (stateFile().exists() || evidenceFile().exists()) fail("matching_recovery_required")
            val input = request.parameters
            val peerId = required(input, "MAESTRO_PEER_MEMBER_ID")
            if (peerId == request.memberId) fail("matching_peer_must_differ")
            val top = coordinate(input, "MAESTRO_MATCHING_LEFT_TOP_LATITUDE", -90.0, 90.0)
            val left = coordinate(input, "MAESTRO_MATCHING_LEFT_TOP_LONGITUDE", -180.0, 180.0)
            val bottom = coordinate(input, "MAESTRO_MATCHING_RIGHT_BOTTOM_LATITUDE", -90.0, 90.0)
            val right = coordinate(input, "MAESTRO_MATCHING_RIGHT_BOTTOM_LONGITUDE", -180.0, 180.0)
            if (top <= bottom || left >= right) fail("matching_invalid_bounds")
            val peer =
                read(api.getMatchingMembers(top, left, bottom, right)).singleOrNull { it.memberId == peerId }
                    ?: fail("matching_peer_unavailable")
            val profile = read(api.getMatchingProfile(peerId))
            if (profile.nickname.isBlank() || peer.nickname != profile.nickname) fail("matching_peer_identity_changed")
            // 서버가 돌려준 지정 회원의 실제 장소만 사용하며, 최소 ID를 고르는 순서는 고정합니다.
            val location =
                peer.locations.filter {
                    it.exerciseLocationId > 0 && it.latitude.isFinite() && it.longitude.isFinite() &&
                        it.latitude in bottom..top && it.longitude in left..right
                }.minByOrNull { it.exerciseLocationId } ?: fail("matching_peer_location_unavailable")
            val query = required(input, "MAESTRO_MATCHING_SEARCH_QUERY")
            if (!query.all { it.code in 32..126 }) fail("matching_search_query_requires_ascii")
            val catalogName = required(input, "MAESTRO_MATCHING_CATALOG_PLACE_NAME")
            val place = catalogPlace(query, catalogName, location.latitude, location.longitude)
            // 권한 거부 검색은 실제 앱 기본 위치에서 실행하므로 같은 결과가 그 위치에서도 유효해야 합니다.
            val deniedPlace = catalogPlace(query, catalogName, 37.5666805, 126.9784147)
            if (place != deniedPlace) fail("matching_catalog_result_changed")
            val places = read(api.getExercisePlaces())
            if (places.size >= 10 || places.any { it.id <= 0 } || places.map { it.id }.toSet().size != places.size) {
                fail("matching_place_capacity_or_identity_invalid")
            }
            val roomId =
                required(input, "MAESTRO_MATCHING_ROOM_ID").toLongOrNull()
                    ?.takeIf { it > 0 } ?: fail("matching_room_invalid")
            val rooms = read(api.getChattingRooms())
            val peerRooms = rooms.filter { it.otherMemberId == peerId }
            if (peerRooms.size != 1 || peerRooms.single().chatRoomId != roomId ||
                rooms.any { it.chatRoomId <= 0 } || rooms.map { it.chatRoomId }.toSet().size != rooms.size
            ) {
                fail("matching_existing_peer_room_required")
            }
            val marker = "e2e-m-${request.runId}"
            if (places.any { it.name == marker || it.name == "$marker-edit" }) fail("matching_marker_collision")
            val address = address(place)
            val variables =
                linkedMapOf(
                    "MATCHING_PLACE_NAME" to marker,
                    "MATCHING_EDITED_PLACE_NAME" to "$marker-edit",
                    "MATCHING_LATITUDE" to location.latitude.toString(),
                    "MATCHING_LONGITUDE" to location.longitude.toString(),
                    "MATCHING_PEER_ID" to peerId,
                    "MATCHING_PEER_NICKNAME" to profile.nickname,
                    "MATCHING_PEER_LOCATION_ID" to location.exerciseLocationId.toString(),
                    "MATCHING_SEARCH_QUERY" to query,
                    "MATCHING_CATALOG_NAME" to place.placeName,
                    "MATCHING_CATALOG_ADDRESS" to address,
                )
            val state =
                JSONObject()
                    .put("memberId", request.memberId)
                    .put("runId", request.runId)
                    .put("flow", request.flow)
                    .put("marker", marker)
                    .put("peerId", peerId)
                    .put("locationId", location.exerciseLocationId)
                    .put("roomId", roomId)
                    .put("catalogAddress", address)
                    .put("catalogLatitude", place.latitude.toDouble())
                    .put("catalogLongitude", place.longitude.toDouble())
                    .put("places", JSONArray().apply { places.forEach { put(placeJson(it)) } })
                    .put(
                        "rooms",
                        JSONArray().apply {
                            rooms.forEach { put(JSONObject().put("id", it.chatRoomId).put("peerId", it.otherMemberId)) }
                        },
                    )
            // 소유권 기준과 실행 ID는 UI를 시작하기 전에 앱 내부에 저장합니다.
            write(stateFile(), state)
            write(evidenceFile(), JSONObject().put("runId", request.runId).put("events", JSONArray()))
            return variables
        }

        override suspend fun cleanup(request: MaestroFeatureRequest) {
            requireRequest(request)
            if (!stateFile().exists()) {
                // prepare가 전제조건에서 중단되면 UI와 서버 변경은 아직 시작하지 않은 상태입니다.
                if (evidenceFile().exists()) fail("matching_snapshot_missing")
                return
            }
            val state = readState(request)
            val baseline = baselinePlaces(state)
            val baselineIds = baseline.map { it.id }.toSet()
            val marker = state.getString("marker")
            val owned = read(api.getExercisePlaces()).filter { MatchingFixturePolicy.ownsPlace(it, baselineIds, marker) }
            // 전체 목록을 조회한 뒤 삭제하며, 기존 ID와 다른 실행의 표식은 보존합니다.
            for (place in owned) {
                val current = read(api.getExercisePlaces()).singleOrNull { it.id == place.id } ?: continue
                if (!MatchingFixturePolicy.ownsPlace(current, baselineIds, marker)) fail("matching_place_ownership_changed")
                if (!api.deleteExercisePlace(current.id).succeed) fail("matching_place_cleanup_failed")
            }
            val events = events(request, allowMissing = true)
            val navigatedIds =
                events.filter { it.optString("type") == "chat" && it.optString("peerId") == state.getString("peerId") }
                    .map { it.getLong("roomId") }.toSet()
            val baselineRooms = roomOwners(state)
            val ownedRooms =
                read(api.getChattingRooms()).filter {
                    MatchingFixturePolicy.ownsRoom(
                        it.chatRoomId,
                        it.otherMemberId,
                        baselineRooms.keys,
                        navigatedIds,
                        state.getString("peerId"),
                    )
                }
            for (room in ownedRooms) {
                val current = read(api.getChattingRooms()).singleOrNull { it.chatRoomId == room.chatRoomId } ?: continue
                if (
                    !MatchingFixturePolicy.ownsRoom(
                        current.chatRoomId,
                        current.otherMemberId,
                        baselineRooms.keys,
                        navigatedIds,
                        state.getString("peerId"),
                    )
                ) {
                    fail("matching_room_ownership_changed")
                }
                // 기존 전용 방은 보존합니다. API는 본인 계정의 퇴장이며 상대방 이력 삭제가 아닙니다.
                if (!api.deleteChatRoom(current.chatRoomId).succeed) fail("matching_room_cleanup_failed")
            }
            val remaining = read(api.getExercisePlaces())
            if (remaining.any { MatchingFixturePolicy.ownsPlace(it, baselineIds, marker) } ||
                baseline.any { old -> remaining.singleOrNull { it.id == old.id } != old }
            ) {
                fail("matching_place_baseline_changed")
            }
            val remainingRooms = read(api.getChattingRooms()).associate { it.chatRoomId to it.otherMemberId }
            if (baselineRooms.any { (id, peer) -> remainingRooms[id] != peer } ||
                remainingRooms.keys != baselineRooms.keys
            ) {
                fail("matching_room_baseline_changed")
            }
            // 공통 정리·세션 해제 뒤 호스트의 최종 pm clear까지 증거를 보존해 같은 소유권으로 재시도합니다.
        }

        private suspend fun verify(
            request: MaestroFeatureRequest,
            state: JSONObject,
        ) {
            val baseline = baselinePlaces(state)
            val current = read(api.getExercisePlaces())
            if (current.toSet() != baseline.toSet()) fail("matching_final_places_differ")
            val owners = read(api.getChattingRooms()).associate { it.chatRoomId to it.otherMemberId }
            if (owners != roomOwners(state)) fail("matching_final_rooms_differ")
            val events = events(request)
            if (request.flow == "matching-place-crud") {
                val snapshots = events.filter { it.getString("type") == "places" }.map { it.getJSONArray("places") }
                val marker = state.getString("marker")
                val created = snapshots.flatMap { objects(it) }.filter { it.getString("name") == marker }
                val edited = snapshots.flatMap { objects(it) }.filter { it.getString("name") == "$marker-edit" }
                if (created.isEmpty() || edited.isEmpty()) fail("matching_crud_server_evidence_missing")
                val ids = (created + edited).map { it.getLong("id") }.toSet()
                if (ids.size != 1 || ids.single() in baseline.map { it.id }) fail("matching_crud_identity_changed")
                if (
                    (created + edited).any {
                        it.getString("address") != state.getString("catalogAddress") ||
                            it.getDouble("latitude") != state.getDouble("catalogLatitude") ||
                            it.getDouble("longitude") != state.getDouble("catalogLongitude")
                    }
                ) {
                    fail("matching_crud_catalog_value_changed")
                }
            }
            if (request.flow == "matching-member-to-chat") {
                val chats = events.filter { it.getString("type") == "chat" }
                val denied = events.filter { it.getString("type") == "denied" }
                if (chats.size != 1 || denied.isEmpty() ||
                    (chats + denied).any {
                        it.getString("peerId") != state.getString("peerId") ||
                            it.getLong("locationId") != state.getLong("locationId")
                    } ||
                    chats.single().getLong("roomId") != state.getLong("roomId") ||
                    owners[state.getLong("roomId")] != state.getString("peerId")
                ) {
                    fail("matching_chat_identity_or_denial_failed")
                }
            }
        }

        private suspend fun catalogPlace(
            query: String,
            name: String,
            latitude: Double,
            longitude: Double,
        ): PlaceDocument {
            val place =
                catalog.searchPlacesByKeyword(query, latitude, longitude).documents.singleOrNull { it.placeName == name }
                    ?: fail("matching_catalog_result_unavailable")
            val lat = place.latitude.toDoubleOrNull()
            val lon = place.longitude.toDoubleOrNull()
            if (lat == null || lon == null || !lat.isFinite() || !lon.isFinite() ||
                lat !in -90.0..90.0 || lon !in -180.0..180.0 || address(place).isBlank()
            ) {
                fail("matching_catalog_value_invalid")
            }
            return place
        }

        private fun address(place: PlaceDocument): String = place.roadAddressName.ifBlank { place.addressName }

        private fun requireRequest(request: MaestroFeatureRequest) {
            if (request.feature != "matching" ||
                request.flow !in setOf("matching-place-crud", "matching-place-validation", "matching-member-to-chat") ||
                !Regex("[a-f0-9]{8,32}").matches(request.runId)
            ) {
                fail("matching_request_invalid")
            }
        }

        private fun readState(request: MaestroFeatureRequest): JSONObject {
            if (!stateFile().isFile) fail("matching_snapshot_missing")
            val state = readJson(stateFile())
            if (state.getString("memberId") != request.memberId || state.getString("runId") != request.runId ||
                state.getString("flow") != request.flow
            ) {
                fail("matching_snapshot_identity_invalid")
            }
            return state
        }

        private fun events(
            request: MaestroFeatureRequest,
            allowMissing: Boolean = false,
        ): List<JSONObject> {
            if (!evidenceFile().exists() && allowMissing) return emptyList()
            if (!evidenceFile().isFile) fail("matching_evidence_missing")
            val document = readJson(evidenceFile())
            if (document.getString("runId") != request.runId) fail("matching_evidence_identity_invalid")
            return objects(document.getJSONArray("events"))
        }

        private fun baselinePlaces(state: JSONObject): List<GetExercisePlaceDTO> =
            objects(state.getJSONArray("places")).map {
                GetExercisePlaceDTO(
                    it.getLong("id"),
                    it.getString("name"),
                    it.getString("address"),
                    it.getDouble("latitude"),
                    it.getDouble("longitude"),
                )
            }

        private fun roomOwners(state: JSONObject): Map<Long, String> =
            objects(state.getJSONArray("rooms")).associate {
                it.getLong("id") to it.getString("peerId")
            }

        private fun placeJson(place: GetExercisePlaceDTO): JSONObject =
            JSONObject()
                .put("id", place.id)
                .put("name", place.name)
                .put("address", place.address)
                .put("latitude", place.latitude)
                .put("longitude", place.longitude)

        private fun objects(array: JSONArray): List<JSONObject> = (0 until array.length()).map { array.getJSONObject(it) }

        private fun required(
            input: JSONObject,
            key: String,
        ): String =
            input.optString(key).takeIf { it.isNotBlank() }
                ?: fail("matching_required_input_missing")

        private fun coordinate(
            input: JSONObject,
            key: String,
            min: Double,
            max: Double,
        ): Double =
            required(input, key).toDoubleOrNull()
                ?.takeIf { it.isFinite() && it in min..max } ?: fail("matching_invalid_bounds")

        private fun <T> read(response: BaseResponse<T>): T =
            response.data.takeIf { response.succeed }
                ?: fail("matching_api_read_failed")

        private fun stateFile(): File = File(context.filesDir, "maestro-matching-state.json")

        private fun evidenceFile(): File = File(context.filesDir, MatchingFixtureEvidence.FILE_NAME)

        private fun readJson(file: File): JSONObject =
            JSONObject(AtomicFile(file).openRead().bufferedReader(Charsets.UTF_8).use { it.readText() })

        private fun write(
            file: File,
            json: JSONObject,
        ) {
            val atomic = AtomicFile(file)
            val stream = atomic.startWrite()
            try {
                Os.fchmod(stream.fd, 384)
                stream.write(json.toString().toByteArray(Charsets.UTF_8))
                stream.fd.sync()
                atomic.finishWrite(stream)
            } catch (_: Exception) {
                atomic.failWrite(stream)
                fail("matching_private_file_failed")
            }
        }

        private fun fail(reason: String): Nothing = throw MaestroSetupFailure(reason)
    }
