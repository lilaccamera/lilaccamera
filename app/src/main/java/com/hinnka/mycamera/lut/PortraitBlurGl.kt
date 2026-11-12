package com.hinnka.mycamera.lut

import android.opengl.GLES30
import com.hinnka.mycamera.preview.PortraitFace
import com.hinnka.mycamera.processor.LensBlurParams
import com.hinnka.mycamera.utils.PLog

/**
 * The Portrait viewfinder: a rough blur outside a sharp head-and-shoulders shape around each face, up to five.
 * Preview only; the saved photo gets its real blur after it is stored.
 *
 * Each sharp area is an ellipse for the head plus a wider shape below it for the shoulders and body, so the
 * frame stays readable; the photo itself is cut out by the subject mask, not by these shapes.
 *
 * The blur is built from the frame shrunk to a quarter of its size, so it costs two small passes and
 * one full-size composite. Each face eases toward its new position and fades in and out on its own, and the
 * effect fades with the faces, so the picture never jumps between detections.
 */
// Face size (square root of the face box's share of the frame) where the blur starts and where it is full.
// Around a smaller, distant face the blur would cover nearly the whole frame; 0.10 is about 1.7 m and
// 0.25 about 0.7 m on the back camera.
private const val BLUR_START_FACE = 0.10f
private const val BLUR_FULL_FACE = 0.25f

/** How much of the viewfinder blur to show for a face of [faceSize]: 0 when distant, 1 when close, smooth between. */
internal fun portraitBlurForFaceSize(faceSize: Float): Float {
    val t = ((faceSize - BLUR_START_FACE) / (BLUR_FULL_FACE - BLUR_START_FACE)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

internal class PortraitBlurGl(private val logTag: String) {
    @Volatile
    var targets: List<PortraitFace> = emptyList()

    // Follows the saved photo's blur for the chosen f-number: how much blur, and how far it spreads.
    @Volatile
    private var apertureAmount = 1f
    @Volatile
    private var apertureSpread = 1f

    fun setAperture(aperture: Float) {
        val params = LensBlurParams.forAperture(aperture)
        apertureAmount = params.blurAmount
        apertureSpread = params.radiusGain.coerceAtMost(MAX_APERTURE_SPREAD)
    }

    // One eased face per slot; a slot is free while its presence is 0.
    private val centerX = FloatArray(MAX_FACES)
    private val centerY = FloatArray(MAX_FACES)
    private val faceWidth = FloatArray(MAX_FACES)
    private val faceHeight = FloatArray(MAX_FACES)
    private val downX = FloatArray(MAX_FACES)
    private val downY = FloatArray(MAX_FACES) { 1f }
    private val presence = FloatArray(MAX_FACES)
    private var strength = 0f
    private val headUniform = FloatArray(MAX_FACES * 4)
    private val bodyUniform = FloatArray(MAX_FACES * 4)
    private val downUniform = FloatArray(MAX_FACES * 2)

    private var blurProgram = 0
    private var blurInput = -1
    private var blurStep = -1
    private var compositeProgram = 0
    private val composite = IntArray(COMPOSITE_UNIFORMS.size) { -1 }

    private val smallTexture = IntArray(2)
    private val smallFramebuffer = IntArray(2)
    private var smallWidth = 0
    private var smallHeight = 0
    private var outputTexture = 0
    private var outputFramebuffer = 0
    private var outputWidth = 0
    private var outputHeight = 0

    /** True while there is anything to draw; the renderer skips the whole effect otherwise. */
    fun isActive(): Boolean = targets.isNotEmpty() || strength > FADE_OUT_FLOOR

    /** Eases the faces and the fade one frame forward; returns false once the effect has faded out. */
    private fun advance(): Boolean {
        val faces = targets.take(MAX_FACES)
        val matched = BooleanArray(MAX_FACES)
        for (face in faces) {
            // The nearest visible slot is the same person; a new face takes a free slot and starts on itself.
            var slot = -1
            var best = Float.MAX_VALUE
            for (k in 0 until MAX_FACES) {
                if (matched[k] || presence[k] <= FADE_OUT_FLOOR) continue
                val distance = kotlin.math.hypot(centerX[k] - face.centerX, centerY[k] - face.centerY)
                if (distance < best) { best = distance; slot = k }
            }
            if (slot < 0) {
                slot = (0 until MAX_FACES).firstOrNull { !matched[it] && presence[it] <= FADE_OUT_FLOOR } ?: continue
                centerX[slot] = face.centerX; centerY[slot] = face.centerY
                faceWidth[slot] = face.width; faceHeight[slot] = face.height
                downX[slot] = face.downX; downY[slot] = face.downY
                // The first face comes in sharp with the blur; later ones sharpen out of it.
                presence[slot] = if (strength > FADE_OUT_FLOOR) 0f else 1f
            } else {
                centerX[slot] += (face.centerX - centerX[slot]) * FOLLOW
                centerY[slot] += (face.centerY - centerY[slot]) * FOLLOW
                faceWidth[slot] += (face.width - faceWidth[slot]) * FOLLOW
                faceHeight[slot] += (face.height - faceHeight[slot]) * FOLLOW
                downX[slot] += (face.downX - downX[slot]) * FOLLOW
                downY[slot] += (face.downY - downY[slot]) * FOLLOW
                val length = kotlin.math.hypot(downX[slot], downY[slot])
                if (length > 1e-3f) { downX[slot] /= length; downY[slot] /= length } else { downX[slot] = 0f; downY[slot] = 1f }
            }
            matched[slot] = true
            presence[slot] += (1f - presence[slot]) * FADE
        }
        for (k in 0 until MAX_FACES) {
            if (matched[k]) continue
            presence[k] -= presence[k] * FADE
            if (presence[k] <= FADE_OUT_FLOOR) presence[k] = 0f
        }
        if (faces.isNotEmpty()) {
            strength += (1f - strength) * FADE
        } else {
            strength -= strength * FADE
            if (strength <= FADE_OUT_FLOOR) strength = 0f
        }
        return strength > FADE_OUT_FLOOR
    }

    /** Returns the texture to show, or the source when nothing is drawn or a pass failed. */
    fun render(sourceTextureId: Int, width: Int, height: Int, drawQuad: (Int) -> Unit): Int {
        if (!advance()) return sourceTextureId
        // The closest face sets the blur, so distant faces alone leave the frame as it is.
        var faceBlur = 0f
        for (k in 0 until MAX_FACES) {
            if (presence[k] > 0f) {
                faceBlur = maxOf(faceBlur, portraitBlurForFaceSize(kotlin.math.sqrt(faceWidth[k] * faceHeight[k])))
            }
        }
        val blurAmount = faceBlur * apertureAmount
        if (blurAmount <= 0f) return sourceTextureId
        if (!ensureResources(width, height)) return sourceTextureId
        val upstreamError = GLES30.glGetError()
        if (upstreamError != GLES30.GL_NO_ERROR) {
            PLog.e(logTag, "Portrait blur skipped after upstream glError $upstreamError")
            return sourceTextureId
        }

        GLES30.glDisable(GLES30.GL_BLEND)
        GLES30.glUseProgram(blurProgram)
        val spread = apertureSpread
        // Two passes, across and down into the small texture: a light blur that keeps the frame readable.
        var input = sourceTextureId
        var inputWidth = width
        val passes = arrayOf(floatArrayOf(2f, 0f), floatArrayOf(0f, 1f))
        for ((index, direction) in passes.withIndex()) {
            val destination = index % 2
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, smallFramebuffer[destination])
            GLES30.glViewport(0, 0, smallWidth, smallHeight)
            GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, input)
            GLES30.glUniform1i(blurInput, 0)
            // The first pass reads the full-size frame, the others the small one.
            val texelX = 1f / (if (index == 0) inputWidth else smallWidth)
            val texelY = 1f / (if (index == 0) height else smallHeight)
            GLES30.glUniform2f(blurStep, direction[0] * texelX * spread, direction[1] * texelY * spread)
            drawQuad(blurProgram)
            input = smallTexture[destination]
            inputWidth = smallWidth
        }

        for (k in 0 until MAX_FACES) {
            if (presence[k] <= 0f) {
                // A free slot sits far outside the frame with a unit size, so the shader never divides by zero.
                headUniform.fill(FAR_AWAY, k * 4, k * 4 + 2); headUniform.fill(1f, k * 4 + 2, k * 4 + 4)
                bodyUniform.fill(FAR_AWAY * width, k * 4, k * 4 + 2); bodyUniform.fill(1f, k * 4 + 2, k * 4 + 4)
                downUniform[k * 2] = 0f; downUniform[k * 2 + 1] = -1f
                continue
            }
            // Texture space runs up, the viewfinder's face box runs down.
            val faceY = 1f - centerY[k]
            // The sharp area starts at an oval for the head.
            headUniform[k * 4] = centerX[k]
            headUniform[k * 4 + 1] = faceY
            headUniform[k * 4 + 2] = faceWidth[k] * HEAD_SCALE
            headUniform[k * 4 + 3] = faceHeight[k] * HEAD_SCALE
            // The body hangs below the head along the person's own down, in pixels so a tilt keeps its shape.
            val faceSizePx = kotlin.math.sqrt(faceWidth[k] * width * faceHeight[k] * height)
            bodyUniform[k * 4] = centerX[k] * width + downX[k] * faceSizePx * BODY_DROP_FACES
            bodyUniform[k * 4 + 1] = faceY * height - downY[k] * faceSizePx * BODY_DROP_FACES
            bodyUniform[k * 4 + 2] = faceSizePx * BODY_WIDTH_FACES
            bodyUniform[k * 4 + 3] = faceSizePx * BODY_HEIGHT_FACES
            downUniform[k * 2] = downX[k]
            downUniform[k * 2 + 1] = -downY[k]
        }
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, outputFramebuffer)
        GLES30.glViewport(0, 0, width, height)
        GLES30.glUseProgram(compositeProgram)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, sourceTextureId)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE1)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, input)
        GLES30.glUniform1i(composite[U_SHARP], 0)
        GLES30.glUniform1i(composite[U_BLUR], 1)
        GLES30.glUniform4fv(composite[U_HEAD], MAX_FACES, headUniform, 0)
        GLES30.glUniform4fv(composite[U_BODY], MAX_FACES, bodyUniform, 0)
        GLES30.glUniform2fv(composite[U_DOWN], MAX_FACES, downUniform, 0)
        GLES30.glUniform1fv(composite[U_PRESENCE], MAX_FACES, presence, 0)
        GLES30.glUniform2f(composite[U_SIZE_PX], width.toFloat(), height.toFloat())
        GLES30.glUniform1f(composite[U_STRENGTH], strength)
        GLES30.glUniform1f(composite[U_BLUR_AMOUNT], blurAmount)
        GLES30.glUniform1f(composite[U_FEATHER], FEATHER)
        drawQuad(compositeProgram)
        val error = GLES30.glGetError()
        GLES30.glActiveTexture(GLES30.GL_TEXTURE1)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        GLES30.glUseProgram(0)
        if (error != GLES30.GL_NO_ERROR) {
            PLog.e(logTag, "Portrait blur pass failed: glError=$error")
            return sourceTextureId
        }
        return outputTexture
    }

    fun resetAfterContextLoss() {
        blurProgram = 0
        compositeProgram = 0
        smallTexture.fill(0)
        smallFramebuffer.fill(0)
        smallWidth = 0
        smallHeight = 0
        outputTexture = 0
        outputFramebuffer = 0
        outputWidth = 0
        outputHeight = 0
    }

    fun release() {
        GlUtils.deleteProgram(blurProgram)
        GlUtils.deleteProgram(compositeProgram)
        blurProgram = 0
        compositeProgram = 0
        releaseTargets()
    }

    private fun ensureResources(width: Int, height: Int): Boolean {
        if (blurProgram == 0 || compositeProgram == 0) {
            if (!buildPrograms()) return false
        }
        val wantSmallWidth = (width / SMALL_DIVISOR).coerceAtLeast(1)
        val wantSmallHeight = (height / SMALL_DIVISOR).coerceAtLeast(1)
        if (outputFramebuffer != 0 && outputWidth == width && outputHeight == height &&
            smallWidth == wantSmallWidth && smallHeight == wantSmallHeight
        ) {
            return true
        }
        val maxTextureSize = IntArray(1)
        GLES30.glGetIntegerv(GLES30.GL_MAX_TEXTURE_SIZE, maxTextureSize, 0)
        if (width <= 0 || height <= 0 || width > maxTextureSize[0] || height > maxTextureSize[0]) {
            PLog.e(logTag, "Portrait blur target ${width}x$height exceeds GL_MAX_TEXTURE_SIZE=${maxTextureSize[0]}")
            return false
        }
        releaseTargets()
        for (i in 0..1) {
            if (!createTarget(wantSmallWidth, wantSmallHeight, smallTexture, smallFramebuffer, i)) {
                releaseTargets()
                return false
            }
        }
        smallWidth = wantSmallWidth
        smallHeight = wantSmallHeight
        val output = IntArray(1)
        val outputFbo = IntArray(1)
        if (!createTarget(width, height, output, outputFbo, 0)) {
            releaseTargets()
            return false
        }
        outputTexture = output[0]
        outputFramebuffer = outputFbo[0]
        outputWidth = width
        outputHeight = height
        return true
    }

    private fun createTarget(width: Int, height: Int, textures: IntArray, framebuffers: IntArray, index: Int): Boolean {
        val id = IntArray(1)
        GLES30.glGenTextures(1, id, 0)
        textures[index] = id[0]
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textures[index])
        GLES30.glTexStorage2D(GLES30.GL_TEXTURE_2D, 1, GLES30.GL_RGBA8, width, height)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glGenFramebuffers(1, id, 0)
        framebuffers[index] = id[0]
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, framebuffers[index])
        GLES30.glFramebufferTexture2D(
            GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0, GLES30.GL_TEXTURE_2D, textures[index], 0,
        )
        val status = GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER)
        val error = GLES30.glGetError()
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
        if (status != GLES30.GL_FRAMEBUFFER_COMPLETE || error != GLES30.GL_NO_ERROR) {
            PLog.e(logTag, "Portrait blur framebuffer incomplete: status=$status glError=$error")
            return false
        }
        return true
    }

    private fun releaseTargets() {
        for (i in 0..1) {
            if (smallFramebuffer[i] != 0) GLES30.glDeleteFramebuffers(1, intArrayOf(smallFramebuffer[i]), 0)
            if (smallTexture[i] != 0) GLES30.glDeleteTextures(1, intArrayOf(smallTexture[i]), 0)
            smallFramebuffer[i] = 0
            smallTexture[i] = 0
        }
        if (outputFramebuffer != 0) GLES30.glDeleteFramebuffers(1, intArrayOf(outputFramebuffer), 0)
        if (outputTexture != 0) GLES30.glDeleteTextures(1, intArrayOf(outputTexture), 0)
        outputFramebuffer = 0
        outputTexture = 0
        smallWidth = 0
        smallHeight = 0
        outputWidth = 0
        outputHeight = 0
    }

    private fun buildPrograms(): Boolean {
        blurProgram = link("blur", BLUR_FRAGMENT)
        compositeProgram = link("composite", COMPOSITE_FRAGMENT)
        if (blurProgram == 0 || compositeProgram == 0) {
            release()
            return false
        }
        blurInput = GLES30.glGetUniformLocation(blurProgram, "uInputTexture")
        blurStep = GLES30.glGetUniformLocation(blurProgram, "uStep")
        for (i in COMPOSITE_UNIFORMS.indices) {
            composite[i] = GLES30.glGetUniformLocation(compositeProgram, COMPOSITE_UNIFORMS[i])
        }
        return true
    }

    private fun link(name: String, fragmentSource: String): Int {
        val vertexShader = GlUtils.compileShader(GLES30.GL_VERTEX_SHADER, Shaders.SIMPLE_VERTEX_SHADER)
        val fragmentShader = GlUtils.compileShader(GLES30.GL_FRAGMENT_SHADER, fragmentSource)
        if (vertexShader == 0 || fragmentShader == 0) {
            if (vertexShader != 0) GLES30.glDeleteShader(vertexShader)
            if (fragmentShader != 0) GLES30.glDeleteShader(fragmentShader)
            PLog.e(logTag, "Failed to compile portrait blur $name shaders")
            return 0
        }
        val program = GlUtils.linkProgram(vertexShader, fragmentShader)
        GLES30.glDeleteShader(vertexShader)
        GLES30.glDeleteShader(fragmentShader)
        if (program == 0) PLog.e(logTag, "Failed to link portrait blur $name program")
        return program
    }

    private companion object {
        // Also in the composite shader's loop.
        const val MAX_FACES = 5
        const val FAR_AWAY = -100f
        const val SMALL_DIVISOR = 4
        // How fast the face and the fade follow, per frame.
        const val FOLLOW = 0.3f
        const val FADE = 0.18f
        // Wider spacing of the nine blur taps would break the blur into separate copies.
        const val MAX_APERTURE_SPREAD = 2f
        const val FADE_OUT_FLOOR = 0.01f
        // The blur reaches full strength this far outside the sharp shapes, in their radii.
        const val FEATHER = 0.3f
        // The body below the head, in face sizes: centre below the face centre, half width and half height.
        // Its top overlaps the chin; its sides reach about one and a half face widths, the shoulders.
        const val BODY_DROP_FACES = 1.9f
        const val BODY_WIDTH_FACES = 1.5f
        const val BODY_HEIGHT_FACES = 1.5f
        // The head oval reaches a little outside the face box.
        const val HEAD_SCALE = 0.62f

        const val U_SHARP = 0
        const val U_BLUR = 1
        const val U_HEAD = 2
        const val U_BODY = 3
        const val U_DOWN = 4
        const val U_PRESENCE = 5
        const val U_SIZE_PX = 6
        const val U_STRENGTH = 7
        const val U_FEATHER = 8
        const val U_BLUR_AMOUNT = 9
        val COMPOSITE_UNIFORMS = arrayOf(
            "uSharpTexture", "uBlurTexture", "uHead", "uBody", "uDown", "uPresence",
            "uSizePx", "uStrength", "uFeather", "uBlurAmount",
        )

        // Nine taps of a gaussian along one direction; the step is in texels of the pass's input.
        val BLUR_FRAGMENT = """
            #version 300 es
            precision highp float;
            uniform highp sampler2D uInputTexture;
            uniform vec2 uStep;
            in vec2 vTexCoord;
            out vec4 fragColor;
            void main() {
                vec3 sum = texture(uInputTexture, vTexCoord).rgb * 0.2270270270;
                sum += (texture(uInputTexture, vTexCoord + uStep * 1.3846153846).rgb +
                        texture(uInputTexture, vTexCoord - uStep * 1.3846153846).rgb) * 0.3162162162;
                sum += (texture(uInputTexture, vTexCoord + uStep * 3.2307692308).rgb +
                        texture(uInputTexture, vTexCoord - uStep * 3.2307692308).rgb) * 0.0702702703;
                fragColor = vec4(sum, 1.0);
            }
        """.trimIndent()

        val COMPOSITE_FRAGMENT = """
            #version 300 es
            precision highp float;
            uniform highp sampler2D uSharpTexture;
            uniform highp sampler2D uBlurTexture;
            uniform vec4 uHead[5]; // centre, radii; texture space
            uniform vec4 uBody[5]; // centre, radii across and along down; pixels
            uniform vec2 uDown[5]; // unit, texture space
            uniform float uPresence[5];
            uniform vec2 uSizePx;
            uniform float uStrength;
            uniform float uBlurAmount;
            uniform float uFeather;
            in vec2 vTexCoord;
            out vec4 fragColor;
            void main() {
                vec3 sharp = texture(uSharpTexture, vTexCoord).rgb;
                vec3 blurred = texture(uBlurTexture, vTexCoord).rgb;
                float keep = 0.0;
                for (int i = 0; i < 5; i++) {
                    vec2 body = vTexCoord * uSizePx - uBody[i].xy;
                    vec2 down = uDown[i];
                    vec2 bodyAxes = vec2(dot(body, vec2(down.y, -down.x)), dot(body, down)) / uBody[i].zw;
                    float r = min(length((vTexCoord - uHead[i].xy) / uHead[i].zw), length(bodyAxes));
                    keep = max(keep, uPresence[i] * (1.0 - smoothstep(1.0, 1.0 + uFeather, r)));
                }
                fragColor = vec4(mix(sharp, blurred, (1.0 - keep) * uStrength * uBlurAmount), 1.0);
            }
        """.trimIndent()
    }
}
