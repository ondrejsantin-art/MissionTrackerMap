package com.example.missiontrackermap.repository

import android.content.Context
import android.util.Log
import com.example.missiontrackermap.SupabaseConfig
import com.example.missiontrackermap.model.PendingProgressSync
import com.example.missiontrackermap.model.UserProgressEntry
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File

private const val TAG = "MissionProgressSyncMgr"
private const val QUEUE_FILE = "pending_progress_sync.json"

@Serializable
private data class UpsertProgressRequest(
    val mission_id: String,
    val user_id: String,
    val user_name: String,
    val completed_points: List<String>,
    val updated_at: String = ""
)

@Serializable
private data class PendingQueue(val entries: List<PendingProgressSync> = emptyList())

/**
 * Handles Supabase reads/writes for the `mission_progress` table.
 *
 * Push behavior:
 *  - [pushProgress] upserts the caller's own row using the authenticated JWT.
 *  - On network failure the entry is appended to a local queue file.
 *  - [flushPendingQueue] replays queued entries; successful ones are removed.
 */
class MissionProgressSyncManager(
    private val context: Context,
    private val client: OkHttpClient = OkHttpClient()
) {

    private val json = Json { ignoreUnknownKeys = true }

    private val anonHeaders = mapOf(
        "apikey" to SupabaseConfig.ANON_KEY,
        "Authorization" to "Bearer ${SupabaseConfig.ANON_KEY}"
    )

    /**
     * Upsert progress for [missionId] to Supabase.
     * Returns true on success, false on failure (caller should queue the entry).
     */
    fun pushProgress(
        missionId: String,
        userName: String,
        completedPoints: List<String>,
        userId: String
    ): Boolean {
        return try {
            val now = java.time.Instant.now().toString()
            val payload = json.encodeToString(
                UpsertProgressRequest.serializer(),
                UpsertProgressRequest(
                    mission_id = missionId,
                    user_id = userId,
                    user_name = userName,
                    completed_points = completedPoints,
                    updated_at = now
                )
            )

            val url = "${SupabaseConfig.URL}/rest/v1/mission_progress?on_conflict=mission_id,user_id"
            val request = Request.Builder()
                .url(url)
                .apply { anonHeaders.forEach { (k, v) -> addHeader(k, v) } }
                .addHeader("Content-Type", "application/json")
                .addHeader("Prefer", "resolution=merge-duplicates,return=minimal")
                .post(payload.toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "pushProgress HTTP ${response.code}: ${response.body?.string()}")
                    return false
                }
                Log.i(TAG, "pushProgress OK for mission=$missionId user=$userId")
                true
            }
        } catch (e: Exception) {
            Log.w(TAG, "pushProgress failed: ${e.message}")
            false
        }
    }

    /**
     * Fetches all user-progress rows for [missionId] using public select permissions.
     */
    fun fetchAllProgress(
        missionId: String
    ): List<UserProgressEntry> {
        val url = "${SupabaseConfig.URL}/rest/v1/mission_progress" +
                "?mission_id=eq.$missionId&select=user_id,user_name,completed_points,updated_at"
        val request = Request.Builder()
            .url(url)
            .apply { anonHeaders.forEach { (k, v) -> addHeader(k, v) } }
            .build()

        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                Log.w(TAG, "fetchAllProgress HTTP ${response.code}")
                return emptyList()
            }
            val body = response.body?.string() ?: return emptyList()
            json.decodeFromString<List<UserProgressEntry>>(body)
        }
    }

    // --- Offline queue ---

    /** Append a failed push entry to the local disk queue. */
    fun enqueue(missionId: String, userName: String, completedPoints: List<String>) {
        val file = queueFile()
        val existing = readQueue(file)
        // Replace any existing entry for the same mission so only latest state is queued
        val filtered = existing.entries.filter { it.missionId != missionId }
        val updated = PendingQueue(filtered + PendingProgressSync(missionId, userName, completedPoints))
        writeQueue(file, updated)
        Log.d(TAG, "Queued progress for mission=$missionId (queue size=${updated.entries.size})")
    }

    /**
     * Attempt to replay all queued entries.
     * Entries that succeed are removed; failures remain for the next attempt.
     */
    fun flushPendingQueue(userId: String) {
        val file = queueFile()
        val queue = readQueue(file)
        if (queue.entries.isEmpty()) return

        Log.i(TAG, "Flushing ${queue.entries.size} pending progress entries")
        val remaining = mutableListOf<PendingProgressSync>()
        for (entry in queue.entries) {
            val ok = pushProgress(entry.missionId, entry.userName, entry.completedPoints, userId)
            if (!ok) remaining.add(entry)
        }
        writeQueue(file, PendingQueue(remaining))
        Log.i(TAG, "Flush done: ${queue.entries.size - remaining.size} sent, ${remaining.size} still pending")
    }

    fun hasPendingEntries(): Boolean = readQueue(queueFile()).entries.isNotEmpty()

    private fun queueFile() = File(context.filesDir, QUEUE_FILE)

    private fun readQueue(file: File): PendingQueue {
        if (!file.exists()) return PendingQueue()
        return try {
            json.decodeFromString<PendingQueue>(file.readText())
        } catch (e: Exception) {
            Log.w(TAG, "Could not read queue file, resetting: ${e.message}")
            PendingQueue()
        }
    }

    private fun writeQueue(file: File, queue: PendingQueue) {
        try {
            file.writeText(json.encodeToString(PendingQueue.serializer(), queue))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write queue file: ${e.message}")
        }
    }
}
