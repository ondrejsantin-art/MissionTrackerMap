package com.example.missiontrackermap.model

import kotlinx.serialization.Serializable

/**
 * Persisted progress for a single mission.
 * Completed points are identified by [CalibrationPoint.name].
 */
@Serializable
data class MissionProgress(
    val completedPoints: Set<String> = emptySet()
)
