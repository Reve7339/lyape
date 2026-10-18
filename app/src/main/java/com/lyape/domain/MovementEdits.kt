package com.lyape.domain

import java.time.Instant

/**
 * Transiciones puras de conciliación.
 *
 * No dependen de Android ni de Room: reciben un [Movement] y devuelven una copia nueva. Mantienen
 * `source`, `capturedAt`, datos de captura, huella y `createdAt`; solo cambian los campos
 * conciliables y actualizan `updatedAt` e `isEdited`.
 */
object MovementEdits {

    /** Reclasifica un movimiento activo como negocio o personal. */
    fun classify(
        movement: Movement,
        classification: MovementClassification,
        now: Instant,
    ): Movement {
        require(classification != MovementClassification.EXCLUDED) {
            "Para anular usa MovementEdits.exclude."
        }
        if (movement.classification == classification && movement.previousClassification == null) {
            return movement
        }
        return movement.copy(
            classification = classification,
            previousClassification = null,
            isEdited = true,
            updatedAt = now,
        )
    }

    /**
     * Anula sin borrado físico. Recuerda la clasificación activa en `previousClassification` para
     * que [restore] pueda devolverla a negocio o personal.
     */
    fun exclude(movement: Movement, now: Instant): Movement {
        if (movement.classification == MovementClassification.EXCLUDED) return movement
        return movement.copy(
            classification = MovementClassification.EXCLUDED,
            previousClassification = movement.classification,
            isEdited = true,
            updatedAt = now,
        )
    }

    /**
     * Restaura un movimiento anulado a su clasificación activa anterior. Si por algún motivo no
     * quedó registrada, vuelve a negocio.
     */
    fun restore(movement: Movement, now: Instant): Movement {
        if (movement.classification != MovementClassification.EXCLUDED) return movement
        return movement.copy(
            classification = movement.previousClassification ?: MovementClassification.BUSINESS,
            previousClassification = null,
            isEdited = true,
            updatedAt = now,
        )
    }

    /** Edita los campos conciliables y conserva el resto de la trazabilidad. */
    fun edit(
        movement: Movement,
        amountCents: Long,
        occurredAt: Instant,
        senderName: String?,
        description: String?,
        adjustmentReason: String?,
        now: Instant,
    ): Movement {
        validateAmount(movement.source, amountCents)
        val reason = adjustmentReason.normalizedText()
        if (movement.source == MovementSource.ADJUSTMENT) {
            require(reason != null) { "Un ajuste necesita un motivo." }
        }
        return movement.copy(
            amountCents = amountCents,
            occurredAt = occurredAt,
            senderName = senderName.normalizedText(),
            description = description.normalizedText(),
            adjustmentReason = if (movement.source == MovementSource.ADJUSTMENT) {
                reason
            } else {
                movement.adjustmentReason
            },
            isEdited = true,
            updatedAt = now,
        )
    }

    /** Un pago debe ser positivo; un ajuste no puede ser cero. */
    fun validateAmount(source: MovementSource, amountCents: Long) {
        if (source == MovementSource.ADJUSTMENT) {
            require(amountCents != 0L) { "Un ajuste no puede ser cero." }
        } else {
            require(amountCents > 0L) { "Un pago debe ser mayor que cero." }
        }
    }
}

internal fun String?.normalizedText(): String? = this?.trim()?.takeIf { it.isNotEmpty() }
