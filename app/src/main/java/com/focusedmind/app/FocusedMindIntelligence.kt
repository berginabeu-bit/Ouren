package com.focusedmind.app

import android.content.Context
import java.util.concurrent.TimeUnit

/** Lightweight offline intelligence: detects schedule collisions without requiring a network. */
object FocusedMindIntelligence {
    private val CONFLICT_WINDOW_MS = TimeUnit.MINUTES.toMillis(20)

    data class Conflict(val other: FocusedMindStore.Commitment, val distanceMinutes: Long)

    fun conflicts(context: Context, timestamp: Long, ignoreId: Long? = null): List<Conflict> =
        FocusedMindStore.commitments(context)
            .asSequence()
            .filter { it.id != ignoreId }
            .map { it to kotlin.math.abs(it.timestamp - timestamp) }
            .filter { it.second <= CONFLICT_WINDOW_MS }
            .map { (commitment, distance) -> Conflict(commitment, TimeUnit.MILLISECONDS.toMinutes(distance)) }
            .sortedBy { it.distanceMinutes }
            .toList()

    fun hasConflict(context: Context, timestamp: Long, ignoreId: Long? = null): Boolean =
        conflicts(context, timestamp, ignoreId).isNotEmpty()
}
