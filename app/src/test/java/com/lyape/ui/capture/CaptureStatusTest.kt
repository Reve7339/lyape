package com.lyape.ui.capture

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * El estado de captura combina acceso a notificaciones y pausa con la misma regla en Ajustes y en
 * la barra superior del Resumen.
 */
class CaptureStatusTest {

    @Test
    fun `sin acceso siempre es sin permiso`() {
        assertEquals(
            CaptureStatus.NO_PERMISSION,
            captureStatus(hasAccess = false, paused = false),
        )
        assertEquals(
            CaptureStatus.NO_PERMISSION,
            captureStatus(hasAccess = false, paused = true),
        )
    }

    @Test
    fun `con acceso la pausa define el estado`() {
        assertEquals(CaptureStatus.PAUSED, captureStatus(hasAccess = true, paused = true))
        assertEquals(CaptureStatus.ACTIVE, captureStatus(hasAccess = true, paused = false))
    }

    @Test
    fun `cada estado tiene su etiqueta`() {
        assertEquals("Activa", CaptureStatus.ACTIVE.label)
        assertEquals("Pausada", CaptureStatus.PAUSED.label)
        assertEquals("Sin permiso", CaptureStatus.NO_PERMISSION.label)
    }
}
