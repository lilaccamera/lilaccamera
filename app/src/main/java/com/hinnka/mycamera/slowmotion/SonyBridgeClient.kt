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
)

/**
 * Binds to the privileged companion (the LilacSonyBridge system app) that owns Sony's native calls.
 * Slow motion is offered only on the lilac phone, with the companion installed and reporting 960 fps.
 */
class SonyBridgeClient(private val context: Context, private val onDied: () -> Unit = {}) {
    @Volatile var bridge: ISonyBridge? = null
        private set
    private var connection: ServiceConnection? = null

    /** Binds and waits for the connection; false when the companion is missing or does not answer. */
    fun connect(timeoutMs: Long = 5_000): Boolean {
        if (bridge != null) return true
        val latch = CountDownLatch(1)
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                bridge = ISonyBridge.Stub.asInterface(binder)
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
                if (!client.connect()) return null
                val bundle = client.bridge?.capabilities ?: return null
                PLog.i(TAG, "Sony bridge capabilities: superSlow=${bundle.getBoolean("superSlow")} " +
                    "info=${bundle.getStringArrayList("info")} frameNums=${bundle.getIntegerArrayList("frameNums")}")
                if (!bundle.getBoolean("superSlow")) return null
                // each entry is "width/height/fps"
                val sizes = bundle.getStringArrayList("info").orEmpty().mapNotNull { entry ->
                    val p = entry.split("/").mapNotNull { it.toIntOrNull() }
                    if (p.size == 3 && p[2] == SlowMotionSpec.FPS) p[0] to p[1] else null
                }.distinct()
                return SlowMotionCapabilities(sizes, bundle.getIntegerArrayList("frameNums").orEmpty().distinct())
            } catch (e: Exception) {
                PLog.w(TAG, "Sony bridge capability query failed: ${e.message}")
                return null
            } finally {
                client.disconnect()
            }
        }
    }
}
