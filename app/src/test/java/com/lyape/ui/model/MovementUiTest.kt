package com.lyape.ui.model

import com.lyape.domain.Movement
import com.lyape.domain.MovementClassification
import com.lyape.domain.MovementSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

/** La vista de un movimiento es solo presentación: no toca Android ni Room. */
class MovementUiTest {

    private val lima = ZoneId.of("America/Lima")

    @Test
    fun `mapea un pago capturado a la vista de lista`() {
        val ui = movement().toUi(lima)

        assertEquals("María Rojas", ui.title)
        assertEquals("Detectado", ui.originLabel)
        assertEquals("5:42 pm", ui.timeLabel)
        assertEquals("MR", ui.avatarLabel)
        assertEquals(MovementClassification.BUSINESS, ui.classification)
        assertFalse(ui.isExcluded)
        assertFalse(ui.reviewed)
    }

    @Test
    fun `respeta el formato de 24 horas del sistema`() {
        val ui = movement().toUi(lima, use24Hour = true)

        assertEquals("17:42", ui.timeLabel)
        assertEquals("15 oct. 2026 · 17:42", ui.dateTimeLabel)
    }

    @Test
    fun `sin remitente usa un titulo de respaldo segun el origen`() {
        assertEquals(
            "Pago sin nombre",
            movement(senderName = null, source = MovementSource.NOTIFICATION).toUi(lima).title,
        )
        assertEquals(
            "Venta agregada",
            movement(senderName = null, source = MovementSource.MANUAL).toUi(lima).title,
        )
        val adjustment = movement(
            senderName = null,
            source = MovementSource.ADJUSTMENT,
            adjustmentReason = "Devolución",
        ).toUi(lima)
        assertEquals("Ajuste", adjustment.title)
        assertEquals("Ajuste", adjustment.originLabel)
        assertEquals("Devolución", adjustment.supportingLabel)
    }

    @Test
    fun `marca los movimientos anulados`() {
        val ui = movement(classification = MovementClassification.EXCLUDED).toUi(lima)
        assertTrue(ui.isExcluded)
    }

    @Test
    fun `construye el avatar con una o dos palabras`() {
        assertEquals("PE", movement(senderName = "Pedro").toUi(lima).avatarLabel)
        assertEquals("+", movement(senderName = "   ").toUi(lima).avatarLabel)
    }

    @Test
    fun `usa el monto en centimos sin convertirlo`() {
        assertEquals(3_500L, movement(amountCents = 3_500).toUi(lima).amountCents)
    }

    @Test
    fun `formatea la fecha completa y recuerda la clasificacion previa`() {
        val ui = movement(
            classification = MovementClassification.EXCLUDED,
            previousClassification = MovementClassification.PERSONAL,
        ).toUi(lima)

        assertTrue(ui.isExcluded)
        assertEquals(MovementClassification.PERSONAL, ui.previousClassification)
        assertTrue(ui.dateTimeLabel.contains("2026"))
        assertTrue(ui.dateTimeLabel.contains("5:42 pm"))
    }

    private fun movement(
        senderName: String? = "María Rojas",
        source: MovementSource = MovementSource.NOTIFICATION,
        classification: MovementClassification = MovementClassification.BUSINESS,
        amountCents: Long = 3_500,
        occurredAt: Instant = Instant.parse("2026-10-15T22:42:00Z"),
        reviewed: Boolean = false,
        previousClassification: MovementClassification? = null,
        adjustmentReason: String? = null,
    ): Movement = Movement(
        id = UUID.fromString("00000000-0000-0000-0000-000000000001"),
        amountCents = amountCents,
        occurredAt = occurredAt,
        capturedAt = occurredAt,
        source = source,
        classification = classification,
        reviewed = reviewed,
        createdAt = occurredAt,
        updatedAt = occurredAt,
        senderName = senderName,
        adjustmentReason = adjustmentReason,
        previousClassification = previousClassification,
    )
}
