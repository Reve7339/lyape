package com.lyape.voice

/**
 * Controla si se puede reproducir sonido para anuncios y solicita el audio focus.
 *
 * Respeta el modo del teléfono (silencio/vibrar) y el estado de No molestar, y luego
 * solicita un audio focus transitorio que puede hacer ducking a otras fuentes.
 *
 * No cambia volúmenes, no usa alarmas y no emite sonido: solo decide y gestiona la
 * prioridad de audio.
 */
interface SpeechAudioGate {

    /**
     * Devuelve verdadero si el teléfono puede reproducir un anuncio ahora.
     *
     * Falsa en silencio (pero no en vibrar) o con No molestar activo.
     */
    fun canSpeak(): Boolean

    /**
     * Solicita audio focus transitorio. Falso si el teléfono no puede reproducir o el
     * focus se deniega (por ejemplo, otra aplicación lo retuvo).
     *
     * [onLoss] se invoca si otra aplicación obtiene el foco mientras esta solicitud sigue
     * vigente. El controlador debe detener la voz y vaciar pendientes; no se reanuda solo.
     */
    fun requestFocus(onLoss: () -> Unit): Boolean

    /** Libera el audio focus obtenido y desactiva cualquier callback pendiente. */
    fun abandonFocus()
}
