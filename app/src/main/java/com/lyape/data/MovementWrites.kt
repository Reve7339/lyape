package com.lyape.data

import com.lyape.domain.Movement
import com.lyape.domain.MovementClassification
import com.lyape.domain.MovementSource
import java.time.Instant
import java.util.UUID

/**
 * Contenido canónico de un alta.
 *
 * Excluye timestamps internos, marcas de revisión y metadatos de captura: un reintento del mismo
 * intento no cambia por volver a generar `now()` ni por una recreación. Incluye todo lo editable
 * que define la operación para que un mismo id con otros datos no se confirme como guardado.
 */
internal data class MovementAttemptContent(
    val amountCents: Long,
    val occurredAt: Instant,
    val source: MovementSource,
    val classification: MovementClassification,
    val accountId: String?,
    val currency: String,
    val senderName: String?,
    val description: String?,
    val adjustmentReason: String?,
)

internal fun Movement.attemptContent(): MovementAttemptContent = MovementAttemptContent(
    amountCents = amountCents,
    occurredAt = occurredAt.toPersistedPrecision(),
    source = source,
    classification = classification,
    accountId = accountId,
    currency = currency,
    senderName = senderName.normalizedAttemptText(),
    description = description.normalizedAttemptText(),
    adjustmentReason = adjustmentReason.normalizedAttemptText(),
)

internal fun String?.normalizedAttemptText(): String? =
    this?.trim()?.takeIf { it.isNotEmpty() }

/**
 * Room persiste [Instant] con precisión de milisegundos. Las comparaciones de idempotencia usan
 * esa misma representación para que un reintento con submilisegundos coincida con lo leído de la
 * base sin tolerancias ni cambios de esquema. No se usa para ordenar ni para mostrar fechas.
 */
internal fun Instant.toPersistedPrecision(): Instant = Instant.ofEpochMilli(toEpochMilli())

/**
 * Resultado de una edición protegida por revisión.
 *
 * [AlreadyApplied] permite recuperar el éxito de un envío cuyo resultado no llegó a la interfaz;
 * [Conflict] obliga a conservar lo escrito y revisar la versión guardada antes de reintentar.
 */
sealed interface MovementEditResult {
    data object Applied : MovementEditResult
    data object AlreadyApplied : MovementEditResult
    data object NotFound : MovementEditResult
    data object Conflict : MovementEditResult
}

/** Mismo id de intento con contenido distinto: nunca se ignoran silenciosamente los datos nuevos. */
class MovementAttemptConflictException(message: String) : IllegalStateException(message)

/**
 * Operaciones mínimas del formulario. Permite probar el ciclo de guardado con un doble controlado
 * sin introducir un framework de inyección de dependencias.
 */
interface MovementFormStore {
    suspend fun saveManual(
        amountCents: Long,
        occurredAt: Instant,
        label: String?,
        accountId: String?,
        classification: MovementClassification,
        id: UUID,
    ): UUID

    suspend fun saveAdjustment(
        amountCents: Long,
        occurredAt: Instant,
        reason: String,
        accountId: String?,
        classification: MovementClassification,
        id: UUID,
    ): UUID

    suspend fun saveEdit(
        id: UUID,
        amountCents: Long,
        occurredAt: Instant,
        senderName: String?,
        description: String?,
        adjustmentReason: String?,
        accountId: String?,
        updateAccount: Boolean,
        expectedVersion: Long,
        editAttemptId: UUID,
    ): MovementEditResult
}
