package com.lyape.domain

import java.time.Instant
import java.util.UUID

/**
 * Crea movimientos agregados por el usuario.
 *
 * Un pago manual nace como ingreso del negocio y un ajuste como corrección del negocio; ambos
 * quedan pendientes de revisión y nunca se crean con monto cero.
 */
object MovementFactory {

    fun manual(
        id: UUID,
        amountCents: Long,
        occurredAt: Instant,
        label: String?,
        now: Instant,
    ): Movement {
        require(amountCents > 0L) { "Un pago manual debe ser mayor que cero." }
        return Movement(
            id = id,
            amountCents = amountCents,
            occurredAt = occurredAt,
            capturedAt = now,
            source = MovementSource.MANUAL,
            classification = MovementClassification.BUSINESS,
            reviewed = false,
            createdAt = now,
            updatedAt = now,
            senderName = label.normalizedText(),
        )
    }

    fun adjustment(
        id: UUID,
        amountCents: Long,
        occurredAt: Instant,
        reason: String,
        now: Instant,
    ): Movement {
        require(amountCents != 0L) { "Un ajuste no puede ser cero." }
        val trimmedReason = reason.normalizedText()
        require(trimmedReason != null) { "Un ajuste necesita un motivo." }
        return Movement(
            id = id,
            amountCents = amountCents,
            occurredAt = occurredAt,
            capturedAt = now,
            source = MovementSource.ADJUSTMENT,
            classification = MovementClassification.BUSINESS,
            reviewed = false,
            createdAt = now,
            updatedAt = now,
            adjustmentReason = trimmedReason,
        )
    }
}
