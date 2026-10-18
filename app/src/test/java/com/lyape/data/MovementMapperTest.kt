package com.lyape.data

import com.lyape.domain.Movement
import com.lyape.domain.MovementClassification
import com.lyape.domain.MovementSource
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.util.UUID

class MovementMapperTest {

    private val movement = Movement(
        id = UUID.fromString("11111111-2222-3333-4444-555555555555"),
        amountCents = -800,
        currency = "PEN",
        occurredAt = Instant.parse("2026-10-14T18:00:00Z"),
        capturedAt = Instant.parse("2026-10-14T18:00:05Z"),
        source = MovementSource.ADJUSTMENT,
        classification = MovementClassification.BUSINESS,
        reviewed = true,
        senderName = null,
        description = "Corrección",
        adjustmentReason = "Devolución",
        notificationKey = null,
        deduplicationHash = "hash",
        isEdited = true,
        createdAt = Instant.parse("2026-10-14T18:01:00Z"),
        updatedAt = Instant.parse("2026-10-14T18:02:00Z"),
    )

    @Test
    fun `el mapeo a entidad usa columnas primitivas`() {
        val entity = movement.toEntity()

        assertEquals("11111111-2222-3333-4444-555555555555", entity.id.toString())
        assertEquals(-800L, entity.amountCents)
        assertEquals(MovementSource.ADJUSTMENT, entity.source)
        assertEquals(MovementClassification.BUSINESS, entity.classification)
        assertEquals(1_792_000_800_000L, entity.occurredAt.toEpochMilli())
    }

    @Test
    fun `el viaje de ida y vuelta conserva el movimiento`() {
        assertEquals(movement, movement.toEntity().toDomain())
    }
}
