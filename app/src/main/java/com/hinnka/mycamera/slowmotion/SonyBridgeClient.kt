package com.hinnka.mycamera.slowmotion

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import com.hinnka.mycamera.camera.DeviceQuirks
import com.hinnka.mycamera.utils.PLog
import io.github.lilaccamera.sonybridge.ISonyBridge
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** What the ROM's Sony photo service offers for 960 fps slow motion on the rear camera. */
data class SlowMotionCapabilities(
    /** Video sizes (width, height) offered at 960 fps. */
    val sizes: List<Pair<Int, Int>>,
    /** Burst lengths in frames. */
    val frameNums: List<Int>,
    /** Sony's other video modes and their sizes (bridge version 3 and later; empty before). */
    val videoModes: List<SonyVideoMode> = emptyList(),
)

/** One of Sony's video modes at one size and rate, as the bridge's enterVideoMode takes it. */
data class SonyVideoMode(val mode: Int, val width: Int, val height: Int, val fps: Int) {
    companion object {
        const val NORMAL = 0 // also the high frame rates, chosen by fps
        const val STEADYSHOT = 1
        const val INTELLIGENT_ACTIVE = 2

        /** Sony's app uses this viewfinder size for every video mode; the spike confirmed it for each one. */
        const val PREVIEW_WIDTH = 1280
        const val PREVIEW_HEIGHT = 720

        /** Vendor session mode of Sony's own camera app for video (any size but VGA). */
        const val SESSION_MODE = 0x8003

        /** Parses the bridge's "w/h/fps" entries. */
        fun parse(mode: Int, entries: List<String>?): List<SonyVideoMode> = entries.orEmpty().mapNotNull { entry ->
            val p = entry.split("/").mapNotNull { it.toIntOrNull() }
            if (p.size == 3) SonyVideoMode(mode, p[0], p[1], p[2]) else null
        }
    }
}

/**
 * Binds to the privileged companion (the LilacSonyBridge system app) that owns Sony's native calls.
 * Slow motion is offered only on the lilac phone, with the companion installed and reporting 960 fps.
 */
class SonyBridgeClient(private val context: Context, private val onDied: () -> Unit = {}) {
    @Volatile var bridge: ISonyBridge? = null
        private set
    /** The bridge's interface version, read once on connect; a feature is offered only from the version that brought it. */
    @Volatile var version = 0
        private set
    private var connection: ServiceConnection? = null

    /** Binds and waits for the connection; false when the companion is missing or does not answer. */
    fun connect(timeoutMs: Long = 5_000): Boolean {
        if (bridge != null) return true
        val latch = CountDownLatch(1)
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                val b = ISonyBridge.Stub.asInterface(binder)
                version = runCatching { b.version() }.getOrDefault(0)
                bridge = b
                latch.countDown()
            }

            override fun onServiceDisconnected(name: ComponentName) {
                PLog.w(TAG, "Sony bridge disconnected")
                bridge = null
                onDied()
            }
        }
        val bound = try {
            context.bindService(Intent().setClassName(PACKAGE, "$PACKAGE.BridgeService"), connection, Context.BIND_AUTO_CREATE)
        } catch (e: SecurityException) {
            PLog.w(TAG, "Sony bridge bind refused: ${e.message}")
            false
        }
        if (!bound || !latch.await(timeoutMs, TimeUnit.MILLISECONDS)) {
            runCatching { context.unbindService(connection) }
            return false
        }
        this.connection = connection
        return true
    }

    fun disconnect() {
        bridge = null
        connection?.let { runCatching { context.unbindService(it) } }
        connection = null
    }

    companion object {
        private const val TAG = "SonyBridgeClient"
        const val PACKAGE = "io.github.lilaccamera.sonybridge"

        /** First interface version with each feature. */
        const val SLOW_MOTION_SINCE = 1
        const val VIDEO_MODES_SINCE = 3

        /** Whether the slow-motion feature could exist at all here; cheap, no binding. */
        fun isCompanionInstalled(context: Context): Boolean =
            DeviceQuirks.isLilac && try {
                context.packageManager.getPackageInfo(PACKAGE, 0)
                true
            } catch (_: PackageManager.NameNotFoundException) {
                false
            }

        /**
         * Asks the companion what the service supports (binds, asks, unbinds). Blocking: call off the main thread.
         * Null when this is not the lilac phone, the companion is missing, or it reports no 960 fps.
         */
        fun queryCapabilities(context: Context): SlowMotionCapabilities? {
            if (!isCompanionInstalled(context)) return null
            val client = SonyBridgeClient(context)
            try {
                if (!client.connect() || client.version < SLOW_MOTION_SINCE) return null
                val bundle = client.bridge?.capabilities ?: return null
                PLog.i(TAG, "Sony bridge capabilities: superSlow=${bundle.getBoolean("superSlow")} " +
                    "info=${bundle.getStringArrayList("info")} frameNums=${bundle.getIntegerArrayList("frameNums")} " +
                    "version=${client.version} highFps=${bundle.getStringArrayList("highFps")}")
                if (!bundle.getBoolean("superSlow")) return null
                // each entry is "width/height/fps"
                val sizes = bundle.getStringArrayList("info").orEmpty().mapNotNull { entry ->
                    val p = entry.split("/").mapNotNull { it.toIntOrNull() }
                    if (p.size == 3 && p[2] == SlowMotionSpec.FPS) p[0] to p[1] else null
                }.distinct()
                val videoModes = if (client.version < VIDEO_MODES_SINCE) emptyList() else
                    SonyVideoMode.parse(SonyVideoMode.NORMAL, bundle.getStringArrayList("highFps")) +
                        SonyVideoMode.parse(SonyVideoMode.STEADYSHOT, bundle.getStringArrayList("steadyShot")) +
                        SonyVideoMode.parse(SonyVideoMode.INTELLIGENT_ACTIVE, bundle.getStringArrayList("intelligentActive"))
                return SlowMotionCapabilities(sizes, bundle.getIntegerArrayList("frameNums").orEmpty().distinct(), videoModes)
            } catch (e: Exception) {
                PLog.w(TAG, "Sony bridge capability query failed: ${e.message}")
                return null
            } finally {
                client.disconnect()
            }
        }
    }
}
