package com.lyape.voice

/**
 * Errores que puede experimentar el controlador de voz.
 *
 * El controlador nunca lanza excepciones al entorno; cada fallo se representa en el estado
 * y la interfaz puede mostrarlo o reintentar. El cierre de la sesión no es un error: vive en
 * `VoiceState.closed`.
 */
enum class VoiceError {

    /** No se pudieron leer las preferencias locales. La voz no está disponible. */
    PREFERENCE_UNAVAILABLE,

    /** Fallo al iniciar el motor de texto a voz del teléfono. */
    ENGINE_UNAVAILABLE,

    /** Ninguna voz en español local está disponible. */
    NO_SPANISH_VOICE,

    /** El teléfono está en silencio o en No molestar. */
    AUDIO_BLOCKED,

    /** Otra aplicación retuvo el audio. */
    FOCUS_DENIED,

    /** Otra aplicación obtuvo el foco durante la reproducción. */
    FOCUS_LOST,

    /** Falló la reproducción del anuncio. */
    PLAYBACK_FAILED,

    /** No se pudo guardar el cambio de preferencia. */
    SAVE_FAILED,

    /** La voz seleccionada no está disponible en el motor. */
    VOICE_UNAVAILABLE,

    /** No se pudo iniciar el servicio de avisos. */
    SERVICE_UNAVAILABLE,

    /** El aviso persistente no puede mostrarse; LYape no inicia la sesión sin él. */
    NOTIFICATION_PERMISSION,

    /** La captura está pausada, sin acceso o no se puede comprobar. */
    CAPTURE_UNAVAILABLE,
}
