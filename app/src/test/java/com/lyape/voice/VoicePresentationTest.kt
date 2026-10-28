package com.lyape.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contrato de presentación de Ajustes para B04.
 *
 * Estas pruebas se escribieron antes de corregir la lógica y fallan con la versión que solo
 * distinguía «inicializando» y «lista de voces vacía».
 */
class VoicePresentationTest {

    private fun state(
        enabled: Boolean = false,
        preferenceAvailable: Boolean = true,
        initializing: Boolean = false,
        engineReady: Boolean = false,
        voices: List<SpanishVoiceOption> = emptyList(),
        selectedVoiceId: String? = null,
        speaking: Boolean = false,
        saving: Boolean = false,
        saveRetryTarget: Boolean? = null,
        error: VoiceError? = null,
    ) = VoiceState(
        enabled = enabled,
        preferenceAvailable = preferenceAvailable,
        initializing = initializing,
        engineReady = engineReady,
        voices = voices,
        selectedVoiceId = selectedVoiceId,
        speaking = speaking,
        saving = saving,
        saveRetryTarget = saveRetryTarget,
        error = error,
    )

    @Test
    fun `voz apagada sin consultar no es ausencia de voz`() {
        assertEquals(VoiceAvailability.DISABLED, state(enabled = false).availability())
    }

    @Test
    fun `voz activada inicializando muestra progreso`() {
        assertEquals(
            VoiceAvailability.CHECKING,
            state(enabled = true, initializing = true).availability(),
        )
    }

    @Test
    fun `consulta exitosa sin voces muestra ausencia`() {
        assertEquals(
            VoiceAvailability.NO_VOICE,
            state(
                enabled = true,
                engineReady = true,
                error = VoiceError.NO_SPANISH_VOICE,
            ).availability(),
        )
    }

    @Test
    fun `motor fallido no se presenta como ausencia de idioma`() {
        assertEquals(
            VoiceAvailability.ENGINE_UNAVAILABLE,
            state(enabled = true, error = VoiceError.ENGINE_UNAVAILABLE).availability(),
        )
    }

    @Test
    fun `preferencia ilegible tiene estado propio`() {
        assertEquals(
            VoiceAvailability.PREFERENCE_UNAVAILABLE,
            state(preferenceAvailable = false, error = VoiceError.PREFERENCE_UNAVAILABLE).availability(),
        )
    }

    @Test
    fun `voz elegida eliminada tiene estado propio`() {
        assertEquals(
            VoiceAvailability.VOICE_UNAVAILABLE,
            state(
                enabled = true,
                engineReady = true,
                voices = listOf(SpanishVoiceOption("es-MX", "Español (México)", "es-MX", "MX")),
                error = VoiceError.VOICE_UNAVAILABLE,
            ).availability(),
        )
    }

    @Test
    fun `fallo de guardado permite reintentar con el interruptor`() {
        assertTrue(
            state(enabled = false, saving = false, error = VoiceError.SAVE_FAILED).canToggle,
        )
    }

    @Test
    fun `fallo de guardado expone el objetivo de reintento`() {
        val state = state(
            enabled = false,
            saving = false,
            saveRetryTarget = true,
            error = VoiceError.SAVE_FAILED,
        )
        assertTrue(state.canRetrySave)
        assertEquals(true, state.saveRetryTarget)
    }

    @Test
    fun `sesion cerrada no tiene controles activos`() {
        val state = VoiceState(closed = true, preferenceAvailable = true)
        assertEquals(VoiceAvailability.CLOSED, state.availability())
        assertTrue(!state.canToggle)
        assertTrue(!state.canRetrySave)
    }
}
