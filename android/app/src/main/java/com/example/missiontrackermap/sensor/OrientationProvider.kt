package com.example.missiontrackermap.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

private const val TAG = "OrientationProvider"

class OrientationProvider(context: Context) {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    fun orientationFlow(): Flow<Float> = callbackFlow {
        Log.d(TAG, "Starting orientation sensor updates")
        val rotationVectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        
        val listener = object : SensorEventListener {
            private val rotationMatrix = FloatArray(9)
            private val orientationValues = FloatArray(3)
            
            // For fallback
            private var gravity: FloatArray? = null
            private var geomagnetic: FloatArray? = null

            // Low-pass filter (Exponential Moving Average) variables
            private var sinSum = 0f
            private var cosSum = 0f
            private var isFirst = true
            private val alpha = 0.15f // smoothing factor: lower is smoother but slower response

            override fun onSensorChanged(event: SensorEvent) {
                var headingRad: Float? = null

                if (event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
                    SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                    SensorManager.getOrientation(rotationMatrix, orientationValues)
                    headingRad = orientationValues[0] // rotation around Z axis
                } else {
                    if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                        gravity = event.values.clone()
                    } else if (event.sensor.type == Sensor.TYPE_MAGNETIC_FIELD) {
                        geomagnetic = event.values.clone()
                    }
                    
                    val grav = gravity
                    val geo = geomagnetic
                    if (grav != null && geo != null) {
                        val r = FloatArray(9)
                        val i = FloatArray(9)
                        if (SensorManager.getRotationMatrix(r, i, grav, geo)) {
                            SensorManager.getOrientation(r, orientationValues)
                            headingRad = orientationValues[0]
                        }
                    }
                }

                if (headingRad != null) {
                    // Smooth the angle using sin/cos to handle wrap-around at 360/0 degrees
                    val s = sin(headingRad)
                    val c = cos(headingRad)

                    if (isFirst) {
                        sinSum = s
                        cosSum = c
                        isFirst = false
                    } else {
                        sinSum += alpha * (s - sinSum)
                        cosSum += alpha * (c - cosSum)
                    }

                    val smoothedRad = atan2(sinSum, cosSum)
                    val headingDeg = (smoothedRad * 180f / kotlin.math.PI.toFloat() + 360f) % 360f
                    trySend(headingDeg)
                }
            }

            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}
        }

        if (rotationVectorSensor != null) {
            sensorManager.registerListener(listener, rotationVectorSensor, SensorManager.SENSOR_DELAY_UI)
        } else {
            Log.w(TAG, "Rotation Vector sensor not available, falling back to Accelerometer + Magnetometer")
            val accel = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            val mag = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
            if (accel != null) sensorManager.registerListener(listener, accel, SensorManager.SENSOR_DELAY_UI)
            if (mag != null) sensorManager.registerListener(listener, mag, SensorManager.SENSOR_DELAY_UI)
        }

        awaitClose {
            Log.d(TAG, "Stopping orientation sensor updates")
            sensorManager.unregisterListener(listener)
        }
    }
}
