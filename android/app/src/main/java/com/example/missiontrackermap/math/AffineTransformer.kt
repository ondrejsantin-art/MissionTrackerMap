package com.example.missiontrackermap.math

import androidx.compose.ui.geometry.Offset
import com.example.missiontrackermap.model.CalibrationPoint

/**
 * Implements GPS → Pixel mapping using a least-squares affine transform.
 *
 * An affine transform has 6 parameters (a, b, c, d, e, f):
 *   px = a*lat + b*lon + c
 *   py = d*lat + e*lon + f
 *
 * With N calibration points (N ≥ 3), this is an over-determined linear system.
 * We solve it using the normal equations: coefficients = (AᵀA)⁻¹ Aᵀ b
 *
 * For N=3 the solution is exact. For N>3 it minimizes the RMS pixel residual.
 */
class AffineTransformer(points: List<CalibrationPoint>) : CoordinateTransformer {

    private val a: Double  // px = a*lat + b*lon + c
    private val b: Double
    private val c: Double
    private val d: Double  // py = d*lat + e*lon + f
    private val e: Double
    private val f: Double

    init {
        require(points.size >= 3) {
            "AffineTransformer requires at least 3 calibration points, got ${points.size}"
        }
        val (coeffX, coeffY) = fitAffine(points)
        a = coeffX[0]; b = coeffX[1]; c = coeffX[2]
        d = coeffY[0]; e = coeffY[1]; f = coeffY[2]
    }

    override fun gpsToPixel(latitude: Double, longitude: Double): Offset {
        val px = a * latitude + b * longitude + c
        val py = d * latitude + e * longitude + f
        return Offset(px.toFloat(), py.toFloat())
    }

    /**
     * Fits the affine transform using least-squares normal equations on centered coordinates.
     * Returns a pair of (xCoefficients, yCoefficients), each [a/d, b/e, c/f].
     */
    private fun fitAffine(points: List<CalibrationPoint>): Pair<DoubleArray, DoubleArray> {
        val n = points.size

        // Calculate means for centering
        var meanLat = 0.0
        var meanLon = 0.0
        var meanPx = 0.0
        var meanPy = 0.0
        for (p in points) {
            meanLat += p.gps.latitude
            meanLon += p.gps.longitude
            meanPx += p.pixel.x
            meanPy += p.pixel.y
        }
        meanLat /= n
        meanLon /= n
        meanPx /= n
        meanPy /= n

        // Compute terms for 2x2 normal equations
        var sLat2 = 0.0
        var sLon2 = 0.0
        var sLatLon = 0.0
        var sLatPx = 0.0
        var sLonPx = 0.0
        var sLatPy = 0.0
        var sLonPy = 0.0

        for (p in points) {
            val latDiff = p.gps.latitude - meanLat
            val lonDiff = p.gps.longitude - meanLon
            val pxDiff = p.pixel.x - meanPx
            val pyDiff = p.pixel.y - meanPy

            sLat2 += latDiff * latDiff
            sLon2 += lonDiff * lonDiff
            sLatLon += latDiff * lonDiff
            sLatPx += latDiff * pxDiff
            sLonPx += lonDiff * pxDiff
            sLatPy += latDiff * pyDiff
            sLonPy += lonDiff * pyDiff
        }

        val det = sLat2 * sLon2 - sLatLon * sLatLon
        require(det != 0.0) { "Calibration matrix is singular — calibration points may be collinear" }

        val a = (sLatPx * sLon2 - sLonPx * sLatLon) / det
        val b = (sLonPx * sLat2 - sLatPx * sLatLon) / det
        val d = (sLatPy * sLon2 - sLonPy * sLatLon) / det
        val e = (sLonPy * sLat2 - sLatPy * sLatLon) / det

        val c = meanPx - a * meanLat - b * meanLon
        val f = meanPy - d * meanLat - e * meanLon

        return Pair(doubleArrayOf(a, b, c), doubleArrayOf(d, e, f))
    }

    companion object {
        /**
         * Computes the RMS pixel residual for a fitted transformer against the given points.
         * Useful for validation and logging quality of the calibration.
         */
        fun computeRms(transformer: CoordinateTransformer, points: List<CalibrationPoint>): Double {
            val sumSq = points.sumOf { pt ->
                val predicted = transformer.gpsToPixel(pt.gps.latitude, pt.gps.longitude)
                val dx = predicted.x - pt.pixel.x
                val dy = predicted.y - pt.pixel.y
                (dx * dx + dy * dy).toDouble()
            }
            return Math.sqrt(sumSq / points.size)
        }
    }
}
