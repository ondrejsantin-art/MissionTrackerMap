package com.example.missiontrackermap.math

import androidx.compose.ui.geometry.Offset

/**
 * Transforms GPS coordinates (latitude, longitude) to pixel coordinates
 * on a calibrated map image.
 *
 * Implementations may use affine transform, homography, spline, etc.
 * The rest of the app is completely unaware of which algorithm is used.
 */
interface CoordinateTransformer {
    /**
     * Converts a GPS position to pixel coordinates on the map image.
     * Returns the (x, y) pixel offset in image coordinate space.
     */
    fun gpsToPixel(latitude: Double, longitude: Double): Offset
}
