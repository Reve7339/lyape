package com.lyape.voice

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import com.lyape.domain.CapturedPayment
import com.lyape.preferences.AppPreferences
import com.lyape.preferences.CapturePreferenceState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class VoiceSessionPhase { IDLE, STARTING, ACTIVE, FAILED }

data class VoiceSessionState(
    val voice: VoiceState = VoiceState(),
    val phase: VoiceSessionPhase = VoiceSessionPhase.IDLE,
    val sessionRunning: Boolean = false,
    val serviceConfirmed: Boolean = false,
    val token: Long = 0,
)

enum class VoiceSessionAvailability {
    DISABLED,
    PREPARING,
    ACTIVE,
    STOPPED,
    NO_VOICE,
    ENGINE_UNAVAILABLE,
    VOICE_UNAVAILABLE,
    PREFERENCE_UNAVAILABLE,
    SAVE_FAILED,
    SERVICE_UNAVAILABLE,
    NOTIFICATION_BLOCKED,
    CAPTURE_UNAVAILABLE,
}

fun VoiceSessionState.availability(): VoiceSessionAvailability {
    val voice = voice
    return when {
        !voice.preferenceAvailable -> VoiceSessionAvailability.PREFERENCE_UNAVAILABLE
        voice.error == VoiceError.SAVE_FAILED -> VoiceSessionAvailability.SAVE_FAILED
        phase == VoiceSessionPhase.FAILED &&
            voice.error == VoiceError.NOTIFICATION_PERMISSION ->
            VoiceSessionAvailability.NOTIFICATION_BLOCKED
        phase == VoiceSessionPhase.FAILED &&
            voice.error == VoiceError.CAPTURE_UNAVAILABLE ->
            VoiceSessionAvailability.CAPTURE_UNAVAILABLE
        phase == VoiceSessionPhase.FAILED -> when (voice.error) {
            VoiceError.ENGINE_UNAVAILABLE -> VoiceSessionAvailability.ENGINE_UNAVAILABLE
            VoiceError.NO_SPANISH_VOICE -> VoiceSessionAvailability.NO_VOICE
            VoiceError.VOICE_UNAVAILABLE -> VoiceSessionAvailability.VOICE_UNAVAILABLE
            VoiceError.SERVICE_UNAVAILABLE -> VoiceSessionAvailability.SERVICE_UNAVAILABLE
            else -> VoiceSessionAvailability.STOPPED
        }
        !voice.enabled -> VoiceSessionAvailability.DISABLED
        phase == VoiceSessionPhase.STARTING -> when (voice.error) {
            VoiceError.ENGINE_UNAVAILABLE -> VoiceSessionAvailability.ENGINE_UNAVAILABLE
            VoiceError.NO_SPANISH_VOICE -> VoiceSessionAvailability.NO_VOICE
            VoiceError.VOICE_UNAVAILABLE -> VoiceSessionAvailability.VOICE_UNAVAILABLE
            VoiceError.PREFERENCE_UNAVAILABLE ->
                VoiceSessionAvailability.PREFERENCE_UNAVAILABLE
            else -> VoiceSessionAvailability.PREPARING
        }
        phase == VoiceSessionPhase.ACTIVE -> when {
            voice.initializing -> VoiceSessionAvailability.PREPARING
            voice.error == VoiceError.PREFERENCE_UNAVAILABLE ->
                VoiceSessionAvailability.PREFERENCE_UNAVAILABLE
            voice.error == VoiceError.ENGINE_UNAVAILABLE ->
                VoiceSessionAvailability.ENGINE_UNAVAILABLE
            voice.error == VoiceError.NO_SPANISH_VOICE ||
                (voice.engineReady && voice.voices.isEmpty()) ->
                VoiceSessionAvailability.NO_VOICE
            voice.error == VoiceError.VOICE_UNAVAILABLE ->
                VoiceSessionAvailability.VOICE_UNAVAILABLE
            voice.engineReady && voice.voices.isNotEmpty() &&
                voice.selectedVoiceId != null -> VoiceSessionAvailability.ACTIVE
            else -> VoiceSessionAvailability.PREPARING
        }
        else -> VoiceSessionAvailability.STOPPED
    }
}

val VoiceSessionState.canToggle: Boolean
    get() = voice.preferenceAvailable && !voice.saving

val VoiceSessionState.canRetrySave: Boolean
    get() = voice.error == VoiceError.SAVE_FAILED && voice.saveRetryTarget != null

/**
 * Sesión de voz del proceso, independiente de la pantalla de Ajustes.
 *
 * El servicio foreground conserva el proceso. Cada intento recibe un token; la confirmación del
 * servicio, la inicialización del TTS y los callbacks antiguos se comparan contra ese token.
 * Ninguna operación mantiene un bloqueo durante esperas de Android/TTS.
 */
class VoiceSessionCoordinator(
    private val preferences: AppPreferences,
    private val engineFactory: () -> VoiceEngine,
    private val gateFactory: () -> SpeechAudioGate,
    private val serviceHost: VoiceServiceHost,
    private val notificationGate: VoiceNotificationGate,
    private val captureAccess: () -> Boolean,
    private val monotonicClock: () -> Long,
    private val startupTimeoutMillis: Long,
    private val scope: CoroutineScope,
    private val queueFactory: () -> VoiceAnnouncementQueue = { VoiceAnnouncementQueue() },
) {

    constructor(context: Context, preferences: AppPreferences) : this(
        preferences = preferences,
        engineFactory = { AndroidVoiceEngine(context.applicationContext) },
        gateFactory = { AndroidSpeechAudioGate(context.applicationContext) },
        serviceHost = AndroidVoiceServiceHost(context),
        notificationGate = AndroidVoiceNotificationGate(context),
        captureAccess = {
            NotificationManagerCompat.getEnabledListenerPackages(context.applicationContext)
                .contains(context.packageName)
        },
        monotonicClock = { System.nanoTime() / 1_000_000 },
        startupTimeoutMillis = 10_000,
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
    )

    private class ActiveSession(
        val token: Long,
        val controller: VoiceAnnouncementController,
        val drainSignals: Channel<Unit> = Channel(Channel.CONFLATED),
    ) {
        var serviceConfirmed = false
        var engineReady = false
        var closed = false
        var initJob: Job? = null
        var stateJob: Job? = null
        var consumerJob: Job? = null
        var observerJob: Job? = null
        var timeoutJob: Job? = null
    }

    private enum class PersistResult { SAVED, STALE, FAILED }

    private data class PreferenceWrite(
        val token: Long,
        val enabled: Boolean,
        val result: CompletableDeferred<PersistResult>,
    )

    private val lock = Any()
    private val preferenceWrites = Channel<PreferenceWrite>(Channel.UNLIMITED)
    private val _state = MutableStateFlow(VoiceSessionState())
    val state: StateFlow<VoiceSessionState> = _state.asStateFlow()

    private var session: ActiveSession? = null
    private var tokenCounter = 0L
    private var starting = false
    private var offPersistenceJob: Job? = null

    @Volatile
    private var capturePaused = false

    init {
        reloadPreferences()
        scope.launch {
            serviceHost.events.collect(::onServiceEvent)
        }
        scope.launch {
            for (request in preferenceWrites) {
                processPreferenceWrite(request)
            }
        }
    }

    fun reloadPreferences() {
        scope.launch {
            val prefs = readVoicePreferences()
            synchronized(lock) {
                if (session != null || starting) return@launch
                if (_state.value.phase == VoiceSessionPhase.FAILED &&
                    _state.value.voice.error != VoiceError.PREFERENCE_UNAVAILABLE
                ) {
                    return@launch
                }
                _state.value = if (prefs == null) {
                    _state.value.copy(
                        phase = VoiceSessionPhase.IDLE,
                        sessionRunning = false,
                        voice = VoiceState(
                            preferenceAvailable = false,
                            error = VoiceError.PREFERENCE_UNAVAILABLE,
                        ),
                    )
                } else {
                    _state.value.copy(
                        phase = VoiceSessionPhase.IDLE,
                        sessionRunning = false,
                        voice = VoiceState(
                            enabled = prefs.enabled,
                            preferenceAvailable = true,
                        ),
                    )
                }
            }
        }
    }

    fun requestEnabled(enabled: Boolean) {
        scope.launch {
            if (enabled) startSession() else disable()
        }
    }

    suspend fun setEnabled(enabled: Boolean) {
        if (enabled) startSession() else disable()
    }

    /**
     * Orden desde el aviso persistente. Acepta el token de forma atómica y entrega el trabajo
     * de persistencia al scope estable del coordinador; el servicio puede destruirse sin
     * cancelar la escritura.
     */
    fun requestDisableFromService(token: Long): Boolean = beginDisable(token) != null

    /**
     * Apagado explícito. La aceptación es atómica; la persistencia corre en el scope del
     * coordinador y esta llamada solo espera a que termine sin ser su dueña.
     */
    suspend fun disable() {
        beginDisable(expectedToken = null)?.join()
    }

    private data class DisableTransition(
        val active: ActiveSession?,
        val previousToken: Long,
        val token: Long,
        val shouldPersist: Boolean,
    )

    private fun beginDisable(expectedToken: Long?): Job? {
        val transition = synchronized(lock) {
            val active = session
            if (expectedToken != null) {
                if (expectedToken == 0L || expectedToken != tokenCounter ||
                    _state.value.token != expectedToken
                ) {
                    return null
                }
                if (active == null || active.token != expectedToken || active.closed) return null
            } else {
                if (active == null && !starting && offPersistenceJob?.isActive == true) {
                    return null
                }
                val needsWork = active != null ||
                    _state.value.voice.enabled ||
                    _state.value.voice.error == VoiceError.SAVE_FAILED ||
                    starting
                if (!needsWork) return null
            }

            val previousToken = _state.value.token
            if (active != null) {
                session = null
                active.closed = true
            }
            val token = ++tokenCounter
            val shouldPersist = active != null ||
                _state.value.voice.enabled ||
                _state.value.voice.error == VoiceError.SAVE_FAILED ||
                starting
            _state.value = _state.value.copy(
                phase = VoiceSessionPhase.IDLE,
                sessionRunning = false,
                serviceConfirmed = false,
                token = token,
                voice = _state.value.voice.copy(
                    closed = false,
                    enabled = false,
                    initializing = false,
                    engineReady = false,
                    voices = emptyList(),
                    selectedVoiceId = null,
                    speaking = false,
                    saving = shouldPersist,
                    saveRetryTarget = null,
                    error = null,
                ),
            )
            DisableTransition(active, previousToken, token, shouldPersist)
        }

        if (transition.active != null) {
            releaseSession(transition.active)
        } else {
            serviceHost.stop(transition.previousToken)
        }
        if (!transition.shouldPersist) return null

        val job = scope.launch {
            when (persistVoiceEnabled(transition.token, false)) {
                PersistResult.SAVED -> updateStateIfCurrent(transition.token) {
                    it.copy(
                        voice = it.voice.copy(
                            enabled = false,
                            saving = false,
                            error = null,
                            saveRetryTarget = null,
                        ),
                    )
                }
                PersistResult.FAILED -> updateStateIfCurrent(transition.token) {
                    it.copy(
                        phase = VoiceSessionPhase.FAILED,
                        voice = it.voice.copy(
                            enabled = false,
                            saving = false,
                            error = VoiceError.SAVE_FAILED,
                            saveRetryTarget = false,
                        ),
                    )
                }
                PersistResult.STALE -> Unit
            }
        }
        synchronized(lock) {
            if (tokenCounter == transition.token) offPersistenceJob = job
        }
        job.invokeOnCompletion {
            synchronized(lock) {
                if (offPersistenceJob === job) offPersistenceJob = null
            }
        }
        return job
    }

    /** Pausa, pérdida de acceso o cierre por sistema: no escriben la preferencia de voz. */
    fun stopSessionKeepingPreference(expectedToken: Long = _state.value.token) {
        val active = synchronized(lock) {
            if (expectedToken == 0L || expectedToken != tokenCounter ||
                _state.value.token != expectedToken
            ) {
                return
            }
            val current = session?.takeIf {
                it.token == expectedToken && !it.closed
            } ?: return
            session = null
            current.closed = true
            val token = ++tokenCounter
            _state.value = _state.value.copy(
                phase = VoiceSessionPhase.IDLE,
                sessionRunning = false,
                serviceConfirmed = false,
                token = token,
                voice = _state.value.voice.copy(
                    closed = false,
                    initializing = false,
                    engineReady = false,
                    voices = emptyList(),
                    selectedVoiceId = null,
                    speaking = false,
                    error = null,
                ),
            )
            current
        }
        releaseSession(active)
    }

    /** Revalida al volver de ajustes del sistema o antes de emitir audio. */
    fun revalidateAvailability(expectedToken: Long = _state.value.token) {
        if (expectedToken == 0L || expectedToken != currentToken()) return
        if (_state.value.phase != VoiceSessionPhase.STARTING &&
            _state.value.phase != VoiceSessionPhase.ACTIVE
        ) {
            return
        }
        if (!_state.value.voice.enabled || capturePaused || !captureAccess()) {
            stopSessionKeepingPreference(expectedToken)
            return
        }
        if (!notificationGate.canShow()) {
            failSession(expectedToken, VoiceError.NOTIFICATION_PERMISSION)
        }
    }

    fun onCaptured(payment: CapturedPayment) {
        val token = _state.value.token
        if (_state.value.phase != VoiceSessionPhase.ACTIVE) return
        if (!preconditionsOk(token)) {
            revalidateAvailability(token)
            return
        }
        val active = synchronized(lock) {
            session?.takeIf { it.token == token && !it.closed }
        } ?: return
        active.controller.cancelPreview()
        val outcome = active.controller.tryEnqueue(
            id = payment.id.toString(),
            method = payment.medium,
            amountCents = payment.amountCents,
            receivedAtMillis = monotonicClock(),
        )
        if (outcome == VoiceAnnouncementQueue.Outcome.ENQUEUED) {
            active.drainSignals.trySend(Unit)
        }
    }

    fun preview() {
        val token = _state.value.token
        if (_state.value.phase != VoiceSessionPhase.ACTIVE) return
        if (!preconditionsOk(token)) {
            revalidateAvailability(token)
            return
        }
        val active = synchronized(lock) {
            session?.takeIf { it.token == token && !it.closed }
        } ?: return
        val request = active.controller.beginPreview()
        scope.launch { active.controller.preview(request) }
    }

    fun cancelPreview() {
        val active = synchronized(lock) { session }
        active?.controller?.cancelPreview()
    }

    fun selectVoice(id: String) {
        val token = _state.value.token
        val active = synchronized(lock) {
            session?.takeIf { it.token == token && !it.closed }
        } ?: return
        scope.launch { active.controller.selectVoice(id) }
    }

    private suspend fun startSession() {
        val canStart = synchronized(lock) {
            if (session != null || starting) {
                false
            } else {
                starting = true
                true
            }
        }
        if (!canStart) return

        try {
            val token = synchronized(lock) { ++tokenCounter }
            if (!captureAccess()) {
                failBeforeStart(token, VoiceError.CAPTURE_UNAVAILABLE)
                return
            }
            val captureState = try {
                preferences.captureState.first()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                failBeforeStart(token, VoiceError.PREFERENCE_UNAVAILABLE)
                return
            }
            if (token != currentToken()) return
            capturePaused = captureState != CapturePreferenceState.ACTIVE
            if (capturePaused) {
                failBeforeStart(token, VoiceError.CAPTURE_UNAVAILABLE)
                return
            }
            if (!notificationGate.canShow()) {
                failBeforeStart(token, VoiceError.NOTIFICATION_PERMISSION)
                return
            }

            when (persistVoiceEnabled(token, true)) {
                PersistResult.STALE -> return
                PersistResult.FAILED -> {
                    failBeforeStart(token, VoiceError.SAVE_FAILED, saveRetry = true)
                    return
                }
                PersistResult.SAVED -> Unit
            }
            if (token != currentToken()) return

            val controller = VoiceAnnouncementController(
                engine = engineFactory(),
                preferences = preferences,
                audioGate = gateFactory(),
                queue = queueFactory(),
                clock = monotonicClock,
                canPlay = { canPlay(token) },
            )
            val active = ActiveSession(token, controller)
            val accepted = synchronized(lock) {
                if (token != tokenCounter || session != null) {
                    false
                } else {
                    session = active
                    true
                }
            }
            if (!accepted) {
                controller.close()
                return
            }

            updateStateIfCurrent(token) {
                it.copy(
                    phase = VoiceSessionPhase.STARTING,
                    sessionRunning = true,
                    serviceConfirmed = false,
                    token = token,
                    voice = VoiceState(
                        enabled = true,
                        preferenceAvailable = true,
                        initializing = true,
                    ),
                )
            }
            if (!isCurrent(token)) return
            active.observerJob = observeSession(token)
            if (!isCurrent(token)) return
            active.timeoutJob = scope.launch {
                delay(startupTimeoutMillis)
                if (!isCurrent(token)) return@launch
                if (_state.value.phase != VoiceSessionPhase.ACTIVE) {
                    val confirmed = synchronized(lock) {
                        session?.takeIf { it.token == token }?.serviceConfirmed == true
                    }
                    failSession(
                        token,
                        if (confirmed) VoiceError.ENGINE_UNAVAILABLE
                        else VoiceError.SERVICE_UNAVAILABLE,
                    )
                }
            }

            if (!isCurrent(token)) return
            val serviceStarted = try {
                serviceHost.start(token)
            } catch (_: Exception) {
                false
            }
            if (!serviceStarted) {
                failSession(token, VoiceError.SERVICE_UNAVAILABLE)
                return
            }
            if (!isCurrent(token)) return

            active.initJob = scope.launch {
                val initialized = try {
                    controller.initialize()
                    true
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    false
                }
                if (!isCurrent(token)) {
                    controller.close()
                    return@launch
                }
                val voice = controller.state.value
                if (!initialized || !voice.engineReady ||
                    voice.voices.isEmpty() || voice.selectedVoiceId == null
                ) {
                    failSession(token, voice.error ?: VoiceError.ENGINE_UNAVAILABLE)
                    return@launch
                }
                synchronized(lock) {
                    session?.takeIf { it.token == token }?.engineReady = true
                }
                _state.update {
                    if (it.token == token) it.copy(voice = voice) else it
                }
                active.stateJob = scope.launch {
                    controller.state.collect { latest ->
                        if (isCurrent(token)) {
                            _state.update {
                                if (it.token == token) it.copy(voice = latest) else it
                            }
                        }
                    }
                }
                maybeActivate(token)
            }
        } finally {
            synchronized(lock) { starting = false }
        }
    }

    private fun observeSession(token: Long): Job = scope.launch {
        launch {
            preferences.capturePaused.collect { paused ->
                capturePaused = paused
                if (paused && isCurrent(token)) {
                    stopSessionKeepingPreference(token)
                }
            }
        }
        launch {
            preferences.voicePreferences.collect { prefs ->
                if (prefs == null) {
                    if (isCurrent(token)) stopSessionKeepingPreference(token)
                } else if (!prefs.enabled && isCurrent(token)) {
                    stopSessionKeepingPreference(token)
                }
            }
        }
    }

    private fun maybeActivate(token: Long) {
        val ready = synchronized(lock) {
            val active = session?.takeIf { it.token == token && !it.closed } ?: return
            active.serviceConfirmed && active.engineReady
        }
        if (!ready) return
        if (!preconditionsOk(token)) {
            revalidateAvailability(token)
            return
        }
        updateStateIfCurrent(token) {
            it.copy(
                phase = VoiceSessionPhase.ACTIVE,
                sessionRunning = true,
                serviceConfirmed = true,
                voice = it.voice.copy(
                    closed = false,
                    initializing = false,
                    error = null,
                ),
            )
        }
        startConsumer(token)
    }

    private fun startConsumer(token: Long) {
        val active = synchronized(lock) {
            session?.takeIf {
                it.token == token && !it.closed && it.consumerJob == null
            }
        } ?: return
        active.consumerJob = scope.launch {
            for (signal in active.drainSignals) {
                if (!isCurrent(token) || _state.value.phase != VoiceSessionPhase.ACTIVE) {
                    break
                }
                try {
                    active.controller.drainPending()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    // Un fallo de audio no detiene la captura ni el consumidor futuro.
                }
            }
        }
    }

    private fun canPlay(token: Long): Boolean {
        if (!isCurrent(token) || _state.value.phase != VoiceSessionPhase.ACTIVE) {
            return false
        }
        if (capturePaused || !captureAccess()) {
            scope.launch { stopSessionKeepingPreference(token) }
            return false
        }
        if (!_state.value.voice.enabled || !notificationGate.canShow()) {
            scope.launch { revalidateAvailability(token) }
            return false
        }
        return true
    }

    private fun preconditionsOk(token: Long): Boolean {
        if (!isCurrent(token)) return false
        if (!_state.value.voice.enabled) return false
        if (capturePaused || !captureAccess()) return false
        return notificationGate.canShow()
    }

    private fun onServiceEvent(event: VoiceServiceEvent) {
        when (event) {
            is VoiceServiceEvent.Confirmed -> {
                if (!isCurrent(event.token)) return
                synchronized(lock) {
                    session?.takeIf { it.token == event.token }?.serviceConfirmed = true
                }
                _state.update {
                    if (it.token == event.token) it.copy(serviceConfirmed = true) else it
                }
                maybeActivate(event.token)
            }
            is VoiceServiceEvent.Failed -> failSession(event.token, event.error)
            is VoiceServiceEvent.NotificationBlocked ->
                failSession(event.token, VoiceError.NOTIFICATION_PERMISSION)
            is VoiceServiceEvent.Stopped -> systemStopped(event.token)
        }
    }

    private fun systemStopped(token: Long) {
        val active = synchronized(lock) {
            if (!isCurrentLocked(token)) return
            val current = session ?: return
            session = null
            current.closed = true
            val newToken = ++tokenCounter
            _state.value = _state.value.copy(
                phase = VoiceSessionPhase.IDLE,
                sessionRunning = false,
                serviceConfirmed = false,
                token = newToken,
                voice = _state.value.voice.copy(
                    closed = false,
                    initializing = false,
                    engineReady = false,
                    voices = emptyList(),
                    selectedVoiceId = null,
                    speaking = false,
                    error = null,
                ),
            )
            current
        }
        releaseSession(active)
    }

    private fun failSession(token: Long, error: VoiceError) {
        val active = synchronized(lock) {
            if (!isCurrentLocked(token)) return
            val current = session ?: return
            session = null
            current.closed = true
            val newToken = ++tokenCounter
            _state.value = _state.value.copy(
                phase = VoiceSessionPhase.FAILED,
                sessionRunning = false,
                serviceConfirmed = false,
                token = newToken,
                voice = _state.value.voice.copy(
                    closed = false,
                    initializing = false,
                    engineReady = false,
                    voices = emptyList(),
                    selectedVoiceId = null,
                    speaking = false,
                    saving = false,
                    error = error,
                ),
            )
            current
        }
        releaseSession(active)
    }

    private fun failBeforeStart(
        token: Long,
        error: VoiceError,
        saveRetry: Boolean = false,
    ) {
        synchronized(lock) {
            if (token != tokenCounter) return
            _state.value = _state.value.copy(
                phase = VoiceSessionPhase.FAILED,
                sessionRunning = false,
                serviceConfirmed = false,
                token = 0,
                voice = _state.value.voice.copy(
                    closed = false,
                    enabled = false,
                    initializing = false,
                    engineReady = false,
                    voices = emptyList(),
                    selectedVoiceId = null,
                    speaking = false,
                    saving = false,
                    error = error,
                    saveRetryTarget = if (saveRetry) true else null,
                ),
            )
        }
    }

    private fun releaseSession(active: ActiveSession) {
        active.initJob?.cancel()
        active.stateJob?.cancel()
        active.consumerJob?.cancel()
        active.observerJob?.cancel()
        active.timeoutJob?.cancel()
        active.controller.close()
        serviceHost.stop(active.token)
    }

    private fun updateStateIfCurrent(
        token: Long,
        transform: (VoiceSessionState) -> VoiceSessionState,
    ) {
        synchronized(lock) {
            if (token != tokenCounter) return
            _state.value = transform(_state.value)
        }
    }

    private fun isCurrentLocked(token: Long): Boolean =
        token != 0L && token == tokenCounter &&
            _state.value.token == token &&
            session?.token == token && session?.closed != true

    private fun isCurrent(token: Long): Boolean =
        synchronized(lock) { isCurrentLocked(token) }

    private fun currentToken(): Long = synchronized(lock) { tokenCounter }

    private suspend fun persistVoiceEnabled(token: Long, enabled: Boolean): PersistResult {
        val request = PreferenceWrite(token, enabled, CompletableDeferred())
        preferenceWrites.send(request)
        return request.result.await()
    }

    private suspend fun processPreferenceWrite(request: PreferenceWrite) {
        if (request.token != currentToken()) {
            request.result.complete(PersistResult.STALE)
            return
        }
        try {
            preferences.setVoiceEnabled(request.enabled)
            request.result.complete(PersistResult.SAVED)
        } catch (cancelled: CancellationException) {
            request.result.cancel(cancelled)
            throw cancelled
        } catch (_: Exception) {
            request.result.complete(PersistResult.FAILED)
        }
    }

    private suspend fun readVoicePreferences(): VoicePreferences? = try {
        preferences.voicePreferences.first()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        null
    }
}
