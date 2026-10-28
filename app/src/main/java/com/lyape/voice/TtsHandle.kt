package com.lyape.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener

/** Resultado de la inicialización del motor, independiente de las constantes de Android. */
internal enum class TtsHandleInitResult {
    SUCCESS,
    ERROR,
}

/** Callbacks de locución ligados a una sesión concreta del motor. */
internal interface TtsUtteranceCallbacks {
    fun onDone(utteranceId: String)
    fun onStop(utteranceId: String, interrupted: Boolean)
    fun onError(utteranceId: String, errorCode: Int)
}

/**
 * Motor TTS mínimo e inyectable.
 *
 * `AndroidVoiceEngine` coordina sesiones, continuaciones y callbacks contra esta interfaz;
 * la implementación real envuelve `TextToSpeech` y las pruebas usan un doble determinista.
 */
internal interface TtsHandle {

    /** Inicia el motor. El callback se invoca una vez con el resultado. */
    fun initialize(onInit: (TtsHandleInitResult) -> Unit)

    /** Aplica los atributos de audio; falso si el motor los rechaza. */
    fun setAudioAttributes(profile: VoiceAudioProfile): Boolean

    /** Registra los callbacks de locución de esta sesión. */
    fun setUtteranceCallbacks(callbacks: TtsUtteranceCallbacks)

    /** Devuelve falso si el motor rechaza el anuncio de inmediato. */
    fun speak(text: String, utteranceId: String): Boolean

    fun stop()

    fun shutdown()

    fun voices(): List<VoiceDescriptor>

    fun selectVoice(id: String): Boolean
}

internal fun interface TtsHandleFactory {
    fun create(): TtsHandle
}

internal class AndroidTtsHandleFactory(private val context: Context) : TtsHandleFactory {
    override fun create(): TtsHandle = AndroidTtsHandle(context)
}

/**
 * Implementación de [TtsHandle] sobre `TextToSpeech`.
 *
 * El `onInit` puede llegar antes o después de que la sesión registre su callback; aquí se
 * retiene el resultado y se entrega una sola vez.
 */
private class AndroidTtsHandle(context: Context) : TtsHandle {

    private val initLock = Any()
    private var pendingInitResult: TtsHandleInitResult? = null
    private var pendingInitCallback: ((TtsHandleInitResult) -> Unit)? = null

    private val tts = TextToSpeech(context) { status ->
        val result = if (status == TextToSpeech.SUCCESS) {
            TtsHandleInitResult.SUCCESS
        } else {
            TtsHandleInitResult.ERROR
        }
        val callback: ((TtsHandleInitResult) -> Unit)?
        synchronized(initLock) {
            callback = pendingInitCallback
            pendingInitCallback = null
            if (callback == null) {
                pendingInitResult = result
            }
        }
        callback?.invoke(result)
    }

    override fun initialize(onInit: (TtsHandleInitResult) -> Unit) {
        val immediate: TtsHandleInitResult?
        synchronized(initLock) {
            immediate = pendingInitResult
            pendingInitResult = null
            if (immediate == null) {
                pendingInitCallback = onInit
            }
        }
        if (immediate != null) {
            onInit(immediate)
        }
    }

    override fun setAudioAttributes(profile: VoiceAudioProfile): Boolean = runCatching {
        tts.setAudioAttributes(VoiceAudioAttributes.build(profile)) == TextToSpeech.SUCCESS
    }.getOrDefault(false)

    override fun setUtteranceCallbacks(callbacks: TtsUtteranceCallbacks) {
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String) = Unit

            override fun onDone(utteranceId: String) {
                callbacks.onDone(utteranceId)
            }

            override fun onStop(utteranceId: String, interrupted: Boolean) {
                callbacks.onStop(utteranceId, interrupted)
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String) {
                callbacks.onError(utteranceId, -1)
            }

            override fun onError(utteranceId: String, errorCode: Int) {
                callbacks.onError(utteranceId, errorCode)
            }
        })
    }

    override fun speak(text: String, utteranceId: String): Boolean = runCatching {
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId) == TextToSpeech.SUCCESS
    }.getOrDefault(false)

    override fun stop() {
        runCatching { tts.stop() }
    }

    override fun shutdown() {
        runCatching {
            tts.stop()
            tts.shutdown()
        }
    }

    override fun voices(): List<VoiceDescriptor> = try {
        tts.voices.orEmpty().mapNotNull { voice ->
            val name = voice.name ?: return@mapNotNull null
            VoiceDescriptor(
                id = name,
                locale = voice.locale,
                isNetworkConnectionRequired = voice.isNetworkConnectionRequired,
                quality = voice.quality,
            )
        }
    } catch (error: Exception) {
        emptyList()
    }

    override fun selectVoice(id: String): Boolean {
        val voice = try {
            tts.voices?.firstOrNull { it.name == id }
        } catch (error: Exception) {
            null
        } ?: return false
        return runCatching {
            tts.setVoice(voice) == TextToSpeech.SUCCESS
        }.getOrDefault(false)
    }
}
