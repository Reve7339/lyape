package com.lyape.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.util.UUID

/** Las transiciones de conciliación son puras: se prueban sin Android ni Room. */
class MovementEditsTest {

    private val base: Instant = Instant.parse("2026-10-15T22:00:00Z")
    private val later: Instant = base.plusSeconds(60)

    private fun movement(
        source: MovementSource = MovementSource.NOTIFICATION,
        classification: MovementClassification = MovementClassification.BUSINESS,
        amountCents: Long = 3_500,
        previousClassification: MovementClassification? = null,
        reviewed: Boolean = false,
    ): Movement = Movement(
        id = UUID.fromString("00000000-0000-0000-0000-000000000001"),
        amountCents = amountCents,
        occurredAt = base,
        capturedAt = base,
        source = source,
        classification = classification,
        reviewed = reviewed,
        createdAt = base,
        updatedAt = base,
        senderName = "María Rojas",
        notificationKey = "clave-android",
        deduplicationHash = "huella",
        previousClassification = previousClassification,
    )

    // --- Clasificar ---

    @Test
    fun `reclasificar a personal cambia la clasificacion y marca edicion`() {
        val updated = MovementEdits.classify(
            movement(),
            MovementClassification.PERSONAL,
            later,
        )

        assertEquals(MovementClassification.PERSONAL, updated.classification)
        assertNull(updated.previousClassification)
        assertTrue(updated.isEdited)
        assertEquals(later, updated.updatedAt)
        // La trazabilidad no se toca.
        assertEquals(MovementSource.NOTIFICATION, updated.source)
        assertEquals(base, updated.capturedAt)
        assertEquals(base, updated.createdAt)
        assertEquals(base, updated.occurredAt)
        assertEquals("clave-android", updated.notificationKey)
        assertEquals("huella", updated.deduplicationHash)
    }

    @Test
    fun `reclasificar al mismo valor no marca edicion`() {
        val original = movement(classification = MovementClassification.PERSONAL)

        assertEquals(original, MovementEdits.classify(original, MovementClassification.PERSONAL, later))
    }

    @Test
    fun `no se puede clasificar directamente como anulado`() {
        assertThrows(IllegalArgumentException::class.java) {
            MovementEdits.classify(movement(), MovementClassification.EXCLUDED, later)
        }
    }

    // --- Anular ---

    @Test
    fun `anular recuerda la clasificacion de negocio`() {
        val excluded = MovementEdits.exclude(movement(), later)

        assertEquals(MovementClassification.EXCLUDED, excluded.classification)
        assertEquals(MovementClassification.BUSINESS, excluded.previousClassification)
        assertTrue(excluded.isEdited)
        assertEquals(later, excluded.updatedAt)
    }

    @Test
    fun `anular recuerda la clasificacion personal`() {
        val excluded = MovementEdits.exclude(
            movement(classification = MovementClassification.PERSONAL),
            later,
        )

        assertEquals(MovementClassification.EXCLUDED, excluded.classification)
        assertEquals(MovementClassification.PERSONAL, excluded.previousClassification)
    }

    @Test
    fun `anular dos veces no cambia nada`() {
        val excluded = movement(
            classification = MovementClassification.EXCLUDED,
            previousClassification = MovementClassification.BUSINESS,
        )

        assertEquals(excluded, MovementEdits.exclude(excluded, later))
    }

    // --- Restaurar ---

    @Test
    fun `restaurar devuelve el movimiento a negocio`() {
        val restored = MovementEdits.restore(
            movement(
                classification = MovementClassification.EXCLUDED,
                previousClassification = MovementClassification.BUSINESS,
            ),
            later,
        )

        assertEquals(MovementClassification.BUSINESS, restored.classification)
        assertNull(restored.previousClassification)
        assertTrue(restored.isEdited)
    }

    @Test
    fun `restaurar devuelve el movimiento a personal`() {
        val restored = MovementEdits.restore(
            movement(
                classification = MovementClassification.EXCLUDED,
                previousClassification = MovementClassification.PERSONAL,
            ),
            later,
        )

        assertEquals(MovementClassification.PERSONAL, restored.classification)
        assertNull(restored.previousClassification)
    }

    @Test
    fun `restaurar sin recuerdo vuelve a negocio`() {
        val restored = MovementEdits.restore(
            movement(classification = MovementClassification.EXCLUDED),
            later,
        )

        assertEquals(MovementClassification.BUSINESS, restored.classification)
    }

    @Test
    fun `restaurar un movimiento activo no cambia nada`() {
        val active = movement(classification = MovementClassification.PERSONAL)

        assertEquals(active, MovementEdits.restore(active, later))
    }

    // --- Editar ---

    @Test
    fun `editar cambia solo los campos conciliables`() {
        val edited = MovementEdits.edit(
            movement = movement(),
            amountCents = 4_200,
            occurredAt = base.plusSeconds(7_200),
            senderName = "  José Torres  ",
            description = "Nota corregida",
            adjustmentReason = null,
            now = later,
        )

        assertEquals(4_200, edited.amountCents)
        assertEquals(base.plusSeconds(7_200), edited.occurredAt)
        assertEquals("José Torres", edited.senderName)
        assertEquals("Nota corregida", edited.description)
        assertTrue(edited.isEdited)
        assertEquals(later, edited.updatedAt)
        assertEquals(MovementSource.NOTIFICATION, edited.source)
        assertEquals(MovementClassification.BUSINESS, edited.classification)
        assertEquals(base, edited.capturedAt)
        assertEquals("huella", edited.deduplicationHash)
    }

    @Test
    fun `editar normaliza etiquetas en blanco a nulo`() {
        val edited = MovementEdits.edit(
            movement = movement(),
            amountCents = 3_500,
            occurredAt = base,
            senderName = "   ",
            description = "",
            adjustmentReason = null,
            now = later,
        )

        assertNull(edited.senderName)
        assertNull(edited.description)
    }

    @Test
    fun `un pago editado no puede quedar en cero ni negativo`() {
        assertThrows(IllegalArgumentException::class.java) {
            MovementEdits.edit(movement(), 0, base, null, null, null, later)
        }
        assertThrows(IllegalArgumentException::class.java) {
            MovementEdits.edit(movement(), -100, base, null, null, null, later)
        }
    }

    @Test
    fun `un ajuste editado puede ser negativo pero necesita motivo`() {
        val edited = MovementEdits.edit(
            movement = movement(source = MovementSource.ADJUSTMENT),
            amountCents = -1_500,
            occurredAt = base,
            senderName = null,
            description = null,
            adjustmentReason = "Devolución",
            now = later,
        )
        assertEquals(-1_500, edited.amountCents)
        assertEquals("Devolución", edited.adjustmentReason)

        assertThrows(IllegalArgumentException::class.java) {
            MovementEdits.edit(
                movement(source = MovementSource.ADJUSTMENT),
                -1_500,
                base,
                null,
                null,
                "   ",
                later,
            )
        }
    }

    @Test
    fun `editar un movimiento conserva la clasificacion`() {
        val edited = MovementEdits.edit(
            movement = movement(classification = MovementClassification.PERSONAL),
            amountCents = 1_000,
            occurredAt = base,
            senderName = null,
            description = null,
            adjustmentReason = null,
            now = later,
        )

        assertEquals(MovementClassification.PERSONAL, edited.classification)
        assertFalse(edited.isExcluded())
    }

    private fun Movement.isExcluded(): Boolean = classification == MovementClassification.EXCLUDED
}
