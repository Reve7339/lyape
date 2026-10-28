package com.lyape.voice

/**
 * Motor TTS falso que permite controlar exactamente cuándo llega cada callback.
 *
 * Los callbacks se conservan aunque se invoquen varias veces para poder simular entregas
 * duplicadas o tardías después de un cierre.
 */
internal class FakeTtsHandle : TtsHandle {

    var attributesConfigured = true
    var initCalls = 0
    var onInit: ((TtsHandleInitResult) -> Unit)? = null
    var callbacks: TtsUtteranceCallbacks? = null

    val spoken = mutableListOf<String>()
    var speakReturn = true
    var onSpeak: ((String) -> Unit)? = null
    var stopCalls = 0
    var shutdownCalls = 0
    var selectedVoice: String? = null
    var voicesList: List<VoiceDescriptor> = emptyList()

    fun completeInit(result: TtsHandleInitResult = TtsHandleInitResult.SUCCESS) {
        onInit?.invoke(result)
    }

    fun done(utteranceId: String) {
        callbacks?.onDone(utteranceId)
    }

    fun stopped(utteranceId: String) {
        callbacks?.onStop(utteranceId, false)
    }

    fun error(utteranceId: String) {
        callbacks?.onError(utteranceId, -1)
    }

    override fun initialize(onInit: (TtsHandleInitResult) -> Unit) {
        initCalls++
        this.onInit = onInit
    }

    override fun setAudioAttributes(profile: VoiceAudioProfile): Boolean = attributesConfigured

    override fun setUtteranceCallbacks(callbacks: TtsUtteranceCallbacks) {
        this.callbacks = callbacks
    }

    override fun speak(text: String, utteranceId: String): Boolean {
        spoken += utteranceId
        onSpeak?.invoke(utteranceId)
        return speakReturn
    }

    override fun stop() {
        stopCalls++
    }

    override fun shutdown() {
        shutdownCalls++
    }

    override fun voices(): List<VoiceDescriptor> = voicesList

    override fun selectVoice(id: String): Boolean {
        selectedVoice = id
        return true
    }
}

internal class FakeTtsHandleFactory : TtsHandleFactory {

    val handles = mutableListOf<FakeTtsHandle>()

    val last: FakeTtsHandle
        get() = handles.last()

    override fun create(): TtsHandle = FakeTtsHandle().also { handles += it }
}
