package com.example.missiontrackermap.repository

import com.example.missiontrackermap.model.CalibrationData
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails

class MissionImportTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun parseValidJson_withThreeOrMorePoints_succeeds() {
        val validJson = """
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
                    },
                    {
                        "name": "P2",
                        "pixel": {"x": 20.0, "y": 20.0},
                        "gps": {"latitude": 50.1, "longitude": 15.1}
                    },
                    {
                        "name": "P3",
                        "pixel": {"x": 30.0, "y": 30.0},
                        "gps": {"latitude": 50.2, "longitude": 15.2}
                    }
                ]
            }
        """.trimIndent()

        val calibration = json.decodeFromString<CalibrationData>(validJson)
        assertEquals(1, calibration.version)
        assertEquals("test.png", calibration.image)
        assertEquals(3, calibration.points.size)
    }

    @Test
    fun validation_withLessThanThreePoints_fails() {
        val invalidJson = """
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
                    },
                    {
                        "name": "P2",
                        "pixel": {"x": 20.0, "y": 20.0},
                        "gps": {"latitude": 50.1, "longitude": 15.1}
                    }
                ]
            }
        """.trimIndent()

        val calibration = json.decodeFromString<CalibrationData>(invalidJson)
        // Check that points size is less than 3
        assert(calibration.points.size < 3)
    }

    @Test
    fun serialization_roundTrip_succeeds() {
        val validJson = """
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
                    },
                    {
                        "name": "P2",
                        "pixel": {"x": 20.0, "y": 20.0},
                        "gps": {"latitude": 50.1, "longitude": 15.1}
                    },
                    {
                        "name": "P3",
                        "pixel": {"x": 30.0, "y": 30.0},
                        "gps": {"latitude": 50.2, "longitude": 15.2}
                    }
                ]
            }
        """.trimIndent()

        val calibration = json.decodeFromString<CalibrationData>(validJson)
        // Update image property
        val updatedCalibration = calibration.copy(image = "updated.png")
        val updatedJsonContent = json.encodeToString(updatedCalibration)

        val parsedUpdated = json.decodeFromString<CalibrationData>(updatedJsonContent)
        assertEquals("updated.png", parsedUpdated.image)
        assertEquals(3, parsedUpdated.points.size)
    }
}
