package com.hinnka.mycamera.processor

import org.junit.Assert.assertTrue
import org.junit.Test

class GlesYuvSpatialShadersTest {
    @Test
    fun outputShadersIncludeTheBrighteningFunction() {
        // Loading the object fails if a shader is built from a snippet declared below it.
        for (shader in listOf(GlesYuvSpatialShaders.lanczosOutput, GlesYuvSpatialShaders.lanczosFrameOutput)) {
            assertTrue(shader.contains("vec3 brightenOutput(vec3 rgb)"))
        }
    }
}
