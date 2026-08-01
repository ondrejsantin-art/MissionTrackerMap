package com.example.missiontrackermap.model

import kotlinx.serialization.Serializable

/**
 * Persisted progress for a single mission.
 * Completed points are identified by [CalibrationPoint.name].
 * [userName] is the display name set by the end-user for progress sharing.
 * Defaults to empty so existing sidecar files without this field parse correctly.
 */
@Serializable
data class MissionProgress(
    val completedPoints: Map<String, Long> = emptyMap(),
    val userName: String = ""
)
