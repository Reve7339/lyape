package com.lyape.ui.demo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoDataTest {

    @Test
    fun `los totales de demostracion son coherentes con la lista`() {
        // Negocio: 3500 + 1650 + 12450 + 2800 + 4800 - 800
        assertEquals(24_400, DemoData.businessTotalCents)
        assertEquals(2_000, DemoData.personalTotalCents)
    }

    @Test
    fun `los pendientes son movimientos detectados sin revisar`() {
        assertEquals(2, DemoData.pending.size)
        assertTrue(DemoData.pending.all { it.source == DemoSource.DETECTED && !it.reviewed })
    }

    @Test
    fun `busca un movimiento por identificador`() {
        val movement = DemoData.movementById("demo-001")

        assertNotNull(movement)
        assertEquals("María Rojas", movement?.senderName)
    }

    @Test
    fun `un identificador desconocido no devuelve movimiento`() {
        assertEquals(null, DemoData.movementById("no-existe"))
    }

    @Test
    fun `el avatar usa iniciales o un signo mas`() {
        val named = DemoMovement(
            id = "x",
            senderName = "María Rojas",
            amountCents = 100,
            source = DemoSource.DETECTED,
            classification = DemoClassification.BUSINESS,
            timeLabel = "1:00 pm",
            detailLabel = "x",
        )
        val manual = DemoMovement(
            id = "y",
            senderName = null,
            amountCents = 100,
            source = DemoSource.MANUAL,
            classification = DemoClassification.BUSINESS,
            timeLabel = "1:00 pm",
            detailLabel = "x",
        )

        assertEquals("MR", named.avatarLabel)
        assertEquals("+", manual.avatarLabel)
        assertEquals("Venta agregada", manual.title)
    }

    @Test
    fun `la curva de demostracion tiene puntos suficientes para dibujar`() {
        assertTrue(DemoData.dayCurve.size >= 2)
    }
}
