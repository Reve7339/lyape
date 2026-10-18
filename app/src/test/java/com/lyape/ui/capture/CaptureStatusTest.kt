package com.lyape.ui.capture

import com.lyape.preferences.CapturePreferenceState
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Ajustes distingue el permiso, la preferencia guardada y los fallos de lectura.
 */
class CaptureStatusTest {

    @Test
    fun `sin acceso siempre es sin permiso`() {
        assertEquals(
            CaptureStatus.NO_PERMISSION,
            captureStatus(hasAccess = false, preference = CapturePreferenceState.ACTIVE),
        )
        assertEquals(
            CaptureStatus.NO_PERMISSION,
            captureStatus(hasAccess = false, preference = CapturePreferenceState.PAUSED),
        )
        assertEquals(CaptureStatus.NO_PERMISSION, captureStatus(false, CapturePreferenceState.UNAVAILABLE))
        assertEquals(CaptureStatus.NO_PERMISSION, captureStatus(false, null))
    }

    @Test
    fun `con acceso la pausa define el estado`() {
        assertEquals(CaptureStatus.PAUSED, captureStatus(true, CapturePreferenceState.PAUSED))
        assertEquals(CaptureStatus.ACTIVE, captureStatus(true, CapturePreferenceState.ACTIVE))
    }

    @Test
    fun `un fallo de lectura no se presenta como una pausa voluntaria`() {
        assertEquals(CaptureStatus.UNAVAILABLE, captureStatus(true, CapturePreferenceState.UNAVAILABLE))
    }

    @Test
    fun `antes de leer las preferencias no anuncia captura activa`() {
        assertEquals(CaptureStatus.LOADING, captureStatus(true, null))
    }

    @Test
    fun `cada estado tiene su etiqueta`() {
        assertEquals("Activa", CaptureStatus.ACTIVE.label)
        assertEquals("Pausada", CaptureStatus.PAUSED.label)
        assertEquals("Sin permiso", CaptureStatus.NO_PERMISSION.label)
        assertEquals("Comprobando", CaptureStatus.LOADING.label)
        assertEquals("No disponible", CaptureStatus.UNAVAILABLE.label)
    }
}
