package com.lyape.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
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

    private companion object {
        val CAPTURE_PAUSED = booleanPreferencesKey("capture_paused")
    }
}
