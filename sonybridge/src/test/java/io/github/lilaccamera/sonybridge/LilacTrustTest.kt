package io.github.lilaccamera.sonybridge

import io.github.lilaccamera.sonybridge.LilacTrust.isTrustedCaller
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LilacTrustTest {
    private val release = "CA67BBFA7DB7174346664DB24FAA924509A9523E95D0B19B8E193B8538108CAD"
    private val pinned = setOf(release)
    private val bridgeKey = "1111111111111111111111111111111111111111111111111111111111111111"
    private val otherKey = "2222222222222222222222222222222222222222222222222222222222222222"

    @Test fun releaseCertAllowedWhateverKeySignsTheBridge() {
        assertTrue(isTrustedCaller("io.github.lilaccamera", setOf(release), setOf(bridgeKey), pinned, trustOwnKey = false))
        assertTrue(isTrustedCaller("io.github.lilaccamera.beta", setOf(release), setOf(bridgeKey), pinned, trustOwnKey = true))
    }

    @Test fun unknownCertRefused() {
        assertFalse(isTrustedCaller("io.github.lilaccamera", setOf(otherKey), setOf(bridgeKey), pinned, trustOwnKey = true))
    }

    @Test fun pinnedCertUnderForeignPackageRefused() {
        assertFalse(isTrustedCaller("com.example.camera", setOf(release), setOf(bridgeKey), pinned, trustOwnKey = true))
        assertFalse(isTrustedCaller("io.github.lilaccameraX", setOf(release), setOf(bridgeKey), pinned, trustOwnKey = true))
    }

    @Test fun ownKeyOnlyWhenTrusted() {
        assertTrue(isTrustedCaller("io.github.lilaccamera.debug", setOf(bridgeKey), setOf(bridgeKey), pinned, trustOwnKey = true))
        assertFalse(isTrustedCaller("io.github.lilaccamera.debug", setOf(bridgeKey), setOf(bridgeKey), pinned, trustOwnKey = false))
    }

    @Test fun rotatedKeyAllowed() {
        // Signing history: the pinned release cert, then the key it was rotated to.
        assertTrue(isTrustedCaller("io.github.lilaccamera", listOf(release, otherKey), setOf(bridgeKey), pinned, trustOwnKey = false))
    }
}
