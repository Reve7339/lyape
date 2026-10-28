package com.lyape.data

import com.lyape.domain.Movement

/** Copia directa entre el modelo persistido y el modelo de dominio. */
fun MovementEntity.toDomain(): Movement = Movement(
    id = id,
    amountCents = amountCents,
    occurredAt = occurredAt,
    capturedAt = capturedAt,
    source = source,
    classification = classification,
    reviewed = reviewed,
    createdAt = createdAt,
    updatedAt = updatedAt,
    currency = currency,
    senderName = senderName,
    description = description,
    adjustmentReason = adjustmentReason,
    notificationKey = notificationKey,
    deduplicationHash = deduplicationHash,
    isEdited = isEdited,
    previousClassification = previousClassification,
    accountId = accountId,
    medium = medium,
    editVersion = editVersion,
    lastEditAttemptId = lastEditAttemptId,
)

fun Movement.toEntity(): MovementEntity = MovementEntity(
    id = id,
    amountCents = amountCents,
    currency = currency,
    occurredAt = occurredAt,
    capturedAt = capturedAt,
    source = source,
    classification = classification,
    reviewed = reviewed,
    senderName = senderName,
    description = description,
    adjustmentReason = adjustmentReason,
    notificationKey = notificationKey,
    deduplicationHash = deduplicationHash,
    isEdited = isEdited,
    createdAt = createdAt,
    updatedAt = updatedAt,
    previousClassification = previousClassification,
    accountId = accountId,
    medium = medium,
    editVersion = editVersion,
    lastEditAttemptId = lastEditAttemptId,
)
