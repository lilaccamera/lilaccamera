package io.github.lilaccamera.sonybridge

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log

/**
 * Passes other apps' photo and video requests on to Lilac. Android offers a request that names no app only to camera
 * apps installed as system apps; this one is, so it takes the request and hands it, named, to Lilac's capture screen.
 * The caller's grant on its output file goes along, and Lilac's answer goes straight back to the caller.
 */
class CaptureForwardActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val lilac = servedLilac(this)
        val started = lilac != null && try {
            // The copy keeps the caller's ClipData and read/write grant flags, so the grant passes on to Lilac.
            startActivity(Intent(intent)
                .setComponent(ComponentName(lilac.packageName, LILAC_CAPTURE))
                .addFlags(Intent.FLAG_ACTIVITY_FORWARD_RESULT))
            true
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "forward to ${lilac.packageName} failed: $e")
            false
        } catch (e: SecurityException) {
            Log.w(TAG, "forward to ${lilac.packageName} failed: $e")
            false
        }
        if (!started) {
            setResult(RESULT_CANCELED)
            refresh(this)
        }
        finish()
    }

    companion object {
        private const val TAG = "LilacSonyBridge"
        private const val LILAC_CAPTURE = "com.hinnka.mycamera.CaptureActivity"

        /**
         * The Lilac this bridge serves: the stable app if installed and trusted, else the beta; null when neither.
         * shortcut: the stable app always wins when both are installed; make it a choice if beta users ask.
         */
        fun servedLilac(context: Context): ApplicationInfo? =
            listOf(LilacTrust.STABLE, LilacTrust.BETA).firstOrNull { LilacTrust.isTrusted(context, it) }
                ?.let { context.packageManager.getApplicationInfo(it, 0) }

        /**
         * Turns the forwarder on only while a trusted Lilac is installed as an ordinary app. A Lilac installed as a
         * system app answers these requests itself; forwarding too would list it twice.
         */
        fun refresh(context: Context) {
            val lilac = servedLilac(context)
            val on = lilac != null && lilac.flags and ApplicationInfo.FLAG_SYSTEM == 0
            context.packageManager.setComponentEnabledSetting(
                ComponentName(context, CaptureForwardActivity::class.java),
                if (on) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP,
            )
            // tools/bridge.sh check reads this line.
            Log.i(TAG, "forwarder ${if (on) "on" else "off"}, serves ${lilac?.packageName ?: "no Lilac"}" +
                (if (lilac != null && !on) " (a system app, answers requests itself)" else "") +
                ", interface ${BridgeService.INTERFACE_VERSION}")
        }
    }
}
