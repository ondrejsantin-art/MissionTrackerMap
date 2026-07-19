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
import com.example.missiontrackermap.model.CalibrationPoint
import com.example.missiontrackermap.model.GpsCoordinate
import com.example.missiontrackermap.model.MissionProgress
import com.example.missiontrackermap.model.PixelCoordinate
import com.example.missiontrackermap.repository.CredentialManager
import com.example.missiontrackermap.repository.MissionTrackerRepository
import com.example.missiontrackermap.repository.MissionFileHelper
import com.example.missiontrackermap.repository.SupabaseAuthManager
import com.example.missiontrackermap.repository.SupabaseSyncManager
import com.example.missiontrackermap.sensor.OrientationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

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
    private val orientationProvider = OrientationProvider(application)
    private val credentialManager = CredentialManager(application)
    private val authManager = SupabaseAuthManager()

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

    private val _syncStatus = MutableStateFlow<String>("Idle")
    val syncStatus: StateFlow<String> = _syncStatus

    private val _remoteMissions = MutableStateFlow<Map<String, Int>>(emptyMap())
    val remoteMissions: StateFlow<Map<String, Int>> = _remoteMissions

    private val _localVersions = MutableStateFlow<Map<String, Int>>(emptyMap())
    val localVersions: StateFlow<Map<String, Int>> = _localVersions

    // --- Auth state ---
    private val _isLoggedIn = MutableStateFlow(false)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn

    private val _loginEmail = MutableStateFlow("")
    val loginEmail: StateFlow<String> = _loginEmail

    // --- Mission progress ---
    private val _completedPoints = MutableStateFlow<Set<String>>(emptySet())
    val completedPoints: StateFlow<Set<String>> = _completedPoints

    fun toggleMissionPoint(pointName: String) {
        val current = _completedPoints.value
        _completedPoints.value = if (pointName in current) {
            current - pointName
        } else {
            current + pointName
        }
        viewModelScope.launch(Dispatchers.IO) {
            repository.saveProgress(_currentMissionId.value, MissionProgress(_completedPoints.value))
        }
    }

    fun resetMission() {
        _completedPoints.value = emptySet()
        viewModelScope.launch(Dispatchers.IO) {
            repository.clearProgress(_currentMissionId.value)
        }
    }

    // --- GPS state ---
    private val _gpsLocation = MutableStateFlow<GpsCoordinate?>(null)
    val gpsLocation: StateFlow<GpsCoordinate?> = _gpsLocation

    val isGpsOverridden: StateFlow<Boolean> = locationProvider.isOverrideEnabled

    fun toggleGpsOverride() {
        locationProvider.isOverrideEnabled.value = !locationProvider.isOverrideEnabled.value
    }

    // --- Map rotation state ---
    private val _isMapRotationEnabled = MutableStateFlow(false)
    val isMapRotationEnabled: StateFlow<Boolean> = _isMapRotationEnabled

    fun toggleMapRotation() {
        _isMapRotationEnabled.value = !_isMapRotationEnabled.value
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val deviceHeading: StateFlow<Float> = _isMapRotationEnabled.flatMapLatest { enabled ->
        if (enabled) {
            orientationProvider.orientationFlow()
        } else {
            flowOf(0f)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0f)

    val compassHeading: StateFlow<Float> = orientationProvider.orientationFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0f)

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
        syncMissions()
        autoLogin()
    }

    /** Auto-login with saved credentials on startup. */
    private fun autoLogin() {
        val creds = credentialManager.getCredentials() ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val result = authManager.login(creds.first, creds.second)
            if (result.isSuccess) {
                _isLoggedIn.value = true
                _loginEmail.value = creds.first
                Log.i(TAG, "Auto-login successful")
            } else {
                Log.w(TAG, "Auto-login failed: ${result.exceptionOrNull()?.message}")
            }
        }
    }

    fun login(email: String, password: String): Result<String> {
        val result = authManager.login(email, password)
        if (result.isSuccess) {
            credentialManager.saveCredentials(email, password)
            _isLoggedIn.value = true
            _loginEmail.value = email
        }
        return result
    }

    fun logout() {
        authManager.logout()
        credentialManager.clearCredentials()
        _isLoggedIn.value = false
        _loginEmail.value = ""
    }

    fun syncMissions() {
        viewModelScope.launch(Dispatchers.IO) {
            _syncStatus.value = "Syncing"
            val syncManager = SupabaseSyncManager(getApplication())
            val remoteMap = try {
                syncManager.fetchRemoteMissions()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to fetch remote list", e)
                emptyMap()
            }
            _remoteMissions.value = remoteMap

            val success = syncManager.sync()
            if (success) {
                _syncStatus.value = "Success"
                refreshMissions()
                loadMission(_currentMissionId.value)
            } else {
                _syncStatus.value = "Error"
            }
        }
    }

    fun syncSingleMission(missionId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _syncStatus.value = "Syncing $missionId"
            val syncManager = SupabaseSyncManager(getApplication())
            val success = try {
                syncManager.downloadMission(missionId)
                true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to download $missionId", e)
                false
            }
            if (success) {
                _syncStatus.value = "Success"
                refreshMissions()
                val remoteMap = try {
                    syncManager.fetchRemoteMissions()
                } catch (e: Exception) {
                    emptyMap()
                }
                _remoteMissions.value = remoteMap

                if (_currentMissionId.value == missionId) {
                    loadMission(missionId)
                }
            } else {
                _syncStatus.value = "Error syncing $missionId"
            }
        }
    }

    fun getLocalMissionVersion(missionId: String): Int {
        return repository.getMissionVersion(missionId)
    }

    fun refreshMissions(fetchRemote: Boolean = false) {
        val list = repository.availableMissions()
        _availableMissions.value = list
        _localVersions.value = list.associateWith { repository.getMissionVersion(it) }

        if (fetchRemote) {
            viewModelScope.launch(Dispatchers.IO) {
                val syncManager = SupabaseSyncManager(getApplication())
                try {
                    val remoteMap = syncManager.fetchRemoteMissions()
                    _remoteMissions.value = remoteMap
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to check actual remote versions: ${e.message}", e)
                }
            }
        }
    }

    fun selectMission(missionId: String) {
        loadMission(missionId)
    }

    fun importMission(missionName: String, imageUri: android.net.Uri, jsonUri: android.net.Uri): Result<Unit> {
        return try {
            val context = getApplication<Application>()

            if (missionName.isBlank()) {
                return Result.failure(Exception("Mission name cannot be blank"))
            }
            if (isBuiltInMission(missionName)) {
                return Result.failure(Exception("A built-in mission with this name already exists"))
            }

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
            if (missionDir.exists()) {
                return Result.failure(Exception("A mission with this name already exists"))
            }
            if (!missionDir.mkdirs()) {
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

            // Load persisted progress for this mission
            val progress = repository.loadProgress(missionId)
            _completedPoints.value = progress.completedPoints

            Log.i(TAG, "Mission '${mission.missionId}' ready: " +
                    "${mission.calibration.imageWidth}x${mission.calibration.imageHeight}px, " +
                    "${mission.calibration.points.size} calibration points")
        }
    }

    fun isBuiltInMission(missionId: String): Boolean {
        val assetMissions = try {
            getApplication<Application>().assets.list("missions")?.toList() ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
        return assetMissions.contains(missionId)
    }

    fun isCloudMission(missionId: String): Boolean {
        val context = getApplication<Application>()
        val localMissionDir = File(File(context.filesDir, "missions"), missionId)
        return File(localMissionDir, ".cloud").exists()
    }

    fun renameMission(oldMissionId: String, newMissionId: String): Result<Unit> {
        if (newMissionId.isBlank()) {
            return Result.failure(Exception("New name cannot be blank"))
        }
        if (isBuiltInMission(oldMissionId) || isCloudMission(oldMissionId)) {
            return Result.failure(Exception("Cannot rename built-in or cloud mission"))
        }
        if (isBuiltInMission(newMissionId) || isCloudMission(newMissionId)) {
            return Result.failure(Exception("A built-in or cloud mission with this name already exists"))
        }
        val context = getApplication<Application>()
        val fileHelper = MissionFileHelper(File(context.filesDir, "missions"))
        val result = fileHelper.renameMission(oldMissionId, newMissionId)
        if (result.isSuccess) {
            if (_currentMissionId.value == oldMissionId) {
                loadMission(newMissionId)
            }
            refreshMissions()
        }
        return result
    }

    fun deleteMission(missionId: String): Result<Unit> {
        if (isBuiltInMission(missionId) || isCloudMission(missionId)) {
            return Result.failure(Exception("Cannot delete built-in or cloud mission"))
        }
        val context = getApplication<Application>()
        val fileHelper = MissionFileHelper(File(context.filesDir, "missions"))
        val result = fileHelper.deleteMission(missionId)
        if (result.isSuccess) {
            if (_currentMissionId.value == missionId) {
                loadMission("scarif")
            }
            refreshMissions()
        }
        return result
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

    // --- Mission editing ---

    /**
     * Returns true if the logged-in user is the author (owner) of this mission.
     */
    fun canEditMission(missionId: String): Boolean {
        if (!authManager.isAuthenticated) return false
        val syncManager = SupabaseSyncManager(getApplication())
        val ownerId = syncManager.getMissionOwnerId(missionId) ?: return false
        return ownerId == authManager.getUserId()
    }

    /**
     * Updates a single calibration point in memory and saves to local JSON.
     */
    fun updatePoint(index: Int, name: String, objective: String?, pixelX: Double, pixelY: Double) {
        val cal = _calibration.value ?: return
        if (index < 0 || index >= cal.points.size) return

        val oldPoint = cal.points[index]
        val newPoint = oldPoint.copy(
            name = name,
            missionObjective = objective,
            pixel = PixelCoordinate(x = pixelX, y = pixelY)
        )

        val newPoints = cal.points.toMutableList().apply { set(index, newPoint) }
        val updatedCal = cal.copy(points = newPoints)
        _calibration.value = updatedCal

        // Persist to local JSON
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            val fileHelper = MissionFileHelper(File(context.filesDir, "missions"))
            val result = fileHelper.saveCalibration(_currentMissionId.value, updatedCal)
            if (result.isFailure) {
                Log.e(TAG, "Failed to save calibration: ${result.exceptionOrNull()?.message}")
            }
        }
    }

    /**
     * Publishes the current mission's calibration data to Supabase.
     * Increments the version and PATCHes the json_data column.
     */
    fun publishMission(): Result<Unit> {
        if (!authManager.isAuthenticated) {
            return Result.failure(Exception("Not logged in"))
        }
        val cal = _calibration.value ?: return Result.failure(Exception("No mission loaded"))
        val missionId = _currentMissionId.value

        return try {
            val headers = authManager.getAuthHeaders()
            val localVersion = cal.version
            val newVersion = localVersion + 1
            val updatedCal = cal.copy(version = newVersion)

            val jsonEncoder = Json { ignoreUnknownKeys = true }
            val calibrationJson = jsonEncoder.encodeToString(CalibrationData.serializer(), updatedCal)

            val payload = """{"json_data": $calibrationJson, "version": $newVersion}"""

            val url = "${com.example.missiontrackermap.SupabaseConfig.URL}/rest/v1/missions?id=eq.$missionId"
            val client = OkHttpClient()
            val request = Request.Builder()
                .url(url)
                .apply {
                    headers.forEach { (k, v) -> addHeader(k, v) }
                }
                .addHeader("Content-Type", "application/json")
                .addHeader("Prefer", "return=representation")
                .patch(payload.toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    return Result.failure(Exception("Publish failed (HTTP ${response.code}): $body"))
                }
                val responseBody = response.body?.string() ?: "[]"
                if (responseBody == "[]" || responseBody.isBlank()) {
                    return Result.failure(Exception("No permission to update this mission"))
                }
            }

            // Update local state with new version
            _calibration.value = updatedCal
            val context = getApplication<Application>()
            val fileHelper = MissionFileHelper(File(context.filesDir, "missions"))
            fileHelper.saveCalibration(missionId, updatedCal)
            refreshMissions()

            Log.i(TAG, "Published mission '$missionId' as version $newVersion")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Publish error: ${e.message}", e)
            Result.failure(e)
        }
    }
}
