package com.example.missiontrackermap.model

import kotlinx.serialization.Serializable

/**
 * A single participant's progress for a mission as fetched from Supabase.
 * Field names match the Supabase `mission_progress` table columns.
 */
@Serializable
data class UserProgressEntry(
    val user_id: String,
    val user_name: String,
    val completed_points: Map<String, Long>,
    val updated_at: String
)
