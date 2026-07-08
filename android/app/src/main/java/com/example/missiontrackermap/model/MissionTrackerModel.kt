package com.example.missiontrackermap.model

/**
 * Represents a loaded mission: image bitmap bytes + calibration data.
 * Replaces the stub MissionTrackerModel.
 */
data class MissionState(
    val missionId: String,
    val imageBytes: ByteArray,
    val calibration: CalibrationData
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MissionState) return false
        return missionId == other.missionId
    }

    override fun hashCode(): Int = missionId.hashCode()
}
