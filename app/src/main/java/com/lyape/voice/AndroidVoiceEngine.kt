package com.lyape.voice

import android.content.Context
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Motor de texto a voz.
 *
 * Toda la coordinación del adaptador está serializada por un único bloqueo y ligada a una
 * sesión: el motor ([TtsHandle]), la continuación de inicialización, la continuación de
 * locución y los callbacks pertenecen a la misma generación. Un cierre o reinicio invalida
 * la sesión anterior; sus callbacks tardíos no pueden completar la nueva.
 *
 * La interfaz [TtsHandleFactory] es inyectable para probar esta coordinación en JVM sin
 * teléfono ni emulador. La implementación real envuelve `TextToSpeech`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AndroidVoiceEngine internal constructor(
    private val factory: TtsHandleFactory,
) : VoiceEngine {

    constructor(context: Context) : this(AndroidTtsHandleFactory(context.applicationContext))

    private val lock = Any()
    private var session = 0L
    private var handle: TtsHandle? = null
    private var ready = false
    private var initContinuation: CancellableContinuation<Boolean>? = null
    private var speakContinuation: CancellableContinuation<VoiceSpeakResult>? = null
    private var utteranceId: String? = null

    override suspend fun initialize(): Boolean {
        synchronized(lock) {
            if (ready && handle != null) return true
        }
        return suspendCancellableCoroutine { continuation ->
            if (!continuation.isActive) {
                return@suspendCancellableCoroutine
            }

            val invalidated: InvalidatedSession
            val expected: Long
            synchronized(lock) {
                invalidated = invalidateSessionLocked()
                expected = session
                initContinuation = continuation
            }
            release(invalidated)

            continuation.invokeOnCancellation {
                val cancelled: InvalidatedSession?
                synchronized(lock) {
                    cancelled = if (session == expected && initContinuation === continuation) {
                        invalidateSessionLocked()
                    } else {
                        null
                    }
                }
                cancelled?.let { release(it) }
            }

            val created = try {
                factory.create()
            } catch (error: Exception) {
                null
            }
            if (created == null) {
                finishInitialization(expected, false)
                return@suspendCancellableCoroutine
            }

            val bound = synchronized(lock) {
                if (session != expected) {
                    false
                } else {
                    handle = created
                    true
                }
            }
            if (!bound) {
                created.shutdown()
                return@suspendCancellableCoroutine
            }

            val configured = runCatching {
                created.setAudioAttributes(VoiceAudioAttributes.profile)
            }.getOrDefault(false)
            if (!isCurrent(expected)) {
                created.shutdown()
                return@suspendCancellableCoroutine
            }
            if (!configured) {
                finishInitialization(expected, false)
                return@suspendCancellableCoroutine
            }

            created.setUtteranceCallbacks(utteranceCallbacks(expected))
            if (!isCurrent(expected)) {
                created.shutdown()
                return@suspendCancellableCoroutine
            }

            created.initialize { result ->
                finishInitialization(expected, result == TtsHandleInitResult.SUCCESS)
            }
        }
    }

    private fun finishInitialization(expected: Long, success: Boolean) {
        val continuation: CancellableContinuation<Boolean>?
        var staleHandle: TtsHandle? = null
        synchronized(lock) {
            if (session != expected) return
            continuation = initContinuation ?: return
            initContinuation = null
            ready = success
            if (!success) {
                staleHandle = handle
                handle = null
            }
        }
        staleHandle?.shutdown()
        if (continuation?.isActive == true) {
            continuation.resume(success, null)
        }
    }

    override suspend fun speak(text: String, utteranceId: String): VoiceSpeakResult {
        return suspendCancellableCoroutine { continuation ->
            if (!continuation.isActive) {
                return@suspendCancellableCoroutine
            }

            var rejected = false
            var expected = 0L
            var selectedHandle: TtsHandle? = null
            synchronized(lock) {
                val current = handle
                if (!ready || current == null || speakContinuation != null) {
                    rejected = true
                } else {
                    expected = session
                    selectedHandle = current
                    speakContinuation = continuation
                    this.utteranceId = utteranceId
                }
            }
            if (rejected) {
                continuation.resume(VoiceSpeakResult.FAILED, null)
                return@suspendCancellableCoroutine
            }
            val currentHandle = selectedHandle
            if (currentHandle == null) {
                continuation.resume(VoiceSpeakResult.FAILED, null)
                return@suspendCancellableCoroutine
            }

            continuation.invokeOnCancellation {
                completeSpeak(expected, utteranceId, VoiceSpeakResult.STOPPED)
                currentHandle.stop()
            }

            if (!isCurrent(expected)) {
                completeSpeak(expected, utteranceId, VoiceSpeakResult.STOPPED)
                return@suspendCancellableCoroutine
            }

            val accepted = try {
                currentHandle.speak(text, utteranceId)
            } catch (error: Exception) {
                false
            }
            if (!accepted) {
                // Si un callback ya completó esta locución, esta llamada no hace nada.
                completeSpeak(expected, utteranceId, VoiceSpeakResult.FAILED)
            }
        }
    }

    private fun completeSpeak(
        expectedSession: Long,
        expectedUtteranceId: String,
        result: VoiceSpeakResult,
    ) {
        val continuation: CancellableContinuation<VoiceSpeakResult>?
        synchronized(lock) {
            if (session != expectedSession || utteranceId != expectedUtteranceId) return
            continuation = speakContinuation ?: return
            speakContinuation = null
            utteranceId = null
        }
        if (continuation?.isActive == true) {
            continuation.resume(result, null)
        }
    }

    private fun utteranceCallbacks(expected: Long): TtsUtteranceCallbacks =
        object : TtsUtteranceCallbacks {
            override fun onDone(utteranceId: String) {
                completeSpeak(expected, utteranceId, VoiceSpeakResult.DONE)
            }

            override fun onStop(utteranceId: String, interrupted: Boolean) {
                completeSpeak(expected, utteranceId, VoiceSpeakResult.STOPPED)
            }

            override fun onError(utteranceId: String, errorCode: Int) {
                completeSpeak(expected, utteranceId, VoiceSpeakResult.FAILED)
            }
        }

    override fun availableVoices(): List<SpanishVoiceOption> {
        val current = synchronized(lock) { handle } ?: return emptyList()
        return try {
            SpanishVoiceSelector.select(current.voices())
        } catch (error: Exception) {
            emptyList()
        }
    }

    override fun selectVoice(id: String?): Boolean {
        if (id == null) return false
        val current = synchronized(lock) { handle } ?: return false
        return try {
            current.selectVoice(id)
        } catch (error: Exception) {
            false
        }
    }

    override fun stop() {
        val expected: Long
        val currentUtterance: String?
        val currentHandle: TtsHandle?
        synchronized(lock) {
            expected = session
            currentUtterance = utteranceId
            currentHandle = handle
        }
        currentHandle?.stop()
        if (currentUtterance != null) {
            completeSpeak(expected, currentUtterance, VoiceSpeakResult.STOPPED)
        }
    }

    override fun shutdown() {
        val invalidated: InvalidatedSession
        synchronized(lock) {
            invalidated = invalidateSessionLocked()
        }
        release(invalidated)
    }

    private fun isCurrent(expected: Long): Boolean =
        synchronized(lock) { session == expected }

    private fun invalidateSessionLocked(): InvalidatedSession {
        session++
        ready = false
        val invalidated = InvalidatedSession(
            handle = handle,
            initialization = initContinuation,
            speech = speakContinuation,
        )
        handle = null
        initContinuation = null
        speakContinuation = null
        utteranceId = null
        return invalidated
    }

    private fun release(invalidated: InvalidatedSession) {
        invalidated.handle?.shutdown()
        invalidated.initialization?.let { continuation ->
            if (continuation.isActive) {
                continuation.resume(false, null)
            }
        }
        invalidated.speech?.let { continuation ->
            if (continuation.isActive) {
                continuation.resume(VoiceSpeakResult.STOPPED, null)
            }
        }
    }

    private data class InvalidatedSession(
        val handle: TtsHandle?,
        val initialization: CancellableContinuation<Boolean>?,
        val speech: CancellableContinuation<VoiceSpeakResult>?,
    )
}
