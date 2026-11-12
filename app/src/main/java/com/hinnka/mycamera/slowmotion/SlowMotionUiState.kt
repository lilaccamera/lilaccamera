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
    /** The scene is too dark for the 1/960 s exposure cap. */
    val dark: Boolean = false,
)
