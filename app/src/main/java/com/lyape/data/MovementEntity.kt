package com.lyape.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.lyape.domain.MovementClassification
import com.lyape.domain.MovementSource
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
    indices = [Index("occurredAt")],
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
)
