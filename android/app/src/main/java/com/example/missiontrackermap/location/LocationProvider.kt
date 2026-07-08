package com.example.missiontrackermap.location

import com.example.missiontrackermap.model.GpsCoordinate
import kotlinx.coroutines.flow.Flow

/**
 * Provides a continuous stream of GPS coordinates.
 * Implementations may use FusedLocationProvider, mock data for testing, etc.
 */
interface LocationProvider {
    /**
     * Returns a cold Flow that emits the device's GPS position whenever it changes.
     * The Flow completes when the coroutine scope is cancelled.
     */
    fun locationFlow(): Flow<GpsCoordinate>
}
