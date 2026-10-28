package com.lyape.voice

/**
 * Estado observable del controlador de voz, sin lógica.
 *
 * `closed` representa el ciclo de vida de la instancia y no un error transitorio: una vez
 * cerrado, el controlador no vuelve a inicializarse ni a reproducir.
 */
data class VoiceState(
    val closed: Boolean = false,
    val enabled: Boolean = false,
    val preferenceAvailable: Boolean = true,
    val initializing: Boolean = false,
    val engineReady: Boolean = false,
    val voices: List<SpanishVoiceOption> = emptyList(),
    val selectedVoiceId: String? = null,
    val speaking: Boolean = false,
    val saving: Boolean = false,
    val saveRetryTarget: Boolean? = null,
    val error: VoiceError? = null,
)
