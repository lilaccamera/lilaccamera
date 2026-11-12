package com.hinnka.mycamera.ui.camera

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.hinnka.mycamera.camera.AmbientLightReader
import kotlinx.coroutines.delay

// The front viewfinder's false magenta goes at about 14 lux; the offer starts below 20 to leave a margin, and
// goes again only above 30, so it does not flicker at the edge.
private const val OfferBelowLux = 20f
private const val WithdrawAboveLux = 30f
private const val OfferAfterMs = 2_000L
/** Below this the light reading shows on the picture's top edge with the other marks. */
internal const val LuxMarkBelow = 30f

/** The light sensor's reading while [enabled] and the app is in front; null otherwise or before its first reading. */
@Composable
internal fun rememberAmbientLux(enabled: Boolean): Float? {
    val context = LocalContext.current
    var lux by remember { mutableStateOf<Float?>(null) }
    val reader = remember(context) { AmbientLightReader(context.applicationContext) { lux = it } }
    LifecycleResumeEffect(enabled, reader) {
        if (enabled) reader.start(null)
        onPauseOrDispose {
            reader.stop()
            lux = null
        }
    }
    return if (enabled) lux else null
}

/**
 * Photo mode's offer of Night in low light: it shows after 2 s below 20 lux and goes above 30 lux. A tap on the
 * picture or a shot puts it away until the camera reopens ([reset]).
 */
@Stable
internal class LowLightOffer {
    var offered by mutableStateOf(false)
        internal set
    var dismissed by mutableStateOf(false)
        private set

    fun dismiss() {
        if (offered) dismissed = true
    }

    fun reset() {
        dismissed = false
    }
}

@Composable
internal fun rememberLowLightOffer(lux: Float?, cameraKey: Any?): LowLightOffer {
    val offer = remember { LowLightOffer() }
    val dark = lux != null && lux < OfferBelowLux
    val bright = lux == null || lux > WithdrawAboveLux
    LaunchedEffect(dark) {
        if (dark) {
            delay(OfferAfterMs)
            offer.offered = true
        }
    }
    LaunchedEffect(bright) { if (bright) offer.offered = false }
    // A camera switch or the app coming back reopens the camera.
    LaunchedEffect(cameraKey, lux == null) { offer.reset() }
    return offer
}
