package com.example.missiontrackermap.location

import android.annotation.SuppressLint
import android.content.Context
import android.os.Looper
import android.util.Log
import com.example.missiontrackermap.model.GpsCoordinate
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

private const val TAG = "FusedLocationProvider"
private const val LOCATION_INTERVAL_MS = 1000L
private const val LOCATION_FASTEST_INTERVAL_MS = 500L

private const val OVERRIDE_GPS = true
private const val OVERRIDE_LATITUDE = 50.8761064
private const val OVERRIDE_LONGITUDE = 15.1338606

/**
 * Real GPS implementation using Google Play Services FusedLocationProviderClient.
 *
 * Requires:
 *  - Manifest: ACCESS_FINE_LOCATION + ACCESS_COARSE_LOCATION
 *  - Runtime permission granted before calling locationFlow()
 */
class FusedLocationProvider(private val context: Context) : LocationProvider {

    private val fusedClient = LocationServices.getFusedLocationProviderClient(context)

    @SuppressLint("MissingPermission") // Permission is checked before this is called
    override fun locationFlow(): Flow<GpsCoordinate> = callbackFlow {
        if (OVERRIDE_GPS) {
            val coordinate = GpsCoordinate(
                latitude = OVERRIDE_LATITUDE,
                longitude = OVERRIDE_LONGITUDE
            )
            Log.d(TAG, "GPS OVERRIDE: lat=%.6f, lon=%.6f".format(coordinate.latitude, coordinate.longitude))
            trySend(coordinate)
            awaitClose {
                Log.d(TAG, "Stopping mock location updates")
            }
            return@callbackFlow
        }

        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            LOCATION_INTERVAL_MS
        )
            .setMinUpdateIntervalMillis(LOCATION_FASTEST_INTERVAL_MS)
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { location ->
                    val coordinate = GpsCoordinate(
                        latitude = location.latitude,
                        longitude = location.longitude
                    )
                    Log.d(TAG, "GPS: lat=%.6f, lon=%.6f, acc=%.1fm"
                        .format(coordinate.latitude, coordinate.longitude, location.accuracy))
                    trySend(coordinate)
                }
            }
        }

        fusedClient.requestLocationUpdates(
            locationRequest,
            callback,
            Looper.getMainLooper()
        )

        awaitClose {
            Log.d(TAG, "Stopping location updates")
            fusedClient.removeLocationUpdates(callback)
        }
    }
}
