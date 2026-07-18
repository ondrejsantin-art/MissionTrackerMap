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
private data class SupabaseMissionVersion(
    val id: String,
    val version: Int
)

@Serializable
private data class SupabaseMissionDetail(
    val version: Int,
    val json_data: JsonObject
)

class SupabaseSyncManager(private val context: Context) {

    private val client = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }

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
        val url = "${SupabaseConfig.URL}/rest/v1/missions?select=id,version"
        val request = Request.Builder()
            .url(url)
            .addHeader("apikey", SupabaseConfig.ANON_KEY)
            .addHeader("Authorization", "Bearer ${SupabaseConfig.ANON_KEY}")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Failed to fetch remote missions: HTTP ${response.code}")
            val body = response.body?.string() ?: throw IOException("Empty body response from remote missions fetch")
            val list = json.decodeFromString<List<SupabaseMissionVersion>>(body)
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
        val imageUrl = "${SupabaseConfig.URL}/storage/v1/object/public/mission-images/$missionId/$imageName"
        val imageRequest = Request.Builder()
            .url(imageUrl)
            .addHeader("apikey", SupabaseConfig.ANON_KEY)
            .addHeader("Authorization", "Bearer ${SupabaseConfig.ANON_KEY}")
            .build()

        client.newCall(imageRequest).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Failed to download image '$imageName': HTTP ${response.code}")
            val bytes = response.body?.bytes() ?: throw IOException("Image response body is null")
            
            val imageFile = File(localMissionDir, imageName)
            FileOutputStream(imageFile).use { fos ->
                fos.write(bytes)
            }
            Log.d(TAG, "Successfully downloaded image '$imageName' to ${imageFile.absolutePath}")
        }

        // 4. Save JSON only after image succeeds to prevent partial corrupt states
        val calibrationFile = File(localMissionDir, "$missionId.json")
        calibrationFile.writeText(jsonStr)
        Log.i(TAG, "Successfully updated mission '$missionId' JSON at ${calibrationFile.absolutePath}")
    }
}
