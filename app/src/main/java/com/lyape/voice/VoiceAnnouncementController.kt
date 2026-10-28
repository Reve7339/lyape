package com.lyape.voice

import com.lyape.preferences.AppPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

/**
 * Controlador de voz: gestiona la inicialización asíncrona, el estado, la selección de voz,
 * la cola acotada y el disparo de anuncios de prototipo.
 *
 * Este controlador es el único componente público de voz: no se integra con el ingestor, la
 * base de datos ni los movimientos. No hay reproducción de capturas pasadas y ninguna
 * llamada a repositorios.
 *
 * El cierre es definitivo e idempotente. Una generación interna invalida las operaciones
 * pendientes de una sesión anterior (cierre, desactivación o pérdida de foco), y cada
 * reanudación comprueba que la generación siga vigente antes de publicar estado, seleccionar
 * voz, persistir, pedir foco o hablar.
 */
class VoiceAnnouncementController internal constructor(
    private val engine: VoiceEngine,
    private val preferences: AppPreferences,
    private val audioGate: SpeechAudioGate,
    private val queue: VoiceAnnouncementQueue = VoiceAnnouncementQueue(),
    /** Reloj monotónico de admisión/caducidad; no es la fecha de negocio del pago. */
    private val clock: () -> Long = { System.nanoTime() / 1_000_000 },
    private val canPlay: () -> Boolean = { true },
) {

    private val initMutex = Mutex()
    private val drainMutex = Mutex()
    private val generation = AtomicLong(0L)
    private val previewGeneration = AtomicLong(0L)
    private val previewLock = Any()

    @Volatile
    private var closed = false

    @Volatile
    private var focusEpoch = 0L

    private val _state = MutableStateFlow(VoiceState(initializing = true))
    val state: StateFlow<VoiceState> = _state.asStateFlow()

    @Volatile
    private var currentItemId: String? = null

    /**
     * Inicialización perezosa de la voz y lectura de preferencias. Se puede llamar varias
     * veces; al volver a invocarla se vuelven a leer las preferencias. Si la instancia ya se
     * cerró, no hace nada.
     */
    suspend fun initialize() {
        if (closed) return
        val expected = generation.get()
        val prefs = readVoicePreferences()
        if (!isActive(expected)) return
        if (prefs == null) {
            publishIfActive(
                expected,
                VoiceState(
                    enabled = false,
                    preferenceAvailable = false,
                    error = VoiceError.PREFERENCE_UNAVAILABLE,
                ),
            )
            return
        }
        publishIfActive(
            expected,
            VoiceState(
                enabled = prefs.enabled,
                preferenceAvailable = true,
            ),
        )
        if (prefs.enabled) {
            ensureEngineInitialized(prefs.voiceId, expected)
        }
    }

    /**
     * Activar o desactivar los anuncios. Desactivar detiene la reproducción en curso, vacía
     * la cola, libera el motor y persiste la preferencia después. Si el guardado falla, el
     * apagado local ya quedó aplicado y se ofrece reintentar.
     */
    suspend fun setEnabled(enabled: Boolean) {
        if (closed) return
        if (enabled) {
            enable()
        } else {
            disable()
        }
    }

    /** Selecciona una voz de las disponibles. Solo se aplica si está disponible. */
    suspend fun selectVoice(id: String) {
        if (closed) return
        val expected = generation.get()
        if (!state.value.voices.any { it.id == id }) {
            updateIfActive(expected) { it.copy(error = VoiceError.VOICE_UNAVAILABLE) }
            return
        }
        val ok = try {
            engine.selectVoice(id)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            false
        }
        if (!isActive(expected)) return
        if (!ok) {
            updateIfActive(expected) { it.copy(error = VoiceError.VOICE_UNAVAILABLE) }
            return
        }
        try {
            preferences.setVoiceName(id)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            updateIfActive(expected) {
                it.copy(selectedVoiceId = id, error = VoiceError.SAVE_FAILED)
            }
            return
        }
        updateIfActive(expected) { it.copy(selectedVoiceId = id, error = null) }
    }

    /**
     * Reproduce la frase de prueba: "Prueba. Plin: un sol".
     *
     * Este método no crea movimientos, no toca la base de datos ni reproduce capturas
     * anteriores. Es un anuncio sintético aislado con identificador único.
     */
    suspend fun preview() {
        val request = beginPreview()
        preview(request)
    }

    /**
     * Identidad de una pulsación de Probar, asignada antes de lanzar su coroutine. Una
     * pulsación nueva invalida las anteriores y retira la prueba visible, sin tocar cobros.
     */
    fun beginPreview(): Long = synchronized(previewLock) {
        val request = previewGeneration.incrementAndGet()
        queue.remove { it.id.startsWith(PREVIEW_PREFIX) }
        if (currentItemId?.startsWith(PREVIEW_PREFIX) == true) {
            engine.stop()
        }
        request
    }

    suspend fun preview(previewRequest: Long) {
        if (closed || !state.value.enabled) return
        val expected = generation.get()
        fun requestActive(): Boolean =
            !closed && isActive(expected) && state.value.enabled &&
                previewRequest == previewGeneration.get()

        if (!requestActive()) return
        val prefs = readVoicePreferences()
        if (!requestActive()) return
        if (prefs == null) {
            updateIfActive(expected) {
                it.copy(
                    preferenceAvailable = false,
                    enabled = false,
                    error = VoiceError.PREFERENCE_UNAVAILABLE,
                )
            }
            return
        }
        if (!prefs.enabled) {
            updateIfActive(expected) { it.copy(enabled = false) }
            return
        }
        ensureEngineInitialized(prefs.voiceId, expected)
        if (!requestActive()) return
        if (!engineUsable()) return

        val text = "Prueba. ${PaymentAnnouncementFormatter.format(PaymentMethod.PLIN, 100)}"
        val item = VoiceAnnouncement(
            "$PREVIEW_PREFIX$previewRequest-${UUID.randomUUID()}",
            text,
            clock(),
            previewGeneration = previewRequest,
        )
        if (!requestActive()) return
        if (queue.enqueue(item, clock()) == VoiceAnnouncementQueue.Outcome.ENQUEUED) {
            drainQueue(expected)
        }
    }

    /**
     * Cancela solo la prueba sintética. Si hay un cobro real pendiente, se conserva y se
     * reproduce después; si la prueba está sonando, se detiene sin vaciar la cola real.
     */
    fun cancelPreview() {
        synchronized(previewLock) {
            previewGeneration.incrementAndGet()
            queue.remove { it.id.startsWith(PREVIEW_PREFIX) }
            if (currentItemId?.startsWith(PREVIEW_PREFIX) == true) {
                engine.stop()
            }
        }
    }

    /**
     * Admite un anuncio real sin inicializar el motor ni esperar a la cola. La coordinación
     * decide si hay sesión; el consumidor único ejecuta [drainPending].
     */
    fun tryEnqueue(
        id: String,
        method: PaymentMethod,
        amountCents: Long,
        receivedAtMillis: Long = clock(),
    ): VoiceAnnouncementQueue.Outcome? {
        if (closed || !state.value.enabled) return null
        val text = PaymentAnnouncementFormatter.format(method, amountCents) ?: return null
        return queue.enqueue(
            VoiceAnnouncement(id, text, receivedAtMillis),
            clock(),
        )
    }

    /** Consumidor único: procesa la cola vigente sin crear una coroutine por evento. */
    suspend fun drainPending() {
        if (closed) return
        drainQueue(generation.get())
    }

    /**
     * Compatibilidad con la integración anterior: admite y drena en la misma llamada. El
     * coordinador usa [tryEnqueue] + un consumidor único para no acumular esperas.
     */
    suspend fun announceInternal(
        id: String,
        method: PaymentMethod,
        amountCents: Long,
        receivedAtMillis: Long = clock(),
    ) {
        if (closed || !state.value.enabled) return
        val expected = generation.get()
        val prefs = readVoicePreferences()
        if (!isActive(expected)) return
        if (prefs == null) {
            updateIfActive(expected) {
                it.copy(
                    preferenceAvailable = false,
                    enabled = false,
                    error = VoiceError.PREFERENCE_UNAVAILABLE,
                )
            }
            return
        }
        if (!prefs.enabled) {
            updateIfActive(expected) { it.copy(enabled = false) }
            return
        }
        ensureEngineInitialized(prefs.voiceId, expected)
        if (!isActive(expected)) return
        if (!engineUsable()) return

        if (tryEnqueue(id, method, amountCents, receivedAtMillis) ==
            VoiceAnnouncementQueue.Outcome.ENQUEUED
        ) {
            drainQueue(expected)
        }
    }

    /**
     * Cierre definitivo al abandonar la pantalla o su visibilidad: detiene la reproducción,
     * vacía pendientes, libera foco y motor. Es idempotente y no modifica la preferencia
     * guardada; apagar los anuncios es una acción distinta ([setEnabled] con `false`).
     */
    fun close() {
        if (closed) return
        closed = true
        generation.incrementAndGet()
        releaseEngine()
        _state.value = VoiceState(closed = true, preferenceAvailable = true)
    }

    // ---------------------------------------------------------------------
    // Privados
    // ---------------------------------------------------------------------

    private suspend fun enable() {
        if (state.value.enabled) return
        val expected = generation.get()
        _state.value = _state.value.copy(
            saving = true,
            error = null,
            saveRetryTarget = null,
        )
        try {
            preferences.setVoiceEnabled(true)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            updateIfActive(expected) {
                it.copy(
                    saving = false,
                    error = VoiceError.SAVE_FAILED,
                    saveRetryTarget = true,
                )
            }
            return
        }
        if (!isActive(expected)) return
        _state.value = _state.value.copy(
            enabled = true,
            saving = false,
            error = null,
            saveRetryTarget = null,
        )
        val prefs = readVoicePreferences()
        if (!isActive(expected)) return
        if (prefs == null) {
            updateIfActive(expected) {
                it.copy(
                    preferenceAvailable = false,
                    error = VoiceError.PREFERENCE_UNAVAILABLE,
                )
            }
            return
        }
        ensureEngineInitialized(prefs.voiceId, expected)
    }

    private suspend fun disable() {
        generation.incrementAndGet()
        releaseEngine()
        _state.value = _state.value.copy(
            enabled = false,
            speaking = false,
            initializing = false,
            engineReady = false,
            voices = emptyList(),
            selectedVoiceId = null,
            saving = false,
            error = null,
            saveRetryTarget = null,
        )
        try {
            preferences.setVoiceEnabled(false)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            if (!closed) {
                _state.value = _state.value.copy(
                    error = VoiceError.SAVE_FAILED,
                    saveRetryTarget = false,
                )
            }
        }
    }

    private fun releaseEngine() {
        queue.clear()
        audioGate.abandonFocus()
        engine.stop()
        engine.shutdown()
    }

    private fun isActive(expected: Long): Boolean =
        !closed && generation.get() == expected

    private fun publishIfActive(expected: Long, state: VoiceState) {
        if (isActive(expected)) {
            _state.value = state
        }
    }

    private fun updateIfActive(expected: Long, transform: (VoiceState) -> VoiceState) {
        if (isActive(expected)) {
            _state.value = transform(_state.value)
        }
    }

    private fun engineUsable(): Boolean =
        state.value.engineReady &&
            state.value.voices.isNotEmpty() &&
            state.value.selectedVoiceId != null

    private suspend fun readVoicePreferences(): VoicePreferences? {
        return try {
            preferences.voicePreferences.first()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            null
        }
    }

    private suspend fun ensureEngineInitialized(preferredVoiceId: String?, expected: Long) {
        initMutex.withLock {
            if (!isActive(expected)) return@withLock
            if (state.value.engineReady) {
                selectPreferredVoice(preferredVoiceId, expected)
                return@withLock
            }

            updateIfActive(expected) { it.copy(initializing = true, error = null) }
            val initialized = try {
                engine.initialize()
            } catch (cancellation: CancellationException) {
                engine.shutdown()
                throw cancellation
            } catch (error: Exception) {
                false
            }

            if (!isActive(expected)) {
                engine.shutdown()
                return@withLock
            }
            if (!initialized) {
                updateIfActive(expected) {
                    it.copy(
                        initializing = false,
                        engineReady = false,
                        voices = emptyList(),
                        selectedVoiceId = null,
                        error = VoiceError.ENGINE_UNAVAILABLE,
                    )
                }
                return@withLock
            }

            val voices = try {
                engine.availableVoices()
            } catch (cancellation: CancellationException) {
                engine.shutdown()
                throw cancellation
            } catch (error: Exception) {
                emptyList()
            }

            if (!isActive(expected)) {
                engine.shutdown()
                return@withLock
            }
            if (voices.isEmpty()) {
                updateIfActive(expected) {
                    it.copy(
                        initializing = false,
                        engineReady = true,
                        voices = emptyList(),
                        selectedVoiceId = null,
                        error = VoiceError.NO_SPANISH_VOICE,
                    )
                }
                return@withLock
            }

            val selected = findSelected(voices, preferredVoiceId)
            val selectedOk = try {
                engine.selectVoice(selected.id)
            } catch (cancellation: CancellationException) {
                engine.shutdown()
                throw cancellation
            } catch (error: Exception) {
                false
            }

            if (!isActive(expected)) {
                engine.shutdown()
                return@withLock
            }
            if (!selectedOk) {
                updateIfActive(expected) {
                    it.copy(
                        initializing = false,
                        engineReady = true,
                        voices = voices,
                        selectedVoiceId = null,
                        error = VoiceError.VOICE_UNAVAILABLE,
                    )
                }
                return@withLock
            }

            if (preferredVoiceId != selected.id) {
                try {
                    preferences.setVoiceName(selected.id)
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (error: Exception) {
                    // El fallo de persistencia de una voz elegida no invalida el uso en
                    // curso; se volverá a elegir al reiniciar.
                }
                if (!isActive(expected)) {
                    engine.shutdown()
                    return@withLock
                }
            }

            updateIfActive(expected) {
                it.copy(
                    initializing = false,
                    engineReady = true,
                    voices = voices,
                    selectedVoiceId = selected.id,
                    error = null,
                )
            }
        }
    }

    private fun findSelected(
        voices: List<SpanishVoiceOption>,
        preferred: String?,
    ): SpanishVoiceOption = voices.firstOrNull { it.id == preferred } ?: voices.first()

    private fun selectPreferredVoice(preferred: String?, expected: Long) {
        if (!isActive(expected)) return
        if (preferred != null && state.value.selectedVoiceId == preferred) return
        val selected = state.value.voices.firstOrNull { it.id == preferred }
            ?: state.value.voices.firstOrNull()
            ?: return
        val applied = runCatching { engine.selectVoice(selected.id) }.getOrDefault(false)
        if (applied && isActive(expected)) {
            _state.value = _state.value.copy(selectedVoiceId = selected.id)
        }
    }

    private fun onAudioFocusLost(expected: Long) {
        if (!isActive(expected)) return
        focusEpoch++
        queue.clear()
        audioGate.abandonFocus()
        engine.stop()
        updateIfActive(expected) {
            it.copy(speaking = false, error = VoiceError.FOCUS_LOST)
        }
    }

    private suspend fun drainQueue(expected: Long) {
        drainMutex.withLock {
            while (true) {
                if (!isActive(expected)) {
                    queue.clear()
                    break
                }
                val item = queue.takeNext(clock()) ?: break
                if (!isActive(expected)) {
                    queue.clear()
                    break
                }
                val isPreview = item.id.startsWith(PREVIEW_PREFIX)
                if (isPreview && item.previewGeneration != null &&
                    item.previewGeneration != previewGeneration.get()
                ) {
                    queue.remove { it.id.startsWith(PREVIEW_PREFIX) }
                    continue
                }

                val prefs = readVoicePreferences()
                if (!isActive(expected)) {
                    queue.clear()
                    break
                }
                if (prefs == null) {
                    queue.clear()
                    updateIfActive(expected) {
                        it.copy(error = VoiceError.PREFERENCE_UNAVAILABLE)
                    }
                    break
                }
                if (!prefs.enabled) {
                    queue.clear()
                    updateIfActive(expected) { it.copy(enabled = false) }
                    break
                }
                if (!canPlay()) {
                    queue.clear()
                    break
                }
                if (!audioGate.canSpeak()) {
                    queue.clear()
                    updateIfActive(expected) { it.copy(error = VoiceError.AUDIO_BLOCKED) }
                    break
                }

                val focusVersion = focusEpoch
                if (!audioGate.requestFocus { onAudioFocusLost(expected) }) {
                    queue.clear()
                    updateIfActive(expected) { it.copy(error = VoiceError.FOCUS_DENIED) }
                    break
                }
                if (!isActive(expected) || focusEpoch != focusVersion) {
                    queue.clear()
                    audioGate.abandonFocus()
                    break
                }
                if (!canPlay()) {
                    queue.clear()
                    audioGate.abandonFocus()
                    break
                }
                if (isPreview && item.previewGeneration != null &&
                    item.previewGeneration != previewGeneration.get()
                ) {
                    queue.remove { it.id.startsWith(PREVIEW_PREFIX) }
                    audioGate.abandonFocus()
                    continue
                }

                val canStart = synchronized(previewLock) {
                    if (isPreview && item.previewGeneration != null &&
                        item.previewGeneration != previewGeneration.get()
                    ) {
                        false
                    } else {
                        currentItemId = item.id
                        true
                    }
                }
                if (!canStart) {
                    queue.remove { it.id.startsWith(PREVIEW_PREFIX) }
                    audioGate.abandonFocus()
                    continue
                }
                updateIfActive(expected) { it.copy(speaking = true, error = null) }
                var result: VoiceSpeakResult
                try {
                    result = engine.speak(item.text, item.id)
                } catch (cancellation: CancellationException) {
                    queue.clear()
                    throw cancellation
                } catch (error: Exception) {
                    result = VoiceSpeakResult.FAILED
                } finally {
                    synchronized(previewLock) {
                        if (currentItemId == item.id) currentItemId = null
                    }
                    audioGate.abandonFocus()
                    updateIfActive(expected) { it.copy(speaking = false) }
                }

                if (!isActive(expected) || focusEpoch != focusVersion) {
                    queue.clear()
                    break
                }

                when (result) {
                    VoiceSpeakResult.DONE -> {
                        // Siguiente anuncio de la cola.
                    }
                    VoiceSpeakResult.STOPPED -> {
                        if (isPreview) {
                            queue.remove { it.id.startsWith(PREVIEW_PREFIX) }
                        } else {
                            queue.clear()
                            break
                        }
                    }
                    VoiceSpeakResult.FAILED -> {
                        if (isPreview) {
                            queue.remove { it.id.startsWith(PREVIEW_PREFIX) }
                            updateIfActive(expected) { it.copy(error = VoiceError.PLAYBACK_FAILED) }
                        } else {
                            queue.clear()
                            updateIfActive(expected) { it.copy(error = VoiceError.PLAYBACK_FAILED) }
                            break
                        }
                    }
                }
            }
        }
    }

    private companion object {
        const val PREVIEW_PREFIX = "preview-"
    }
}
