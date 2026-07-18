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
}
