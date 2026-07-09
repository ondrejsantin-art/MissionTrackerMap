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
     * Loads a mission by ID from internal storage or assets.
     * Returns [MissionState] on success, null on failure (with Logcat error).
     */
    fun load(missionId: String): MissionState? {
        return try {
            val localMissionDir = java.io.File(java.io.File(context.filesDir, MISSIONS_ROOT), missionId)
            val calibration: CalibrationData
            val imageBytes: ByteArray

            if (localMissionDir.exists() && localMissionDir.isDirectory) {
                val calibrationFile = java.io.File(localMissionDir, "$missionId.json")
                if (!calibrationFile.exists()) {
                    throw java.io.FileNotFoundException("Calibration file not found at ${calibrationFile.absolutePath}")
                }
                val rawJson = calibrationFile.readText()
                calibration = json.decodeFromString<CalibrationData>(rawJson)

                val imageFile = java.io.File(localMissionDir, calibration.image)
                if (!imageFile.exists()) {
                    throw java.io.FileNotFoundException("Image file not found at ${imageFile.absolutePath}")
                }
                imageBytes = imageFile.readBytes()
            } else {
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
     * Returns the list of available mission IDs (asset subfolder names + local storage).
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
                missionsDir.list()?.toList() ?: emptyList()
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to list local missions: ${e.message}")
            emptyList()
        }

        return (assetMissions + localMissions).distinct()
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
