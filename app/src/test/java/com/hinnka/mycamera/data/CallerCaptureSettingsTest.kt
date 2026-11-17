package com.hinnka.mycamera.data

import com.hinnka.mycamera.video.CaptureMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The settings the capture screen for another app keeps in the stored settings, to put back on the next ordinary
 * start when the process was killed while it was open. UserPreferences itself needs Android, so only the stored
 * form is tested here.
 */
class CallerCaptureSettingsTest {
    @Test
    fun keptSettingsComeBackUnchanged() {
        val before = SettingsBeforeCaller(CaptureMode.VIDEO, true, false, true, "Kodak", "{\"grain\":0.4,\n\"name\":\"a\nb\"}")
        assertEquals(before, SettingsBeforeCaller.decode(before.encode()))
    }

    @Test
    fun noLookChosenStaysNoLookChosen() {
        val before = SettingsBeforeCaller(CaptureMode.PHOTO, false, true, false, null, "")
        assertEquals(before, SettingsBeforeCaller.decode(before.encode()))
    }

    @Test
    fun aValueThisCodeDidNotWriteIsDropped() {
        assertNull(SettingsBeforeCaller.decode("PHOTO\ntrue"))
        assertNull(SettingsBeforeCaller.decode("SLOWMO\ntrue\nfalse\nfalse\n-\n{}"))
        assertNull(SettingsBeforeCaller.decode("PHOTO\nyes\nfalse\nfalse\n-\n{}"))
        assertNull(SettingsBeforeCaller.decode("PHOTO\ntrue\nfalse\nfalse\nKodak\n{}"))
    }
}
