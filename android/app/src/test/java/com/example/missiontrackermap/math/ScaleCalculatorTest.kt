package com.example.missiontrackermap.math

import com.example.missiontrackermap.model.CalibrationPoint
import com.example.missiontrackermap.model.GpsCoordinate
import com.example.missiontrackermap.model.PixelCoordinate
import kotlin.math.cos
import java.lang.Math.toRadians
import kotlin.math.PI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ScaleCalculatorTest {

    private val scarifPoints = listOf(
        CalibrationPoint("01", PixelCoordinate(2980.0, 1348.0), GpsCoordinate(50.880185, 15.1371217)),
        CalibrationPoint("02", PixelCoordinate(676.0, 227.0), GpsCoordinate(50.8837408, 15.1257856)),
        CalibrationPoint("03", PixelCoordinate(2183.0, 2649.0), GpsCoordinate(50.8761064, 15.1338606)),
        CalibrationPoint("04", PixelCoordinate(1537.0, 4619.0), GpsCoordinate(50.8695631, 15.1299092)),
        CalibrationPoint("05", PixelCoordinate(1647.0, 5040.0), GpsCoordinate(50.8681872, 15.1305156)),
        CalibrationPoint("06", PixelCoordinate(2937.0, 4781.0), GpsCoordinate(50.8690533, 15.1367883)),
        CalibrationPoint("07", PixelCoordinate(3041.0, 4176.0), GpsCoordinate(50.8708681, 15.1377544)),
        CalibrationPoint("archiv", PixelCoordinate(3195.0, 5738.0), GpsCoordinate(50.86592, 15.1380633))
    )

    @Test
    fun calculateOneKmPixelLength_emptyPoints_returnsZero() {
        val result = ScaleCalculator.calculateOneKmPixelLength(emptyList())
        assertEquals(0f, result)
    }

    @Test
    fun calculateOneKmPixelLength_fewerThanThreePoints_returnsZero() {
        val result = ScaleCalculator.calculateOneKmPixelLength(scarifPoints.take(2))
        assertEquals(0f, result)
    }

    @Test
    fun calculateOneKmPixelLength_scarifPoints_returnsPositiveValue() {
        val result = ScaleCalculator.calculateOneKmPixelLength(scarifPoints)
        println("Scarif 1 km pixel length: $result px")
        // We know it should be roughly around 2888 - 3220 px
        assertTrue(result > 2000f)
        assertTrue(result < 4000f)
    }

    @Test
    fun calculateOneKmPixelLength_syntheticMap_returnsExactValue() {
        // Construct a simple map: north -> up, east -> right
        // GPS space: lat 50.0..51.0, lon 15.0..16.0 -> pixel 0..1000, 0..1000
        val synthetic = listOf(
            CalibrationPoint("NW", PixelCoordinate(0.0, 0.0), GpsCoordinate(51.0, 15.0)),
            CalibrationPoint("NE", PixelCoordinate(1000.0, 0.0), GpsCoordinate(51.0, 16.0)),
            CalibrationPoint("SW", PixelCoordinate(0.0, 1000.0), GpsCoordinate(50.0, 15.0)),
            CalibrationPoint("SE", PixelCoordinate(1000.0, 1000.0), GpsCoordinate(50.0, 16.0))
        )

        // Center latitude is 50.5.
        // metersPerDegreeLon at lat 50.5 = (PI/180) * 6371000 * cos(50.5)
        val latRad = toRadians(50.5)
        val metersPerDegreeLon = (PI / 180.0) * 6371000.0 * cos(latRad)
        val expectedPxPerKm = (1000.0 * 1000.0 / metersPerDegreeLon).toFloat()

        val result = ScaleCalculator.calculateOneKmPixelLength(synthetic)
        println("Synthetic 1 km pixel length: expected = $expectedPxPerKm, got = $result")
        assertEquals(expectedPxPerKm, result, 0.01f)
    }
}
