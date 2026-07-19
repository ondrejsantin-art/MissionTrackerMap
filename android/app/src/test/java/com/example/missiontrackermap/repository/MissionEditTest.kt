package com.example.missiontrackermap.repository

import com.example.missiontrackermap.model.CalibrationData
import com.example.missiontrackermap.model.CalibrationPoint
import com.example.missiontrackermap.model.GpsCoordinate
import com.example.missiontrackermap.model.PixelCoordinate
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MissionEditTest {

    private lateinit var tempMissionsDir: File
    private lateinit var helper: MissionFileHelper
    private val json = Json { ignoreUnknownKeys = true }

    @BeforeTest
    fun setUp() {
        tempMissionsDir = File.createTempFile("temp_missions", "").apply {
            delete()
            mkdir()
        }
        helper = MissionFileHelper(tempMissionsDir)
    }

    @AfterTest
    fun tearDown() {
        tempMissionsDir.deleteRecursively()
    }

    private fun sampleCalibration(): CalibrationData = CalibrationData(
        version = 1,
        image = "test.png",
        imageWidth = 1000,
        imageHeight = 800,
        points = listOf(
            CalibrationPoint(
                name = "P001",
                pixel = PixelCoordinate(100.0, 200.0),
                gps = GpsCoordinate(49.0, 14.0),
                missionObjective = "Find the treasure"
            ),
            CalibrationPoint(
                name = "P002",
                pixel = PixelCoordinate(300.0, 400.0),
                gps = GpsCoordinate(49.1, 14.1),
                missionObjective = null
            ),
            CalibrationPoint(
                name = "P003",
                pixel = PixelCoordinate(500.0, 600.0),
                gps = GpsCoordinate(49.2, 14.2),
                missionObjective = "Final checkpoint"
            )
        )
    )

    // --- saveCalibration tests ---

    @Test
    fun saveCalibration_validMission_writesJsonFile() {
        val missionId = "test_mission"
        val missionDir = File(tempMissionsDir, missionId).apply { mkdir() }
        val cal = sampleCalibration()

        val result = helper.saveCalibration(missionId, cal)
        assertTrue(result.isSuccess)

        val jsonFile = File(missionDir, "$missionId.json")
        assertTrue(jsonFile.exists())

        val loaded = json.decodeFromString<CalibrationData>(jsonFile.readText())
        assertEquals(cal.version, loaded.version)
        assertEquals(cal.points.size, loaded.points.size)
        assertEquals("P001", loaded.points[0].name)
        assertEquals("Find the treasure", loaded.points[0].missionObjective)
    }

    @Test
    fun saveCalibration_missingDir_fails() {
        val result = helper.saveCalibration("nonexistent", sampleCalibration())
        assertTrue(result.isFailure)
    }

    @Test
    fun saveCalibration_overwritesExisting() {
        val missionId = "overwrite_test"
        File(tempMissionsDir, missionId).apply { mkdir() }

        val cal = sampleCalibration()
        helper.saveCalibration(missionId, cal)

        val updated = cal.copy(version = 5)
        val result = helper.saveCalibration(missionId, updated)
        assertTrue(result.isSuccess)

        val jsonFile = File(File(tempMissionsDir, missionId), "$missionId.json")
        val loaded = json.decodeFromString<CalibrationData>(jsonFile.readText())
        assertEquals(5, loaded.version)
    }

    // --- Point update (copy pattern) tests ---

    @Test
    fun calibrationPoint_copy_updatesName() {
        val point = CalibrationPoint(
            name = "P001",
            pixel = PixelCoordinate(100.0, 200.0),
            gps = GpsCoordinate(49.0, 14.0),
            missionObjective = "Original"
        )
        val updated = point.copy(name = "NewName")
        assertEquals("NewName", updated.name)
        assertEquals("Original", updated.missionObjective)
        assertEquals(100.0, updated.pixel.x)
    }

    @Test
    fun calibrationPoint_copy_updatesPixelAndObjective() {
        val point = CalibrationPoint(
            name = "P001",
            pixel = PixelCoordinate(100.0, 200.0),
            gps = GpsCoordinate(49.0, 14.0),
            missionObjective = "Original"
        )
        val updated = point.copy(
            pixel = PixelCoordinate(x = 500.0, y = 600.0),
            missionObjective = "Updated objective"
        )
        assertEquals(500.0, updated.pixel.x)
        assertEquals(600.0, updated.pixel.y)
        assertEquals("Updated objective", updated.missionObjective)
        assertEquals("P001", updated.name)
    }

    @Test
    fun calibrationPoint_copy_clearsObjective() {
        val point = CalibrationPoint(
            name = "P001",
            pixel = PixelCoordinate(100.0, 200.0),
            gps = GpsCoordinate(49.0, 14.0),
            missionObjective = "Something"
        )
        val updated = point.copy(missionObjective = null)
        assertNull(updated.missionObjective)
    }

    @Test
    fun calibrationData_updatePointInList_preservesOthers() {
        val cal = sampleCalibration()

        val updatedPoint = cal.points[1].copy(
            name = "ModifiedP002",
            missionObjective = "New objective",
            pixel = PixelCoordinate(x = 999.0, y = 888.0)
        )
        val newPoints = cal.points.toMutableList().apply { set(1, updatedPoint) }
        val updatedCal = cal.copy(points = newPoints)

        assertEquals(3, updatedCal.points.size)
        assertEquals("P001", updatedCal.points[0].name)
        assertEquals("ModifiedP002", updatedCal.points[1].name)
        assertEquals("New objective", updatedCal.points[1].missionObjective)
        assertEquals(999.0, updatedCal.points[1].pixel.x)
        assertEquals("P003", updatedCal.points[2].name)
    }

    // --- Owner ID persistence tests ---

    @Test
    fun ownerFile_writtenAndReadBack() {
        val missionId = "owned_mission"
        val missionDir = File(tempMissionsDir, missionId).apply { mkdir() }
        val ownerFile = File(missionDir, ".owner")

        // Simulate writing owner_id
        val ownerId = "user-uuid-12345"
        ownerFile.writeText(ownerId)

        assertTrue(ownerFile.exists())
        assertEquals(ownerId, ownerFile.readText().trim())
    }

    @Test
    fun ownerFile_missingMission_returnsNull() {
        val ownerFile = File(File(tempMissionsDir, "nonexistent"), ".owner")
        assertTrue(!ownerFile.exists())
    }
}
