package com.lyape.voice

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Coordina la sesión de voz de Ajustes con la visibilidad de la pantalla.
 *
 * Mantiene una sola instancia vigente. Al detenerse, cierra la sesión (detiene voz, libera
 * motor y foco) sin tocar preferencias. Al volver, crea una instancia nueva solo si la
 * anterior quedó cerrada; nunca reutiliza un controlador cerrado ni acumula motores por
 * recomposición o reentrada.
 */
class VoiceScreenSession internal constructor(
    private val factory: () -> VoiceAnnouncementController,
) {

    private val _controller = MutableStateFlow(factory())
    val controller: StateFlow<VoiceAnnouncementController> = _controller.asStateFlow()

    /** Prepara una sesión válida si la actual ya se cerró. */
    @Synchronized
    fun start() {
        if (_controller.value.state.value.closed) {
            _controller.value = factory()
        }
    }

    /** Detiene y libera la sesión actual, conservando las preferencias guardadas. */
    @Synchronized
    fun stop() {
        _controller.value.close()
    }

    /** Cierre al abandonar la composición. */
    @Synchronized
    fun close() {
        _controller.value.close()
    }
}
