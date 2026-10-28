package com.lyape.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceSessionPresentationTest {

    private val voiceOption = SpanishVoiceOption("es-PE", "Español (Perú)", "es-PE", "PE")

    private fun state(
        enabled: Boolean = true,
        preferenceAvailable: Boolean = true,
        phase: VoiceSessionPhase = VoiceSessionPhase.ACTIVE,
        engineReady: Boolean = true,
        voices: List<SpanishVoiceOption> = listOf(voiceOption),
        selectedVoiceId: String? = voiceOption.id,
        saving: Boolean = false,
        saveRetryTarget: Boolean? = null,
        error: VoiceError? = null,
    ) = VoiceSessionState(
        voice = VoiceState(
            enabled = enabled,
            preferenceAvailable = preferenceAvailable,
            initializing = phase == VoiceSessionPhase.STARTING,
            engineReady = engineReady,
            voices = voices,
            selectedVoiceId = selectedVoiceId,
            saving = saving,
            saveRetryTarget = saveRetryTarget,
            error = error,
        ),
        phase = phase,
        sessionRunning = phase == VoiceSessionPhase.STARTING ||
            phase == VoiceSessionPhase.ACTIVE,
    )

    @Test
    fun `apagada no parece una sesion detenida`() {
        assertEquals(
            VoiceSessionAvailability.DISABLED,
            state(enabled = false, phase = VoiceSessionPhase.IDLE).availability(),
        )
    }

    @Test
    fun `preferencia activa sin servicio se presenta como detenida`() {
        assertEquals(
            VoiceSessionAvailability.STOPPED,
            state(
                phase = VoiceSessionPhase.IDLE,
                engineReady = false,
                voices = emptyList(),
                selectedVoiceId = null,
            ).availability(),
        )
    }

    @Test
    fun `inicializando no se presenta como activa`() {
        assertEquals(
            VoiceSessionAvailability.PREPARING,
            state(
                phase = VoiceSessionPhase.STARTING,
                engineReady = false,
                selectedVoiceId = null,
            ).availability(),
        )
    }

    @Test
    fun `sesion lista se presenta como activa`() {
        assertEquals(VoiceSessionAvailability.ACTIVE, state().availability())
    }

    @Test
    fun `motor fallido tiene estado propio`() {
        assertEquals(
            VoiceSessionAvailability.ENGINE_UNAVAILABLE,
            state(
                phase = VoiceSessionPhase.FAILED,
                error = VoiceError.ENGINE_UNAVAILABLE,
            ).availability(),
        )
    }

    @Test
    fun `sin voces tras consulta se presenta como ausencia`() {
        assertEquals(
            VoiceSessionAvailability.NO_VOICE,
            state(voices = emptyList(), selectedVoiceId = null).availability(),
        )
    }

    @Test
    fun `preferencia ilegible no se presenta como ausencia de sesion`() {
        assertEquals(
            VoiceSessionAvailability.PREFERENCE_UNAVAILABLE,
            state(
                preferenceAvailable = false,
                phase = VoiceSessionPhase.IDLE,
                error = VoiceError.PREFERENCE_UNAVAILABLE,
            ).availability(),
        )
    }

    @Test
    fun `fallo de guardado mantiene el interruptor y el reintento`() {
        val state = state(
            phase = VoiceSessionPhase.FAILED,
            error = VoiceError.SAVE_FAILED,
            saveRetryTarget = false,
        )
        assertEquals(VoiceSessionAvailability.SAVE_FAILED, state.availability())
        assertTrue(state.canToggle)
        assertTrue(state.canRetrySave)
    }

    @Test
    fun `aviso bloqueado no se presenta como sesion activa`() {
        assertEquals(
            VoiceSessionAvailability.NOTIFICATION_BLOCKED,
            state(
                enabled = false,
                phase = VoiceSessionPhase.FAILED,
                error = VoiceError.NOTIFICATION_PERMISSION,
            ).availability(),
        )
    }

    @Test
    fun `captura no disponible no se presenta como activa`() {
        assertEquals(
            VoiceSessionAvailability.CAPTURE_UNAVAILABLE,
            state(
                enabled = false,
                phase = VoiceSessionPhase.FAILED,
                error = VoiceError.CAPTURE_UNAVAILABLE,
            ).availability(),
        )
    }
}
