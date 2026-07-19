package com.example.missiontrackermap.repository

import android.content.Context
import android.util.Log
import com.example.missiontrackermap.SupabaseConfig
import com.example.missiontrackermap.model.CalibrationData
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

private const val TAG = "SupabaseSyncManager"
private const val MISSIONS_ROOT = "missions"

@Serializable
internal data class SupabaseMissionVersion(
    val id: String,
    val version: Int,
    val owner_id: String? = null
)

@Serializable
internal data class SupabaseMissionDetail(
    val version: Int,
    val json_data: JsonObject
)

class SupabaseSyncManager(
    private val context: Context,
    private val client: OkHttpClient = OkHttpClient()
) {

    private val json = Json { ignoreUnknownKeys = true }
    private var _ownerIds: Map<String, String?> = emptyMap()

    /**
     * Synchronizes missions from Supabase.
     * Returns true if sync succeeded or was not needed, false on failure.
     */
    fun sync(): Boolean {
        Log.i(TAG, "Starting Supabase sync...")
        return try {
            val remoteMissions = fetchRemoteMissions()
            for ((remoteId, remoteVersion) in remoteMissions) {
                val localVersion = getLocalMissionVersion(remoteId)
                if (localVersion == null || localVersion < remoteVersion) {
                    Log.i(TAG, "Syncing mission '$remoteId': local version = ${localVersion ?: "none"}, remote version = $remoteVersion")
                    downloadMission(remoteId)
                } else {
                    Log.d(TAG, "Mission '$remoteId' is up-to-date (version $localVersion)")
                }
            }
            Log.i(TAG, "Supabase sync finished successfully.")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error during Supabase sync: ${e.message}", e)
            false
        }
    }

    fun fetchRemoteMissions(): Map<String, Int> {
        val url = "${SupabaseConfig.URL}/rest/v1/missions?select=id,version,owner_id"
        val request = Request.Builder()
            .url(url)
            .addHeader("apikey", SupabaseConfig.ANON_KEY)
            .addHeader("Authorization", "Bearer ${SupabaseConfig.ANON_KEY}")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Failed to fetch remote missions: HTTP ${response.code}")
            val body = response.body?.string() ?: throw IOException("Empty body response from remote missions fetch")
            val list = json.decodeFromString<List<SupabaseMissionVersion>>(body)
            // Store owner_id mapping for later use
            _ownerIds = list.associate { it.id to it.owner_id }
            return list.associate { it.id to it.version }
        }
    }

    private fun getLocalMissionVersion(missionId: String): Int? {
        val localMissionDir = File(File(context.filesDir, MISSIONS_ROOT), missionId)
        val calibrationFile = File(localMissionDir, "$missionId.json")
        if (!localMissionDir.exists() || !calibrationFile.exists()) return null

        return try {
            val rawJson = calibrationFile.readText()
            val calibration = json.decodeFromString<CalibrationData>(rawJson)
            calibration.version
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read local version for '$missionId', forcing redownload: ${e.message}")
            null
        }
    }

    fun downloadMission(missionId: String) {
        val localMissionDir = File(File(context.filesDir, MISSIONS_ROOT), missionId)
        if (!localMissionDir.exists()) {
            localMissionDir.mkdirs()
        }
        val markerFile = File(localMissionDir, ".cloud")
        if (!markerFile.exists()) {
            markerFile.createNewFile()
        }

        // 1. Fetch full JSON payload and version column
        val url = "${SupabaseConfig.URL}/rest/v1/missions?id=eq.$missionId&select=version,json_data"
        val request = Request.Builder()
            .url(url)
            .addHeader("apikey", SupabaseConfig.ANON_KEY)
            .addHeader("Authorization", "Bearer ${SupabaseConfig.ANON_KEY}")
            .build()

        val jsonStr = client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Failed to fetch mission detail: HTTP ${response.code}")
            val body = response.body?.string() ?: throw IOException("Empty body for mission detail")
            val details = json.decodeFromString<List<SupabaseMissionDetail>>(body)
            if (details.isEmpty()) throw IOException("Mission '$missionId' not found on Supabase REST API")
            
            val detail = details[0]
            val rawJsonData = detail.json_data.toString()
            val calibration = json.decodeFromString<CalibrationData>(rawJsonData)
            
            // Override local JSON version field with the remote database column version
            val updatedCalibration = calibration.copy(version = detail.version)
            json.encodeToString(CalibrationData.serializer(), updatedCalibration)
        }

        // 2. Parse JSON to get image file name
        val parsedJson = json.parseToJsonElement(jsonStr).jsonObject
        val imageName = parsedJson["image"]?.jsonPrimitive?.content 
            ?: throw IOException("Mission JSON missing 'image' field")

        // 3. Download the map image
        val imageFile = File(localMissionDir, imageName)
        val etagFile = File(localMissionDir, "$imageName.etag")
        val cachedEtag = if (etagFile.exists() && imageFile.exists()) etagFile.readText().trim() else null

        val imageUrl = "${SupabaseConfig.URL}/storage/v1/object/public/mission-images/$missionId/$imageName"
        val imageRequest = Request.Builder()
            .url(imageUrl)
            .addHeader("apikey", SupabaseConfig.ANON_KEY)
            .addHeader("Authorization", "Bearer ${SupabaseConfig.ANON_KEY}")
            .apply {
                if (cachedEtag != null) {
                    addHeader("If-None-Match", cachedEtag)
                }
            }
            .build()

        client.newCall(imageRequest).execute().use { response ->
            if (response.code == 304) {
                Log.i(TAG, "Image '$imageName' is up-to-date (304 Not Modified). Skipping download.")
            } else {
                if (!response.isSuccessful) throw IOException("Failed to download image '$imageName': HTTP ${response.code}")
                val bytes = response.body?.bytes() ?: throw IOException("Image response body is null")
                
                FileOutputStream(imageFile).use { fos ->
                    fos.write(bytes)
                }
                val newEtag = response.header("ETag")
                if (newEtag != null) {
                    etagFile.writeText(newEtag)
                } else {
                    etagFile.delete()
                }
                Log.d(TAG, "Successfully downloaded image '$imageName' to ${imageFile.absolutePath}")
            }
        }

        // 4. Save JSON only after image succeeds to prevent partial corrupt states
        val calibrationFile = File(localMissionDir, "$missionId.json")
        calibrationFile.writeText(jsonStr)
        Log.i(TAG, "Successfully updated mission '$missionId' JSON at ${calibrationFile.absolutePath}")

        // 5. Persist owner_id if known
        val ownerId = _ownerIds[missionId]
        val ownerFile = File(localMissionDir, ".owner")
        if (ownerId != null) {
            ownerFile.writeText(ownerId)
        } else {
            ownerFile.delete()
        }
    }

    /**
     * Reads the persisted owner_id for a local mission, or null if unknown.
     */
    fun getMissionOwnerId(missionId: String): String? {
        val ownerFile = File(File(File(context.filesDir, MISSIONS_ROOT), missionId), ".owner")
        return if (ownerFile.exists()) ownerFile.readText().trim() else null
    }
}
