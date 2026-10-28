package com.lyape.data

import androidx.room.Entity
import androidx.room.ColumnInfo
import androidx.room.Index
import androidx.room.PrimaryKey
import com.lyape.domain.MovementClassification
import com.lyape.domain.MovementSource
import com.lyape.domain.PaymentMedium
import com.lyape.domain.movementTitle
import com.lyape.domain.normalizeMovementSearch
import java.time.Instant
import java.util.UUID

/**
 * Representación persistida de un [com.lyape.domain.Movement].
 *
 * La conversión de tipos (`Instant` a milisegundos, `UUID` a texto y enums a nombres) la resuelve
 * [Converters]. El índice sobre `occurredAt` acelera las consultas por periodo.
 */
@Entity(
    tableName = "movements",
    indices = [Index("occurredAt"), Index(value = ["occurredAt", "createdAt", "id"]), Index(value = ["accountId", "occurredAt", "classification"])],
)
data class MovementEntity(
    @PrimaryKey val id: UUID,
    val amountCents: Long,
    val currency: String,
    val occurredAt: Instant,
    val capturedAt: Instant,
    val source: MovementSource,
    val classification: MovementClassification,
    val reviewed: Boolean,
    val senderName: String?,
    val description: String?,
    val adjustmentReason: String?,
    val notificationKey: String?,
    val deduplicationHash: String?,
    val isEdited: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant,
    /** Clasificación activa recordada al anular; ver `Movement.previousClassification`. */
    val previousClassification: MovementClassification?,
    // Derivados: se reconstruyen en cada mapeo de dominio y durante la migración.
    @ColumnInfo(defaultValue = "''")
    val searchTitle: String = normalizeMovementSearch(movementTitle(senderName, source)),
    @ColumnInfo(defaultValue = "''")
    val searchDescription: String = normalizeMovementSearch(description.orEmpty()),
    @ColumnInfo(defaultValue = "''")
    val searchReason: String = normalizeMovementSearch(
        if (source == MovementSource.ADJUSTMENT) adjustmentReason.orEmpty() else "",
    ),
    val accountId: String? = null,
    @ColumnInfo(defaultValue = "'UNKNOWN'")
    val medium: PaymentMedium = PaymentMedium.UNKNOWN,
    @ColumnInfo(defaultValue = "0")
    val editVersion: Long = 0L,
    val lastEditAttemptId: UUID? = null,
)
