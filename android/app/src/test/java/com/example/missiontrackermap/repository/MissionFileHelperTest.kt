package com.example.missiontrackermap.repository

import com.example.missiontrackermap.model.CalibrationData
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MissionFileHelperTest {

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

    @Test
    fun renameMission_validName_succeeds() {
        val oldName = "old_mission"
        val newName = "new_mission"

        val oldDir = File(tempMissionsDir, oldName).apply { mkdir() }
        val oldJsonFile = File(oldDir, "$oldName.json")
        val oldImageFile = File(oldDir, "$oldName.png")

        val calibration = CalibrationData(
            version = 1,
            image = "$oldName.png",
            imageWidth = 100,
            imageHeight = 100,
            points = emptyList()
        )
        oldJsonFile.writeText(json.encodeToString(CalibrationData.serializer(), calibration))
        oldImageFile.writeBytes(byteArrayOf(1, 2, 3))

        val result = helper.renameMission(oldName, newName)
        assertTrue(result.isSuccess)

        val newDir = File(tempMissionsDir, newName)
        assertTrue(newDir.exists())
        assertTrue(!oldDir.exists())

        val newJsonFile = File(newDir, "$newName.json")
        val newImageFile = File(newDir, "$newName.png")
        assertTrue(newJsonFile.exists())
        assertTrue(newImageFile.exists())
        assertTrue(!oldJsonFile.exists())
        assertTrue(!oldImageFile.exists())

        val newCalibration = json.decodeFromString<CalibrationData>(newJsonFile.readText())
        assertEquals("$newName.png", newCalibration.image)
    }

    @Test
    fun renameMission_blankName_fails() {
        val result = helper.renameMission("some_mission", "   ")
        assertTrue(result.isFailure)
        assertEquals("New name cannot be blank", result.exceptionOrNull()?.message)
    }

    @Test
    fun renameMission_missingSource_fails() {
        val result = helper.renameMission("non_existent", "new_name")
        assertTrue(result.isFailure)
    }

    @Test
    fun renameMission_targetAlreadyExists_fails() {
        val name1 = "mission1"
        val name2 = "mission2"

        File(tempMissionsDir, name1).apply { mkdir() }
        File(tempMissionsDir, name2).apply { mkdir() }

        val result = helper.renameMission(name1, name2)
        assertTrue(result.isFailure)
        assertEquals("A mission with this name already exists", result.exceptionOrNull()?.message)
    }

    @Test
    fun deleteMission_existing_succeeds() {
        val name = "to_delete"
        val missionDir = File(tempMissionsDir, name).apply { mkdir() }
        File(missionDir, "some_file.txt").writeText("hello")

        val result = helper.deleteMission(name)
        assertTrue(result.isSuccess)
        assertTrue(!missionDir.exists())
    }
}
