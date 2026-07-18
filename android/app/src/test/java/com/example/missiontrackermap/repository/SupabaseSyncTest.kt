package com.example.missiontrackermap.repository

import com.example.missiontrackermap.model.CalibrationData
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@Serializable
private data class SupabaseMissionVersion(
    val id: String,
    val version: Int
)

@Serializable
private data class SupabaseMissionDetail(
    val json_data: JsonObject
)

class SupabaseSyncTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun parseSupabaseMissionVersion_validJson_succeeds() {
        val jsonString = """
            [
                {"id": "scarif", "version": 2},
                {"id": "tatooine", "version": 5}
            ]
        """.trimIndent()

        val parsed = json.decodeFromString<List<SupabaseMissionVersion>>(jsonString)
        assertEquals(2, parsed.size)
        assertEquals("scarif", parsed[0].id)
        assertEquals(2, parsed[0].version)
        assertEquals("tatooine", parsed[1].id)
        assertEquals(5, parsed[1].version)
    }

    @Test
    fun parseSupabaseMissionDetail_validJson_succeeds() {
        val jsonString = """
            [
                {
                    "json_data": {
                        "version": 2,
                        "image": "scarif.png",
                        "imageWidth": 4220,
                        "imageHeight": 5964,
                        "points": [
                            {
                                "name": "01",
                                "pixel": {"x": 2980.0, "y": 1348.0},
                                "gps": {"latitude": 50.880185, "longitude": 15.1371217},
                                "missionObjective": "Objective 1"
                            }
                        ]
                    }
                }
            ]
        """.trimIndent()

        val parsed = json.decodeFromString<List<SupabaseMissionDetail>>(jsonString)
        assertEquals(1, parsed.size)
        
        val rawJsonData = parsed[0].json_data.toString()
        val calibration = json.decodeFromString<CalibrationData>(rawJsonData)
        
        assertEquals(2, calibration.version)
        assertEquals("scarif.png", calibration.image)
        assertEquals(1, calibration.points.size)
        assertEquals("01", calibration.points[0].name)
        assertEquals("Objective 1", calibration.points[0].missionObjective)
    }
}
