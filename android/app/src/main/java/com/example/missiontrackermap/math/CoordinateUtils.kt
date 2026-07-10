package com.example.missiontrackermap.math

object CoordinateUtils {
    fun formatCoordinate(value: Double): String = "%.2f".format(value)

    fun calculateNeedleRotation(deviceHeading: Float): Float {
        val normalizedHeading = (deviceHeading % 360f + 360f) % 360f
        return (360f - normalizedHeading) % 360f
    }
}
