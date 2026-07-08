package com.example.missiontrackermap.math

import com.example.missiontrackermap.model.CalibrationPoint
import com.example.missiontrackermap.model.GpsCoordinate
import com.example.missiontrackermap.model.PixelCoordinate
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Unit tests for AffineTransformer.
 *
 * Real calibration data is from the Scarif mission
 * (actual camp area near Liberec, Czech Republic).
 */
class AffineTransformerTest {

    // -----------------------------------------------------------------------
    // Real calibration data from calibration_scarif_test_RMS.json
    // -----------------------------------------------------------------------
    private val realPoints = listOf(
        CalibrationPoint(
            "rozcesti u krize sedlaka pepka",
            PixelCoordinate(1005.0, 604.0),
            GpsCoordinate(50.8768306, 15.1428431)
        ),
        CalibrationPoint(
            "rozcesti pod viaduktem",
            PixelCoordinate(430.0, 238.0),
            GpsCoordinate(50.8813847, 15.1307408)
        ),
        CalibrationPoint(
            "rozcesti modra a lesni cesta u 4ky",
            PixelCoordinate(386.0, 1154.0),
            GpsCoordinate(50.8694086, 15.1297164)
        ),
        CalibrationPoint(
            "odbocka na Dubinske ceste za viaduktem",
            PixelCoordinate(247.0, 292.0),
            GpsCoordinate(50.8806403, 15.1271144)
        ),
        CalibrationPoint(
            "Archiv - sít - pod kopcem u potúcku strom s bolakem dole",
            PixelCoordinate(745.0, 338.0),
            GpsCoordinate(50.880185, 15.1371217)
        )
    )

    // -----------------------------------------------------------------------
    // Test 1: Round-trip accuracy on calibration points
    // For a well-conditioned affine fit, each calibration point should map
    // back to its own pixel within ±5px (least-squares distributes error).
    // -----------------------------------------------------------------------
    @Test
    fun roundTrip_eachCalibrationPoint_withinTolerancePixels() {
        val transformer = AffineTransformer(realPoints)
        val tolerancePx = 5.0

        for (point in realPoints) {
            val predicted = transformer.gpsToPixel(point.gps.latitude, point.gps.longitude)
            val dx = abs(predicted.x - point.pixel.x)
            val dy = abs(predicted.y - point.pixel.y)

            assertTrue(
                dx <= tolerancePx,
                "Point '${point.name}': X residual $dx > ${tolerancePx}px"
            )
            assertTrue(
                dy <= tolerancePx,
                "Point '${point.name}': Y residual $dy > ${tolerancePx}px"
            )
        }
    }

    // -----------------------------------------------------------------------
    // Test 2: RMS residual on real data must be below acceptable threshold
    // -----------------------------------------------------------------------
    @Test
    fun rmsResidual_realData_belowThreshold() {
        val transformer = AffineTransformer(realPoints)
        val rms = AffineTransformer.computeRms(transformer, realPoints)

        println("AffineTransformer RMS on real Scarif data: %.2f px".format(rms))
        assertTrue(rms < 30.0, "RMS residual $rms px exceeds 30px threshold")
    }

    // -----------------------------------------------------------------------
    // Test 3: Exact solution with exactly 3 collinear-free points
    // With 3 points the affine system is exactly determined → residual ≈ 0
    // -----------------------------------------------------------------------
    @Test
    fun exactFit_withThreePoints_zeroResidual() {
        val threePoints = realPoints.take(3)
        val transformer = AffineTransformer(threePoints)
        val rms = AffineTransformer.computeRms(transformer, threePoints)

        println("AffineTransformer RMS (3 points exact): %.4f px".format(rms))
        assertTrue(rms < 0.01, "3-point exact fit RMS should be ~0, got $rms")
    }

    // -----------------------------------------------------------------------
    // Test 4: More points improves or maintains fit quality vs fewer points
    // The 5-point overdetermined fit should have lower residual on excluded
    // points compared to a 3-point fit that ignores them.
    // -----------------------------------------------------------------------
    @Test
    fun moreCalibrationsPoints_doesNotDegradeFit() {
        val fivePointTransformer = AffineTransformer(realPoints)
        val threePointTransformer = AffineTransformer(realPoints.take(3))

        val rms5 = AffineTransformer.computeRms(fivePointTransformer, realPoints)
        val rms3 = AffineTransformer.computeRms(threePointTransformer, realPoints)

        println("RMS (3-point fit on all 5 points): %.2f px".format(rms3))
        println("RMS (5-point fit on all 5 points): %.2f px".format(rms5))

        // The 5-point fit should be better (lower RMS) when tested on all 5 points
        assertTrue(
            rms5 <= rms3 + 1.0, // small tolerance for floating-point edge cases
            "5-point fit RMS ($rms5) should not be worse than 3-point fit RMS ($rms3) by more than 1px"
        )
    }

    // -----------------------------------------------------------------------
    // Test 5: Known synthetic transform — verify correctness
    // Build a synthetic affine map where we know the exact answer.
    // -----------------------------------------------------------------------
    @Test
    fun syntheticTransform_knownMapping_isCorrect() {
        // Construct a simple map: north → up, east → right
        // GPS space: lat 50.0..51.0, lon 15.0..16.0  →  pixel 0..1000, 0..1000
        // Affine: px = 1000*(lon-15.0), py = 1000*(51.0-lat)
        val synthetic = listOf(
            CalibrationPoint("NW", PixelCoordinate(0.0, 0.0), GpsCoordinate(51.0, 15.0)),
            CalibrationPoint("NE", PixelCoordinate(1000.0, 0.0), GpsCoordinate(51.0, 16.0)),
            CalibrationPoint("SW", PixelCoordinate(0.0, 1000.0), GpsCoordinate(50.0, 15.0)),
            CalibrationPoint("SE", PixelCoordinate(1000.0, 1000.0), GpsCoordinate(50.0, 16.0))
        )
        val transformer = AffineTransformer(synthetic)

        // Center of map should map to center pixel
        val center = transformer.gpsToPixel(50.5, 15.5)
        assertEquals(500f, center.x, absoluteTolerance = 0.5f, message = "Center X")
        assertEquals(500f, center.y, absoluteTolerance = 0.5f, message = "Center Y")

        // NE corner
        val ne = transformer.gpsToPixel(51.0, 16.0)
        assertEquals(1000f, ne.x, absoluteTolerance = 0.5f, message = "NE X")
        assertEquals(0f, ne.y, absoluteTolerance = 0.5f, message = "NE Y")
    }

    // -----------------------------------------------------------------------
    // Test 6: Fewer than 3 points throws IllegalArgumentException
    // -----------------------------------------------------------------------
    @Test
    fun fewerThanThreePoints_throwsIllegalArgumentException() {
        assertFailsWith<IllegalArgumentException> {
            AffineTransformer(realPoints.take(2))
        }
    }

    @Test
    fun emptyPoints_throwsIllegalArgumentException() {
        assertFailsWith<IllegalArgumentException> {
            AffineTransformer(emptyList())
        }
    }

    // -----------------------------------------------------------------------
    // Test 7: Transformer is deterministic (same input → same output)
    // -----------------------------------------------------------------------
    @Test
    fun deterministic_sameInputSameOutput() {
        val transformer1 = AffineTransformer(realPoints)
        val transformer2 = AffineTransformer(realPoints)

        val gps = realPoints[0].gps
        val result1 = transformer1.gpsToPixel(gps.latitude, gps.longitude)
        val result2 = transformer2.gpsToPixel(gps.latitude, gps.longitude)

        assertEquals(result1.x, result2.x)
        assertEquals(result1.y, result2.y)
    }
}

// -----------------------------------------------------------------------
// Extension for float comparison with tolerance (not in kotlin.test for floats)
// -----------------------------------------------------------------------
fun assertEquals(expected: Float, actual: Float, absoluteTolerance: Float, message: String? = null) {
    val diff = abs(expected - actual)
    assertTrue(diff <= absoluteTolerance, buildString {
        if (message != null) append("$message: ")
        append("Expected $expected ± $absoluteTolerance but got $actual (diff=$diff)")
    })
}
