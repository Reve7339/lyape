package com.lyape.voice

/**
 * Preferencias locales de avisos de voz.
 *
 * - `enabled`: si se deben anunciar pagos. Inicialmente `false`.
 * - `voiceId`: identificador de la voz española local elegida (puede ser `null`; el
 *   controlador elige la primera disponible cuando no hay ninguna configurada).
 *
 * No contiene ningún dato de pagos, capturas o notificaciones.
 */
data class VoicePreferences(
    val enabled: Boolean = false,
    val voiceId: String? = null,
)
