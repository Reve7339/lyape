package com.lyape.voice

import android.media.AudioAttributes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Correspondencia de atributos en una capa testeable en JVM. No sustituye la validación
 * audible ni la configuración real de `TextToSpeech` en un dispositivo.
 */
class VoiceAudioProfileTest {

    @Test
    fun `el prototipo usa media con contenido de voz`() {
        assertEquals(AudioAttributes.USAGE_MEDIA, VoiceAudioAttributes.profile.usage)
        assertEquals(AudioAttributes.CONTENT_TYPE_SPEECH, VoiceAudioAttributes.profile.contentType)
    }

    @Test
    fun `no usa accesibilidad ni alarmas para obtener prioridad`() {
        assertNotEquals(
            AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY,
            VoiceAudioAttributes.profile.usage,
        )
        assertNotEquals(AudioAttributes.USAGE_ALARM, VoiceAudioAttributes.profile.usage)
    }
}
