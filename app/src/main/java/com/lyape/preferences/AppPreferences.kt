package com.lyape.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "lyape_preferences")

/**
 * Preferencias locales mínimas de LYape.
 *
 * Todo vive en el dispositivo: no hay cuentas, servidor ni telemetría. Por ahora solo se guarda
 * si la captura está pausada; el resto de la configuración se decide en el teléfono.
 */
class AppPreferences(private val dataStore: DataStore<Preferences>) {

    constructor(context: Context) : this(context.applicationContext.dataStore)

    /** `true` cuando el usuario pausó la captura desde Ajustes. */
    val capturePaused: Flow<Boolean> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { preferences -> preferences[CAPTURE_PAUSED] ?: false }

    suspend fun setCapturePaused(paused: Boolean) {
        dataStore.edit { preferences -> preferences[CAPTURE_PAUSED] = paused }
    }

    private companion object {
        val CAPTURE_PAUSED = booleanPreferencesKey("capture_paused")
    }
}
