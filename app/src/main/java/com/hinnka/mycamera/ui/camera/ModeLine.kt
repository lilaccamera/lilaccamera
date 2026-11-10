package com.hinnka.mycamera.ui.camera

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.SystemClock
import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseInOutCubic
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import com.hinnka.mycamera.R
import com.hinnka.mycamera.ui.icons.AppIcons
import com.hinnka.mycamera.ui.prefs.preferenceValue
import com.hinnka.mycamera.utils.VibrationHelper
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sin
import kotlin.random.Random

// Positions on the mode dial. Night and Portrait are listed but cannot be selected yet. Without Pro (the
// front camera has no RAW) Night and Portrait move up one place, next to Photo.
private const val NIGHT = 0
private const val PORTRAIT = 1
private const val PRO = 2
private const val PHOTO = 3
private const val VIDEO = 4

private const val SPACING = 70f          // dp between marks, the footer's column pitch
private const val TRAVEL = 44f           // dp of finger travel per mode: one slow swipe reaches every mode
private const val SWIPE_START = 16f      // dp of mostly sideways push before the list moves
private const val TAP_SLOP = 12f         // dp a neighbour tap may move
private const val FLICK = 0.35f          // dp per ms
private const val RESISTANCE = 0.3f      // past the first selectable mode and Video
private const val BUZZ_GAP = 60L         // ms of stillness between pulses; the vibrator runs pulses that come closer into one

// The work-in-progress note is shown this often per app run; after that the dim marks and the end bar say it.
private var wipToastsLeft = 2

private val ModeAmber = Color(0xFFEF9F27)
private val AutoGreen = Color(0xFF5DCAA5)
private val VideoRed = Color(0xFFE24B4A)
private val RidgeColor = Color(0xFF6B6A65)
private val EndBarColor = Color(0xFF9C9A92)

private fun CameraShootingMode.dialIndex() = when (this) {
    CameraShootingMode.PROFESSIONAL -> PRO
    CameraShootingMode.PHOTO -> PHOTO
    CameraShootingMode.VIDEO -> VIDEO
}

private fun shootingModeAt(index: Int) = when (index) {
    PRO -> CameraShootingMode.PROFESSIONAL
    VIDEO -> CameraShootingMode.VIDEO
    else -> CameraShootingMode.PHOTO
}

/** The list follows the finger slowly near a mode and quickly between modes, so each mode latches. */
private fun latch(r: Float): Float {
    val n = floor(r)
    val t = r - n
    return n + t - 0.8f * sin(2f * PI.toFloat() * t) / (2f * PI.toFloat())
}

private fun resist(p: Float, lo: Int) = when {
    p < lo -> lo + (p - lo) * RESISTANCE
    p > VIDEO -> VIDEO + (p - VIDEO) * RESISTANCE
    else -> p
}

private fun smoothstep(k: Float) = k * k * (3f - 2f * k)

/** Where the mode dial stands. Positions are dial indices; [nudge] is in pixels. */
@Stable
internal class ModeLineState(initial: CameraShootingMode) {
    /** The dial position under the shutter; fractional while it moves. */
    var pos by mutableFloatStateOf(initial.dialIndex().toFloat())
    /** The mode the shutter face and the name show: the nearest selectable detent. */
    var shown by mutableIntStateOf(initial.dialIndex())
    /** The last committed mode. */
    var mode by mutableIntStateOf(initial.dialIndex())
    /** The shutter's sideways give before a swipe starts. */
    var nudge by mutableFloatStateOf(0f)
    /** 0 closed, 1 open. */
    val lift = Animatable(0f)

    val shownMode: CameraShootingMode get() = shootingModeAt(shown)
    /** How far the side controls have made room; they move first, while the list opens. */
    val side: Float get() = smoothstep((lift.value / 0.7f).coerceAtMost(1f))

    internal var posJob: Job? = null
    internal var closeJob: Job? = null
    internal var pastEnd = false
    internal var wipSaid = false
    internal var buzzFreeAt = 0L
}

/**
 * The mode dial around the shutter: Night, Portrait, Pro, Photo, Video. Closed, the next mode sits faint on
 * each side; a deliberate sideways push opens the whole list, which latches on each mode. A tap on a
 * neighbour rolls the list there.
 */
@Composable
internal fun ModeLine(
    state: ModeLineState,
    selected: CameraShootingMode,
    proAvailable: Boolean,
    enabled: Boolean,
    recording: Boolean,
    videoProcessing: Boolean,
    onSelect: (CameraShootingMode) -> Unit,
    modifier: Modifier = Modifier,
    shutter: @Composable (Modifier) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val vibration = remember(context) { VibrationHelper(context) }
    val click = remember(context) { ModeClick(context.applicationContext) }
    DisposableEffect(click) { onDispose { click.release() } }
    val soundEnabled by preferenceValue { it.shutterSoundEnabled }
    val currentEnabled by rememberUpdatedState(enabled)
    val currentRecording by rememberUpdatedState(recording)
    // The first mode the dial can land on, and where each mark sits.
    val lo = if (proAvailable) PRO else PHOTO
    val currentLo by rememberUpdatedState(lo)
    fun slotOf(i: Int) = if (!proAvailable && i < PRO) i + 1 else i
    val currentOnSelect by rememberUpdatedState(onSelect)

    // A mode change from elsewhere (the front camera has no Pro, for example) moves the dial silently.
    val selectedIndex = selected.dialIndex()
    LaunchedEffect(selectedIndex) {
        if (selectedIndex != state.mode) {
            state.posJob?.cancel()
            state.mode = selectedIndex
            state.shown = selectedIndex
            state.pos = selectedIndex.toFloat()
        }
    }

    fun say(text: Int) = Toast.makeText(context, text, Toast.LENGTH_SHORT).show()

    fun sayWip() {
        state.wipSaid = true
        if (wipToastsLeft > 0) {
            wipToastsLeft--
            say(R.string.capture_mode_wip)
        }
    }

    // A fast swipe skips pulses rather than queue them into one long buzz.
    fun buzz(ms: Long) {
        val now = SystemClock.uptimeMillis()
        if (now < state.buzzFreeAt) return
        state.buzzFreeAt = now + ms + BUZZ_GAP
        vibration.vibrate(ms)
    }

    fun setPos(p: Float) {
        state.pos = p
        val first = currentLo
        val over = p < first - 0.08f || p > VIDEO + 0.08f
        if (over && !state.pastEnd) buzz(50L)
        state.pastEnd = over
        if (p < first - 0.15f && !state.wipSaid) sayWip()
        val detent = p.roundToInt().coerceIn(first, VIDEO)
        if (detent != state.shown) {
            state.shown = detent
            buzz(VibrationHelper.LIGHT_TICK_MS)
            if (soundEnabled) click.play()
        }
    }

    fun open() {
        state.closeJob?.cancel()
        scope.launch { state.lift.animateTo(1f, tween(220, easing = EaseOutCubic)) }
    }

    fun close(afterMs: Long) {
        state.closeJob?.cancel()
        state.closeJob = scope.launch {
            delay(afterMs)
            state.lift.animateTo(0f, tween(380, easing = EaseInOutCubic))
        }
    }

    fun roll(target: Int, durationMs: Int, then: () -> Unit) {
        state.posJob?.cancel()
        state.posJob = scope.launch {
            animate(state.pos, target.toFloat(), animationSpec = tween(durationMs, easing = EaseOutCubic)) { v, _ -> setPos(v) }
            then()
        }
    }

    fun settleMs(target: Int) = (160 + 70 * abs(target - state.pos)).toInt()

    fun commit(target: Int, closeAfterMs: Long) {
        if (target != state.mode) {
            state.mode = target
            currentOnSelect(shootingModeAt(target))
        }
        close(closeAfterMs)
    }

    fun tapNeighbour(x: Float) {
        val k = sign(x).toInt()
        val target = state.mode + k
        if (abs(abs(x) - SPACING) >= 28f || target !in NIGHT..VIDEO) return
        if (target < currentLo) {
            sayWip()
        } else {
            // The whole list plays out, which also shows that it can be swiped.
            open()
            roll(target, 380) { commit(target, 420) }
        }
    }

    val names = listOf(
        "",
        stringResource(R.string.capture_mode_portrait),
        stringResource(R.string.capture_mode_professional),
        stringResource(R.string.capture_mode_photo),
        stringResource(R.string.capture_mode_video)
    )

    Box(
        modifier = modifier.drawBehind {
            val lift = state.lift.value
            if (lift == 0f) return@drawBehind
            val cx = size.width / 2
            val cy = size.height / 2
            val unit = SPACING.dp.toPx()
            val bottom = cy + (CameraControlsLayoutDefaults.ModeZoneHeight / 2 + CameraControlsLayoutDefaults.BottomPadding).toPx()
            // A soft backdrop instead of a band of its own: it only shows over a picture reaching the footer.
            val backdropTop = cy - 110.dp.toPx()
            drawRect(
                Brush.verticalGradient(
                    0f to Color.Transparent,
                    0.45f to Color.Black.copy(alpha = 0.72f),
                    startY = backdropTop,
                    endY = bottom
                ),
                topLeft = Offset(0f, backdropTop),
                size = Size(size.width, bottom + 48.dp.toPx() - backdropTop),
                alpha = lift
            )
            // Ridges along the bottom edge, four per mode, travel with the list.
            for (k in 0 until 24) {
                val offsetDp = (k / 4f - 0.5f - state.pos) * SPACING
                val fade = (1f - (offsetDp / 185f).let { it * it }).coerceIn(0f, 1f)
                val x = cx + offsetDp.dp.toPx()
                drawLine(
                    RidgeColor, Offset(x, bottom - 10.dp.toPx()), Offset(x, bottom),
                    strokeWidth = 1.5.dp.toPx(), alpha = lift * 0.8f * fade
                )
            }
            // End bars just outside the first selectable mode and Video: the list stops there.
            for (end in floatArrayOf(lo - 0.5f, VIDEO + 0.62f)) {
                val x = cx + (end - state.pos) * unit
                drawRoundRect(
                    EndBarColor,
                    topLeft = Offset(x - 1.25.dp.toPx(), cy - 15.dp.toPx()),
                    size = Size(2.5.dp.toPx(), 30.dp.toPx()),
                    cornerRadius = CornerRadius(1.25.dp.toPx()),
                    alpha = lift
                )
            }
        },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(CameraControlsLayoutDefaults.ModeZoneWidth, CameraControlsLayoutDefaults.ModeZoneHeight)
                .pointerInput(Unit) {
                    val start = SWIPE_START.dp.toPx()
                    val travel = TRAVEL.dp.toPx()
                    val give = 6.dp.toPx()
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val centre = size.width / 2f
                        val onShutter = abs(down.position.x - centre) < CameraControlsLayoutDefaults.CaptureButtonSize.toPx() / 2
                        val tracker = VelocityTracker().apply { addPosition(down.uptimeMillis, down.position) }
                        var swiping = false
                        // The shutter's long press owns the press, or a locked dial already answered it.
                        var ignored = false
                        var shift = 0f
                        var p0 = 0f
                        var dx = 0f
                        var dy = 0f
                        while (true) {
                            val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            if (ignored) continue
                            tracker.addPosition(change.uptimeMillis, change.position)
                            dx = change.position.x - down.position.x
                            dy = change.position.y - down.position.y
                            if (!swiping) {
                                if (change.isConsumed) {
                                    ignored = true
                                    state.nudge = 0f
                                    continue
                                }
                                val push = abs(dx) > start && abs(dx) > 1.5f * abs(dy)
                                if (!currentEnabled) {
                                    if (push) {
                                        if (currentRecording) say(R.string.capture_mode_stop_recording)
                                        ignored = true
                                    }
                                    continue
                                }
                                if (!push) {
                                    // The shutter gives a little in the push's direction: it slides.
                                    if (onShutter) state.nudge = (dx * 0.3f).coerceIn(-give, give)
                                    continue
                                }
                                swiping = true
                                shift = sign(dx) * start
                                state.nudge = 0f
                                state.posJob?.cancel()
                                state.wipSaid = false
                                p0 = state.pos
                                open()
                            }
                            // Consuming the swipe also cancels the shutter's tap and long press.
                            change.consume()
                            setPos(resist(latch(p0 - (dx - shift) / travel), currentLo))
                        }
                        state.nudge = 0f
                        if (swiping) {
                            // A short flick moves one mode; a longer swipe ending in a flick carries on like a
                            // spun dial; a slow release settles on the nearest mode.
                            val v = -tracker.calculateVelocity().x / density / 1000f
                            val swipedDp = abs(dx - shift) / density
                            var target = state.pos.roundToInt()
                            if (abs(v) > FLICK) {
                                target = if (swipedDp < 1.2f * TRAVEL) {
                                    state.mode + sign(v).toInt()
                                } else {
                                    (state.pos + v * 220f / TRAVEL).roundToInt()
                                }
                            }
                            if (target < currentLo && !state.wipSaid) sayWip()
                            target = target.coerceIn(currentLo, VIDEO)
                            roll(target, settleMs(target)) { commit(target, 260) }
                        } else if (!ignored && !onShutter && currentEnabled &&
                            hypot(dx, dy) / density <= TAP_SLOP
                        ) {
                            tapNeighbour((down.position.x - centre) / density)
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            shutter(Modifier.graphicsLayer { translationX = state.nudge })
        }

        val dim = when {
            recording -> 0f
            videoProcessing -> 0.35f
            else -> 1f
        }
        for (i in NIGHT..VIDEO) {
            if (i == PRO && !proAvailable) continue
            val slot = slotOf(i)
            val neighbour = abs(slot - state.mode) == 1 && enabled
            val unavailable = slot < lo
            Box(
                modifier = Modifier
                    .offset {
                        val lift = state.lift.value
                        val k = slot - state.mode
                        val listX = (slot - state.pos) * SPACING
                        val closedX = if (abs(k) == 1) k * SPACING else listX
                        IntOffset(lerp(closedX, listX, lift).dp.roundToPx(), 0)
                    }
                    .graphicsLayer {
                        val lift = state.lift.value
                        val closedAlpha = if (abs(slot - state.mode) == 1) 0.6f else 0f
                        // Hidden while it passes behind the shutter.
                        val openAlpha = ((abs(slot - state.pos) * SPACING - 52f) / 18f).coerceIn(0f, 1f)
                        alpha = lerp(closedAlpha, openAlpha, lift) * (if (unavailable) 0.4f else 1f) * dim
                    }
                    .clearAndSetSemantics {
                        if (neighbour) {
                            role = Role.Button
                            contentDescription = names[i]
                            onClick {
                                tapNeighbour((slot - state.mode) * SPACING)
                                true
                            }
                        }
                    }
                    .autoRotate(),
                contentAlignment = Alignment.Center
            ) {
                ModeMark(i, current = i == state.shown)
            }
        }

        // The name takes the place above the shutter once the list is mostly open; it stays in screen axes.
        Text(
            text = names[state.shown].uppercase(),
            color = ModeAmber,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 2.sp,
            maxLines = 1,
            modifier = Modifier
                .offset(y = -(CameraControlsLayoutDefaults.CaptureButtonSize / 2 + 12.dp))
                .graphicsLayer { alpha = ((state.lift.value - 0.6f) / 0.4f).coerceIn(0f, 1f) }
        )
    }
}

/** A mark in the style of a camera's mode dial. */
@Composable
private fun ModeMark(index: Int, current: Boolean) {
    val color = if (current) ModeAmber else CameraControlsLayoutDefaults.MarkColor
    val icon = Modifier.size(24.dp)
    when (index) {
        NIGHT -> Icon(AppIcons.MoonStars, contentDescription = null, tint = color, modifier = icon)
        PORTRAIT -> Icon(AppIcons.User, contentDescription = null, tint = color, modifier = icon)
        // Dial markings, the same in every language.
        PRO -> Text("M", color = color, fontSize = 14.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.5.sp)
        PHOTO -> Text("AUTO", color = AutoGreen, fontSize = 10.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.2.sp)
        else -> Icon(AppIcons.MovieCamera, contentDescription = null, tint = VideoRed, modifier = icon)
    }
}

/** The dial click, cut from the shutter sound. Several streams keep the clicks of a fast swipe distinct. */
private class ModeClick(context: Context) {
    // The shutter sound's attributes, so the system mutes both alike.
    private val pool = SoundPool.Builder()
        .setMaxStreams(4)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()
    private val sound = pool.load(context, R.raw.mode_click, 1)
    private var lastMs = 0L

    fun play() {
        val now = SystemClock.uptimeMillis()
        if (now - lastMs < 30) return  // closer clicks would blur into one
        lastMs = now
        // Quiet, and slightly varied in pitch so a run of clicks does not sound like a machine.
        pool.play(sound, 0.35f, 0.35f, 1, 0, 1.05f * (0.97f + Random.nextFloat() * 0.06f))
    }

    fun release() = pool.release()
}
