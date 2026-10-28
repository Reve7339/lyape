package com.lyape.voice

/**
 * Estado de presentación de los avisos de voz para Ajustes.
 *
 * Distingue explícitamente una voz desactivada de una ausencia real de voces. La ausencia
 * solo se muestra tras una consulta exitosa al motor; un fallo de motor o de preferencias
 * tiene su propio estado.
 */
enum class VoiceAvailability {
    CLOSED,
    PREFERENCE_UNAVAILABLE,
    DISABLED,
    CHECKING,
    READY,
    NO_VOICE,
    ENGINE_UNAVAILABLE,
    VOICE_UNAVAILABLE,
}

fun VoiceState.availability(): VoiceAvailability = when {
    closed -> VoiceAvailability.CLOSED
    !preferenceAvailable || error == VoiceError.PREFERENCE_UNAVAILABLE ->
        VoiceAvailability.PREFERENCE_UNAVAILABLE
    !enabled -> VoiceAvailability.DISABLED
    initializing -> VoiceAvailability.CHECKING
    error == VoiceError.ENGINE_UNAVAILABLE -> VoiceAvailability.ENGINE_UNAVAILABLE
    error == VoiceError.VOICE_UNAVAILABLE -> VoiceAvailability.VOICE_UNAVAILABLE
    error == VoiceError.NO_SPANISH_VOICE || (engineReady && voices.isEmpty()) ->
        VoiceAvailability.NO_VOICE
    voices.isNotEmpty() && selectedVoiceId != null -> VoiceAvailability.READY
    else -> VoiceAvailability.CHECKING
}

/**
 * El interruptor sigue disponible tras un fallo de guardado para que el usuario pueda
 * reintentar. El apagado local ya se aplicó por seguridad antes de persistir.
 */
val VoiceState.canToggle: Boolean
    get() = preferenceAvailable && !saving && !closed

/** Hay un cambio de preferencia pendiente de reintentar. */
val VoiceState.canRetrySave: Boolean
    get() = error == VoiceError.SAVE_FAILED && saveRetryTarget != null
