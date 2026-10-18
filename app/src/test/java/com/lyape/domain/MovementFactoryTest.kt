package com.lyape.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.Instant
import java.util.UUID

class MovementFactoryTest {

    private val now: Instant = Instant.parse("2026-10-15T22:00:00Z")
    private val occurredAt: Instant = Instant.parse("2026-10-15T18:00:00Z")
    private val id = UUID.fromString("00000000-0000-0000-0000-0000000000AA")

    @Test
    fun `un pago manual nace como ingreso del negocio pendiente`() {
        val movement = MovementFactory.manual(id, 4_800, occurredAt, "  Bodega Luna  ", now)

        assertEquals(id, movement.id)
        assertEquals(4_800, movement.amountCents)
        assertEquals(occurredAt, movement.occurredAt)
        assertEquals(MovementSource.MANUAL, movement.source)
        assertEquals(MovementClassification.BUSINESS, movement.classification)
        assertFalse(movement.reviewed)
        assertEquals(now, movement.capturedAt)
        assertEquals(now, movement.createdAt)
        assertEquals(now, movement.updatedAt)
        assertEquals("Bodega Luna", movement.senderName)
        assertNull(movement.notificationKey)
        assertNull(movement.previousClassification)
    }

    @Test
    fun `un pago manual sin etiqueta deja el remitente en nulo`() {
        val movement = MovementFactory.manual(id, 1_000, occurredAt, "   ", now)
        assertNull(movement.senderName)
    }

    @Test
    fun `un pago manual debe ser positivo`() {
        assertThrows(IllegalArgumentException::class.java) {
            MovementFactory.manual(id, 0, occurredAt, null, now)
        }
        assertThrows(IllegalArgumentException::class.java) {
            MovementFactory.manual(id, -500, occurredAt, null, now)
        }
    }

    @Test
    fun `un ajuste negativo con motivo se registra como ajuste`() {
        val movement = MovementFactory.adjustment(id, -800, occurredAt, "Devolución", now)

        assertEquals(-800, movement.amountCents)
        assertEquals(MovementSource.ADJUSTMENT, movement.source)
        assertEquals(MovementClassification.BUSINESS, movement.classification)
        assertEquals("Devolución", movement.adjustmentReason)
        assertFalse(movement.reviewed)
    }

    @Test
    fun `un ajuste positivo tambien es un ajuste`() {
        val movement = MovementFactory.adjustment(id, 1_200, occurredAt, "Propina", now)
        assertEquals(1_200, movement.amountCents)
        assertEquals(MovementSource.ADJUSTMENT, movement.source)
    }

    @Test
    fun `un ajuste no puede ser cero y necesita motivo`() {
        assertThrows(IllegalArgumentException::class.java) {
            MovementFactory.adjustment(id, 0, occurredAt, "Motivo", now)
        }
        assertThrows(IllegalArgumentException::class.java) {
            MovementFactory.adjustment(id, -100, occurredAt, "   ", now)
        }
    }
}
