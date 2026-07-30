package com.example.missiontrackermap.model

import kotlinx.serialization.Serializable

/**
 * Represents one failed progress-upload attempt that must be retried when connectivity returns.
 * Persisted as JSON entries in `pending_progress_sync.json` in filesDir.
 */
@Serializable
data class PendingProgressSync(
    val missionId: String,
    val userName: String,
    val completedPoints: List<String>
)
