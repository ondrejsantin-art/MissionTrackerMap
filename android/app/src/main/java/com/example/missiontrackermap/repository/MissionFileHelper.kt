package com.example.missiontrackermap.repository

import com.example.missiontrackermap.model.CalibrationData
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Helper class to manage local mission files (renaming, deleting) on the file system.
 * Separated from Android classes for ease of JVM unit testing.
 */
class MissionFileHelper(private val missionsDir: File) {

    private val json = Json { ignoreUnknownKeys = true }

    fun renameMission(oldMissionId: String, newMissionId: String): Result<Unit> {
        if (newMissionId.isBlank()) {
            return Result.failure(Exception("New name cannot be blank"))
        }
        val oldDir = File(missionsDir, oldMissionId)
        val newDir = File(missionsDir, newMissionId)

        if (!oldDir.exists()) {
            return Result.failure(Exception("Mission does not exist"))
        }
        if (newDir.exists()) {
            return Result.failure(Exception("A mission with this name already exists"))
        }

        if (!oldDir.renameTo(newDir)) {
            return Result.failure(Exception("Failed to rename mission directory"))
        }

        val oldJsonFile = File(newDir, "$oldMissionId.json")
        var imageExtension = "png"
        val calibration = try {
            if (oldJsonFile.exists()) {
                val jsonContent = oldJsonFile.readText()
                json.decodeFromString<CalibrationData>(jsonContent)
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }

        val actualImageName = calibration?.image ?: "$oldMissionId.png"
        val actualImageFile = File(newDir, actualImageName)

        val dotIndex = actualImageName.lastIndexOf('.')
        if (dotIndex != -1) {
            imageExtension = actualImageName.substring(dotIndex + 1)
        }

        val newImageName = "$newMissionId.$imageExtension"
        val newImageFile = File(newDir, newImageName)

        if (actualImageFile.exists()) {
            actualImageFile.renameTo(newImageFile)
        }

        if (calibration != null) {
            val updatedCalibration = calibration.copy(image = newImageName)
            val updatedJsonContent = json.encodeToString(CalibrationData.serializer(), updatedCalibration)
            val newJsonFile = File(newDir, "$newMissionId.json")
            newJsonFile.writeText(updatedJsonContent)
            if (newJsonFile != oldJsonFile) {
                oldJsonFile.delete()
            }
        } else {
            val newJsonFile = File(newDir, "$newMissionId.json")
            oldJsonFile.renameTo(newJsonFile)
        }

        return Result.success(Unit)
    }

    fun deleteMission(missionId: String): Result<Unit> {
        val missionDir = File(missionsDir, missionId)
        if (missionDir.exists()) {
            val deleted = missionDir.deleteRecursively()
            if (!deleted) {
                return Result.failure(Exception("Failed to delete mission files"))
            }
        }
        return Result.success(Unit)
    }
}
