package com.lyape.voice

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.preferencesOf
import com.lyape.preferences.AppPreferences
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Coordinación JVM de la sesión de pantalla. La integración real con el ciclo de vida de
 * Compose sigue pendiente de instrumentación.
 */
class VoiceScreenSessionTest {

    private val preferencesKey = booleanPreferencesKey("voice_enabled")
    private val clock = { 1_000_000_000_000L }

    private class Harness(
        val prefs: TestDataStore,
        val engines: MutableList<FakeVoiceEngine>,
        val session: VoiceScreenSession,
    )

    private fun harness(enabled: Boolean = true): Harness {
        val prefs = TestDataStore(preferencesOf(preferencesKey to enabled))
        val engines = mutableListOf<FakeVoiceEngine>()
        val session = VoiceScreenSession {
            val engine = FakeVoiceEngine()
            engines += engine
            VoiceAnnouncementController(
                engine = engine,
                preferences = AppPreferences(prefs),
                audioGate = FakeSpeechAudioGate(),
                clock = clock,
            )
        }
        return Harness(prefs, engines, session)
    }

    @Test
    fun `start repetido no duplica la sesion inicial`() {
        val harness = harness()
        val first = harness.session.controller.value

        harness.session.start()
        harness.session.start()

        assertEquals(1, harness.engines.size)
        assertTrue(harness.session.controller.value === first)
        assertFalse(first.state.value.closed)
        harness.session.close()
    }

    @Test
    fun `parar libera la sesion y volver crea una nueva`() = runBlocking {
        val harness = harness()
        val first = harness.session.controller.value
        first.initialize()
        val writesBeforeStop = harness.prefs.writes

        harness.session.stop()

        assertTrue(first.state.value.closed)
        assertEquals(1, harness.engines[0].shutdownCalls)
        assertEquals(writesBeforeStop, harness.prefs.writes)
        assertEquals(true, harness.prefs.stored.value[preferencesKey])

        harness.session.start()
        val second = harness.session.controller.value
        assertTrue(second !== first)
        assertEquals(2, harness.engines.size)
        assertFalse(second.state.value.closed)
        harness.session.close()
    }

    @Test
    fun `volver no reproduce la prueba anterior automaticamente`() = runBlocking {
        val harness = harness()
        harness.session.controller.value.initialize()
        harness.session.controller.value.preview()
        assertEquals(1, harness.engines[0].spoken.size)

        harness.session.stop()
        harness.session.start()
        val second = harness.session.controller.value
        second.initialize()

        assertEquals(0, harness.engines[1].spoken.size)
        assertEquals(1, harness.engines[0].spoken.size)
        harness.session.close()
    }

    @Test
    fun `cerrar la sesion conserva la preferencia`() {
        val harness = harness(enabled = true)
        val controller = harness.session.controller.value
        val writesBeforeClose = harness.prefs.writes

        harness.session.close()

        assertTrue(controller.state.value.closed)
        assertEquals(writesBeforeClose, harness.prefs.writes)
        assertEquals(true, harness.prefs.stored.value[preferencesKey])
    }

    @Test
    fun `ciclos de parada e inicio no acumulan motores abiertos`() {
        val harness = harness()
        val seen = mutableListOf<VoiceAnnouncementController>()

        repeat(3) {
            harness.session.stop()
            harness.session.start()
            val current = harness.session.controller.value
            seen += current
            assertFalse(current.state.value.closed)
        }
        seen.dropLast(1).forEach { assertTrue(it.state.value.closed) }

        assertEquals(4, harness.engines.size)
        assertEquals(3, harness.engines.count { it.shutdownCalls > 0 })
        harness.session.close()
    }
}
