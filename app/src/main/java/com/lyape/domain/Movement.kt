package com.lyape.domain

import java.time.Instant
import java.util.UUID

/** Origen de un movimiento. */
enum class MovementSource {
    NOTIFICATION,
    MANUAL,
    ADJUSTMENT,
}

/**
 * Clasificación de un movimiento.
 *
 * - [BUSINESS] participa en los totales.
 * - [PERSONAL] queda visible pero no participa.
 * - [EXCLUDED] está anulado sin borrado físico.
 */
enum class MovementClassification {
    BUSINESS,
    PERSONAL,
    EXCLUDED,
}

/**
 * Movimiento tal como lo entiende el dominio.
 *
 * El monto siempre es un `Long` en céntimos con signo: nunca `Float` ni `Double`.
 */
data class Movement(
    val id: UUID,
    val amountCents: Long,
    val occurredAt: Instant,
    val capturedAt: Instant,
    val source: MovementSource,
    val classification: MovementClassification,
    val reviewed: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant,
    val currency: String = "PEN",
    val senderName: String? = null,
    val description: String? = null,
    val adjustmentReason: String? = null,
    val notificationKey: String? = null,
    val deduplicationHash: String? = null,
    val isEdited: Boolean = false,
    /**
     * Clasificación activa que tenía el movimiento justo antes de anularse.
     *
     * Permite que restaurar devuelva el movimiento a negocio o personal, en lugar de asumir
     * siempre negocio. Es `null` mientras el movimiento no está anulado.
     */
    val previousClassification: MovementClassification? = null,
    /** Cuenta de destino; null conserva los movimientos cuyo medio todavía no se conoce. */
    val accountId: String? = null,
    /** Medio de pago; [PaymentMedium.UNKNOWN] cuando no se puede inferir con seguridad. */
    val medium: PaymentMedium = PaymentMedium.UNKNOWN,
    /** Revisión de las ediciones conciliables; se incrementa dentro de la transacción de guardado. */
    val editVersion: Long = 0L,
    /** Identidad de la última edición aplicada, para reconocer un reenvío ya confirmado. */
    val lastEditAttemptId: UUID? = null,
) {
    /** Solo los movimientos activos del negocio participan en los totales. */
    val participatesInTotals: Boolean
        get() = classification == MovementClassification.BUSINESS
}
