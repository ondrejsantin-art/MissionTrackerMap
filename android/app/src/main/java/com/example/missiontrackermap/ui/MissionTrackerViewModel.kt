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
        loadMission()
    }

    /** Loads the default mission from assets in a background coroutine. */
    private fun loadMission() {
        viewModelScope.launch(Dispatchers.IO) {
            val mission = repository.loadDefaultMission()
            if (mission == null) {
                _loadError.value = "Failed to load mission"
                Log.e(TAG, "Mission load returned null")
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
