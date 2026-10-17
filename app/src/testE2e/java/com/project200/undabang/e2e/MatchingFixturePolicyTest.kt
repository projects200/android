package com.project200.undabang.e2e

import com.project200.data.dto.GetExercisePlaceDTO
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MatchingFixturePolicyTest {
    private val marker = "e2e-m-0123456789abcdef"
    private val place = GetExercisePlaceDTO(21L, marker, "real-address", 37.5, 127.0)

    @Test
    fun `cleanup preserves baseline ids even when names match`() {
        assertFalse(MatchingFixturePolicy.ownsPlace(place, setOf(21L), marker))
    }

    @Test
    fun `cleanup accepts only the exact run marker or edited marker`() {
        assertTrue(MatchingFixturePolicy.ownsPlace(place, emptySet(), marker))
        assertTrue(MatchingFixturePolicy.ownsPlace(place.copy(name = "$marker-edit"), emptySet(), marker))
        assertFalse(MatchingFixturePolicy.ownsPlace(place.copy(name = "$marker-other"), emptySet(), marker))
        assertFalse(MatchingFixturePolicy.ownsPlace(place.copy(name = "e2e-m-another-run"), emptySet(), marker))
    }

    @Test
    fun `room cleanup requires both a new id and navigation evidence for the configured peer`() {
        assertTrue(MatchingFixturePolicy.ownsRoom(22L, "peer", emptySet(), setOf(22L), "peer"))
        assertFalse(MatchingFixturePolicy.ownsRoom(22L, "peer", setOf(22L), setOf(22L), "peer"))
        assertFalse(MatchingFixturePolicy.ownsRoom(22L, "stranger", emptySet(), setOf(22L), "peer"))
        assertFalse(MatchingFixturePolicy.ownsRoom(22L, "peer", emptySet(), emptySet(), "peer"))
    }
}
