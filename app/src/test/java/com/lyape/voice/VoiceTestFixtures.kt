package com.lyape.voice

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.IOException
import java.util.concurrent.CompletableFuture

/**
 * Motor de voz falso para probar el controlador sin `TextToSpeech`.
 */
internal class FakeVoiceEngine(
    var initializeResult: Boolean = true,
    var initializeFailure: Exception? = null,
    var voices: List<SpanishVoiceOption> = listOf(SpanishVoiceOption("es-PE", "Español (Perú)", "es-PE", "PE")),
    var speakResult: VoiceSpeakResult = VoiceSpeakResult.DONE,
) : VoiceEngine {

    var initialized = false
    var selectedVoiceId: String? = null
    val spoken = mutableListOf<Pair<String, String>>()
    var stopCalls = 0
    var shutdownCalls = 0
    var initializeCalls = 0

    override suspend fun initialize(): Boolean {
        initialized = true
        initializeCalls++
        if (initializeFailure != null) {
            throw initializeFailure!!
        }
        return initializeResult
    }

    override fun availableVoices(): List<SpanishVoiceOption> = voices

    override fun selectVoice(id: String?): Boolean {
        selectedVoiceId = id
        return id == null || voices.any { it.id == id }
    }

    override suspend fun speak(text: String, utteranceId: String): VoiceSpeakResult {
        spoken += text to utteranceId
        return speakResult
    }

    override fun stop() {
        stopCalls++
    }

    override fun shutdown() {
        shutdownCalls++
    }
}

/**
 * Motor de voz falso cuyo habla se suspende hasta que la prueba lo complete.
 */
internal class BlockingVoiceEngine : VoiceEngine {

    val started = java.util.concurrent.CompletableFuture<Unit>()
    val stopped = java.util.concurrent.CompletableFuture<Unit>()
    private val speakResult = CompletableDeferred<VoiceSpeakResult>()
    var selectedVoiceId: String? = null
    var stopCalls = 0
    var shutdownCalls = 0
    var speakCalls = 0

    override suspend fun initialize(): Boolean = true
    override fun availableVoices(): List<SpanishVoiceOption> =
        listOf(SpanishVoiceOption("es-PE", "Español (Perú)", "es-PE", "PE"))
    override fun selectVoice(id: String?): Boolean {
        selectedVoiceId = id
        return true
    }

    override suspend fun speak(text: String, utteranceId: String): VoiceSpeakResult {
        started.complete(Unit)
        speakCalls++
        return speakResult.await()
    }

    override fun stop() {
        stopCalls++
        speakResult.complete(VoiceSpeakResult.STOPPED)
        stopped.complete(Unit)
    }

    override fun shutdown() {
        shutdownCalls++
    }
}

/**
 * Controlador de audio falso para decidir canSpeak / requestFocus y simular pérdida de foco.
 *
 * En la implementación anterior el controlador no registraba ningún callback de pérdida, por
 * eso `loseFocus()` no producía efecto. La prueba de fase roja usa ese mismo doble.
 */
internal class FakeSpeechAudioGate(
    var canSpeakValue: Boolean = true,
    var requestFocusValue: Boolean = true,
) : SpeechAudioGate {
    var canSpeakCalls = 0
    var requestFocusCalls = 0
    var abandonFocusCalls = 0
    var onFocusLoss: (() -> Unit)? = null

    fun loseFocus() {
        onFocusLoss?.invoke()
    }

    override fun canSpeak(): Boolean {
        canSpeakCalls++
        return canSpeakValue
    }

    override fun requestFocus(onLoss: () -> Unit): Boolean {
        requestFocusCalls++
        onFocusLoss = if (requestFocusValue) onLoss else null
        return requestFocusValue
    }

    override fun abandonFocus() {
        abandonFocusCalls++
        onFocusLoss = null
    }
}

/**
 * Motor cuya inicialización queda suspendida hasta que la prueba la completa.
 *
 * `shutdown()` no resuelve la inicialización para poder simular un callback tardío después
 * del cierre, que es justo el fallo de B01.
 */
internal class SuspendableInitializeVoiceEngine(
    var initializeResult: Boolean = true,
    var voices: List<SpanishVoiceOption> = listOf(
        SpanishVoiceOption("es-PE", "Español (Perú)", "es-PE", "PE"),
    ),
) : VoiceEngine {

    val initializeStarted = CompletableFuture<Unit>()
    private val result = CompletableDeferred<Boolean>()
    var selectedVoiceId: String? = null
    var initializeCalls = 0
    var stopCalls = 0
    var shutdownCalls = 0
    var speakCalls = 0
    val spoken = mutableListOf<Pair<String, String>>()

    fun completeInitialize(value: Boolean = initializeResult) {
        result.complete(value)
    }

    override suspend fun initialize(): Boolean {
        initializeCalls++
        initializeStarted.complete(Unit)
        return result.await()
    }

    override fun availableVoices(): List<SpanishVoiceOption> = voices

    override fun selectVoice(id: String?): Boolean {
        selectedVoiceId = id
        return true
    }

    override suspend fun speak(text: String, utteranceId: String): VoiceSpeakResult {
        speakCalls++
        spoken += text to utteranceId
        return VoiceSpeakResult.DONE
    }

    override fun stop() {
        stopCalls++
    }

    override fun shutdown() {
        shutdownCalls++
    }
}

/**
 * Motor cuyo `speak` suspende de forma cancelable, para comprobar que la cancelación no se
 * confunde con un fallo de reproducción.
 */
internal class CancellableVoiceEngine : VoiceEngine {

    val speakStarted = CompletableFuture<Unit>()
    val cancelled = CompletableFuture<Unit>()
    var stopCalls = 0
    var shutdownCalls = 0

    override suspend fun initialize(): Boolean = true

    override fun availableVoices(): List<SpanishVoiceOption> =
        listOf(SpanishVoiceOption("es-PE", "Español (Perú)", "es-PE", "PE"))

    override fun selectVoice(id: String?): Boolean = true

    override suspend fun speak(text: String, utteranceId: String): VoiceSpeakResult {
        speakStarted.complete(Unit)
        return suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { cancelled.complete(Unit) }
        }
    }

    override fun stop() {
        stopCalls++
    }

    override fun shutdown() {
        shutdownCalls++
    }
}

/**
 * Almacén de preferencias falso reutilizable, similar al del resto de la base de tests.
 */
internal class TestDataStore(
    private val initial: Preferences = emptyPreferences(),
    var readFailure: Throwable? = null,
    var writeFailure: Throwable? = null,
    var gate: CompletableDeferred<Unit>? = null,
    var gateAfterReads: Int = 0,
    var writeGate: CompletableDeferred<Unit>? = null,
) : DataStore<Preferences> {

    val stored: MutableStateFlow<Preferences> = MutableStateFlow(initial)
    var writeAttempts = 0
    val readCount = MutableStateFlow(0)
    val readStarted = CompletableFuture<Unit>()
    val writeStarted = CompletableFuture<Unit>()
    var reads = 0
    var writes = 0

    override val data: Flow<Preferences> = flow {
        reads++
        readCount.value = reads
        readStarted.complete(Unit)
        if (reads > gateAfterReads) gate?.await()
        readFailure?.let { throw it }
        emitAll(stored)
    }

    override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
        writeAttempts++
        writeFailure?.let { throw it }
        writeStarted.complete(Unit)
        writeGate?.await()
        writes++
        return transform(stored.value).also { stored.value = it }
    }
}

internal val VOICE_ENABLED = booleanPreferencesKey("voice_enabled")
internal val VOICE_NAME = stringPreferencesKey("voice_name")

internal fun runTest(body: suspend () -> Unit) = runBlocking { body() }
