package com.example.missiontrackermap.repository

import android.content.Context
import android.util.Log
import com.example.missiontrackermap.model.MissionProgress
import com.example.missiontrackermap.model.MissionState
import kotlinx.serialization.json.Json

private const val REPO_TAG = "MissionTrackerRepository"
private const val PROGRESS_FILE = "mission_progress.json"

/**
 * Repository that provides mission data to the ViewModel.
 * Also manages per-mission progress (completed mission points).
 */
class MissionTrackerRepository(private val context: Context) {

    private val loader = MissionPackageLoader(context)
    private val json = Json { ignoreUnknownKeys = true }

    fun loadMission(missionId: String): MissionState? = loader.load(missionId)

    fun availableMissions(): List<String> = loader.availableMissions()

    fun getMissionVersion(missionId: String): Int = loader.getMissionVersion(missionId)

    // --- Mission progress (sidecar file per mission) ---

    /**
     * Loads progress from `missions/<missionId>/mission_progress.json`.
     * Returns empty progress if the file doesn't exist yet.
     */
    fun loadProgress(missionId: String): MissionProgress {
        val file = progressFile(missionId) ?: return MissionProgress()
        if (!file.exists()) return MissionProgress()
        return try {
            json.decodeFromString<MissionProgress>(file.readText())
        } catch (e: Exception) {
            Log.w(REPO_TAG, "Failed to read progress for '$missionId': ${e.message}")
            MissionProgress()
        }
    }

    /**
     * Persists [progress] to `missions/<missionId>/mission_progress.json`.
     * For built-in (asset-only) missions, writes into internal filesDir so the assets are not modified.
     */
    fun saveProgress(missionId: String, progress: MissionProgress) {
        val file = progressFileWritable(missionId)
        try {
            file.parentFile?.mkdirs()
            file.writeText(json.encodeToString(MissionProgress.serializer(), progress))
        } catch (e: Exception) {
            Log.e(REPO_TAG, "Failed to save progress for '$missionId': ${e.message}")
        }
    }

    /**
     * Deletes the progress sidecar file, resetting all completed points.
     */
    fun clearProgress(missionId: String) {
        progressFileWritable(missionId).delete()
    }

    // Progress lives in internal filesDir regardless of whether the mission is an asset or local.
    private fun progressFileWritable(missionId: String): java.io.File {
        val dir = java.io.File(java.io.File(context.filesDir, "missions"), missionId)
        dir.mkdirs()
        return java.io.File(dir, PROGRESS_FILE)
    }

    // For reading: check filesDir first (covers both imported and built-in with saved progress).
    private fun progressFile(missionId: String): java.io.File? {
        return progressFileWritable(missionId)
    }
}
