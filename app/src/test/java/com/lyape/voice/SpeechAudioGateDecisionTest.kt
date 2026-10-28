package com.lyape.voice

import android.app.NotificationManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechAudioGateDecisionTest {

    @Test
    fun `solo el filtro explicito de todo permitido autoriza voz`() {
        assertTrue(interruptionAllowsVoice(NotificationManager.INTERRUPTION_FILTER_ALL))
        assertFalse(interruptionAllowsVoice(NotificationManager.INTERRUPTION_FILTER_NONE))
        assertFalse(interruptionAllowsVoice(NotificationManager.INTERRUPTION_FILTER_PRIORITY))
        assertFalse(interruptionAllowsVoice(NotificationManager.INTERRUPTION_FILTER_ALARMS))
        assertFalse(interruptionAllowsVoice(NotificationManager.INTERRUPTION_FILTER_UNKNOWN))
    }
}
