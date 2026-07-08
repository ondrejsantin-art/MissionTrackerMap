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
     * Fits the affine transform using least-squares normal equations.
     * Returns a pair of (xCoefficients, yCoefficients), each [a/d, b/e, c/f].
     */
    private fun fitAffine(points: List<CalibrationPoint>): Pair<DoubleArray, DoubleArray> {
        val n = points.size

        // Build matrix A (n×3) and right-hand side vectors bx, by
        // Row i: [lat_i, lon_i, 1]
        val A = Array(n) { i ->
            doubleArrayOf(points[i].gps.latitude, points[i].gps.longitude, 1.0)
        }
        val bx = DoubleArray(n) { i -> points[i].pixel.x }
        val by = DoubleArray(n) { i -> points[i].pixel.y }

        // Compute AᵀA (3×3) and Aᵀbx, Aᵀby (3-vectors)
        val ata = multiplyAtA(A, n)
        val atbx = multiplyAtB(A, bx, n)
        val atby = multiplyAtB(A, by, n)

        // Solve 3×3 linear system: ata * coeffX = atbx
        val coeffX = solveLinear3x3(ata, atbx)
        val coeffY = solveLinear3x3(ata, atby)

        return Pair(coeffX, coeffY)
    }

    /** Computes AᵀA for an (n×3) matrix A. Returns a 3×3 matrix. */
    private fun multiplyAtA(A: Array<DoubleArray>, n: Int): Array<DoubleArray> {
        val result = Array(3) { DoubleArray(3) }
        for (i in 0 until 3) {
            for (j in 0 until 3) {
                var sum = 0.0
                for (k in 0 until n) sum += A[k][i] * A[k][j]
                result[i][j] = sum
            }
        }
        return result
    }

    /** Computes Aᵀb for an (n×3) matrix A and n-vector b. Returns a 3-vector. */
    private fun multiplyAtB(A: Array<DoubleArray>, b: DoubleArray, n: Int): DoubleArray {
        val result = DoubleArray(3)
        for (i in 0 until 3) {
            var sum = 0.0
            for (k in 0 until n) sum += A[k][i] * b[k]
            result[i] = sum
        }
        return result
    }

    /**
     * Solves a 3×3 linear system M * x = b using Cramer's rule.
     * Robust enough for our use case where M is always well-conditioned
     * (calibration points are spread across the map).
     */
    private fun solveLinear3x3(M: Array<DoubleArray>, b: DoubleArray): DoubleArray {
        val det = det3x3(M)
        require(det != 0.0) { "Calibration matrix is singular — calibration points may be collinear" }

        val x = DoubleArray(3)
        for (col in 0 until 3) {
            // Build matrix with column `col` replaced by b
            val Mcol = Array(3) { r -> M[r].copyOf() }
            for (r in 0 until 3) Mcol[r][col] = b[r]
            x[col] = det3x3(Mcol) / det
        }
        return x
    }

    /** Computes the determinant of a 3×3 matrix using cofactor expansion. */
    private fun det3x3(M: Array<DoubleArray>): Double {
        return M[0][0] * (M[1][1] * M[2][2] - M[1][2] * M[2][1]) -
               M[0][1] * (M[1][0] * M[2][2] - M[1][2] * M[2][0]) +
               M[0][2] * (M[1][0] * M[2][1] - M[1][1] * M[2][0])
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
