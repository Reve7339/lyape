package com.lyape.voice

import android.media.AudioAttributes

/**
 * Atributos de audio compartidos por la solicitud de foco y el motor TTS.
 *
 * Es una prueba de voz iniciada por el usuario: se usa `USAGE_MEDIA` con
 * `CONTENT_TYPE_SPEECH`, sin recurrir a accesibilidad ni alarmas para obtener prioridad.
 */
internal data class VoiceAudioProfile(
    val usage: Int,
    val contentType: Int,
)

internal object VoiceAudioAttributes {

    val profile = VoiceAudioProfile(
        usage = AudioAttributes.USAGE_MEDIA,
        contentType = AudioAttributes.CONTENT_TYPE_SPEECH,
    )

    fun build(profile: VoiceAudioProfile = VoiceAudioAttributes.profile): AudioAttributes =
        AudioAttributes.Builder()
            .setUsage(profile.usage)
            .setContentType(profile.contentType)
            .build()
}
