package com.lyape.voice

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Coordinación determinista del adaptador TTS con una sesión compartida.
 *
 * No usa teléfono ni emulador: inyecta [FakeTtsHandleFactory] y controla el orden exacto
 * de inicialización, callbacks, cierre y reinicio. No valida el `TextToSpeech` real.
 */
class AndroidVoiceEngineSessionTest {

    private suspend fun CoroutineScope.startedEngine(
        factory: FakeTtsHandleFactory,
    ): Pair<AndroidVoiceEngine, FakeTtsHandle> {
        val engine = AndroidVoiceEngine(factory)
        val init = async(Dispatchers.Unconfined) { engine.initialize() }
        val handle = factory.last
        handle.completeInit()
        assertTrue(init.await())
        return engine to handle
    }

    @Test
    fun `callback aceptado, cierre y reinicio ignoran la finalizacion tardia`() = runBlocking {
        val factory = FakeTtsHandleFactory()
        val (engine, firstHandle) = startedEngine(factory)

        val firstSpeak = async(Dispatchers.Unconfined) {
            engine.speak("primera", "u1")
        }
        firstHandle.done("u1")
        assertEquals(VoiceSpeakResult.DONE, firstSpeak.await())

        engine.shutdown()
        assertTrue(firstHandle.shutdownCalls > 0)

        val secondInit = async(Dispatchers.Unconfined) { engine.initialize() }
        val secondHandle = factory.last
        secondHandle.completeInit()
        assertTrue(secondInit.await())

        val secondSpeak = async(Dispatchers.Unconfined) {
            engine.speak("segunda", "u2")
        }
        assertFalse(secondSpeak.isCompleted)

        // Callbacks duplicados de la sesión cerrada no tocan la nueva.
        firstHandle.done("u1")
        firstHandle.stopped("u1")
        firstHandle.error("u1")
        firstHandle.completeInit()

        assertFalse(secondSpeak.isCompleted)

        secondHandle.done("u2")
        assertEquals(VoiceSpeakResult.DONE, secondSpeak.await())
        engine.shutdown()
    }

    @Test
    fun `cancelar una inicializacion antigua no invalida la nueva`() = runBlocking {
        val factory = FakeTtsHandleFactory()
        val engine = AndroidVoiceEngine(factory)

        val oldInit = async(Dispatchers.Unconfined) { engine.initialize() }
        val oldHandle = factory.last

        val newInit = async(Dispatchers.Unconfined) { engine.initialize() }
        val newHandle = factory.last

        oldInit.cancel()

        // La cancelación antigua no debe tocar la sesión vigente.
        assertFalse(newInit.isCompleted)
        assertEquals(0, newHandle.shutdownCalls)
        assertTrue(oldHandle.shutdownCalls > 0)

        newHandle.completeInit(TtsHandleInitResult.SUCCESS)
        assertTrue(newInit.await())

        val speak = async(Dispatchers.Unconfined) { engine.speak("nueva", "u2") }
        assertFalse(speak.isCompleted)
        newHandle.done("u2")
        assertEquals(VoiceSpeakResult.DONE, speak.await())
        engine.shutdown()
    }

    @Test
    fun `finalizacion tardia de inicializacion no afecta a la sesion nueva`() = runBlocking {
        val factory = FakeTtsHandleFactory()
        val engine = AndroidVoiceEngine(factory)

        val firstInit = async(Dispatchers.Unconfined) { engine.initialize() }
        val firstHandle = factory.last

        engine.shutdown()
        assertFalse(firstInit.await())

        val secondInit = async(Dispatchers.Unconfined) { engine.initialize() }
        val secondHandle = factory.last

        firstHandle.completeInit(TtsHandleInitResult.SUCCESS)
        assertFalse(secondInit.isCompleted)

        secondHandle.completeInit(TtsHandleInitResult.SUCCESS)
        assertTrue(secondInit.await())
        engine.shutdown()
    }

    @Test
    fun `cierre durante speak completa STOPPED y descarta el callback tardio`() = runBlocking {
        val factory = FakeTtsHandleFactory()
        val (engine, handle) = startedEngine(factory)

        val speak = async(Dispatchers.Unconfined) {
            engine.speak("pendiente", "u1")
        }
        assertFalse(speak.isCompleted)

        engine.shutdown()
        assertEquals(VoiceSpeakResult.STOPPED, speak.await())

        handle.done("u1")
        handle.error("u1")
        assertEquals(VoiceSpeakResult.STOPPED, speak.await())
    }

    @Test
    fun `error inmediato con callback en la misma llamada completa una sola vez`() = runBlocking {
        val factory = FakeTtsHandleFactory()
        val (engine, handle) = startedEngine(factory)
        handle.speakReturn = false
        handle.onSpeak = { id -> handle.done(id) }

        val result = engine.speak("uno", "u1")

        assertEquals(VoiceSpeakResult.DONE, result)
        // Un callback y un error posteriores no vuelven a completar la misma locución.
        handle.done("u1")
        handle.error("u1")
        engine.shutdown()
    }

    @Test
    fun `error inmediato sin callback completa FAILED e ignora el callback tardio`() = runBlocking {
        val factory = FakeTtsHandleFactory()
        val (engine, handle) = startedEngine(factory)
        handle.speakReturn = false
        handle.onSpeak = null

        val result = engine.speak("uno", "u1")

        assertEquals(VoiceSpeakResult.FAILED, result)
        handle.done("u1")
        handle.stopped("u1")
        handle.error("u1")
        engine.shutdown()
    }

    @Test
    fun `callback de otro hilo antes del error inmediato completa solo DONE`() = runBlocking {
        val factory = FakeTtsHandleFactory()
        val (engine, handle) = startedEngine(factory)
        val callbackRan = CountDownLatch(1)
        handle.speakReturn = false
        handle.onSpeak = { id ->
            Thread {
                handle.done(id)
                callbackRan.countDown()
            }.start()
            assertTrue(callbackRan.await(1, TimeUnit.SECONDS))
        }

        val result = engine.speak("uno", "u1")

        assertEquals(VoiceSpeakResult.DONE, result)
        engine.shutdown()
    }

    @Test
    fun `error inmediato antes del callback de otro hilo completa solo FAILED`() = runBlocking {
        val factory = FakeTtsHandleFactory()
        val (engine, handle) = startedEngine(factory)
        val releaseCallback = CountDownLatch(1)
        val callbackRan = CountDownLatch(1)
        handle.speakReturn = false
        handle.onSpeak = { id ->
            Thread {
                releaseCallback.await(1, TimeUnit.SECONDS)
                handle.done(id)
                callbackRan.countDown()
            }.start()
        }

        val result = engine.speak("uno", "u1")

        assertEquals(VoiceSpeakResult.FAILED, result)
        releaseCallback.countDown()
        assertTrue(callbackRan.await(1, TimeUnit.SECONDS))
        engine.shutdown()
    }
}
