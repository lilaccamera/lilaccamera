package com.hinnka.mycamera.camera

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.PowerManager
import androidx.annotation.StringRes
import com.hinnka.mycamera.R
import java.io.File
import kotlin.math.roundToInt

/**
 * Heat check for video recording. The reading closest to its own stop point wins.
 *
 * - Camera sensor: Sony phones expose each camera sensor's own temperature as a thermal zone named
 *   `sony_camera_N`, readable by apps, only while that camera is open. Sony's thermal service tells
 *   its own camera app to close at 60 °C and kills the camera at 70 °C; recording past that kill can
 *   lose the video. On a 10-minute stabilized recording on the charger (Xperia XZ1 Compact, room at
 *   23 °C) the main sensor went from 46 to 60 °C.
 * - Phone (lilac only, board sensor `msm_therm` in tenths of °C): Sony's thermal service starts a
 *   30-minute camera limit at 46.9 °C and sends its stronger camera notice and heavy CPU cuts at 52 °C.
 * - Battery (all phones): charging is cut from 45 °C; the phone shuts down at 67 °C.
 * - Android's thermal status, on phones whose thermal HAL reports it.
 */
object CameraHeat {
    const val WARN_C = 59
    const val STOP_C = 65

    /**
     * Phone body (board or battery) temperature to wait for after a heat stop. While recording, the camera
     * sensor ran about 13 °C above the board (53 vs 40 °C, 60 vs 46.5 °C), and it cools within seconds once
     * the camera is off, so the body sets how soon it heats up again: from 42 °C it restarts near 55 °C.
     */
    const val COOLED_C = 42

    enum class Level { OK, WARN, STOP }
    enum class Source(@StringRes val label: Int) {
        CAMERA(R.string.heat_source_camera),
        PHONE(R.string.heat_source_phone),
        BATTERY(R.string.heat_source_battery),
    }

    private class Limits(val warnC: Float, val stopC: Float)

    private val limits = mapOf(
        Source.CAMERA to Limits(WARN_C.toFloat(), STOP_C.toFloat()),
        Source.PHONE to Limits(47f, 52f),
        Source.BATTERY to Limits(47f, 50f),
    )

    /** One reading against its own limits; [fraction] is 0 at the warning point and 1 at the stop point. */
    data class Gauge(val source: Source, val nowC: Int, val limitC: Int, val fraction: Float, val isHot: Boolean)

    private fun gauge(source: Source, nowC: Float): Gauge {
        val l = limits.getValue(source)
        return Gauge(
            source = source,
            nowC = nowC.roundToInt(),
            limitC = l.stopC.roundToInt(),
            fraction = (nowC - l.warnC) / (l.stopC - l.warnC),
            isHot = nowC >= l.warnC,
        )
    }

    data class Reading(val gauges: List<Gauge>, val thermalStatus: Int) {
        val level: Level
            get() = when {
                gauges.any { it.fraction >= 1f } || thermalStatus >= PowerManager.THERMAL_STATUS_CRITICAL -> Level.STOP
                gauges.any { it.isHot } || thermalStatus >= PowerManager.THERMAL_STATUS_SEVERE -> Level.WARN
                else -> Level.OK
            }

        /** The reading closest to its stop point, for the on-screen gauge and messages. */
        val closest: Gauge? get() = gauges.maxByOrNull { it.fraction }

        override fun toString() =
            gauges.joinToString(" ") { "${it.source}=${it.nowC}C" } + " thermalStatus=$thermalStatus"
    }

    private fun zoneTempFiles(matches: (String) -> Boolean): List<File> =
        File("/sys/class/thermal").listFiles { f -> f.name.startsWith("thermal_zone") }.orEmpty()
            .filter { zone -> runCatching { matches(File(zone, "type").readText().trim()) }.getOrDefault(false) }
            .map { File(it, "temp") }

    private fun File.readIntOrNull(): Int? = runCatching { readText().trim().toInt() }.getOrNull()

    private val cameraFiles: List<File> by lazy { zoneTempFiles { it.startsWith("sony_camera_") } }
    private val boardFile: File? by lazy {
        if (DeviceQuirks.isLilac) zoneTempFiles { it == "msm_therm" }.firstOrNull() else null
    }

    private fun cameraC(): Float? =
        cameraFiles.mapNotNull { file -> file.readIntOrNull()?.let { if (it > 1000) it / 1000 else it } }
            .maxOrNull()?.toFloat()

    private fun boardC(): Float? = boardFile?.readIntOrNull()?.let { it / 10f }

    private fun batteryC(context: Context): Float? =
        context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
            ?.takeIf { it != Int.MIN_VALUE }
            ?.let { it / 10f }

    fun read(context: Context): Reading {
        val gauges = listOfNotNull(
            cameraC()?.let { gauge(Source.CAMERA, it) },
            boardC()?.let { gauge(Source.PHONE, it) },
            batteryC(context)?.let { gauge(Source.BATTERY, it) },
        )
        val thermalStatus = (context.getSystemService(Context.POWER_SERVICE) as? PowerManager)?.currentThermalStatus
            ?: PowerManager.THERMAL_STATUS_NONE
        return Reading(gauges, thermalStatus)
    }

    /** A reading that works with the camera closed: the board sensor where there is one, else the battery. */
    fun readPhone(context: Context): Gauge? =
        boardC()?.let { gauge(Source.PHONE, it) } ?: batteryC(context)?.let { gauge(Source.BATTERY, it) }
}
