package com.lyape.ui.movements

import com.lyape.domain.Movement
import com.lyape.domain.MovementClassification
import com.lyape.domain.MovementSource
import com.lyape.ui.model.MovementUi
import com.lyape.ui.model.toUi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

class MovementSearchTest {

    private val lima = ZoneId.of("America/Lima")

    @Test
    fun `normaliza mayusculas tildes y espacios`() {
        assertEquals("maria rojas", normalizeForSearch("  María Rojas "))
        assertEquals("deposito a plazo", normalizeForSearch("Depósito Á PLAZO"))
        assertEquals("ajuste", normalizeForSearch("AJUSTÉ"))
    }

    @Test
    fun `una consulta vacia coincide con todo`() {
        val ui = ui(title = "María Rojas")

        assertTrue(ui.matchesSearch(""))
        assertTrue(ui.matchesSearch(normalizeForSearch("   ")))
    }

    @Test
    fun `busca por remitente sin distinguir mayusculas ni tildes`() {
        val ui = ui(title = "María Rojas")

        assertTrue(ui.matchesSearch(normalizeForSearch("maria")))
        assertTrue(ui.matchesSearch(normalizeForSearch("ROJAS")))
        assertTrue(ui.matchesSearch(normalizeForSearch("María")))
        assertFalse(ui.matchesSearch(normalizeForSearch("Carlos")))
    }

    @Test
    fun `busca por descripcion`() {
        val ui = ui(title = "Venta agregada", description = "Pago de servicio de delivery")

        assertTrue(ui.matchesSearch(normalizeForSearch("delivery")))
        assertTrue(ui.matchesSearch(normalizeForSearch("SERVICIO")))
        assertFalse(ui.matchesSearch(normalizeForSearch("alquiler")))
    }

    @Test
    fun `busca por motivo de ajuste`() {
        val ui = ui(title = "Ajuste", supportingLabel = "Devolución de cliente")

        assertTrue(ui.matchesSearch(normalizeForSearch("devolucion")))
        assertTrue(ui.matchesSearch(normalizeForSearch("cliente")))
        assertFalse(ui.matchesSearch(normalizeForSearch("propina")))
    }

    @Test
    fun `sin coincidencia devuelve falso`() {
        val ui = ui(title = "María Rojas", description = "Cobro")

        assertFalse(ui.matchesSearch(normalizeForSearch("zzz")))
    }

    @Test
    fun `la vista expone la descripcion del movimiento`() {
        val occurredAt = Instant.parse("2026-10-15T22:42:00Z")
        val movement = Movement(
            id = UUID.randomUUID(),
            amountCents = 1_000,
            occurredAt = occurredAt,
            capturedAt = occurredAt,
            source = MovementSource.MANUAL,
            classification = MovementClassification.BUSINESS,
            reviewed = false,
            createdAt = occurredAt,
            updatedAt = occurredAt,
            description = "  Venta de mostrador  ",
        )

        val ui = movement.toUi(lima)

        assertEquals("Venta de mostrador", ui.description)
        assertTrue(ui.matchesSearch(normalizeForSearch("mostrador")))
    }

    private fun ui(
        title: String,
        description: String? = null,
        supportingLabel: String? = null,
    ): MovementUi = MovementUi(
        id = "1",
        title = title,
        amountCents = 0L,
        classification = MovementClassification.BUSINESS,
        originLabel = "Detectado",
        supportingLabel = supportingLabel,
        description = description,
        timeLabel = "",
        dateTimeLabel = "",
        avatarLabel = "",
        reviewed = false,
        previousClassification = null,
    )
}
