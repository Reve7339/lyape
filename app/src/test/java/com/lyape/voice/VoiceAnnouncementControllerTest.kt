package com.lyape.voice

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.preferencesOf
import com.lyape.preferences.AppPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.util.concurrent.CompletableFuture

class VoiceAnnouncementControllerTest {

    private val preferencesKey = booleanPreferencesKey("voice_enabled")
    private val voiceNameKey = stringPreferencesKey("voice_name")
    private val job = kotlinx.coroutines.Job()
    private val scope = CoroutineScope(job)

    @After
    fun tearDown() {
        job.cancel()
    }

    private fun buildController(
        prefs: TestDataStore,
        engine: VoiceEngine = FakeVoiceEngine(),
        gate: FakeSpeechAudioGate = FakeSpeechAudioGate(),
        queue: VoiceAnnouncementQueue = VoiceAnnouncementQueue(),
        clock: () -> Long = { 1_000_000_000_000L },
    ) = VoiceAnnouncementController(engine, AppPreferences(prefs), gate, queue, clock)

    private fun preferencesOf(enabled: Boolean, voiceName: String?): TestDataStore {
        val prefs = if (voiceName == null) {
            preferencesOf(preferencesKey to enabled)
        } else {
            preferencesOf(preferencesKey to enabled, voiceNameKey to voiceName)
        }
        return TestDataStore(prefs)
    }

    @Test
    fun `preferencia apagada no inicializa el motor`() = runBlocking {
        val prefs = preferencesOf(enabled = false, voiceName = null)
        val engine = FakeVoiceEngine()
        val controller = buildController(prefs, engine)
        controller.initialize()
        assertFalse(engine.initialized)
        assertEquals(false, controller.state.value.enabled)
        assertEquals(true, controller.state.value.preferenceAvailable)
    }

    @Test
    fun `cancelar preview durante lectura no habla y conserva la sesion`() = runBlocking {
        val readGate = CompletableDeferred<Unit>()
        val prefs = TestDataStore(
            preferencesOf(preferencesKey to true),
            gate = readGate,
            gateAfterReads = 1,
        )
        val engine = FakeVoiceEngine()
        val controller = buildController(prefs, engine)
        scope.launch { controller.initialize() }.join()

        val previewJob = scope.launch { controller.preview() }
        withTimeout(2_000) { prefs.readCount.first { it >= 2 } }
        controller.cancelPreview()
        readGate.complete(Unit)
        previewJob.join()

        assertTrue(engine.spoken.isEmpty())
        assertTrue(controller.state.value.enabled)
        assertFalse(controller.state.value.closed)
        controller.close()
    }

    @Test
    fun `cancelar preview durante preparacion del motor no habla tarde`() = runBlocking {
        val prefs = preferencesOf(enabled = true, voiceName = null)
        val engine = SuspendableInitializeVoiceEngine()
        val controller = buildController(prefs, engine)
        val initJob = scope.launch { controller.initialize() }
        engine.initializeStarted.get()

        val previewJob = scope.launch { controller.preview() }
        withTimeout(2_000) { prefs.readCount.first { it >= 2 } }
        repeat(5) { yield() }
        controller.cancelPreview()
        engine.completeInitialize(true)
        initJob.join()
        previewJob.join()

        assertTrue(engine.spoken.isEmpty())
        assertTrue(controller.state.value.enabled)
        controller.close()
    }

    @Test
    fun `preview preparandose no reaparece despues de un cobro`() = runBlocking {
        val prefs = preferencesOf(enabled = true, voiceName = null)
        val engine = SuspendableInitializeVoiceEngine()
        val controller = buildController(prefs, engine)
        val initJob = scope.launch { controller.initialize() }
        engine.initializeStarted.get()

        val previewJob = scope.launch { controller.preview() }
        withTimeout(2_000) { prefs.readCount.first { it >= 2 } }
        val realJob = scope.launch {
            controller.announceInternal("pago-real", PaymentMethod.YAPE, 100)
        }
        withTimeout(2_000) { prefs.readCount.first { it >= 3 } }
        repeat(5) { yield() }
        controller.cancelPreview()
        engine.completeInitialize(true)
        initJob.join()
        previewJob.join()
        realJob.join()

        assertEquals(1, engine.spoken.size)
        assertEquals("pago-real", engine.spoken.single().second)
        controller.close()
    }

    @Test
    fun `cancelar A e iniciar B no permite que A cancele ni duplique B`() = runBlocking {
        val readGate = CompletableDeferred<Unit>()
        val prefs = TestDataStore(
            preferencesOf(preferencesKey to true),
            gate = readGate,
            gateAfterReads = 1,
        )
        val engine = FakeVoiceEngine()
        val controller = buildController(prefs, engine)
        scope.launch { controller.initialize() }.join()

        val requestA = controller.beginPreview()
        val previewA = scope.launch { controller.preview(requestA) }
        withTimeout(2_000) { prefs.readCount.first { it >= 2 } }
        controller.cancelPreview()
        val requestB = controller.beginPreview()
        val previewB = scope.launch { controller.preview(requestB) }
        withTimeout(2_000) { prefs.readCount.first { it >= 3 } }
        readGate.complete(Unit)
        previewA.join()
        previewB.join()

        assertEquals(1, engine.spoken.size)
        assertTrue(engine.spoken.single().first.startsWith("Prueba."))
        controller.close()
    }

    @Test
    fun `un callback tardio de A no cancela ni duplica la prueba B`() = runBlocking {
        val prefs = preferencesOf(enabled = true, voiceName = null)
        val engine = FakeVoiceEngine()
        val controller = buildController(prefs, engine)
        scope.launch { controller.initialize() }.join()

        val requestA = controller.beginPreview()
        controller.cancelPreview()
        val requestB = controller.beginPreview()

        val lateA = scope.launch { controller.preview(requestA) }
        val previewB = scope.launch { controller.preview(requestB) }
        lateA.join()
        previewB.join()

        assertEquals(1, engine.spoken.size)
        assertTrue(engine.spoken.single().first.startsWith("Prueba."))
        controller.close()
    }

    @Test
    fun `preferencia apagada no anuncia la prueba`() = runBlocking {
        val prefs = preferencesOf(enabled = false, voiceName = null)
        val engine = FakeVoiceEngine()
        val controller = buildController(prefs, engine)
        controller.initialize()
        controller.preview()
        assertTrue(engine.spoken.isEmpty())
    }

    @Test
    fun `preferencia encendida inicializa el motor y selecciona la voz`() = runBlocking {
        val prefs = preferencesOf(enabled = true, voiceName = "es-PE")
        val engine = FakeVoiceEngine()
        val controller = buildController(prefs, engine)
        controller.initialize()
        assertTrue(engine.initialized)
        assertEquals("es-PE", engine.selectedVoiceId)
        assertEquals(true, controller.state.value.enabled)
        assertEquals(true, controller.state.value.engineReady)
        assertEquals(1, controller.state.value.voices.size)
        assertEquals("es-PE", controller.state.value.selectedVoiceId)
    }

    @Test
    fun `preferencia apagada por defecto no habla`() = runBlocking {
        val engine = FakeVoiceEngine()
        val controller = buildController(prefs = TestDataStore(), engine = engine)
        controller.initialize()
        controller.preview()
        assertTrue(engine.spoken.isEmpty())
    }

    @Test
    fun `la voz elegida se selecciona en el motor`() = runBlocking {
        val prefs = preferencesOf(enabled = true, voiceName = "es-MX")
        val engine = FakeVoiceEngine(voices = listOf(
            SpanishVoiceOption("es-MX", "Español (México)", "es-MX", "MX"),
            SpanishVoiceOption("es-PE", "Español (Perú)", "es-PE", "PE"),
        ))
        val controller = buildController(prefs, engine)
        controller.initialize()
        assertEquals("es-MX", engine.selectedVoiceId)
    }

    @Test
    fun `al no configurar voz elige la primera disponible y la recuerda`() = runBlocking {
        val prefs = TestDataStore(preferencesOf(preferencesKey to true))
        val engine = FakeVoiceEngine(
            voices = listOf(SpanishVoiceOption("es-MX", "Español (México)", "es-MX", "MX")),
        )
        val controller = buildController(prefs, engine)
        controller.initialize()
        assertEquals("es-MX", controller.state.value.selectedVoiceId)
        assertEquals(1, prefs.writes)
    }

    @Test
    fun `la prueba genera la frase sintética exacta sin datos de pago`() = runBlocking {
        val prefs = preferencesOf(enabled = true, voiceName = null)
        val engine = FakeVoiceEngine()
        val controller = buildController(prefs, engine)
        controller.initialize()
        controller.preview()
        assertEquals(
            1,
            engine.spoken.count { it.first == "Prueba. Plin: un sol" },
        )
        assertEquals("preview-", engine.spoken.first().second.take("preview-".length))
    }

    @Test
    fun `la prueba no depende de capturas pasadas ni de la base de datos`() = runBlocking {
        val prefs = preferencesOf(enabled = true, voiceName = null)
        val engine = FakeVoiceEngine()
        val controller = buildController(prefs, engine)
        controller.initialize()
        controller.preview()
        assertTrue(engine.spoken.first().first.contains("Plin: un sol"))
    }

    @Test
    fun `falla de lectura de preferencia impide inicializar y hablar`() = runBlocking {
        val failure = IOException("sin leer")
        val engine = FakeVoiceEngine()
        val controller = buildController(prefs = TestDataStore(readFailure = failure), engine = engine)
        controller.initialize()
        assertEquals(VoiceError.PREFERENCE_UNAVAILABLE, controller.state.value.error)
        assertEquals(false, controller.state.value.preferenceAvailable)
        assertEquals(false, controller.state.value.enabled)
        controller.preview()
        assertTrue(engine.spoken.isEmpty())
    }

    @Test
    fun `falla de lectura de preferencia también bloquea la prueba en tiempo de despacho`() = runBlocking {
        val prefs = preferencesOf(enabled = true, voiceName = null)
        val engine = FakeVoiceEngine()
        val controller = buildController(prefs, engine)
        controller.initialize()
        assertTrue(engine.initialized)
        assertTrue(engine.spoken.isEmpty())
        controller.preview()
        assertFalse(engine.spoken.isEmpty())

        val failure = IOException("sin leer")
        prefs.readFailure = failure
        controller.preview()
        assertEquals(VoiceError.PREFERENCE_UNAVAILABLE, controller.state.value.error)
        assertEquals(false, controller.state.value.enabled)
    }

    @Test
    fun `falla de inicialización del motor queda como error sin hablar`() = runBlocking {
        val engine = FakeVoiceEngine(initializeResult = false)
        val controller = buildController(
            prefs = preferencesOf(enabled = true, voiceName = null),
            engine = engine,
        )
        controller.initialize()
        assertEquals(VoiceError.ENGINE_UNAVAILABLE, controller.state.value.error)
        assertEquals(false, controller.state.value.engineReady)
        controller.preview()
        assertTrue(engine.spoken.isEmpty())
    }

    @Test
    fun `al no haber voz en español disponible se indica cómo configurar`() = runBlocking {
        val engine = FakeVoiceEngine(voices = emptyList())
        val controller = buildController(
            prefs = preferencesOf(enabled = true, voiceName = null),
            engine = engine,
        )
        controller.initialize()
        assertEquals(VoiceError.NO_SPANISH_VOICE, controller.state.value.error)
        assertEquals(true, controller.state.value.engineReady)
        assertEquals(emptyList<SpanishVoiceOption>(), controller.state.value.voices)
        controller.preview()
        assertTrue(engine.spoken.isEmpty())
    }

    @Test
    fun `el silencio o No molestar evitan reproducir y se informa`() = runBlocking {
        val engine = FakeVoiceEngine()
        val controller = buildController(
            prefs = preferencesOf(enabled = true, voiceName = null),
            engine = engine,
            gate = FakeSpeechAudioGate(canSpeakValue = false),
        )
        controller.initialize()
        controller.preview()
        assertEquals(VoiceError.AUDIO_BLOCKED, controller.state.value.error)
        assertTrue(engine.spoken.isEmpty())
    }

    @Test
    fun `el audio en uso deniega el foco y se informa sin forzar`() = runBlocking {
        val engine = FakeVoiceEngine()
        val controller = buildController(
            prefs = preferencesOf(enabled = true, voiceName = null),
            engine = engine,
            gate = FakeSpeechAudioGate(canSpeakValue = true, requestFocusValue = false),
        )
        controller.initialize()
        controller.preview()
        assertEquals(VoiceError.FOCUS_DENIED, controller.state.value.error)
        assertTrue(engine.spoken.isEmpty())
    }

    @Test
    fun `el fallo de reproducción queda registrado sin colapsar el controlador`() = runBlocking {
        val engine = FakeVoiceEngine(speakResult = VoiceSpeakResult.FAILED)
        val controller = buildController(
            prefs = preferencesOf(enabled = true, voiceName = null),
            engine = engine,
        )
        controller.initialize()
        controller.preview()
        assertEquals(VoiceError.PLAYBACK_FAILED, controller.state.value.error)
        assertEquals("preview-", engine.spoken.first().second.take("preview-".length))
    }

    @Test
    fun `apagar voz detiene la reproducción en curso y vacía la cola`() = runBlocking {
        val prefs = preferencesOf(enabled = true, voiceName = null)
        val engine = BlockingVoiceEngine()
        val controller = buildController(prefs, engine)
        controller.initialize()
        val previewJob = scope.launch { controller.preview() }
        engine.started.get()
        controller.setEnabled(false)
        engine.stopped.get()
        previewJob.join()
        assertEquals(true, engine.stopCalls > 0)
        assertEquals(false, controller.state.value.enabled)
        assertEquals(false, controller.state.value.speaking)
        controller.close()
    }

    @Test
    fun `si la preferencia se apaga antes de despachar no se reproduce`() = runBlocking {
        val prefs = preferencesOf(enabled = true, voiceName = null)
        val engine = FakeVoiceEngine()
        val controller = buildController(prefs, engine)
        controller.initialize()
        prefs.stored.value = preferencesOf(preferencesKey to false)

        controller.preview()

        assertTrue(engine.spoken.isEmpty())
        assertEquals(false, controller.state.value.enabled)
    }

    @Test
    fun `cierre libera el motor y abandona el foco`() = runBlocking {
        val prefs = preferencesOf(enabled = true, voiceName = null)
        val engine = FakeVoiceEngine()
        val gate = FakeSpeechAudioGate()
        val controller = buildController(prefs, engine, gate)
        controller.initialize()
        controller.close()
        assertEquals(1, engine.shutdownCalls)
        assertEquals(1, engine.stopCalls)
        assertEquals(1, gate.abandonFocusCalls)
    }

    @Test
    fun `salir de Ajustes detiene la reproduccion sin desactivar la preferencia guardada`() = runBlocking {
        val prefs = preferencesOf(enabled = true, voiceName = "es-PE")
        val engine = FakeVoiceEngine()
        val gate = FakeSpeechAudioGate()
        val controller = buildController(prefs, engine, gate)
        controller.initialize()
        val writesBeforeClose = prefs.writes

        controller.close()

        assertEquals(true, prefs.stored.value[preferencesKey])
        assertEquals("es-PE", prefs.stored.value[voiceNameKey])
        assertEquals(writesBeforeClose, prefs.writes)
        assertTrue(engine.stopCalls > 0)
        assertEquals(1, engine.shutdownCalls)
        assertEquals(true, controller.state.value.closed)
    }

    @Test
    fun `salir de Ajustes durante una reproduccion la detiene y conserva la preferencia`() = runBlocking {
        val prefs = preferencesOf(enabled = true, voiceName = "es-PE")
        val engine = BlockingVoiceEngine()
        val controller = buildController(prefs, engine)
        controller.initialize()
        val writesBeforeClose = prefs.writes
        val previewJob = scope.launch { controller.preview() }
        engine.started.get()

        controller.close()
        engine.stopped.get()
        previewJob.join()

        assertTrue(engine.stopCalls > 0)
        assertEquals(true, prefs.stored.value[preferencesKey])
        assertEquals(writesBeforeClose, prefs.writes)
    }

    @Test
    fun `preferencia encendida y motor listo reproducen un movimiento sintético`() = runBlocking {
        val prefs = preferencesOf(enabled = true, voiceName = null)
        val engine = FakeVoiceEngine()
        val controller = buildController(prefs, engine)
        controller.initialize()
        controller.announceInternal("mov-1", PaymentMethod.PLIN, 1050)
        assertEquals(1, engine.spoken.size)
        assertEquals("Plin: diez soles con cincuenta céntimos", engine.spoken.first().first)
        assertEquals("mov-1", engine.spoken.first().second)
    }

    @Test
    fun `el controlador rechaza movimientos no positivos sin hablar`() = runBlocking {
        val engine = FakeVoiceEngine()
        val controller = buildController(prefs = preferencesOf(enabled = true, voiceName = null), engine = engine)
        controller.initialize()
        controller.announceInternal("mov-1", PaymentMethod.PLIN, 0)
        controller.announceInternal("mov-2", PaymentMethod.PLIN, -100)
        assertTrue(engine.spoken.isEmpty())
    }

    @Test
    fun `anuncios con caducidad muy antigua no se reproducen`() = runBlocking {
        val engine = FakeVoiceEngine()
        val controller = buildController(
            prefs = preferencesOf(enabled = true, voiceName = null),
            engine = engine,
            clock = { 1_000_000_000_000L },
        )
        controller.initialize()
        controller.announceInternal("old", PaymentMethod.PLIN, 100, receivedAtMillis = 900_000_000_000L)
        assertTrue(engine.spoken.isEmpty())
    }

    @Test
    fun `el identificador evita reencolados del mismo movimiento`() = runBlocking {
        val engine = FakeVoiceEngine()
        val controller = buildController(
            prefs = preferencesOf(enabled = true, voiceName = null),
            engine = engine,
            clock = { 1_000_000_000_000L },
        )
        controller.initialize()
        controller.announceInternal("mov-1", PaymentMethod.PLIN, 100)
        controller.announceInternal("mov-1", PaymentMethod.PLIN, 100)
        controller.announceInternal("mov-2", PaymentMethod.PLIN, 200)
        assertEquals(2, engine.spoken.size)
    }

    @Test
    fun `los anuncios se reproducen en orden serial`() = runBlocking {
        val engine = FakeVoiceEngine()
        val controller = buildController(
            prefs = preferencesOf(enabled = true, voiceName = null),
            engine = engine,
            clock = { 1_000_000_000_000L },
        )
        controller.initialize()
        controller.announceInternal("mov-1", PaymentMethod.YAPE, 100)
        controller.announceInternal("mov-2", PaymentMethod.PLIN, 200)
        controller.announceInternal("mov-3", PaymentMethod.YAPE, 300)

        assertEquals(listOf("mov-1", "mov-2", "mov-3"), engine.spoken.map { it.second })
    }

    @Test
    fun `reintentar la inicialización vuelve a probar el motor`() = runBlocking {
        val prefs = preferencesOf(enabled = true, voiceName = null)
        val engine = FakeVoiceEngine(
            initializeResult = false,
        )
        val controller = buildController(prefs, engine)
        controller.initialize()
        assertEquals(VoiceError.ENGINE_UNAVAILABLE, controller.state.value.error)
        assertEquals(1, engine.initializeCalls)
        engine.initializeResult = true
        controller.initialize()
        assertEquals(true, controller.state.value.engineReady)
        assertNull(controller.state.value.error)
        assertEquals(2, engine.initializeCalls)
    }

    @Test
    fun `guardar la preferencia encendida no falla ni impide escuchar el estado`() = runBlocking {
        val prefs = TestDataStore(preferencesOf(preferencesKey to false))
        val engine = FakeVoiceEngine()
        val controller = buildController(prefs, engine)
        controller.initialize()
        controller.setEnabled(true)
        assertTrue(engine.initialized)
        assertEquals(true, controller.state.value.enabled)
        assertEquals(true, prefs.stored.value[preferencesKey])
    }

    @Test
    fun `guardar la preferencia apagada no falla ni impide el apagado inmediato`() = runBlocking {
        val prefs = TestDataStore(preferencesOf(preferencesKey to true))
        val engine = FakeVoiceEngine()
        val controller = buildController(prefs, engine)
        controller.initialize()
        val writesBeforeDisabling = prefs.writes
        controller.setEnabled(false)
        assertEquals(false, controller.state.value.enabled)
        assertEquals(false, prefs.stored.value[preferencesKey])
        assertEquals(writesBeforeDisabling + 1, prefs.writes)
    }

    @Test
    fun `seleccionar la voz la guarda y la aplica al motor`() = runBlocking {
        val prefs = preferencesOf(enabled = true, voiceName = "es-PE")
        val engine = FakeVoiceEngine(
            voices = listOf(
                SpanishVoiceOption("es-PE", "Español (Perú)", "es-PE", "PE"),
                SpanishVoiceOption("es-MX", "Español (México)", "es-MX", "MX"),
            ),
        )
        val controller = buildController(prefs, engine)
        controller.initialize()
        val writesBeforeSelecting = prefs.writes
        controller.selectVoice("es-MX")
        assertEquals("es-MX", engine.selectedVoiceId)
        assertEquals("es-MX", controller.state.value.selectedVoiceId)
        assertEquals(writesBeforeSelecting + 1, prefs.writes)
    }

    @Test
    fun `error de guardado de voz muestra un aviso sin interrumpir la sesión`() = runBlocking {
        val prefs = TestDataStore(preferencesOf(preferencesKey to true))
        val engine = FakeVoiceEngine(
            voices = listOf(
                SpanishVoiceOption("es-PE", "Español (Perú)", "es-PE", "PE"),
            ),
        )
        val controller = buildController(prefs, engine)
        controller.initialize()
        prefs.writeFailure = IOException("sin espacio")
        controller.selectVoice("es-PE")
        assertEquals(VoiceError.SAVE_FAILED, controller.state.value.error)
    }

    @Test
    fun `el estado refleja que se está hablando y deja de serlo al terminar`() = runBlocking {
        val engine = FakeVoiceEngine()
        val controller = buildController(prefs = preferencesOf(enabled = true, voiceName = null), engine = engine)
        controller.initialize()
        assertEquals(false, controller.state.value.speaking)
        controller.preview()
        assertEquals(false, controller.state.value.speaking)
    }

    @Test
    fun `el controlador cerrado no se puede usar más`() = runBlocking {
        val engine = FakeVoiceEngine()
        val controller = buildController(prefs = preferencesOf(enabled = true, voiceName = null), engine = engine)
        controller.close()
        assertEquals(true, controller.state.value.closed)
    }

    @Test
    fun `cierre durante la inicializacion no reabre ni selecciona voz`() = runBlocking {
        val prefs = preferencesOf(enabled = true, voiceName = null)
        val engine = SuspendableInitializeVoiceEngine()
        val controller = buildController(prefs, engine)
        val initJob = scope.launch { controller.initialize() }
        engine.initializeStarted.get()

        controller.close()
        engine.completeInitialize(true)
        initJob.join()

        assertTrue(controller.state.value.closed)
        assertEquals(false, controller.state.value.enabled)
        assertNull(engine.selectedVoiceId)
        assertEquals(0, engine.speakCalls)
        assertEquals(0, prefs.writes)
        controller.close()
    }

    @Test
    fun `cierre durante la lectura de preferencias no reabre la sesion`() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val prefs = TestDataStore(preferencesOf(preferencesKey to true), gate = gate)
        val engine = FakeVoiceEngine()
        val controller = buildController(prefs, engine)
        val initJob = scope.launch { controller.initialize() }
        prefs.readStarted.get()

        controller.close()
        gate.complete(Unit)
        initJob.join()

        assertTrue(controller.state.value.closed)
        assertFalse(engine.initialized)
        assertTrue(engine.spoken.isEmpty())
        controller.close()
    }

    @Test
    fun `cerrado es definitivo e idempotente`() = runBlocking {
        val prefs = preferencesOf(enabled = true, voiceName = "es-PE")
        val engine = FakeVoiceEngine()
        val controller = buildController(prefs, engine)

        controller.close()
        controller.close()
        controller.initialize()
        controller.preview()

        assertTrue(controller.state.value.closed)
        assertFalse(engine.initialized)
        assertTrue(engine.spoken.isEmpty())
        assertEquals(1, engine.shutdownCalls)
        assertEquals(0, prefs.writes)
    }

    @Test
    fun `desactivar durante la inicializacion no conserva el motor ni la voz`() = runBlocking {
        val prefs = preferencesOf(enabled = true, voiceName = null)
        val engine = SuspendableInitializeVoiceEngine()
        val controller = buildController(prefs, engine)
        val initJob = scope.launch { controller.initialize() }
        engine.initializeStarted.get()

        controller.setEnabled(false)
        engine.completeInitialize(true)
        initJob.join()

        assertEquals(false, controller.state.value.enabled)
        assertEquals(false, controller.state.value.initializing)
        assertNull(controller.state.value.selectedVoiceId)
        assertNull(engine.selectedVoiceId)
        assertTrue(engine.shutdownCalls >= 1)
        assertEquals(false, prefs.stored.value[preferencesKey])
        controller.close()
    }

    @Test
    fun `cancelar la reproduccion no se registra como fallo`() = runBlocking {
        val prefs = preferencesOf(enabled = true, voiceName = null)
        val engine = CancellableVoiceEngine()
        val controller = buildController(prefs, engine)
        controller.initialize()
        val previewJob = scope.launch { controller.preview() }
        engine.speakStarted.get()

        previewJob.cancelAndJoin()

        assertNull(controller.state.value.error)
        assertFalse(controller.state.value.speaking)
        engine.cancelled.get()
        controller.close()
    }

    @Test
    fun `la perdida de foco detiene la reproduccion y vacia la cola`() = runBlocking {
        val prefs = preferencesOf(enabled = true, voiceName = null)
        val engine = BlockingVoiceEngine()
        val gate = FakeSpeechAudioGate()
        val queue = VoiceAnnouncementQueue()
        val controller = buildController(prefs, engine, gate, queue)
        controller.initialize()
        val previewJob = scope.launch { controller.preview() }
        engine.started.get()

        gate.loseFocus()

        assertTrue(engine.stopCalls > 0)
        assertFalse(controller.state.value.speaking)
        assertEquals(0, queue.countAt(1_000_000_000_000L))
        previewJob.join()
        assertEquals(1, engine.speakCalls)
        assertTrue(gate.abandonFocusCalls > 0)
        controller.close()
    }

    @Test
    fun `callback de foco tardio tras el cierre no reactiva la sesion`() = runBlocking {
        val prefs = preferencesOf(enabled = true, voiceName = null)
        val engine = BlockingVoiceEngine()
        val gate = FakeSpeechAudioGate()
        val controller = buildController(prefs, engine, gate)
        controller.initialize()
        val previewJob = scope.launch { controller.preview() }
        engine.started.get()
        val lateLoss = gate.onFocusLoss

        controller.close()
        previewJob.join()
        lateLoss?.invoke()

        assertTrue(controller.state.value.closed)
        assertFalse(controller.state.value.enabled)
        assertEquals(1, engine.speakCalls)
        controller.close()
    }

    @Test
    fun `fallo al activar expone reintento sin reactivar la voz`() = runBlocking {
        val prefs = TestDataStore(preferencesOf(preferencesKey to false))
        val engine = FakeVoiceEngine()
        val controller = buildController(prefs, engine)
        controller.initialize()
        prefs.writeFailure = IOException("sin espacio")

        controller.setEnabled(true)

        assertFalse(controller.state.value.enabled)
        assertEquals(VoiceError.SAVE_FAILED, controller.state.value.error)
        assertEquals(true, controller.state.value.saveRetryTarget)
        assertTrue(controller.state.value.canToggle)

        prefs.writeFailure = null
        controller.setEnabled(true)

        assertTrue(controller.state.value.enabled)
        assertNull(controller.state.value.error)
        assertNull(controller.state.value.saveRetryTarget)
        controller.close()
    }

    @Test
    fun `fallo al desactivar mantiene la voz apagada localmente y permite reintentar`() = runBlocking {
        val prefs = TestDataStore(preferencesOf(preferencesKey to true))
        val engine = FakeVoiceEngine()
        val controller = buildController(prefs, engine)
        controller.initialize()
        prefs.writeFailure = IOException("sin espacio")

        controller.setEnabled(false)

        assertFalse(controller.state.value.enabled)
        assertEquals(VoiceError.SAVE_FAILED, controller.state.value.error)
        assertEquals(false, controller.state.value.saveRetryTarget)
        assertTrue(engine.stopCalls > 0)
        assertTrue(engine.shutdownCalls > 0)

        prefs.writeFailure = null
        controller.setEnabled(false)

        assertFalse(controller.state.value.enabled)
        assertNull(controller.state.value.error)
        assertEquals(false, prefs.stored.value[preferencesKey])
        controller.close()
    }
}
