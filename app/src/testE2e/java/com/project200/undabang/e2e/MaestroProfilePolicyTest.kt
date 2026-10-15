package com.project200.undabang.e2e

import com.project200.data.dto.GetPreferredExerciseDTO
import com.project200.data.dto.NotificationStateDTO
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class MaestroProfilePolicyTest {
    private val baseline = preference(10, 1)

    @Test
    fun `원본 preference ID가 사라지면 재생성하지 않고 실패한다`() {
        assertThrows(MaestroSetupFailure::class.java) {
            MaestroProfilePolicy.preferenceRecovery(listOf(baseline), listOf(preference(11, 1)), 2)
        }
    }

    @Test
    fun `이번 실행의 추가 종목만 삭제하고 원본 값만 수정한다`() {
        val changed = baseline.copy(skillLevel = "PRO")
        val recovery = MaestroProfilePolicy.preferenceRecovery(listOf(baseline), listOf(changed, preference(20, 2)), 2)
        assertEquals(listOf(20L), recovery.deleteIds)
        assertEquals(listOf(baseline), recovery.restore)
    }

    @Test
    fun `소유하지 않은 추가 종목은 삭제하지 않는다`() {
        assertThrows(MaestroSetupFailure::class.java) {
            MaestroProfilePolicy.preferenceRecovery(listOf(baseline), listOf(baseline, preference(30, 3)), 2)
        }
    }

    @Test
    fun `알림의 서버 확장 type은 원문과 값을 보존한다`() {
        val settings =
            listOf(
                NotificationStateDTO("WORKOUT_REMINDER", true),
                NotificationStateDTO("CHAT_MESSAGE", false),
                NotificationStateDTO("SERVER_NEW_TYPE", true),
            )
        MaestroProfilePolicy.validateNotifications(settings)
        assertEquals("SERVER_NEW_TYPE", settings.last().type)
        assertEquals(true, settings.last().enabled)
        assertThrows(MaestroSetupFailure::class.java) {
            MaestroProfilePolicy.validateNotifications(settings.filterNot { it.type == "CHAT_MESSAGE" })
        }
    }

    @Test
    fun `null과 Android 입력으로 복구할 수 없는 소개는 mutation 전에 거부한다`() {
        for (bio in listOf(null, "", "한글 소개", "percent%text", "line\nbreak")) {
            assertThrows(MaestroSetupFailure::class.java) {
                MaestroProfilePolicy.validateText("OriginalUser", "MALE", bio)
            }
        }
        MaestroProfilePolicy.validateText("OriginalUser", "FEMALE", "Original intro-1")
    }

    @Test
    fun `동일 type에 여러 ID가 있으면 소유권을 추측하지 않는다`() {
        assertThrows(MaestroSetupFailure::class.java) {
            MaestroProfilePolicy.preferenceRecovery(listOf(baseline), listOf(baseline, preference(20, 2), preference(21, 2)), 2)
        }
    }

    private fun preference(
        id: Long,
        type: Long,
    ) = GetPreferredExerciseDTO(id, type, "Sport$type", "BEGINNER", listOf(true, false, false, false, false, false, false), null)
}
