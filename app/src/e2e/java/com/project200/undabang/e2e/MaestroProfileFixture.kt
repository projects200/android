package com.project200.undabang.e2e

import android.content.Context
import android.util.AtomicFile
import com.project200.data.api.ApiService
import com.project200.data.dto.BaseResponse
import com.project200.data.dto.GetPreferredExerciseDTO
import com.project200.data.dto.NotificationStateDTO
import com.project200.data.dto.PutProfileRequest
import com.project200.domain.repository.FcmRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import javax.inject.Inject

internal class MaestroProfileFixture
    @Inject
    constructor(
        @ApplicationContext context: Context,
        private val api: ApiService,
        private val fcmRepository: FcmRepository,
        private val recovery: MaestroProfileRecovery,
        private val notificationReadiness: MaestroProfileNotificationReadiness,
    ) : MaestroFeatureFixture {
        private val snapshotFile = AtomicFile(File(context.filesDir, "maestro-profile-recovery.json"))

        private fun hasSnapshot(): Boolean {
            val path = snapshotFile.baseFile.path
            return snapshotFile.baseFile.exists() || File("$path.bak").exists() || File("$path.new").exists()
        }

        override suspend fun prepare(request: MaestroFeatureRequest): Map<String, String> {
            validateRequest(request)
            if (hasSnapshot()) throw MaestroSetupFailure("profile_recovery_pending")
            val profile = read(api.getProfile())
            MaestroProfilePolicy.validateText(profile.nickname, profile.gender, profile.bio)
            val preferences = read(api.getPreferredExercises())
            MaestroProfilePolicy.validatePreferences(preferences)
            var addedType: Long? = null
            var addedName = ""
            if (request.flow == "profile-preferred-exercise") {
                if (preferences.size !in 1..4) throw MaestroSetupFailure("profile_preference_baseline_unavailable")
                val catalog = read(api.getPreferredExerciseTypes())
                if (
                    catalog.any { it.exerciseId <= 0 || it.exerciseName.isBlank() } ||
                    catalog.map { it.exerciseId }.toSet().size != catalog.size ||
                    preferences.any { original -> catalog.none { it.exerciseId == original.exerciseTypeId } }
                ) {
                    throw MaestroSetupFailure("profile_catalog_invalid")
                }
                val candidate =
                    catalog.firstOrNull { type -> preferences.none { it.exerciseTypeId == type.exerciseId } }
                        ?: throw MaestroSetupFailure("profile_unselected_exercise_missing")
                if (candidate.exerciseName.length > 4096 || candidate.exerciseName.any { it.code < 32 }) {
                    throw MaestroSetupFailure("profile_catalog_invalid")
                }
                addedType = candidate.exerciseId
                addedName = candidate.exerciseName
            }
            val notificationSnapshot = notificationReadiness.prepare(request.flow)
            val snapshot =
                JSONObject()
                    .put("memberId", request.memberId)
                    .put("runId", request.runId)
                    .put("flow", request.flow)
                    .put("nickname", profile.nickname)
                    .put("gender", profile.gender)
                    .put("bio", profile.bio)
                    .put("fcmToken", notificationSnapshot?.token ?: JSONObject.NULL)
                    .put(
                        "preferences",
                        JSONArray().apply { preferences.forEach { put(encodePreference(it)) } },
                    )
                    .put(
                        "notifications",
                        notificationSnapshot?.let {
                            JSONArray().apply {
                                it.settings.forEach { put(JSONObject().put("type", it.type).put("enabled", it.enabled)) }
                            }
                        } ?: JSONObject.NULL,
                    )
                    .put("addedType", addedType ?: JSONObject.NULL)
                    .put("addedName", addedName)
            // 서버 설정/프로필 쓰기 전에 원본 ID/값과 해당 flow의 기기 토큰을 앱 전용 파일에 보관합니다.
            save(snapshot)
            if (notificationSnapshot != null) {
                val token = notificationSnapshot.token
                val notifications = notificationSnapshot.settings
                if (fcmRepository.getSavedToken() != token) throw MaestroSetupFailure("profile_fcm_identity_changed")
                write(
                    api.patchNotiState(
                        token,
                        notifications.filter { it.type in knownTypes }.map { it.copy(enabled = false) },
                    ),
                )
                val prepared = read(api.getNotiState(token))
                MaestroProfilePolicy.validateNotifications(prepared)
                val expected = notifications.map { if (it.type in knownTypes) it.copy(enabled = false) else it }
                if (notificationValues(prepared) != notificationValues(expected)) {
                    throw MaestroSetupFailure("profile_notification_prepare_failed")
                }
            }
            return mapOf(
                "E2E_PROFILE_ORIGINAL_NICKNAME" to profile.nickname,
                "E2E_PROFILE_ORIGINAL_BIO" to requireNotNull(profile.bio),
                "E2E_PROFILE_NEW_NICKNAME" to "e2e${request.runId}",
                "E2E_PROFILE_NEW_BIO" to "e2e-intro-${request.runId}",
                "E2E_PROFILE_ADDED_TYPE_ID" to (addedType?.toString() ?: ""),
                "E2E_PROFILE_ADDED_NAME" to addedName,
                "E2E_PROFILE_BASELINE_TYPE_IDS" to preferences.joinToString(",") { it.exerciseTypeId.toString() },
            )
        }

        override suspend fun cleanup(request: MaestroFeatureRequest) {
            validateRequest(request)
            if (!hasSnapshot()) return
            val snapshot = JSONObject(snapshotFile.openRead().bufferedReader().use { it.readText() })
            if (
                snapshot.getString("memberId") != request.memberId || snapshot.getString("runId") != request.runId ||
                snapshot.getString("flow") != request.flow
            ) {
                throw MaestroSetupFailure("profile_recovery_identity_failed")
            }
            if (!snapshot.has("fcmToken") || !snapshot.has("notifications")) {
                throw MaestroSetupFailure("profile_recovery_notification_invalid")
            }
            val notificationSnapshot =
                if (snapshot.isNull("fcmToken") && snapshot.isNull("notifications")) {
                    if (request.flow == "profile-notification") throw MaestroSetupFailure("profile_recovery_notification_invalid")
                    null
                } else {
                    if (snapshot.isNull("fcmToken") || snapshot.isNull("notifications")) {
                        throw MaestroSetupFailure("profile_recovery_notification_invalid")
                    }
                    val token = snapshot.getString("fcmToken")
                    if (token.isBlank()) throw MaestroSetupFailure("profile_fcm_identity_changed")
                    val notifications = decodeNotifications(snapshot.getJSONArray("notifications"))
                    MaestroProfilePolicy.validateNotifications(notifications)
                    MaestroProfileNotificationSnapshot(token, notifications)
                }
            val original =
                PutProfileRequest(
                    snapshot.getString("nickname"),
                    snapshot.getString("gender"),
                    snapshot.getString("bio"),
                )
            val preferences = decodePreferences(snapshot.getJSONArray("preferences"))
            MaestroProfilePolicy.validateText(original.nickname, original.gender, original.bio)
            val addedType = if (snapshot.isNull("addedType")) null else snapshot.getLong("addedType")
            recovery.restore(original, preferences, notificationSnapshot, addedType, request.flow, request.runId)
            // 이후 공용 정리가 실패해도 같은 소유권으로 재시도하도록 호스트의 최종 pm clear까지 snapshot을 보존합니다.
        }

        private fun validateRequest(request: MaestroFeatureRequest) {
            if (request.feature != "profile" || request.flow !in flows || !runIdPattern.matches(request.runId)) {
                throw MaestroSetupFailure("profile_request_invalid")
            }
        }

        private fun save(snapshot: JSONObject) {
            val file = snapshotFile
            val content = snapshot.toString()
            val stream = file.startWrite()
            try {
                stream.write(content.toByteArray(Charsets.UTF_8))
                file.finishWrite(stream)
            } catch (_: Exception) {
                file.failWrite(stream)
                throw MaestroSetupFailure("profile_snapshot_write_failed")
            }
            if (file.openRead().bufferedReader().use { it.readText() } != content) {
                throw MaestroSetupFailure("profile_snapshot_write_failed")
            }
        }

        private fun encodePreference(value: GetPreferredExerciseDTO): JSONObject =
            JSONObject()
                .put("id", value.preferredExerciseId)
                .put("type", value.exerciseTypeId)
                .put("name", value.exerciseName)
                .put("skill", value.skillLevel)
                .put("days", JSONArray(value.daysOfWeek))
                .put("image", value.imageUrl ?: JSONObject.NULL)

        private fun decodePreferences(values: JSONArray): List<GetPreferredExerciseDTO> =
            (0 until values.length()).map { index ->
                val value = values.getJSONObject(index)
                val days = value.getJSONArray("days")
                GetPreferredExerciseDTO(
                    value.getLong("id"),
                    value.getLong("type"),
                    value.getString("name"),
                    value.getString("skill"),
                    (0 until days.length()).map { days.getBoolean(it) },
                    if (value.isNull("image")) null else value.getString("image"),
                )
            }

        private fun decodeNotifications(values: JSONArray): List<NotificationStateDTO> =
            (0 until values.length()).map { index ->
                val value = values.getJSONObject(index)
                NotificationStateDTO(value.getString("type"), value.getBoolean("enabled"))
            }

        private fun notificationValues(values: List<NotificationStateDTO>) = values.associate { it.type to it.enabled }

        private fun <T> read(response: BaseResponse<T>): T {
            if (!response.succeed) throw MaestroSetupFailure("profile_fixture_read_failed")
            return response.data ?: throw MaestroSetupFailure("profile_fixture_read_failed")
        }

        private fun write(response: BaseResponse<Unit?>) {
            if (!response.succeed) throw MaestroSetupFailure("profile_fixture_write_failed")
        }

        private companion object {
            val runIdPattern = Regex("[a-f0-9]{16}")
            val knownTypes = setOf("WORKOUT_REMINDER", "CHAT_MESSAGE")
            val flows = setOf("profile-edit", "profile-edit-validation", "profile-preferred-exercise", "profile-notification")
        }
    }
