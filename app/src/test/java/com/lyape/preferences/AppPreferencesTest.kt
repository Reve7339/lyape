package com.lyape.preferences

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.preferencesOf
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class AppPreferencesTest {
    private val pausedKey = booleanPreferencesKey("capture_paused")

    @Test
    fun `una instalacion nueva conserva la captura habilitada`() = runBlocking {
        assertFalse(AppPreferences(FakeStore()).capturePaused.first())
    }

    @Test
    fun `respeta una pausa guardada`() = runBlocking {
        val store = FakeStore(preferencesOf(pausedKey to true))
        assertTrue(AppPreferences(store).capturePaused.first())
    }

    @Test
    fun `respeta una reactivacion guardada`() = runBlocking {
        val store = FakeStore(preferencesOf(pausedKey to false))
        assertFalse(AppPreferences(store).capturePaused.first())
    }

    @Test
    fun `un fallo de lectura bloquea la captura sin modificar lo guardado`() = runBlocking {
        val store = FakeStore(preferencesOf(pausedKey to true)).apply {
            readFailure = IOException("disco no disponible")
        }
        assertTrue(AppPreferences(store).capturePaused.first())
        assertTrue(store.stored.value[pausedKey] == true)
        assertEquals(0, store.writes)
    }

    @Test
    fun `datos corruptos no habilitan la captura`() = runBlocking {
        val store = FakeStore().apply { readFailure = CorruptionException("datos invalidos") }
        assertTrue(AppPreferences(store).capturePaused.first())
    }

    @Test
    fun `un fallo inesperado de lectura tampoco habilita la captura`() = runBlocking {
        val store = FakeStore().apply { readFailure = IllegalStateException("no disponible") }
        assertTrue(AppPreferences(store).capturePaused.first())
    }

    @Test
    fun `la cancelacion no se transforma en una preferencia`() = runBlocking {
        val cancellation = CancellationException("coleccion cancelada")
        val store = FakeStore().apply { readFailure = cancellation }
        try {
            AppPreferences(store).capturePaused.first()
            fail("La cancelacion debe propagarse")
        } catch (actual: CancellationException) {
            assertSame(cancellation, actual)
        }
    }

    @Test
    fun `una nueva lectura recupera la pausa persistida tras un fallo temporal`() = runBlocking {
        val store = FakeStore(preferencesOf(pausedKey to true)).apply {
            readFailure = IOException("fallo temporal")
        }
        val preferences = AppPreferences(store)
        assertTrue(preferences.capturePaused.first())
        store.readFailure = null
        assertTrue(preferences.capturePaused.first())
        assertEquals(2, store.reads)
        assertEquals(0, store.writes)
    }

    @Test
    fun `un fallo despues de una lectura activa bloquea la captura`() = runBlocking {
        val store = object : DataStore<Preferences> {
            override val data: Flow<Preferences> = flow {
                emit(preferencesOf(pausedKey to false))
                throw IOException("fallo posterior")
            }

            override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
                error("Esta prueba no debe escribir")
        }
        assertEquals(listOf(false, true), AppPreferences(store).capturePaused.toList())
    }

    @Test
    fun `pausar y reactivar persisten la eleccion`() = runBlocking {
        val store = FakeStore()
        val preferences = AppPreferences(store)
        preferences.setCapturePaused(true)
        assertTrue(preferences.capturePaused.first())
        preferences.setCapturePaused(false)
        assertFalse(preferences.capturePaused.first())
        assertEquals(2, store.writes)
    }

    @Test
    fun `una escritura fallida no borra la pausa guardada`() = runBlocking {
        val failure = IOException("sin espacio")
        val store = FakeStore(preferencesOf(pausedKey to true)).apply { writeFailure = failure }
        val preferences = AppPreferences(store)
        try {
            preferences.setCapturePaused(false)
            fail("La escritura fallida debe notificarse")
        } catch (actual: IOException) {
            assertSame(failure, actual)
        }
        assertTrue(preferences.capturePaused.first())
    }

    @Test
    fun `el estado distingue un fallo de una pausa voluntaria`() = runBlocking {
        val store = FakeStore().apply { readFailure = IOException("fallo temporal") }
        assertEquals(CapturePreferenceState.UNAVAILABLE, AppPreferences(store).captureState.first())
    }

    @Test
    fun `reintentar tras recuperar la lectura respeta la captura habilitada`() = runBlocking {
        val store = FakeStore(preferencesOf(pausedKey to false)).apply {
            readFailure = IOException("fallo temporal")
        }
        val preferences = AppPreferences(store)
        assertEquals(CapturePreferenceState.UNAVAILABLE, preferences.captureState.first())
        store.readFailure = null
        assertEquals(CapturePreferenceState.ACTIVE, preferences.captureState.first())
        assertEquals(0, store.writes)
    }

    @Test
    fun `el estado de una pausa persistida es distinto de no disponible`() = runBlocking {
        val preferences = AppPreferences(FakeStore(preferencesOf(pausedKey to true)))
        assertEquals(CapturePreferenceState.PAUSED, preferences.captureState.first())
    }

    @Test
    fun `los errores fatales no se ocultan como una preferencia`() = runBlocking {
        val failure = AssertionError("fallo fatal simulado")
        val preferences = AppPreferences(FakeStore().apply { readFailure = failure })
        var actual: AssertionError? = null
        try {
            preferences.captureState.first()
        } catch (error: AssertionError) {
            actual = error
        }
        assertSame(failure, actual)
    }

    private class FakeStore(initial: Preferences = emptyPreferences()) : DataStore<Preferences> {
        val stored = MutableStateFlow(initial)
        var readFailure: Throwable? = null
        var writeFailure: Throwable? = null
        var reads = 0
        var writes = 0

        override val data: Flow<Preferences> = flow {
            reads++
            readFailure?.let { throw it }
            emitAll(stored)
        }

        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
            writeFailure?.let { throw it }
            writes++
            return transform(stored.value).also { stored.value = it }
        }
    }
}
