package com.example.missiontrackermap.model

import kotlinx.serialization.Serializable

@Serializable
data class PixelCoordinate(
    val x: Double,
    val y: Double
)

@Serializable
data class GpsCoordinate(
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float? = null
)

@Serializable
data class CalibrationPoint(
    val name: String,
    val pixel: PixelCoordinate,
    val gps: GpsCoordinate
)

@Serializable
data class CalibrationData(
    val version: Int,
    val image: String,
    val imageWidth: Int,
    val imageHeight: Int,
    val points: List<CalibrationPoint>,
    val selectedPoint: String? = null
)
