package com.hinnka.mycamera.raw

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RawPhotonHdrRatioInfoTest {
    @Test
    fun writeKeepsLimitAndWantedRatioFromTheSummary() {
        val summary = "stage=MGC_ML_AE_FINALIZE\nhdrRatioBeforeLimit=7.7070866\nfinalHdrRatio=7.7\neffectiveMaxHdrRatio=7.7\n"
        val props = RawPhotonHdrMetadata.write(emptyMap(), 7.7f, sceneExposureSummary = summary)
        assertEquals(7.7f, RawPhotonHdrMetadata.read(props)!!, 1e-4f)
        assertEquals(7.7f, RawPhotonHdrMetadata.readLimit(props)!!, 1e-4f)
        assertEquals(7.707f, RawPhotonHdrMetadata.readWanted(props)!!, 1e-3f)
        // A later write without a summary drops stale values instead of keeping another capture's.
        val rewritten = RawPhotonHdrMetadata.write(props, 4f)
        assertNull(RawPhotonHdrMetadata.readLimit(rewritten))
    }
}
