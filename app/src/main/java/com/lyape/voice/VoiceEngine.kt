package com.lyape.voice

/**
 * Capa de abstracción del motor de texto a voz.
 *
 * Permite probar el controlador y la interfaz sin `TextToSpeech` real. Solo se invocan
 * métodos asíncronos de forma serial desde el controlador: no hay cola de TTS, cada
 * anuncio se despacha y se espera a terminar antes de reproducir el siguiente.
 */
interface VoiceEngine {

    /** Inicializa el motor de forma asíncrona. Devuelve verdadero si queda listo. */
    suspend fun initialize(): Boolean

    /** Voces en español locales disponibles, seleccionadas según la política de voz. */
    fun availableVoices(): List<SpanishVoiceOption>

    /** Selecciona la voz por su identificador. Devuelve verdadero si se aplicó. */
    fun selectVoice(id: String?): Boolean

    /**
     * Despacha un único anuncio de forma asíncrona y bloqueante para el llamador.
     *
     * La reproducción finaliza con uno de los resultados de `VoiceSpeakResult`; no se
     * lanzan excepciones en condiciones normales. El llamador debe ser estrictamente
     * serial, porque aquí no hay cola propia.
     */
    suspend fun speak(text: String, utteranceId: String): VoiceSpeakResult

    /** Detiene la reproducción actual. No libera recursos del motor. */
    fun stop()

    /** Cierre definitivo del motor. */
    fun shutdown()
}
