package com.hinnka.mycamera.lut

import android.opengl.GLES30
import com.hinnka.mycamera.preview.PortraitFace
import com.hinnka.mycamera.utils.PLog

/**
 * The Portrait viewfinder: a rough blur outside an ellipse around the tracked face, and a thin outline
 * of the face itself. Preview only; the saved photo gets its real blur after it is stored.
 *
 * The blur is built from the frame shrunk to a quarter of its size, so it costs four small passes and
 * one full-size composite. The face eases toward each new detection, and the effect fades in and out
 * with it, so the picture never jumps between the few detections made each second.
 */
internal class PortraitBlurGl(
    private val logTag: String,
    private val outlineWidthPx: Float,
) {
    @Volatile
    var target: PortraitFace? = null

    private var centerX = 0.5f
    private var centerY = 0.5f
    private var faceWidth = 0.3f
    private var faceHeight = 0.4f
    private var strength = 0f

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
    fun isActive(): Boolean = target != null || strength > FADE_OUT_FLOOR

    /** Eases the face and the fade one frame forward; returns false once the effect has faded out. */
    private fun advance(): Boolean {
        val face = target
        if (face != null) {
            if (strength <= FADE_OUT_FLOOR) {
                // Appearing: start on the face instead of sliding in from the old place.
                centerX = face.centerX; centerY = face.centerY
                faceWidth = face.width; faceHeight = face.height
            } else {
                centerX += (face.centerX - centerX) * FOLLOW
                centerY += (face.centerY - centerY) * FOLLOW
                faceWidth += (face.width - faceWidth) * FOLLOW
                faceHeight += (face.height - faceHeight) * FOLLOW
            }
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
        if (!ensureResources(width, height)) return sourceTextureId
        val upstreamError = GLES30.glGetError()
        if (upstreamError != GLES30.GL_NO_ERROR) {
            PLog.e(logTag, "Portrait blur skipped after upstream glError $upstreamError")
            return sourceTextureId
        }

        GLES30.glDisable(GLES30.GL_BLEND)
        GLES30.glUseProgram(blurProgram)
        // Four passes: across and down into the small texture, then once more with wider steps.
        var input = sourceTextureId
        var inputWidth = width
        val passes = arrayOf(
            floatArrayOf(2f, 0f), floatArrayOf(0f, 1f), floatArrayOf(1.5f, 0f), floatArrayOf(0f, 1.5f),
        )
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
            GLES30.glUniform2f(blurStep, direction[0] * texelX, direction[1] * texelY)
            drawQuad(blurProgram)
            input = smallTexture[destination]
            inputWidth = smallWidth
        }

        // Texture space runs up, the viewfinder's face box runs down.
        val faceY = 1f - centerY
        val sharpHalfWidth = faceWidth * SHARP_WIDTH_FACES
        val sharpHalfHeight = faceHeight * SHARP_HEIGHT_FACES
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, outputFramebuffer)
        GLES30.glViewport(0, 0, width, height)
        GLES30.glUseProgram(compositeProgram)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, sourceTextureId)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE1)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, input)
        GLES30.glUniform1i(composite[U_SHARP], 0)
        GLES30.glUniform1i(composite[U_BLUR], 1)
        GLES30.glUniform2f(composite[U_CENTER], centerX, faceY - faceHeight * SHARP_SHIFT_FACES)
        GLES30.glUniform2f(composite[U_RADIUS], sharpHalfWidth, sharpHalfHeight)
        GLES30.glUniform2f(composite[U_OUTLINE_CENTER], centerX, faceY)
        GLES30.glUniform2f(composite[U_OUTLINE_RADIUS], faceWidth * OUTLINE_SCALE, faceHeight * OUTLINE_SCALE)
        GLES30.glUniform2f(composite[U_SIZE_PX], width.toFloat(), height.toFloat())
        GLES30.glUniform1f(composite[U_STRENGTH], strength)
        GLES30.glUniform1f(composite[U_FEATHER], FEATHER)
        GLES30.glUniform1f(composite[U_OUTLINE_PX], outlineWidthPx)
        GLES30.glUniform4f(composite[U_OUTLINE_COLOR], OUTLINE_R, OUTLINE_G, OUTLINE_B, OUTLINE_ALPHA)
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
        const val SMALL_DIVISOR = 4
        // How fast the face and the fade follow, per frame.
        const val FOLLOW = 0.3f
        const val FADE = 0.18f
        const val FADE_OUT_FLOOR = 0.01f
        // The sharp ellipse, in half-faces: a head and shoulders around the detected face.
        const val SHARP_WIDTH_FACES = 1.9f
        const val SHARP_HEIGHT_FACES = 2.6f
        // Moved down so it takes in the neck and shoulders.
        const val SHARP_SHIFT_FACES = 0.45f
        const val FEATHER = 0.55f
        // The outline hugs the face a little outside its box.
        const val OUTLINE_SCALE = 0.62f
        const val OUTLINE_R = 0.937f
        const val OUTLINE_G = 0.624f
        const val OUTLINE_B = 0.153f
        const val OUTLINE_ALPHA = 0.9f

        const val U_SHARP = 0
        const val U_BLUR = 1
        const val U_CENTER = 2
        const val U_RADIUS = 3
        const val U_OUTLINE_CENTER = 4
        const val U_OUTLINE_RADIUS = 5
        const val U_SIZE_PX = 6
        const val U_STRENGTH = 7
        const val U_FEATHER = 8
        const val U_OUTLINE_PX = 9
        const val U_OUTLINE_COLOR = 10
        val COMPOSITE_UNIFORMS = arrayOf(
            "uSharpTexture", "uBlurTexture", "uCenter", "uRadius", "uOutlineCenter", "uOutlineRadius",
            "uSizePx", "uStrength", "uFeather", "uOutlinePx", "uOutlineColor",
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
            uniform vec2 uCenter;
            uniform vec2 uRadius;
            uniform vec2 uOutlineCenter;
            uniform vec2 uOutlineRadius;
            uniform vec2 uSizePx;
            uniform float uStrength;
            uniform float uFeather;
            uniform float uOutlinePx;
            uniform vec4 uOutlineColor;
            in vec2 vTexCoord;
            out vec4 fragColor;
            void main() {
                vec3 sharp = texture(uSharpTexture, vTexCoord).rgb;
                vec3 blurred = texture(uBlurTexture, vTexCoord).rgb;
                float r = length((vTexCoord - uCenter) / uRadius);
                float outside = smoothstep(1.0, 1.0 + uFeather, r);
                vec3 color = mix(sharp, blurred, outside * uStrength);
                // Distance in pixels to the outline's ellipse, from the implicit function and its gradient.
                vec2 q = (vTexCoord - uOutlineCenter) / uOutlineRadius;
                float f = dot(q, q) - 1.0;
                vec2 gradient = 2.0 * q / uOutlineRadius / uSizePx;
                float distancePx = abs(f) / max(length(gradient), 1e-6);
                float line = 1.0 - smoothstep(uOutlinePx * 0.5 - 0.5, uOutlinePx * 0.5 + 0.5, distancePx);
                color = mix(color, uOutlineColor.rgb, line * uOutlineColor.a * uStrength);
                fragColor = vec4(color, 1.0);
            }
        """.trimIndent()
    }
}
