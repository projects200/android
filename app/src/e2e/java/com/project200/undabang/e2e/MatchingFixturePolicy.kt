package com.project200.undabang.e2e

import com.project200.data.dto.GetExercisePlaceDTO

internal object MatchingFixturePolicy {
    fun ownsPlace(
        place: GetExercisePlaceDTO,
        baselineIds: Set<Long>,
        marker: String,
    ): Boolean =
        place.id !in baselineIds &&
            (
                place.name == marker ||
                    (place.name.length == marker.length + 5 && place.name.startsWith(marker) && place.name.endsWith("-edit"))
            )

    fun ownsRoom(
        roomId: Long,
        otherMemberId: String,
        baselineIds: Set<Long>,
        navigatedIds: Set<Long>,
        peerId: String,
    ): Boolean = roomId !in baselineIds && roomId in navigatedIds && otherMemberId == peerId
}
