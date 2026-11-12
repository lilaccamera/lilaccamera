package com.hinnka.mycamera.camera

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.SystemClock
import com.hinnka.mycamera.utils.PLog
import java.util.ArrayDeque
import kotlin.math.sqrt

class BurstGyroRecorder(context: Context) {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE_UNCALIBRATED)
        ?: sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private val samples = ArrayDeque<GyroSample>(MAX_SAMPLES)
    private val lock = Any()

    @Volatile
    private var recording = false

    private val listener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            if (!recording || event.values.size < 3) return
            val hasBias = event.sensor.type == Sensor.TYPE_GYROSCOPE_UNCALIBRATED && event.values.size >= 6
            val sample = GyroSample(
                timestampNs = event.timestamp,
                x = event.values[0] - if (hasBias) event.values[3] else 0f,
                y = event.values[1] - if (hasBias) event.values[4] else 0f,
                z = event.values[2] - if (hasBias) event.values[5] else 0f,
            )
            synchronized(lock) {
                while (samples.size >= MAX_SAMPLES) samples.removeFirst()
                samples.addLast(sample)
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    fun start(handler: Handler?): Boolean {
        if (recording) return true
        val gyro = sensor ?: run {
            PLog.w(TAG, "Burst Gyro unavailable")
            return false
        }
        synchronized(lock) { samples.clear() }
        recording = sensorManager.registerListener(
            listener,
            gyro,
            SensorManager.SENSOR_DELAY_FASTEST,
            handler,
        )
        if (!recording) PLog.w(TAG, "Failed to register burst Gyro listener")
        return recording
    }

    fun stop() {
        if (!recording) return
        recording = false
        sensorManager.unregisterListener(listener)
    }

    fun exposureWindow(startTimestampNs: Long, exposureTimeNs: Long): GyroExposureWindow {
        val safeExposureNs = exposureTimeNs.coerceAtLeast(0L)
        val endTimestampNs = startTimestampNs.saturatingAdd(safeExposureNs)
        if (safeExposureNs == 0L) {
            return GyroExposureWindow.unavailable(startTimestampNs, endTimestampNs)
        }
        val guardStart = (startTimestampNs - WINDOW_GUARD_NS).coerceAtLeast(0L)
        val guardEnd = endTimestampNs.saturatingAdd(WINDOW_GUARD_NS)
        val snapshot = synchronized(lock) {
            samples.filter { it.timestampNs in guardStart..guardEnd }
        }
        return GyroExposureWindowIntegrator.integrate(snapshot, startTimestampNs, endTimestampNs)
    }

    /**
     * Root-mean-square rotation speed in rad/s over [windowNs] ending [skipLatestNs] ago, or null with too
     * few samples. Skipping the latest moment leaves out the finger landing on the shutter.
     * The window's mean rate is taken out first: on a tripod the gyro reads a steady offset of about
     * 0.05 rad/s that is not motion, while hand tremor swings around zero.
     * shortcut: a steady slow pan is taken out too; fine while the shot asks the user to hold still.
     */
    fun recentAngularSpeed(windowNs: Long, skipLatestNs: Long = 0L): Float? {
        val until = SystemClock.elapsedRealtimeNanos() - skipLatestNs
        val since = until - windowNs
        val recent = synchronized(lock) { samples.filter { it.timestampNs in since..until } }
        if (recent.size < MIN_RECENT_SAMPLES) return null
        val mx = recent.sumOf { it.x.toDouble() } / recent.size
        val my = recent.sumOf { it.y.toDouble() } / recent.size
        val mz = recent.sumOf { it.z.toDouble() } / recent.size
        val variance = recent.sumOf {
            (it.x - mx) * (it.x - mx) + (it.y - my) * (it.y - my) + (it.z - mz) * (it.z - mz)
        } / recent.size
        PLog.d(
            TAG,
            "Recent gyro: offset=%.4f shake=%.4f rad/s samples=%d"
                .format(sqrt(mx * mx + my * my + mz * mz), sqrt(variance), recent.size),
        )
        return sqrt(variance).toFloat()
    }

    fun release() {
        stop()
        synchronized(lock) { samples.clear() }
    }

    private fun Long.saturatingAdd(value: Long): Long {
        return if (value > 0L && this > Long.MAX_VALUE - value) Long.MAX_VALUE else this + value
    }

    private companion object {
        const val TAG = "BurstGyroRecorder"
        const val MAX_SAMPLES = 8_192
        const val WINDOW_GUARD_NS = 2_000_000L
        const val MIN_RECENT_SAMPLES = 20
    }
}
