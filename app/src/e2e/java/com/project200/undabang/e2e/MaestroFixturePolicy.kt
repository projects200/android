package com.project200.undabang.e2e

import java.time.LocalDate
import java.time.YearMonth

internal object MaestroFixturePolicy {
    private val reservedTitle = Regex("maestro-e2e-[a-f0-9]{32}(-edited)?")

    fun ownsTitle(title: String): Boolean = reservedTitle.matches(title)

    fun monthlyRanges(
        fixtureStartDate: LocalDate,
        today: LocalDate,
    ): Sequence<ClosedRange<LocalDate>> {
        if (fixtureStartDate > today) throw MaestroSetupFailure("fixture_range_invalid")
        return sequence {
            var start = fixtureStartDate
            while (start <= today) {
                val end = minOf(YearMonth.from(start).atEndOfMonth(), today)
                yield(start..end)
                if (end == today) break
                start = end.plusDays(1)
            }
        }
    }
}
