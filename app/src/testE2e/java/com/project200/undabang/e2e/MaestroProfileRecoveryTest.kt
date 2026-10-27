package com.project200.undabang.e2e

import com.google.common.truth.Truth.assertThat
import com.project200.data.api.ApiService
import com.project200.data.dto.BaseResponse
import com.project200.data.dto.DeletePreferredExerciseDTO
import com.project200.data.dto.GetPreferredExerciseDTO
import com.project200.data.dto.GetProfileDTO
import com.project200.data.dto.NotificationStateDTO
import com.project200.data.dto.PostPreferredExerciseDTO
import com.project200.data.dto.PutProfileRequest
import com.project200.domain.repository.FcmRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test

class MaestroProfileRecoveryTest {
    private val api = mockk<ApiService>()
    private val fcm = mockk<FcmRepository>()
    private val recovery = MaestroProfileRecovery(api, fcm)
    private val original = PutProfileRequest("OriginalUser", "UNKNOWN", "Original intro")
    private val runId = "0123456789abcdef"
    private val token = "unit-test-device-identity"
    private val preference =
        GetPreferredExerciseDTO(10, 1, "Sport", "BEGINNER", listOf(true, false, false, false, false, false, false), null)
    private val notifications =
        listOf(
            NotificationStateDTO("WORKOUT_REMINDER", true),
            NotificationStateDTO("CHAT_MESSAGE", false),
            NotificationStateDTO("NEW_SERVER_TYPE", true),
        )
    private val notificationSnapshot = MaestroProfileNotificationSnapshot(token, notifications)

    @Test
    fun `원본 ID를 유지하고 이번 실행의 추가 ID만 삭제한다`() =
        runTest {
            baseline()
            val extra = preference.copy(preferredExerciseId = 20, exerciseTypeId = 2)
            coEvery { api.getPreferredExercises() } returnsMany listOf(success(listOf(preference, extra)), success(listOf(preference)))
            coEvery { api.deletePreferredExercises(DeletePreferredExerciseDTO(listOf(20))) } returns success<Unit?>(null)
            recovery.restore(original, listOf(preference), notificationSnapshot, 2, "profile-preferred-exercise", runId)
            coVerify(exactly = 1) { api.deletePreferredExercises(DeletePreferredExerciseDTO(listOf(20))) }
            coVerify(exactly = 0) { api.postPreferredExercises(any()) }
            coVerify(exactly = 0) { api.patchPreferredExercises(any()) }
        }

    @Test
    fun `원문 type 복구와 재조회를 수행하되 다른 flow의 알림 변경은 실패로 남긴다`() =
        runTest {
            baseline()
            coEvery { api.getProfile() } returnsMany listOf(success(profile("e2e$runId", "e2e-intro-$runId")), success(profile()))
            coEvery { api.editProfile(original) } returns success<Unit?>(null)
            val changed = notifications.map { if (it.type == "CHAT_MESSAGE") it.copy(enabled = true) else it }
            coEvery { api.getNotiState(token) } returnsMany listOf(success(changed), success(notifications))
            coEvery { api.patchNotiState(token, notifications) } returns success<Unit?>(null)
            expectFailure { recovery.restore(original, listOf(preference), notificationSnapshot, null, "profile-edit", runId) }
            coVerify(exactly = 1) { api.editProfile(original) }
            coVerify(exactly = 1) { api.patchNotiState(token, notifications) }
            coVerify(exactly = 2) { api.getNotiState(token) }
        }

    @Test
    fun `빈 소개 원본은 빈 문자열 PUT과 정확한 GET 재조회로 복구한다`() =
        runTest {
            baseline()
            val emptyOriginal = original.copy(bio = "")
            coEvery { api.getProfile() } returnsMany
                listOf(success(profile("e2e$runId", "e2e-intro-$runId")), success(profile(bio = "")))
            coEvery { api.editProfile(emptyOriginal) } returns success<Unit?>(null)
            MaestroProfilePolicy.validateText(emptyOriginal.nickname, emptyOriginal.gender, emptyOriginal.bio)

            recovery.restore(emptyOriginal, listOf(preference), null, null, "profile-edit", runId)

            coVerify(exactly = 1) { api.editProfile(emptyOriginal) }
            coVerify(exactly = 2) { api.getProfile() }
            coVerify(exactly = 0) { fcm.getSavedToken() }
        }

    @Test
    fun `정규식 문자가 있는 실제 소개도 원문 그대로 복구하고 재조회한다`() =
        runTest {
            baseline()
            val literal = original.copy(bio = "[run].* (3+2)? $ ^ \\E")
            MaestroProfilePolicy.validateText(literal.nickname, literal.gender, literal.bio)
            coEvery { api.getProfile() } returnsMany
                listOf(success(profile("e2e$runId", "e2e-intro-$runId")), success(profile(bio = literal.bio)))
            coEvery { api.editProfile(literal) } returns success<Unit?>(null)

            recovery.restore(literal, listOf(preference), null, null, "profile-edit", runId)

            coVerify(exactly = 1) { api.editProfile(literal) }
            coVerify(exactly = 2) { api.getProfile() }
        }

    @Test
    fun `빈 소개 PUT 성공 뒤 null이나 다른 문자열을 읽으면 복구 실패다`() =
        runTest {
            baseline()
            val emptyOriginal = original.copy(bio = "")
            coEvery { api.editProfile(emptyOriginal) } returns success<Unit?>(null)
            for (readback in listOf(null, " ", "e2e-intro-$runId")) {
                coEvery { api.getProfile() } returnsMany
                    listOf(success(profile("e2e$runId", "e2e-intro-$runId")), success(profile(bio = readback)))

                expectFailure { recovery.restore(emptyOriginal, listOf(preference), null, null, "profile-edit", runId) }
            }
            coVerify(exactly = 3) { api.editProfile(emptyOriginal) }
            coVerify(exactly = 6) { api.getProfile() }
        }

    @Test
    fun `원본 preference 변경은 같은 type PATCH로 복구해도 테스트 실패를 유지한다`() =
        runTest {
            baseline()
            coEvery { api.getPreferredExercises() } returnsMany
                listOf(success(listOf(preference.copy(skillLevel = "PRO"))), success(listOf(preference)))
            coEvery {
                api.patchPreferredExercises(listOf(PostPreferredExerciseDTO(1, "BEGINNER", preference.daysOfWeek)))
            } returns success<Unit?>(null)
            expectFailure { recovery.restore(original, listOf(preference), notificationSnapshot, 2, "profile-preferred-exercise", runId) }
            coVerify(exactly = 1) {
                api.patchPreferredExercises(listOf(PostPreferredExerciseDTO(1, "BEGINNER", preference.daysOfWeek)))
            }
            coVerify(exactly = 0) { api.postPreferredExercises(any()) }
        }

    @Test
    fun `알림 조회 오류는 빈 성공이 아니며 다른 영역의 복구를 막지 않는다`() =
        runTest {
            baseline()
            coEvery { api.getProfile() } returnsMany listOf(success(profile("e2e$runId", "e2e-intro-$runId")), success(profile()))
            coEvery { api.editProfile(original) } returns success<Unit?>(null)
            coEvery { api.getNotiState(token) } returns BaseResponse(false, "ERROR", "", emptyList())
            expectFailure { recovery.restore(original, listOf(preference), notificationSnapshot, null, "profile-edit", runId) }
            coVerify(exactly = 1) { api.editProfile(original) }
            coVerify(exactly = 0) { api.patchNotiState(any(), any()) }
        }

    @Test
    fun `FCM identity가 바뀌면 새 기기로 원본 설정을 보내지 않는다`() =
        runTest {
            baseline()
            coEvery { fcm.getSavedToken() } returns "different-device"
            expectFailure { recovery.restore(original, listOf(preference), notificationSnapshot, null, "profile-notification", runId) }
            coVerify(exactly = 0) { api.getNotiState(any()) }
            coVerify(exactly = 0) { api.patchNotiState(any(), any()) }
        }

    @Test
    fun `서버 성공 응답 뒤 재조회 값이 다르면 복구 성공을 반환하지 않는다`() =
        runTest {
            baseline()
            val changed = notifications.map { if (it.type == "WORKOUT_REMINDER") it.copy(enabled = false) else it }
            coEvery { api.getNotiState(token) } returns success(changed)
            coEvery { api.patchNotiState(token, notifications) } returns success<Unit?>(null)
            expectFailure { recovery.restore(original, listOf(preference), notificationSnapshot, null, "profile-notification", runId) }
        }

    @Test
    fun `실패한 PATCH는 응답 data가 있어도 복구 성공이 아니다`() =
        runTest {
            baseline()
            val changed = notifications.map { if (it.type == "WORKOUT_REMINDER") it.copy(enabled = false) else it }
            coEvery { api.getNotiState(token) } returns success(changed)
            coEvery { api.patchNotiState(token, notifications) } returns BaseResponse(false, "ERROR", "", Unit)
            expectFailure { recovery.restore(original, listOf(preference), notificationSnapshot, null, "profile-notification", runId) }
            coVerify(exactly = 1) { api.patchNotiState(token, notifications) }
        }

    @Test
    fun `알림 snapshot이 없는 다른 flow는 알림 저장소와 API 없이 원본을 복구한다`() =
        runTest {
            coEvery { api.getProfile() } returnsMany listOf(success(profile("e2e$runId", "e2e-intro-$runId")), success(profile()))
            coEvery { api.editProfile(original) } returns success<Unit?>(null)
            coEvery { api.getPreferredExercises() } returns success(listOf(preference))
            recovery.restore(original, listOf(preference), null, null, "profile-edit", runId)
            coVerify(exactly = 1) { api.editProfile(original) }
            coVerify(exactly = 0) { fcm.getSavedToken() }
            coVerify(exactly = 0) { api.getNotiState(any()) }
            coVerify(exactly = 0) { api.patchNotiState(any(), any()) }
        }

    @Test
    fun `알림 flow의 누락 snapshot은 원본이 같아도 실패하고 다른 영역을 조회한다`() =
        runTest {
            baseline()
            expectFailure { recovery.restore(original, listOf(preference), null, null, "profile-notification", runId) }
            coVerify(exactly = 2) { api.getProfile() }
            coVerify(exactly = 2) { api.getPreferredExercises() }
            coVerify(exactly = 0) { fcm.getSavedToken() }
            coVerify(exactly = 0) { api.patchNotiState(any(), any()) }
        }

    @Test
    fun `같은 실행을 다시 복구해도 이미 복구한 프로필을 다시 쓰지 않는다`() =
        runTest {
            baseline()
            var current = profile("e2e$runId", "e2e-intro-$runId")
            coEvery { api.getProfile() } coAnswers { success(current) }
            coEvery { api.editProfile(original) } coAnswers {
                current = profile()
                success<Unit?>(null)
            }

            recovery.restore(original, listOf(preference), null, null, "profile-edit", runId)
            recovery.restore(original, listOf(preference), null, null, "profile-edit", runId)

            assertThat(current).isEqualTo(profile())
            coVerify(exactly = 1) { api.editProfile(original) }
            coVerify(exactly = 4) { api.getProfile() }
            coVerify(exactly = 0) { api.deletePreferredExercises(any()) }
            coVerify(exactly = 0) { api.postPreferredExercises(any()) }
            coVerify(exactly = 0) { api.patchPreferredExercises(any()) }
            coVerify(exactly = 0) { fcm.getSavedToken() }
            coVerify(exactly = 0) { fcm.registerToken() }
        }

    @Test
    fun `같은 실행의 추가 ID를 이미 삭제했으면 원본 ID와 기기 설정을 보존한다`() =
        runTest {
            baseline()
            val extra = preference.copy(preferredExerciseId = 20, exerciseTypeId = 2)
            var current = listOf(preference, extra)
            coEvery { api.getPreferredExercises() } coAnswers { success(current) }
            coEvery { api.deletePreferredExercises(DeletePreferredExerciseDTO(listOf(20))) } coAnswers {
                current = listOf(preference)
                success<Unit?>(null)
            }

            recovery.restore(original, listOf(preference), notificationSnapshot, 2, "profile-preferred-exercise", runId)
            recovery.restore(original, listOf(preference), notificationSnapshot, 2, "profile-preferred-exercise", runId)

            assertThat(current).containsExactly(preference)
            coVerify(exactly = 1) { api.deletePreferredExercises(DeletePreferredExerciseDTO(listOf(20))) }
            coVerify(exactly = 4) { api.getPreferredExercises() }
            coVerify(exactly = 0) { api.editProfile(any()) }
            coVerify(exactly = 0) { api.postPreferredExercises(any()) }
            coVerify(exactly = 0) { api.patchPreferredExercises(any()) }
            coVerify(exactly = 4) { fcm.getSavedToken() }
            coVerify(exactly = 4) { api.getNotiState(token) }
            coVerify(exactly = 0) { api.patchNotiState(any(), any()) }
            coVerify(exactly = 0) { fcm.registerToken() }
        }

    @Test
    fun `같은 실행의 알림을 다시 복구하면 같은 기기의 원본 행을 재조회만 한다`() =
        runTest {
            baseline()
            var current = notifications.map { if (it.type == "WORKOUT_REMINDER") it.copy(enabled = false) else it }
            coEvery { api.getNotiState(token) } coAnswers { success(current) }
            coEvery { api.patchNotiState(token, notifications) } coAnswers {
                current = notifications
                success<Unit?>(null)
            }

            recovery.restore(original, listOf(preference), notificationSnapshot, null, "profile-notification", runId)
            recovery.restore(original, listOf(preference), notificationSnapshot, null, "profile-notification", runId)

            assertThat(current).containsExactlyElementsIn(notifications)
            coVerify(exactly = 1) { api.patchNotiState(token, notifications) }
            coVerify(exactly = 4) { api.getNotiState(token) }
            coVerify(exactly = 4) { fcm.getSavedToken() }
            coVerify(exactly = 0) { fcm.registerToken() }
            coVerify(exactly = 0) { api.editProfile(any()) }
            coVerify(exactly = 0) { api.deletePreferredExercises(any()) }
            coVerify(exactly = 0) { api.postPreferredExercises(any()) }
            coVerify(exactly = 0) { api.patchPreferredExercises(any()) }
        }

    @Test
    fun `복구 완료 뒤 재시도해도 바뀐 FCM identity는 거부한다`() =
        runTest {
            baseline()
            recovery.restore(original, listOf(preference), notificationSnapshot, null, "profile-notification", runId)
            coEvery { fcm.getSavedToken() } returns "different-device"

            expectFailure { recovery.restore(original, listOf(preference), notificationSnapshot, null, "profile-notification", runId) }

            coVerify(exactly = 2) { api.getNotiState(token) }
            coVerify(exactly = 0) { api.patchNotiState(any(), any()) }
            coVerify(exactly = 0) { fcm.registerToken() }
        }

    private fun baseline() {
        coEvery { api.getProfile() } returns success(profile())
        coEvery { api.getPreferredExercises() } returns success(listOf(preference))
        coEvery { api.getNotiState(token) } returns success(notifications)
        coEvery { fcm.getSavedToken() } returns token
    }

    private fun profile(
        nickname: String = original.nickname,
        bio: String? = original.bio,
    ) = GetProfileDTO(null, null, nickname, original.gender, "1990-01-01", bio, 0, 0, 0, emptyList())

    private fun <T> success(data: T): BaseResponse<T> = BaseResponse(true, "OK", "", data)

    private suspend fun expectFailure(operation: suspend () -> Unit) {
        try {
            operation()
            throw AssertionError("복구 실패가 성공으로 처리되었습니다")
        } catch (failure: MaestroSetupFailure) {
            assertThat(failure.reason).isEqualTo("profile_restore_failed")
        }
    }
}
