package com.eazpire.creator.ar.poster

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager

/**
 * Pre-warm gyro/accel sensors before ARCore session resume (Samsung crash workaround).
 */
object ArCoreSensorWarmup {
    private var listener: SensorEventListener? = null
    private var sensorManager: SensorManager? = null

    fun start(context: Context) {
        if (listener != null) return
        val manager = context.applicationContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val noop = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) = Unit
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        val sensors = listOfNotNull(
            manager.getDefaultSensor(Sensor.TYPE_GYROSCOPE_UNCALIBRATED),
            manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER_UNCALIBRATED),
        )
        sensors.forEach { sensor ->
            manager.registerListener(noop, sensor, SensorManager.SENSOR_DELAY_FASTEST)
        }
        if (sensors.isNotEmpty()) {
            listener = noop
            sensorManager = manager
        }
    }

    fun stop() {
        val manager = sensorManager ?: return
        listener?.let { manager.unregisterListener(it) }
        listener = null
        sensorManager = null
    }
}
