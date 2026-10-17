package com.project200.undabang.e2e

import com.project200.data.api.ApiService
import com.project200.data.dto.BaseResponse
import com.project200.data.dto.DeletePreferredExerciseDTO
import com.project200.data.dto.GetPreferredExerciseDTO
import com.project200.data.dto.NotificationStateDTO
import com.project200.data.dto.PostPreferredExerciseDTO
import com.project200.data.dto.PutProfileRequest
import com.project200.domain.repository.FcmRepository
import javax.inject.Inject

internal class MaestroProfileRecovery
    @Inject
    constructor(
        private val api: ApiService,
        private val fcmRepository: FcmRepository,
    ) {
        suspend fun restore(
            original: PutProfileRequest,
            preferences: List<GetPreferredExerciseDTO>,
            notificationSnapshot: MaestroProfileNotificationSnapshot?,
            addedType: Long?,
            flow: String,
            runId: String,
        ) {
            // 한 영역의 조회/복구 실패가 다른 영역의 원본 복구를 막지 않게 합니다.
            var failed = flow == "profile-notification" && notificationSnapshot == null

            suspend fun restoreArea(block: suspend () -> Unit) {
                try {
                    block()
                } catch (_: Exception) {
                    failed = true
                }
            }

            restoreArea {
                val current = read(api.getProfile())
                val allowedNicknames =
                    when (flow) {
                        "profile-edit" -> setOf(original.nickname, "e2e$runId")
                        "profile-edit-validation" -> setOf(original.nickname, "e2ea$runId", "e2eb$runId", "e2e615!invalid")
                        else -> setOf(original.nickname)
                    }
                if (current.nickname !in allowedNicknames || current.bio !in setOf(original.bio, "e2e-intro-$runId")) {
                    throw MaestroSetupFailure("profile_recovery_ownership_failed")
                }
                val changed =
                    current.nickname != original.nickname || current.gender != original.gender || current.bio != original.bio
                if (current.gender != original.gender || (flow != "profile-edit" && changed)) failed = true
                if (changed) write(api.editProfile(original))
                val restored = read(api.getProfile())
                if (restored.nickname != original.nickname || restored.gender != original.gender || restored.bio != original.bio) {
                    throw MaestroSetupFailure("profile_restore_failed")
                }
            }
            restoreArea {
                val current = read(api.getPreferredExercises())
                val recovery = MaestroProfilePolicy.preferenceRecovery(preferences, current, addedType)
                // 기존 행은 이 시나리오에서 변경하지 않습니다. 복구가 되어도 잘못된 쓰기는 실패입니다.
                if (recovery.restore.isNotEmpty() || (flow != "profile-preferred-exercise" && recovery.deleteIds.isNotEmpty())) {
                    failed = true
                }
                if (recovery.deleteIds.isNotEmpty()) {
                    write(api.deletePreferredExercises(DeletePreferredExerciseDTO(recovery.deleteIds)))
                }
                if (recovery.restore.isNotEmpty()) {
                    write(
                        api.patchPreferredExercises(
                            recovery.restore.map {
                                PostPreferredExerciseDTO(it.exerciseTypeId, it.skillLevel, it.daysOfWeek)
                            },
                        ),
                    )
                }
                val restored = read(api.getPreferredExercises())
                MaestroProfilePolicy.validatePreferences(restored)
                if (MaestroProfilePolicy.preferenceValues(restored) != MaestroProfilePolicy.preferenceValues(preferences)) {
                    throw MaestroSetupFailure("profile_preference_restore_failed")
                }
            }
            // 다른 flow의 새 snapshot에는 알림 영역이 없습니다. 기존 snapshot은 flow와 무관하게 복구합니다.
            if (notificationSnapshot != null) {
                restoreArea {
                    val token = notificationSnapshot.token
                    val notifications = notificationSnapshot.settings
                    if (token.isBlank() || fcmRepository.getSavedToken() != token) {
                        throw MaestroSetupFailure("profile_fcm_identity_changed")
                    }
                    val current = read(api.getNotiState(token))
                    MaestroProfilePolicy.validateNotifications(current)
                    if (
                        current.map { it.type }.toSet() != notifications.map { it.type }.toSet() ||
                        values(current.filter { it.type !in knownTypes }) != values(notifications.filter { it.type !in knownTypes })
                    ) {
                        throw MaestroSetupFailure("profile_recovery_ownership_failed")
                    }
                    if (flow != "profile-notification" && values(current) != values(notifications)) failed = true
                    if (values(current) != values(notifications)) write(api.patchNotiState(token, notifications))
                    val restored = read(api.getNotiState(token))
                    MaestroProfilePolicy.validateNotifications(restored)
                    if (fcmRepository.getSavedToken() != token || values(restored) != values(notifications)) {
                        throw MaestroSetupFailure("profile_notification_restore_failed")
                    }
                }
            }
            // 성공해도 snapshot은 호스트의 최종 pm clear까지 남겨 같은 실행의 재시도를 허용합니다.
            if (failed) throw MaestroSetupFailure("profile_restore_failed")
        }

        private fun values(values: List<NotificationStateDTO>) = values.associate { it.type to it.enabled }

        private fun <T> read(response: BaseResponse<T>): T {
            if (!response.succeed) throw MaestroSetupFailure("profile_fixture_read_failed")
            return response.data ?: throw MaestroSetupFailure("profile_fixture_read_failed")
        }

        private fun write(response: BaseResponse<Unit?>) {
            if (!response.succeed) throw MaestroSetupFailure("profile_fixture_write_failed")
        }

        private companion object {
            val knownTypes = setOf("WORKOUT_REMINDER", "CHAT_MESSAGE")
        }
    }
