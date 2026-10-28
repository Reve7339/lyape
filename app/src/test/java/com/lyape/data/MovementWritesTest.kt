package com.lyape.data

import com.lyape.domain.Movement
import com.lyape.domain.MovementFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.time.Instant
import java.util.UUID

class MovementWritesTest {

    private fun movement(occurredAt: Instant, amountCents: Long = 1_000L): Movement =
        MovementFactory.manual(
            id = UUID.fromString("00000000-0000-0000-0000-0000000000aa"),
            amountCents = amountCents,
            occurredAt = occurredAt,
            label = "Bodega",
            now = Instant.parse("2026-10-27T12:00:00Z"),
        )

    @Test
    fun `un alta con submilisegundos usa la precision que persiste Room`() {
        val precise = Instant.parse("2026-10-27T10:00:00.123456789Z")
        val persisted = Instant.parse("2026-10-27T10:00:00.123Z")

        val attempt = movement(precise).attemptContent()

        assertEquals(persisted, attempt.occurredAt)
        assertEquals(movement(persisted).attemptContent(), attempt)
    }

    @Test
    fun `un milisegundo distinto sigue siendo contenido distinto`() {
        val first = movement(Instant.parse("2026-10-27T10:00:00.123Z")).attemptContent()
        val second = movement(Instant.parse("2026-10-27T10:00:00.124Z")).attemptContent()

        assertNotEquals(first, second)
    }
}
