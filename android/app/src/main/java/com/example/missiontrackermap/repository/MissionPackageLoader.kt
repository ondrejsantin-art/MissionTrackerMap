package com.example.missiontrackermap.repository

import android.content.Context
import android.util.Log
import com.example.missiontrackermap.model.CalibrationData
import com.example.missiontrackermap.model.MissionState
import kotlinx.serialization.json.Json

private const val TAG = "MissionPackageLoader"
private const val MISSIONS_ROOT = "missions"

/**
 * Loads a Mission Package from the app's assets folder.
 *
 * Expected asset structure:
 *   assets/missions/<missionId>/<missionId>.json
 *   assets/missions/<missionId>/<image-file-name>
 *
 * The image filename is read from <missionId>.json's "image" field.
 *
 * Future enhancement (Task G): also support loading from external storage
 * (/sdcard/MissionTrackerMap/) so new missions can be added without reinstalling.
 */
class MissionPackageLoader(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Loads a mission by ID from SD Card, internal storage, or assets.
     * Returns [MissionState] on success, null on failure (with Logcat error).
     */
    fun load(missionId: String): MissionState? {
        return try {
            val sdCardMissionDir = java.io.File("/sdcard/MissionTrackerMap/missions", missionId)
            val sdCardCalibrationFile = java.io.File(sdCardMissionDir, "$missionId.json")

            val localMissionDir = java.io.File(java.io.File(context.filesDir, MISSIONS_ROOT), missionId)
            val localCalibrationFile = java.io.File(localMissionDir, "$missionId.json")

            val calibration: CalibrationData
            val imageBytes: ByteArray

            if (sdCardMissionDir.exists() && sdCardMissionDir.isDirectory && sdCardCalibrationFile.exists()) {
                Log.i(TAG, "Loading mission '$missionId' from SD Card")
                val rawJson = sdCardCalibrationFile.readText()
                calibration = json.decodeFromString<CalibrationData>(rawJson)

                val imageFile = java.io.File(sdCardMissionDir, calibration.image)
                if (!imageFile.exists()) {
                    throw java.io.FileNotFoundException("Image file not found on SD Card at ${imageFile.absolutePath}")
                }
                imageBytes = imageFile.readBytes()
            } else if (localMissionDir.exists() && localMissionDir.isDirectory && localCalibrationFile.exists()) {
                Log.i(TAG, "Loading mission '$missionId' from internal synced storage")
                val rawJson = localCalibrationFile.readText()
                calibration = json.decodeFromString<CalibrationData>(rawJson)

                val imageFile = java.io.File(localMissionDir, calibration.image)
                if (!imageFile.exists()) {
                    throw java.io.FileNotFoundException("Image file not found in internal storage at ${imageFile.absolutePath}")
                }
                imageBytes = imageFile.readBytes()
            } else {
                Log.i(TAG, "Loading mission '$missionId' from assets")
                calibration = loadCalibration(missionId)
                imageBytes = loadImage(missionId, calibration.image)
            }

            Log.i(TAG, "Mission '$missionId' loaded: image=${calibration.imageWidth}x${calibration.imageHeight}, " +
                    "calibration points=${calibration.points.size}")
            calibration.points.forEachIndexed { i, pt ->
                Log.d(TAG, "  Point ${i + 1}: '${pt.name}' → pixel(${pt.pixel.x}, ${pt.pixel.y}), " +
                        "gps(${pt.gps.latitude}, ${pt.gps.longitude})")
            }

            MissionState(
                missionId = missionId,
                imageBytes = imageBytes,
                calibration = calibration
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load mission '$missionId': ${e.message}", e)
            null
        }
    }

    /**
     * Returns the list of available mission IDs (asset subfolder names + local storage + SD card).
     */
    fun availableMissions(): List<String> {
        val assetMissions = try {
            context.assets.list(MISSIONS_ROOT)?.toList() ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to list asset missions: ${e.message}")
            emptyList()
        }

        val localMissions = try {
            val missionsDir = java.io.File(context.filesDir, MISSIONS_ROOT)
            if (missionsDir.exists() && missionsDir.isDirectory) {
                missionsDir.listFiles()?.filter { dir ->
                    dir.isDirectory && java.io.File(dir, "${dir.name}.json").exists()
                }?.map { it.name } ?: emptyList()
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to list local missions: ${e.message}")
            emptyList()
        }

        val sdCardMissions = try {
            val sdDir = java.io.File("/sdcard/MissionTrackerMap/missions")
            if (sdDir.exists() && sdDir.isDirectory) {
                sdDir.listFiles()?.filter { dir ->
                    dir.isDirectory && java.io.File(dir, "${dir.name}.json").exists()
                }?.map { it.name } ?: emptyList()
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to list SD card missions: ${e.message}")
            emptyList()
        }

        return (assetMissions + localMissions + sdCardMissions).distinct()
    }

    /**
     * Reads only the version field of a mission calibration without loading the image.
     * Returns 0 if the mission doesn't exist or fails to parse.
     */
    fun getMissionVersion(missionId: String): Int {
        return try {
            val sdCardMissionDir = java.io.File("/sdcard/MissionTrackerMap/missions", missionId)
            val sdCardCalibrationFile = java.io.File(sdCardMissionDir, "$missionId.json")

            val localMissionDir = java.io.File(java.io.File(context.filesDir, MISSIONS_ROOT), missionId)
            val localCalibrationFile = java.io.File(localMissionDir, "$missionId.json")

            val rawJson = if (sdCardMissionDir.exists() && sdCardCalibrationFile.exists()) {
                sdCardCalibrationFile.readText()
            } else if (localMissionDir.exists() && localCalibrationFile.exists()) {
                localCalibrationFile.readText()
            } else {
                val path = "$MISSIONS_ROOT/$missionId/$missionId.json"
                context.assets.open(path).bufferedReader().use { it.readText() }
            }
            val calibration = json.decodeFromString<CalibrationData>(rawJson)
            calibration.version
        } catch (e: Exception) {
            0
        }
    }

    private fun loadCalibration(missionId: String): CalibrationData {
        val path = "$MISSIONS_ROOT/$missionId/$missionId.json"
        val rawJson = context.assets.open(path).bufferedReader().use { it.readText() }
        return json.decodeFromString<CalibrationData>(rawJson)
    }

    private fun loadImage(missionId: String, imageName: String): ByteArray {
        val path = "$MISSIONS_ROOT/$missionId/$imageName"
        return context.assets.open(path).use { it.readBytes() }
    }
}
