package com.example.missiontrackermap.repository

import com.example.missiontrackermap.model.CalibrationData
import com.example.missiontrackermap.model.MissionProgress
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MissionProgressTest {

    private val json = Json { ignoreUnknownKeys = true }

    // --- JSON Schema: missionObjective field ---

    @Test
    fun missionObjective_absent_parsesAsNull() {
        val jsonStr = """
            {
                "version": 1,
                "image": "test.png",
                "imageWidth": 100,
                "imageHeight": 100,
                "points": [
                    {
                        "name": "P1",
                        "pixel": {"x": 10.0, "y": 10.0},
                        "gps": {"latitude": 50.0, "longitude": 15.0}
                    }
                ]
            }
        """.trimIndent()

        val calibration = json.decodeFromString<CalibrationData>(jsonStr)
        assertNull(calibration.points[0].missionObjective)
    }

    @Test
    fun missionObjective_presentAndNonBlank_parsedCorrectly() {
        val jsonStr = """
            {
                "version": 1,
                "image": "test.png",
                "imageWidth": 100,
                "imageHeight": 100,
                "points": [
                    {
                        "name": "Checkpoint Alpha",
                        "pixel": {"x": 10.0, "y": 10.0},
                        "gps": {"latitude": 50.0, "longitude": 15.0},
                        "missionObjective": "Retrieve the intel package."
                    }
                ]
            }
        """.trimIndent()

        val calibration = json.decodeFromString<CalibrationData>(jsonStr)
        assertEquals("Retrieve the intel package.", calibration.points[0].missionObjective)
    }

    @Test
    fun missionObjective_roundTrip_preservesValue() {
        val jsonStr = """
            {
                "version": 1,
                "image": "test.png",
                "imageWidth": 100,
                "imageHeight": 100,
                "points": [
                    {
                        "name": "P1",
                        "pixel": {"x": 10.0, "y": 10.0},
                        "gps": {"latitude": 50.0, "longitude": 15.0},
                        "missionObjective": "Find the beacon."
                    },
                    {
                        "name": "P2",
                        "pixel": {"x": 20.0, "y": 20.0},
                        "gps": {"latitude": 50.1, "longitude": 15.1}
                    }
                ]
            }
        """.trimIndent()

        val original = json.decodeFromString<CalibrationData>(jsonStr)
        val encoded = json.encodeToString(original)
        val decoded = json.decodeFromString<CalibrationData>(encoded)

        assertEquals("Find the beacon.", decoded.points[0].missionObjective)
        assertNull(decoded.points[1].missionObjective)
    }

    // --- Mission point filtering logic ---

    @Test
    fun missionPoints_nullObjective_notConsideredMissionPoint() {
        val jsonStr = """
            {
                "version": 1, "image": "t.png", "imageWidth": 10, "imageHeight": 10,
                "points": [
                    {"name": "P1", "pixel": {"x":1.0,"y":1.0}, "gps": {"latitude":50.0,"longitude":15.0}}
                ]
            }
        """.trimIndent()
        val cal = json.decodeFromString<CalibrationData>(jsonStr)
        val missionPoints = cal.points.filter { !it.missionObjective.isNullOrBlank() }
        assertTrue(missionPoints.isEmpty())
    }

    @Test
    fun missionPoints_nonBlankObjective_isMissionPoint() {
        val jsonStr = """
            {
                "version": 1, "image": "t.png", "imageWidth": 10, "imageHeight": 10,
                "points": [
                    {"name": "P1", "pixel": {"x":1.0,"y":1.0}, "gps": {"latitude":50.0,"longitude":15.0},
                     "missionObjective": "Do something."}
                ]
            }
        """.trimIndent()
        val cal = json.decodeFromString<CalibrationData>(jsonStr)
        val missionPoints = cal.points.filter { !it.missionObjective.isNullOrBlank() }
        assertEquals(1, missionPoints.size)
        assertEquals("P1", missionPoints[0].name)
    }

    @Test
    fun missionPoints_blankObjective_notConsideredMissionPoint() {
        val jsonStr = """
            {
                "version": 1, "image": "t.png", "imageWidth": 10, "imageHeight": 10,
                "points": [
                    {"name": "P1", "pixel": {"x":1.0,"y":1.0}, "gps": {"latitude":50.0,"longitude":15.0},
                     "missionObjective": "   "}
                ]
            }
        """.trimIndent()
        val cal = json.decodeFromString<CalibrationData>(jsonStr)
        val missionPoints = cal.points.filter { !it.missionObjective.isNullOrBlank() }
        assertTrue(missionPoints.isEmpty())
    }

    // --- MissionProgress model ---

    @Test
    fun missionProgress_emptyDefault_roundTrip() {
        val progress = MissionProgress()
        val encoded = json.encodeToString(MissionProgress.serializer(), progress)
        val decoded = json.decodeFromString<MissionProgress>(encoded)
        assertTrue(decoded.completedPoints.isEmpty())
    }

    @Test
    fun missionProgress_withPoints_roundTrip() {
        val progress = MissionProgress(completedPoints = setOf("Alpha", "Bravo"))
        val encoded = json.encodeToString(MissionProgress.serializer(), progress)
        val decoded = json.decodeFromString<MissionProgress>(encoded)
        assertEquals(setOf("Alpha", "Bravo"), decoded.completedPoints)
    }

    // --- Toggle logic (pure set operations, mirrors ViewModel) ---

    @Test
    fun toggle_addsPointWhenNotPresent() {
        var completed: Set<String> = emptySet()
        completed = if ("Alpha" in completed) completed - "Alpha" else completed + "Alpha"
        assertTrue("Alpha" in completed)
    }

    @Test
    fun toggle_removesPointWhenPresent() {
        var completed: Set<String> = setOf("Alpha")
        completed = if ("Alpha" in completed) completed - "Alpha" else completed + "Alpha"
        assertFalse("Alpha" in completed)
    }

    @Test
    fun toggle_twiceRestoresOriginalState() {
        var completed: Set<String> = emptySet()
        completed = if ("Alpha" in completed) completed - "Alpha" else completed + "Alpha"
        completed = if ("Alpha" in completed) completed - "Alpha" else completed + "Alpha"
        assertTrue(completed.isEmpty())
    }

    @Test
    fun testCoordinateTransformation() {
        val center = Coordinate(100f, 100f)
        val p = Coordinate(120f, 130f)
        val zoomScale = 2f
        val zoomOffset = Coordinate(10f, -20f)
        val heading = 90f // 90 degrees clockwise rotation (angle = -90)

        // 1. Scale around center
        // p = (120, 130) -> center (100, 100). dx = 20, dy = 30.
        // scaled = center + dx*2, dy*2 = (140, 160)
        val px1 = center.x + (p.x - center.x) * zoomScale
        val py1 = center.y + (p.y - center.y) * zoomScale
        assertEquals(140f, px1)
        assertEquals(160f, py1)

        // 2. Translate
        // translated = (140 + 10, 160 - 20) = (150, 140)
        val px2 = px1 + zoomOffset.x
        val py2 = py1 + zoomOffset.y
        assertEquals(150f, px2)
        assertEquals(140f, py2)

        // 3. Rotate around center by -heading (-90 degrees)
        // dx = 150 - 100 = 50
        // dy = 140 - 100 = 40
        // Rotation by -90 deg: cos(-90) = 0, sin(-90) = -1
        // rx = center.x + (dx * cos - dy * sin) = 100 + (50 * 0 - 40 * -1) = 140
        // ry = center.y + (dx * sin + dy * cos) = 100 + (50 * -1 + 40 * 0) = 50
        val rad = -heading * (Math.PI / 180.0).toFloat()
        val cosVal = kotlin.math.cos(rad)
        val sinVal = kotlin.math.sin(rad)
        val dx = px2 - center.x
        val dy = py2 - center.y
        val px3 = center.x + (dx * cosVal - dy * sinVal)
        val py3 = center.y + (dx * sinVal + dy * cosVal)

        assertEquals(140f, px3, 0.01f)
        assertEquals(50f, py3, 0.01f)
    }

    private data class Coordinate(val x: Float, val y: Float)
}
