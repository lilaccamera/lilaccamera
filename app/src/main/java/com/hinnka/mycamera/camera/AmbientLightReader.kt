package com.hinnka.mycamera.camera

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler

/** The phone's ambient light sensor: the latest reading in lux, also passed to [onLux] when given. */
class AmbientLightReader(context: Context, private val onLux: ((Float) -> Unit)? = null) {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val sensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)
    private var registered = false

    @Volatile var latestLux: Float? = null
        private set
    @Volatile var readings = 0
        private set

    private val listener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            latestLux = event.values.firstOrNull()
            readings++
            latestLux?.let { onLux?.invoke(it) }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    /** An on-change sensor reports its current value soon after registering, then on every change. */
    fun start(handler: Handler?) {
        if (registered || sensor == null) return
        latestLux = null
        readings = 0
        registered = sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL, handler)
    }

    fun stop() {
        if (!registered) return
        sensorManager.unregisterListener(listener)
        registered = false
    }
}
