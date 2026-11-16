package com.hinnka.mycamera.slowmotion

/** What the screen needs to know about slow motion. */
data class SlowMotionUiState(
    /** Lilac phone, companion installed and 960 fps reported: the switch is offered. */
    val available: Boolean = false,
    /** The switch is on: the next video recording is a slow-motion recording. */
    val enabled: Boolean = false,
    /** A slow-motion recording is running (the camera key then fires bursts instead of stopping). */
    val active: Boolean = false,
    /** A burst may be fired now. */
    val canTrigger: Boolean = false,
    /** Bursts taken in the running recording, and how many one recording can hold. */
    val bursts: Int = 0,
    val maxBursts: Int = SlowMotionSpec.MAX_BURSTS,
    /** Counts the times the switch was turned off because the resolution was changed; the screen says why. */
    val resolutionNotice: Int = 0,
    /** The burst being captured: frames arrived so far, of [burstTotal]; then done for a moment, then none. */
    val burstPhase: BurstPhase = BurstPhase.NONE,
    val burstFrames: Int = 0,
    val burstTotal: Int = 0,
    /** The scene is too dark for the 1/960 s exposure cap. */
    val dark: Boolean = false,
)

enum class BurstPhase { NONE, CAPTURING, DONE }
