package com.project200.undabang.e2e

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalDate

class MaestroFixturePolicyTest {
    private val title = "maestro-e2e-0123456789abcdef0123456789abcdef"

    @Test
    fun `예약한 제목과 수정 제목만 소유한다`() {
        assertThat(MaestroFixturePolicy.ownsTitle(title)).isTrue()
        assertThat(MaestroFixturePolicy.ownsTitle("$title-edited")).isTrue()
        for (unrelated in listOf("${title}0", "prefix-$title", "$title-edited-again", "$title\n", title.uppercase())) {
            assertThat(MaestroFixturePolicy.ownsTitle(unrelated)).isFalse()
        }
    }

    @Test
    fun `명시한 수명만 윤년 월별로 빈틈없이 조회한다`() {
        val ranges =
            MaestroFixturePolicy.monthlyRanges(
                LocalDate.of(2024, 1, 31),
                LocalDate.of(2024, 3, 2),
            ).toList()

        assertThat(ranges).containsExactly(
            LocalDate.of(2024, 1, 31)..LocalDate.of(2024, 1, 31),
            LocalDate.of(2024, 2, 1)..LocalDate.of(2024, 2, 29),
            LocalDate.of(2024, 3, 1)..LocalDate.of(2024, 3, 2),
        ).inOrder()
    }

    @Test
    fun `오늘부터 시작하면 오늘만 조회한다`() {
        val today = LocalDate.of(2026, 10, 12)
        assertThat(MaestroFixturePolicy.monthlyRanges(today, today).toList()).containsExactly(today..today)
    }

    @Test
    fun `미래 시작일은 조회 전에 거절한다`() {
        val today = LocalDate.of(2026, 10, 12)
        try {
            MaestroFixturePolicy.monthlyRanges(today.plusDays(1), today)
            throw AssertionError("미래 시작일이 허용되었습니다")
        } catch (failure: MaestroSetupFailure) {
            assertThat(failure.reason).isEqualTo("fixture_range_invalid")
        }
    }
}
