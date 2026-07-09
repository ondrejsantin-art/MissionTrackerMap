package com.example.missiontrackermap.ui

import android.app.Application
import android.graphics.BitmapFactory
import android.util.Log
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.missiontrackermap.location.FusedLocationProvider
import com.example.missiontrackermap.math.AffineTransformer
import com.example.missiontrackermap.math.CoordinateTransformer
import com.example.missiontrackermap.model.CalibrationData
import com.example.missiontrackermap.model.GpsCoordinate
import com.example.missiontrackermap.repository.MissionTrackerRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlinx.serialization.json.Json

private const val TAG = "MissionTrackerViewModel"

/**
 * ViewModel for the MapScreen.
 *
 * Responsibilities:
 *  - Load mission (image + calibration) from repository
 *  - Collect GPS location updates
 *  - Compute pixel position by combining calibration + GPS via AffineTransformer
 *  - Expose all state as StateFlows for the UI
 */
class MissionTrackerViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = MissionTrackerRepository(application)
    private val locationProvider = FusedLocationProvider(application)

    // --- Mission state ---
    private val _mapBitmap = MutableStateFlow<ImageBitmap?>(null)
    val mapBitmap: StateFlow<ImageBitmap?> = _mapBitmap

    private val _calibration = MutableStateFlow<CalibrationData?>(null)
    val calibration: StateFlow<CalibrationData?> = _calibration

    private val _loadError = MutableStateFlow<String?>(null)
    val loadError: StateFlow<String?> = _loadError

    private val _currentMissionId = MutableStateFlow<String>("scarif")
    val currentMissionId: StateFlow<String> = _currentMissionId

    private val _availableMissions = MutableStateFlow<List<String>>(emptyList())
    val availableMissions: StateFlow<List<String>> = _availableMissions

    // --- GPS state ---
    private val _gpsLocation = MutableStateFlow<GpsCoordinate?>(null)
    val gpsLocation: StateFlow<GpsCoordinate?> = _gpsLocation

    // --- Computed dot position (Task F) ---
    // Combines calibration data + GPS position → pixel Offset on the image
    val dotPosition: StateFlow<Offset?> = combine(_calibration, _gpsLocation) { cal, gps ->
        if (cal == null || gps == null) return@combine null

        return@combine try {
            val transformer: CoordinateTransformer = AffineTransformer(cal.points)
            val pixel = transformer.gpsToPixel(gps.latitude, gps.longitude)
            Log.d(TAG, "GPS(${gps.latitude}, ${gps.longitude}) → Pixel(${pixel.x}, ${pixel.y})")
            pixel
        } catch (e: Exception) {
            Log.e(TAG, "Transform failed: ${e.message}")
            null
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, null)

    init {
        refreshMissions()
        loadMission("scarif")
    }

    fun refreshMissions() {
        _availableMissions.value = repository.availableMissions()
    }

    fun selectMission(missionId: String) {
        loadMission(missionId)
    }

    fun importMission(missionName: String, imageUri: android.net.Uri, jsonUri: android.net.Uri): Result<Unit> {
        return try {
            val context = getApplication<Application>()

            // 1. Read JSON file content
            val jsonContent = context.contentResolver.openInputStream(jsonUri)?.use { input ->
                input.bufferedReader().readText()
            } ?: return Result.failure(Exception("Failed to open calibration file"))

            // 2. Parse and validate JSON
            val jsonDecoder = Json { ignoreUnknownKeys = true }
            val calibration = try {
                jsonDecoder.decodeFromString<CalibrationData>(jsonContent)
            } catch (e: Exception) {
                return Result.failure(Exception("Invalid JSON format: ${e.message}"))
            }

            if (calibration.points.size < 3) {
                return Result.failure(Exception("Calibration must have at least 3 points (found ${calibration.points.size})"))
            }

            // 3. Create destination directory
            val missionsDir = File(context.filesDir, "missions")
            val missionDir = File(missionsDir, missionName)
            if (!missionDir.exists() && !missionDir.mkdirs()) {
                return Result.failure(Exception("Failed to create mission directory"))
            }

            // 4. Update JSON calibration data and write to destination
            val updatedCalibration = calibration.copy(image = "$missionName.png")
            val updatedJsonContent = Json.encodeToString(CalibrationData.serializer(), updatedCalibration)
            val jsonDestFile = File(missionDir, "$missionName.json")
            jsonDestFile.writeText(updatedJsonContent)

            // 5. Copy the PNG image file to destination
            val imageDestFile = File(missionDir, "$missionName.png")
            context.contentResolver.openInputStream(imageUri)?.use { input ->
                imageDestFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: return Result.failure(Exception("Failed to copy image file"))

            Log.i(TAG, "Successfully imported mission: $missionName")

            // 6. Refresh available missions and load the imported mission
            refreshMissions()
            loadMission(missionName)

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error importing mission", e)
            Result.failure(e)
        }
    }

    /** Loads a mission from repository in a background coroutine. */
    private fun loadMission(missionId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _loadError.value = null
            val mission = repository.loadMission(missionId)
            if (mission == null) {
                _loadError.value = "Failed to load mission '$missionId'"
                Log.e(TAG, "Mission load returned null for '$missionId'")
                return@launch
            }

            // Decode bitmap on IO thread (can be heavy for large PNGs)
            val bitmap = withContext(Dispatchers.IO) {
                BitmapFactory.decodeByteArray(mission.imageBytes, 0, mission.imageBytes.size)
                    ?.asImageBitmap()
            }

            if (bitmap == null) {
                _loadError.value = "Failed to decode map image"
                Log.e(TAG, "BitmapFactory returned null")
                return@launch
            }

            _mapBitmap.value = bitmap
            _calibration.value = mission.calibration
            _currentMissionId.value = missionId

            Log.i(TAG, "Mission '${mission.missionId}' ready: " +
                    "${mission.calibration.imageWidth}x${mission.calibration.imageHeight}px, " +
                    "${mission.calibration.points.size} calibration points")
        }
    }

    /**
     * Call this after location permission has been granted.
     * Starts collecting GPS updates and feeding them into the state.
     */
    fun startLocationUpdates() {
        viewModelScope.launch {
            try {
                locationProvider.locationFlow().collect { gps ->
                    _gpsLocation.value = gps
                }
            } catch (e: SecurityException) {
                Log.e(TAG, "Location permission not granted: ${e.message}")
                _loadError.value = "Location permission required"
            }
        }
    }
}
