package com.example.missiontrackermap.math

import com.example.missiontrackermap.model.CalibrationPoint
import kotlin.math.cos
import java.lang.Math.toRadians
import kotlin.math.PI
import kotlin.math.sqrt

object ScaleCalculator {
    private const val EARTH_RADIUS_METERS = 6371000.0

    /**
     * Calculates the length of 1 kilometer (1000 meters) in image pixels
     * using the calibration points and the AffineTransformer.
     * Returns 0f if the calibration list is empty or the transform fails.
     */
    fun calculateOneKmPixelLength(points: List<CalibrationPoint>): Float {
        if (points.isEmpty()) return 0f
        
        return try {
            val transformer = AffineTransformer(points)
            val centerLat = points.map { it.gps.latitude }.average()
            val centerLon = points.map { it.gps.longitude }.average()

            // Meters per degree longitude at center latitude
            val latRad = toRadians(centerLat)
            val metersPerDegreeLon = (PI / 180.0) * EARTH_RADIUS_METERS * cos(latRad)
            if (metersPerDegreeLon == 0.0) return 0f

            // Delta longitude corresponding to 1000 meters (1 km)
            val deltaLon = 1000.0 / metersPerDegreeLon

            // Map the center point and the point shifted by deltaLon
            val p1 = transformer.gpsToPixel(centerLat, centerLon)
            val p2 = transformer.gpsToPixel(centerLat, centerLon + deltaLon)

            val dx = p2.x - p1.x
            val dy = p2.y - p1.y
            sqrt((dx * dx + dy * dy).toDouble()).toFloat()
        } catch (e: Exception) {
            0f
        }
    }
}
