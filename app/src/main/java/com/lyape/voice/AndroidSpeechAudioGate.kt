package com.lyape.voice

import android.app.NotificationManager
import android.app.NotificationManager.INTERRUPTION_FILTER_ALL
import android.content.Context
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
import android.media.AudioManager.AUDIOFOCUS_LOSS
import android.media.AudioManager.AUDIOFOCUS_LOSS_TRANSIENT
import android.media.AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK
import android.media.AudioManager.AUDIOFOCUS_REQUEST_GRANTED
import android.media.AudioManager.RINGER_MODE_SILENT

/**
 * Implementación de [SpeechAudioGate] con API del sistema.
 *
 * Usa los mismos atributos de audio que el motor TTS y observa la pérdida de foco. Los
 * callbacks de una solicitud anterior se ignoran mediante una generación interna.
 */
class AndroidSpeechAudioGate(context: Context) : SpeechAudioGate {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private var focusRequest: AudioFocusRequest? = null
    private var focusGeneration = 0

    override fun canSpeak(): Boolean {
        if (audioManager.ringerMode == RINGER_MODE_SILENT) {
            return false
        }
        val interruptionFilter = notificationManager.currentInterruptionFilter
        // INTERRUPTION_FILTER_UNKNOWN no permite afirmar que el audio esté permitido. En ese
        // caso se omite solo la voz; la captura y el guardado del cobro continúan.
        if (!interruptionAllowsVoice(interruptionFilter)) {
            return false
        }
        return true
    }

    override fun requestFocus(onLoss: () -> Unit): Boolean {
        abandonFocus()
        if (!canSpeak()) {
            return false
        }

        val generation = focusGeneration
        val listener = AudioManager.OnAudioFocusChangeListener { change ->
            if (generation != focusGeneration) return@OnAudioFocusChangeListener
            if (change == AUDIOFOCUS_LOSS ||
                change == AUDIOFOCUS_LOSS_TRANSIENT ||
                change == AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK
            ) {
                focusGeneration++
                onLoss()
            }
        }

        val request = AudioFocusRequest.Builder(AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(VoiceAudioAttributes.build())
            .setOnAudioFocusChangeListener(listener)
            .build()
        focusRequest = request
        val granted = audioManager.requestAudioFocus(request) == AUDIOFOCUS_REQUEST_GRANTED
        if (!granted) {
            focusRequest = null
            focusGeneration++
        }
        return granted
    }

    override fun abandonFocus() {
        focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        focusRequest = null
        focusGeneration++
    }
}

/** Solo el filtro explícito de «todo permitido» autoriza la voz. */
internal fun interruptionAllowsVoice(filter: Int): Boolean = filter == INTERRUPTION_FILTER_ALL
