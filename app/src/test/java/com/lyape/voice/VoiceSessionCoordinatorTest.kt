package com.lyape.voice

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.preferencesOf
import com.lyape.domain.CaptureProvider
import com.lyape.domain.CapturedPayment
import com.lyape.preferences.AppPreferences
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.time.Instant
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class VoiceSessionCoordinatorTest {

    private val capturePausedKey = booleanPreferencesKey("capture_paused")

    @Test
    fun `inicio solicitado sin confirmacion queda preparando y no habla`() = runTest {
        val env = harness(backgroundScope)
        env.start()
        runCurrent()

        assertEquals(VoiceSessionPhase.STARTING, env.coordinator.state.value.phase)
        assertFalse(env.coordinator.state.value.serviceConfirmed)
        assertEquals(
            VoiceSessionAvailability.PREPARING,
            env.coordinator.state.value.availability(),
        )
        assertTrue(env.spoken.isEmpty())
        assertEquals(1, env.host.starts)
    }

    @Test
    fun `confirmacion y motor listo activan una sola vez`() = runTest {
        val env = harness(backgroundScope)
        env.start()
        env.confirm()
        runCurrent()

        assertEquals(VoiceSessionPhase.ACTIVE, env.coordinator.state.value.phase)
        assertEquals(VoiceSessionAvailability.ACTIVE, env.coordinator.state.value.availability())
        assertEquals(1, env.engineFactoryCalls)

        env.coordinator.requestEnabled(true)
        runCurrent()
        assertEquals(1, env.engineFactoryCalls)
        assertEquals(1, env.host.starts)

        env.capture("pago-1")
        runCurrent()
        assertEquals(1, env.spoken.size)
    }

    @Test
    fun `fallo de inicio no deja activo ni conserva recursos`() = runTest {
        val env = harness(backgroundScope)
        env.host.startResult = false
        env.start()
        runCurrent()

        assertEquals(VoiceSessionPhase.FAILED, env.coordinator.state.value.phase)
        assertEquals(
            VoiceSessionAvailability.SERVICE_UNAVAILABLE,
            env.coordinator.state.value.availability(),
        )
        assertTrue(env.shutdownCalls >= 1)
        assertTrue(env.spoken.isEmpty())
        assertEquals(1, env.host.starts)
    }

    @Test
    fun `sin confirmacion dentro del plazo se limpia y no queda preparando`() = runTest {
        val env = harness(backgroundScope)
        env.start()
        runCurrent()
        advanceTimeBy(1_001)
        runCurrent()

        assertEquals(VoiceSessionPhase.FAILED, env.coordinator.state.value.phase)
        assertFalse(env.coordinator.state.value.sessionRunning)
        assertTrue(env.shutdownCalls >= 1)
        assertTrue(env.spoken.isEmpty())
    }

    @Test
    fun `una inicializacion TTS bloqueada no deja Preparando para siempre`() = runTest {
        val engine = SuspendableInitializeVoiceEngine()
        val env = harness(backgroundScope, engine)
        env.start()
        env.confirm()
        runCurrent()
        assertEquals(VoiceSessionPhase.STARTING, env.coordinator.state.value.phase)

        advanceTimeBy(1_001)
        runCurrent()

        assertEquals(VoiceSessionPhase.FAILED, env.coordinator.state.value.phase)
        assertEquals(
            VoiceSessionAvailability.ENGINE_UNAVAILABLE,
            env.coordinator.state.value.availability(),
        )
        assertTrue(env.shutdownCalls >= 1)
        engine.completeInitialize(true)
        runCurrent()
        assertNotEquals(VoiceSessionPhase.ACTIVE, env.coordinator.state.value.phase)
    }

    @Test
    fun `una confirmacion tardia de A no activa ni detiene B`() = runTest {
        val env = harness(backgroundScope)
        env.start()
        val tokenA = env.host.lastToken
        env.coordinator.stopSessionKeepingPreference()
        runCurrent()

        env.start()
        val tokenB = env.host.lastToken
        assertNotEquals(tokenA, tokenB)
        assertEquals(VoiceSessionPhase.STARTING, env.coordinator.state.value.phase)

        env.host.confirm(tokenA)
        runCurrent()
        assertEquals(VoiceSessionPhase.STARTING, env.coordinator.state.value.phase)
        assertNotEquals(VoiceSessionPhase.ACTIVE, env.coordinator.state.value.phase)

        env.host.stopEvent(tokenA)
        runCurrent()
        assertEquals(VoiceSessionPhase.STARTING, env.coordinator.state.value.phase)

        env.host.failEvent(tokenA)
        runCurrent()
        assertEquals(VoiceSessionPhase.STARTING, env.coordinator.state.value.phase)

        env.host.confirm(tokenB)
        runCurrent()
        assertEquals(VoiceSessionPhase.ACTIVE, env.coordinator.state.value.phase)
        assertEquals(2, env.engineFactoryCalls)
    }

    @Test
    fun `servicio detenido durante la inicializacion no se activa al completar`() = runTest {
        val engine = SuspendableInitializeVoiceEngine()
        val env = harness(backgroundScope, engine)
        env.start()
        runCurrent()
        env.confirm()
        runCurrent()
        assertEquals(VoiceSessionPhase.STARTING, env.coordinator.state.value.phase)

        env.host.stopEvent()
        runCurrent()
        engine.completeInitialize(true)
        runCurrent()

        assertEquals(VoiceSessionPhase.IDLE, env.coordinator.state.value.phase)
        assertFalse(env.coordinator.state.value.sessionRunning)
        assertTrue(env.spoken.isEmpty())
        assertTrue(env.shutdownCalls >= 1)
    }

    @Test
    fun `servicio detenido por sistema conserva la preferencia y no reproduce historial`() = runTest {
        val env = harness(backgroundScope)
        env.start()
        env.confirm()
        runCurrent()

        env.host.stopEvent()
        runCurrent()

        assertEquals(VoiceSessionPhase.IDLE, env.coordinator.state.value.phase)
        assertTrue(env.coordinator.state.value.voice.enabled)
        assertTrue(env.store.stored.value[VOICE_ENABLED] == true)
        assertTrue(env.spoken.isEmpty())
    }

    @Test
    fun `desactivar durante una inicializacion bloqueada no espera ni activa tarde`() = runTest {
        val engine = SuspendableInitializeVoiceEngine()
        val env = harness(backgroundScope, engine)
        env.start()
        runCurrent()
        assertEquals(VoiceSessionPhase.STARTING, env.coordinator.state.value.phase)

        env.coordinator.disable()
        runCurrent()
        engine.completeInitialize(true)
        runCurrent()

        assertNotEquals(VoiceSessionPhase.ACTIVE, env.coordinator.state.value.phase)
        assertFalse(env.coordinator.state.value.voice.enabled)
        assertEquals(false, env.store.stored.value[VOICE_ENABLED])
        assertTrue(env.spoken.isEmpty())
    }

    @Test
    fun `la pausa detiene la sesion sin depender de Ajustes`() = runTest {
        val env = harness(backgroundScope)
        env.start()
        env.confirm()
        runCurrent()

        env.store.stored.value = preferencesOf(
            VOICE_ENABLED to true,
            capturePausedKey to true,
        )
        runCurrent()

        assertEquals(VoiceSessionPhase.IDLE, env.coordinator.state.value.phase)
        assertTrue(env.coordinator.state.value.voice.enabled)
        assertTrue(env.spoken.isEmpty())
        assertTrue(env.shutdownCalls >= 1)
    }

    @Test
    fun `revocar acceso despues de admitir un pago evita el audio`() = runTest {
        val env = harness(backgroundScope)
        env.start()
        env.confirm()
        runCurrent()

        env.capture("pago-admitido")
        env.accessAllowed = false
        runCurrent()

        assertEquals(VoiceSessionPhase.IDLE, env.coordinator.state.value.phase)
        assertTrue(env.spoken.isEmpty())
        assertTrue(env.coordinator.state.value.voice.enabled)
    }

    @Test
    fun `revocar acceso mientras se espera foco evita la locucion`() = runTest {
        lateinit var env: Harness
        val gate = object : SpeechAudioGate {
            override fun canSpeak(): Boolean = true

            override fun requestFocus(onLoss: () -> Unit): Boolean {
                env.accessAllowed = false
                return true
            }

            override fun abandonFocus() = Unit
        }
        env = harness(backgroundScope, gate = gate)
        env.start()
        env.confirm()
        runCurrent()

        env.capture("pago-con-foco")
        runCurrent()

        assertTrue(env.spoken.isEmpty())
        assertEquals(VoiceSessionPhase.IDLE, env.coordinator.state.value.phase)
    }

    @Test
    fun `una preferencia de captura ilegible no inicia sesion`() = runTest {
        val env = harness(backgroundScope)
        env.store.readFailure = IOException("preferencias")
        env.start()
        runCurrent()

        assertEquals(VoiceSessionPhase.FAILED, env.coordinator.state.value.phase)
        assertEquals(
            VoiceSessionAvailability.CAPTURE_UNAVAILABLE,
            env.coordinator.state.value.availability(),
        )
        assertEquals(0, env.engineFactoryCalls)
    }

    @Test
    fun `una preferencia de voz ilegible mantiene silencio y no anuncia acumulado`() = runTest {
        val env = harness(backgroundScope)
        env.start()
        env.confirm()
        runCurrent()

        env.store.readFailure = IOException("preferencias")
        env.capture("pago-ilegible")
        runCurrent()

        assertTrue(env.spoken.isEmpty())
        assertEquals(
            VoiceSessionAvailability.PREFERENCE_UNAVAILABLE,
            env.coordinator.state.value.availability(),
        )

        env.store.readFailure = null
        runCurrent()
        assertTrue(env.spoken.isEmpty())
    }

    @Test
    fun `desactivar con la cola llena no espera a vaciarla`() = runTest {
        val engine = SequenceVoiceEngine()
        val env = harness(backgroundScope, engine)
        env.start()
        env.confirm()
        runCurrent()

        repeat(100) { env.capture("lleno-$it") }
        runCurrent()
        assertTrue(env.queue.countAt(env.clock) <= 10)

        env.coordinator.disable()
        runCurrent()

        assertEquals(VoiceSessionPhase.IDLE, env.coordinator.state.value.phase)
        assertFalse(env.coordinator.state.value.voice.enabled)
        assertEquals(0, env.queue.countAt(env.clock))
        assertEquals(1, engine.speakCalls)
    }

    @Test
    fun `aviso bloqueado antes de iniciar no crea motor ni servicio`() = runTest {
        val env = harness(backgroundScope)
        env.gateAllowed = false
        env.start()
        runCurrent()

        assertEquals(VoiceSessionPhase.FAILED, env.coordinator.state.value.phase)
        assertEquals(
            VoiceSessionAvailability.NOTIFICATION_BLOCKED,
            env.coordinator.state.value.availability(),
        )
        assertEquals(0, env.engineFactoryCalls)
        assertEquals(0, env.host.starts)
    }

    @Test
    fun `bloqueo del aviso durante la sesion corta audio y servicio`() = runTest {
        val env = harness(backgroundScope)
        env.start()
        env.confirm()
        runCurrent()

        env.gateAllowed = false
        env.coordinator.revalidateAvailability()
        runCurrent()

        assertEquals(VoiceSessionPhase.FAILED, env.coordinator.state.value.phase)
        assertEquals(
            VoiceSessionAvailability.NOTIFICATION_BLOCKED,
            env.coordinator.state.value.availability(),
        )
        assertTrue(env.shutdownCalls >= 1)
        assertTrue(env.host.stops >= 1)
    }

    @Test
    fun `una rafaga no crea una espera por pago y respeta el limite`() = runTest {
        val engine = SequenceVoiceEngine()
        val env = harness(backgroundScope, engine)
        env.start()
        env.confirm()
        runCurrent()

        repeat(100) { env.capture("pago-$it") }
        runCurrent()

        assertTrue(env.queue.countAt(env.clock) <= 10)
        assertEquals(1, engine.speakCalls)
        assertEquals(VoiceSessionPhase.ACTIVE, env.coordinator.state.value.phase)
        val liveJobs = backgroundScope.coroutineContext[Job]?.children?.count() ?: 0
        assertTrue("La ráfaga creó $liveJobs trabajos vivos", liveJobs < 25)
    }

    @Test
    fun `la caducidad usa el reloj monotono y no reproduce lo viejo`() = runTest {
        val env = harness(backgroundScope)
        env.start()
        env.confirm()
        runCurrent()

        env.capture("pago-viejo")
        env.clock += 30_001
        runCurrent()

        assertTrue(env.spoken.isEmpty())
        assertEquals(0, env.queue.countAt(env.clock))
    }

    @Test
    fun `un cobro interrumpe la prueba y conserva el orden real`() = runTest {
        val engine = SequenceVoiceEngine()
        val env = harness(backgroundScope, engine)
        env.start()
        env.confirm()
        runCurrent()

        env.coordinator.preview()
        runCurrent()
        assertEquals(1, engine.speakCalls)

        env.capture("pago-real")
        runCurrent()

        assertEquals(2, engine.speakCalls)
        assertEquals(env.lastCapturedId.toString(), engine.spoken.last().second)
    }

    @Test
    fun `apagado del servicio durante TTS no deja activo ni duplica liberacion`() = runTest {
        val engine = SuspendableInitializeVoiceEngine()
        val env = harness(backgroundScope, engine)
        env.start()
        env.confirm()
        runCurrent()
        val token = env.coordinator.state.value.token
        assertEquals(VoiceSessionPhase.STARTING, env.coordinator.state.value.phase)

        assertTrue(env.coordinator.requestDisableFromService(token))
        engine.completeInitialize(true)
        runCurrent()

        assertNotEquals(VoiceSessionPhase.ACTIVE, env.coordinator.state.value.phase)
        assertFalse(env.coordinator.state.value.voice.enabled)
        assertEquals(false, env.store.stored.value[VOICE_ENABLED])
        assertEquals(1, env.host.stops)
        assertTrue(env.spoken.isEmpty())
    }

    @Test
    fun `desactivar desde servicio persiste aunque se cancele el scope del servicio`() = runTest {
        val env = harness(backgroundScope)
        env.start()
        env.confirm()
        runCurrent()
        val token = env.coordinator.state.value.token
        val writesBefore = env.store.writeAttempts
        env.store.writeGate = CompletableDeferred()
        val serviceScope = CoroutineScope(
            SupervisorJob() + StandardTestDispatcher(testScheduler),
        )
        var accepted = false
        serviceScope.launch {
            accepted = env.coordinator.requestDisableFromService(token)
        }
        runCurrent()
        assertTrue(accepted)
        assertEquals(writesBefore + 1, env.store.writeAttempts)

        serviceScope.cancel()
        env.store.writeGate?.complete(Unit)
        runCurrent()

        assertEquals(false, env.store.stored.value[VOICE_ENABLED])
        assertFalse(env.coordinator.state.value.voice.enabled)
        assertEquals(VoiceSessionPhase.IDLE, env.coordinator.state.value.phase)
        assertTrue(env.shutdownCalls >= 1)
        assertTrue(env.spoken.isEmpty())
    }

    @Test
    fun `fallo de escritura desde servicio deja error recuperable y reintento`() = runTest {
        val env = harness(backgroundScope)
        env.start()
        env.confirm()
        runCurrent()
        val token = env.coordinator.state.value.token
        env.store.writeFailure = IOException("sin espacio")
        val serviceScope = CoroutineScope(
            SupervisorJob() + StandardTestDispatcher(testScheduler),
        )
        serviceScope.launch {
            env.coordinator.requestDisableFromService(token)
        }
        runCurrent()
        serviceScope.cancel()
        runCurrent()

        assertFalse(env.coordinator.state.value.voice.enabled)
        assertEquals(
            VoiceError.SAVE_FAILED,
            env.coordinator.state.value.voice.error,
        )
        assertEquals(false, env.coordinator.state.value.voice.saveRetryTarget)
        assertTrue(env.spoken.isEmpty())

        env.store.writeFailure = null
        env.coordinator.requestEnabled(false)
        runCurrent()
        assertEquals(false, env.store.stored.value[VOICE_ENABLED])
        assertEquals(null, env.coordinator.state.value.voice.error)
    }

    @Test
    fun `una orden vieja del servicio no detiene B`() = runTest {
        val env = harness(backgroundScope)
        env.start()
        env.confirm()
        runCurrent()
        val tokenA = env.coordinator.state.value.token
        env.coordinator.stopSessionKeepingPreference(tokenA)
        runCurrent()

        env.start()
        env.confirm()
        runCurrent()
        val tokenB = env.coordinator.state.value.token
        val stopsBefore = env.host.stops
        assertFalse(env.coordinator.requestDisableFromService(tokenA))
        runCurrent()

        assertEquals(VoiceSessionPhase.ACTIVE, env.coordinator.state.value.phase)
        assertEquals(tokenB, env.coordinator.state.value.token)
        assertEquals(stopsBefore, env.host.stops)
        assertEquals(2, env.engineFactoryCalls)
    }

    @Test
    fun `desactivar repetido no duplica cierres ni escrituras`() = runTest {
        val env = harness(backgroundScope)
        env.start()
        env.confirm()
        runCurrent()
        val token = env.coordinator.state.value.token
        val writesBefore = env.store.writeAttempts
        val stopsBefore = env.host.stops

        assertTrue(env.coordinator.requestDisableFromService(token))
        assertFalse(env.coordinator.requestDisableFromService(token))
        runCurrent()

        assertEquals(writesBefore + 1, env.store.writeAttempts)
        assertEquals(stopsBefore + 1, env.host.stops)
        assertFalse(env.coordinator.state.value.voice.enabled)
        assertEquals(VoiceSessionPhase.IDLE, env.coordinator.state.value.phase)
        assertTrue(env.spoken.isEmpty())
    }

    @Test
    fun `una orden diferida de la sesion A no detiene B`() = runTest {
        val env = harness(backgroundScope)
        env.start()
        env.confirm()
        runCurrent()
        val tokenA = env.coordinator.state.value.token
        env.coordinator.stopSessionKeepingPreference(tokenA)
        runCurrent()

        env.start()
        env.confirm()
        runCurrent()
        val tokenB = env.coordinator.state.value.token
        val stopsBefore = env.host.stops

        val deferredStop = launch(start = CoroutineStart.LAZY) {
            env.coordinator.stopSessionKeepingPreference(tokenA)
        }
        val deferredRevalidate = launch(start = CoroutineStart.LAZY) {
            env.coordinator.revalidateAvailability(tokenA)
        }
        deferredStop.start()
        deferredRevalidate.start()
        runCurrent()

        assertEquals(VoiceSessionPhase.ACTIVE, env.coordinator.state.value.phase)
        assertEquals(tokenB, env.coordinator.state.value.token)
        assertEquals(stopsBefore, env.host.stops)
        assertEquals(2, env.engineFactoryCalls)
    }

    @Test
    fun `un guardado true tardio no revive una sesion apagada`() = runTest {
        val writeGate = CompletableDeferred<Unit>()
        val store = TestDataStore(
            preferencesOf(
                VOICE_ENABLED to false,
                booleanPreferencesKey("capture_paused") to false,
            ),
            writeGate = writeGate,
        )
        val env = harness(backgroundScope, store = store)

        env.coordinator.requestEnabled(true)
        runCurrent()
        store.writeStarted.get()

        val disableJob = launch { env.coordinator.disable() }
        runCurrent()
        writeGate.complete(Unit)
        runCurrent()
        disableJob.join()

        assertEquals(false, store.stored.value[VOICE_ENABLED])
        assertFalse(env.coordinator.state.value.voice.enabled)
        assertNotEquals(VoiceSessionPhase.ACTIVE, env.coordinator.state.value.phase)
        assertTrue(env.spoken.isEmpty())
    }

    @Test
    fun `fallo al guardar apagado mantiene silencio y permite reintentar`() = runTest {
        val env = harness(backgroundScope)
        env.start()
        env.confirm()
        runCurrent()
        env.store.writeFailure = IOException("sin espacio")

        env.coordinator.disable()
        runCurrent()

        assertFalse(env.coordinator.state.value.voice.enabled)
        assertEquals(VoiceError.SAVE_FAILED, env.coordinator.state.value.voice.error)
        assertEquals(false, env.coordinator.state.value.voice.saveRetryTarget)
        assertTrue(env.spoken.isEmpty())

        env.store.writeFailure = null
        env.coordinator.disable()
        runCurrent()
        assertEquals(false, env.store.stored.value[VOICE_ENABLED])
        assertEquals(null, env.coordinator.state.value.voice.error)
    }

    private fun TestScope.harness(
        scope: CoroutineScope,
        engine: VoiceEngine = FakeVoiceEngine(),
        gate: SpeechAudioGate = FakeSpeechAudioGate(),
        store: TestDataStore = TestDataStore(
            preferencesOf(
                VOICE_ENABLED to false,
                booleanPreferencesKey("capture_paused") to false,
            ),
        ),
        timeoutMillis: Long = 1_000,
    ): Harness {
        val harness = Harness(this, scope, engine, gate, store, timeoutMillis)
        runCurrent()
        return harness
    }

    private class Harness(
        private val testScope: TestScope,
        scope: CoroutineScope,
        val engine: VoiceEngine,
        gate: SpeechAudioGate,
        val store: TestDataStore,
        timeoutMillis: Long,
    ) {
        val host = FakeServiceHost()
        val queue = VoiceAnnouncementQueue()
        var engineFactoryCalls = 0
        var gateAllowed = true
        var accessAllowed = true
        var clock = 1_000_000L
        var lastCapturedId: UUID = UUID.randomUUID()

        val spoken: List<Pair<String, String>>
            get() = when (val current = engine) {
                is FakeVoiceEngine -> current.spoken
                is SequenceVoiceEngine -> current.spoken
                else -> emptyList()
            }

        val shutdownCalls: Int
            get() = when (val current = engine) {
                is FakeVoiceEngine -> current.shutdownCalls
                is SuspendableInitializeVoiceEngine -> current.shutdownCalls
                is SequenceVoiceEngine -> current.shutdownCalls
                else -> 0
            }

        val coordinator = VoiceSessionCoordinator(
            preferences = AppPreferences(store),
            engineFactory = {
                engineFactoryCalls++
                engine
            },
            gateFactory = { gate },
            serviceHost = host,
            notificationGate = { gateAllowed },
            captureAccess = { accessAllowed },
            monotonicClock = { clock },
            startupTimeoutMillis = timeoutMillis,
            scope = scope,
            queueFactory = { queue },
        )

        fun start() {
            coordinator.requestEnabled(true)
            testScope.runCurrent()
        }

        fun confirm(token: Long = host.lastToken) {
            host.confirm(token)
            testScope.runCurrent()
        }

        fun capture(id: String) {
            lastCapturedId = UUID.nameUUIDFromBytes(id.toByteArray())
            coordinator.onCaptured(
                CapturedPayment(
                    id = lastCapturedId,
                    medium = CaptureProvider.YAPE.medium,
                    amountCents = 1_000L,
                    occurredAt = Instant.parse("2026-10-26T12:00:00Z"),
                ),
            )
        }
    }

    private class FakeServiceHost : VoiceServiceHost {
        override val events = MutableSharedFlow<VoiceServiceEvent>(
            replay = 1,
            extraBufferCapacity = 16,
        )
        var starts = 0
        var stops = 0
        var startResult = true
        var lastToken = 0L
        private var activeToken = 0L

        override fun start(token: Long): Boolean {
            starts++
            lastToken = token
            if (startResult) activeToken = token
            return startResult
        }

        override fun stop(token: Long) {
            if (activeToken != token) return
            activeToken = 0L
            stops++
        }

        fun confirm(token: Long = lastToken) {
            events.tryEmit(VoiceServiceEvent.Confirmed(token))
        }

        fun stopEvent(token: Long = lastToken) {
            events.tryEmit(VoiceServiceEvent.Stopped(token))
        }

        fun failEvent(token: Long = lastToken) {
            events.tryEmit(VoiceServiceEvent.Failed(token))
        }
    }

    private class SequenceVoiceEngine : VoiceEngine {
        private val pending = mutableListOf<CompletableDeferred<VoiceSpeakResult>>()
        val spoken = mutableListOf<Pair<String, String>>()
        var speakCalls = 0
        var shutdownCalls = 0

        override suspend fun initialize(): Boolean = true

        override fun availableVoices(): List<SpanishVoiceOption> =
            listOf(SpanishVoiceOption("es-PE", "Español (Perú)", "es-PE", "PE"))

        override fun selectVoice(id: String?): Boolean = true

        override suspend fun speak(text: String, utteranceId: String): VoiceSpeakResult {
            speakCalls++
            spoken += text to utteranceId
            val result = CompletableDeferred<VoiceSpeakResult>()
            pending += result
            return result.await()
        }

        override fun stop() {
            pending.forEach { if (!it.isCompleted) it.complete(VoiceSpeakResult.STOPPED) }
            pending.clear()
        }

        override fun shutdown() {
            shutdownCalls++
            stop()
        }
    }
}
