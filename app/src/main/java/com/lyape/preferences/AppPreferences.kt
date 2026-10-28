package com.lyape.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.lyape.voice.VoicePreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "lyape_preferences")

enum class CapturePreferenceState { ACTIVE, PAUSED, UNAVAILABLE }

/**
 * Preferencias locales mínimas de LYape.
 *
 * Todo vive en el dispositivo: no hay cuentas, servidor ni telemetría. Por ahora solo se guarda
 * si la captura está pausada; el resto de la configuración se decide en el teléfono.
 */
class AppPreferences(private val dataStore: DataStore<Preferences>) {

    constructor(context: Context) : this(context.applicationContext.dataStore)

    /** Una lectura fallida no equivale a una instalación nueva ni modifica lo persistido. */
    val captureState: Flow<CapturePreferenceState> = dataStore.data
        .map { preferences ->
            if (preferences[CAPTURE_PAUSED] == true) CapturePreferenceState.PAUSED
            else CapturePreferenceState.ACTIVE
        }
        .catch { error ->
            if (error is CancellationException || error !is Exception) throw error
            emit(CapturePreferenceState.UNAVAILABLE)
        }

    /** La captura también se bloquea si no se puede comprobar la preferencia. */
    val capturePaused: Flow<Boolean> = captureState.map { it != CapturePreferenceState.ACTIVE }

    suspend fun setCapturePaused(paused: Boolean) {
        dataStore.edit { preferences -> preferences[CAPTURE_PAUSED] = paused }
    }

    // ---------------------------------------------------------------------
    // Voz
    // ---------------------------------------------------------------------

    /** Preferencias de avisos de voz. Una lectura fallida devuelve `null`. */
    val voicePreferences: Flow<VoicePreferences?> = flow {
        try {
            emitAll(
                dataStore.data
                    .map { preferences ->
                        VoicePreferences(
                            enabled = preferences[VOICE_ENABLED] == true,
                            voiceId = preferences[VOICE_NAME],
                        )
                    }
            )
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            emit(null)
        }
    }

    /**
     * Activa o desactiva los avisos de voz.
     *
     * La voz está apagada por defecto y es independiente de la captura de pagos.
     */
    suspend fun setVoiceEnabled(enabled: Boolean) {
        dataStore.edit { preferences -> preferences[VOICE_ENABLED] = enabled }
    }

    suspend fun setVoiceName(name: String?) {
        dataStore.edit { preferences ->
            if (name == null) {
                preferences.remove(VOICE_NAME)
            } else {
                preferences[VOICE_NAME] = name
            }
        }
    }

    /** La explicación del aviso persistente se muestra una vez, antes de la primera sesión. */
    val voiceNoticeShown: Flow<Boolean> = dataStore.data
        .map { preferences -> preferences[VOICE_NOTICE_SHOWN] == true }
        .catch { error ->
            if (error is CancellationException || error !is Exception) throw error
            emit(false)
        }

    suspend fun setVoiceNoticeShown() {
        dataStore.edit { preferences -> preferences[VOICE_NOTICE_SHOWN] = true }
    }

    // ---------------------------------------------------------------------
    // Privados
    // ---------------------------------------------------------------------

    private companion object {
        val CAPTURE_PAUSED = booleanPreferencesKey("capture_paused")

        val VOICE_ENABLED = booleanPreferencesKey("voice_enabled")
        val VOICE_NAME = stringPreferencesKey("voice_name")
        val VOICE_NOTICE_SHOWN = booleanPreferencesKey("voice_notice_shown")
    }
}
