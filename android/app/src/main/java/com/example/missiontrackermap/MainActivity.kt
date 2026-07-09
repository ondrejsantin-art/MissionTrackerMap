package com.example.missiontrackermap

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Surface
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.rememberNavController
import com.example.missiontrackermap.ui.MissionTrackerApp
import com.example.missiontrackermap.ui.MissionTrackerTheme
import com.example.missiontrackermap.ui.MissionTrackerViewModel

private const val TAG = "MainActivity"

class MainActivity : ComponentActivity() {

    private var viewModelRef: MissionTrackerViewModel? = null

    // Location permission launcher
    private val requestLocationPermission = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false

        if (fineGranted || coarseGranted) {
            Log.i(TAG, "Location permission granted — starting GPS updates")
            viewModelRef?.startLocationUpdates()
        } else {
            Log.w(TAG, "Location permission denied — GPS dot will not be shown")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MissionTrackerTheme {
                Surface {
                    val navController = rememberNavController()
                    val viewModel: MissionTrackerViewModel = viewModel()
                    viewModelRef = viewModel

                    androidx.compose.runtime.LaunchedEffect(viewModel) {
                        if (ContextCompat.checkSelfPermission(
                                this@MainActivity,
                                Manifest.permission.ACCESS_FINE_LOCATION
                            ) == PackageManager.PERMISSION_GRANTED
                        ) {
                            Log.i(TAG, "Starting location updates from Compose LaunchedEffect")
                            viewModel.startLocationUpdates()
                        }
                    }

                    MissionTrackerApp(navController = navController, viewModel = viewModel)
                }
            }
        }

        // Request location permission — start GPS if already granted, else prompt
        checkAndRequestLocationPermission()
    }

    private fun checkAndRequestLocationPermission() {
        val fineGranted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (fineGranted) {
            Log.i(TAG, "Location permission already granted — starting GPS updates")
            // ViewModel is set in setContent above; safe to call after setContent
            viewModelRef?.startLocationUpdates()
        } else {
            Log.i(TAG, "Requesting location permission")
            requestLocationPermission.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }
}
