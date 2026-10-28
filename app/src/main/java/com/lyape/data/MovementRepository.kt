package com.lyape.data

import com.lyape.domain.DateRange
import com.lyape.domain.Movement
import com.lyape.domain.MovementClassification
import com.lyape.domain.MovementEdits
import com.lyape.domain.MovementFactory
import com.lyape.domain.MovementCounts
import com.lyape.domain.MovementSource
import com.lyape.domain.PaymentMedium
import com.lyape.domain.normalizeMovementSearch
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import com.lyape.domain.Totals
import com.lyape.domain.TotalsCalculator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Punto de acceso a los movimientos para el resto de la app.
 *
 * Devuelve siempre modelos de dominio y delega el cálculo de totales en
 * [TotalsCalculator] y las transiciones de conciliación en [MovementEdits], de modo que exista
 * una única regla de negocio. Ninguna operación borra filas: anular usa `EXCLUDED`.
 */
/** Operaciones mínimas que necesita el ingestor; permite probar fallos sin Room. */
interface CaptureMovementStore {
    suspend fun findDedupCandidates(candidate: Movement, window: Duration): List<Movement>

    suspend fun add(movement: Movement)
}

/** La cuenta explícita dejó de existir o dejó de ser válida; no es un fallo de lectura. */
class AccountUnavailableException(message: String) : IllegalArgumentException(message)

class MovementRepository(
    private val dao: MovementDao,
    private val now: () -> Instant = Instant::now,
    private val newId: () -> UUID = UUID::randomUUID,
) : CaptureMovementStore, MovementFormStore {

    fun history(
        classification: MovementClassification?,
        query: String,
    ): Flow<PagingData<Movement>> = Pager(
        config = HISTORY_PAGING_CONFIG,
        pagingSourceFactory = { dao.pageHistory(classification, normalizeMovementSearch(query)) },
    ).flow.map { page -> page.map(MovementEntity::toDomain) }

    fun observeCounts(): Flow<MovementCounts> = dao.observeCounts()

    fun observeInRange(range: DateRange): Flow<List<Movement>> =
        dao.observeInRange(range.start.toEpochMilli(), range.endExclusive.toEpochMilli())
            .map { entities -> entities.map(MovementEntity::toDomain) }

    /** Movimientos de una fecha local, incluidos los anulados: la lista nunca oculta historia. */
    fun observeDay(date: LocalDate): Flow<List<Movement>> = observeInRange(DateRange.day(date))

    fun observePendingInRange(range: DateRange): Flow<List<Movement>> =
        dao.observePendingInRange(range.start.toEpochMilli(), range.endExclusive.toEpochMilli())
            .map { entities -> entities.map(MovementEntity::toDomain) }

    fun observePendingOn(date: LocalDate): Flow<List<Movement>> =
        observePendingInRange(DateRange.day(date))

    suspend fun movementsInRange(range: DateRange): List<Movement> =
        dao.getInRange(range.start.toEpochMilli(), range.endExclusive.toEpochMilli())
            .map(MovementEntity::toDomain)

    suspend fun totalsInRange(range: DateRange): Totals =
        TotalsCalculator.totalFor(movementsInRange(range))

    fun observeTotalsInRange(range: DateRange): Flow<Totals> =
        observeInRange(range).map(TotalsCalculator::totalFor)

    suspend fun findById(id: UUID): Movement? = dao.getById(id)?.toDomain()

    /** Observa un movimiento concreto; emite `null` si ya no existe. */
    fun observeById(id: UUID): Flow<Movement?> =
        dao.observeById(id).map { it?.toDomain() }

    /**
     * Candidatos acotados que podrían chocar con [candidate], dentro de `[occurredAt - window,
     * occurredAt + window]`: misma clave de Android o misma huella. La decisión final la toma
     * [com.lyape.notification.DuplicateDetector], que distingue "misma entrega" (clave e instante
     * iguales) de "pago distinto con la misma clave".
     */
    override suspend fun findDedupCandidates(candidate: Movement, window: Duration): List<Movement> {
        val center = candidate.occurredAt.toEpochMilli()
        val delta = window.toMillis()
        return dao.findDedupCandidates(
            notificationKey = candidate.notificationKey,
            deduplicationHash = candidate.deduplicationHash,
            medium = candidate.medium.takeUnless { it == PaymentMedium.UNKNOWN }?.name,
            fromMillis = center - delta,
            toMillis = center + delta,
        ).map(MovementEntity::toDomain)
    }

    /**
     * Inserta exactamente lo recibido. La captura debe resolver su cuenta explícita antes de
     * llamar aquí; no se asigna Yape a notificaciones sin cuenta.
     */
    override suspend fun add(movement: Movement) {
        if (movement.source == MovementSource.NOTIFICATION) {
            require(movement.amountCents > 0L) { "Un cobro capturado debe ser mayor que cero." }
        }
        validateAccount(movement.accountId)
        dao.insert(movement.toEntity())
    }

    suspend fun addAll(movements: List<Movement>) {
        movements.forEach { validateAccount(it.accountId) }
        dao.insertAll(movements.map { it.toEntity() })
    }

    suspend fun update(movement: Movement) {
        validateAccount(movement.accountId)
        dao.update(movement.toEntity())
    }

    // --- Conciliación ---

    /** Registra un pago perdido. Devuelve el identificador del movimiento creado. */
    suspend fun addManual(
        amountCents: Long,
        occurredAt: Instant,
        label: String?,
        accountId: String? = null,
        classification: MovementClassification = MovementClassification.BUSINESS,
        id: UUID = newId(),
    ): UUID {
        require(classification != MovementClassification.EXCLUDED)
        validateAccount(accountId)
        val movement = MovementFactory.manual(
            id = id,
            amountCents = amountCents,
            occurredAt = occurredAt,
            label = label,
            now = now(),
        ).copy(accountId = accountId, classification = classification)
        insertIdempotent(movement)
        return movement.id
    }

    /** Registra un ajuste positivo o negativo con motivo obligatorio. */
    suspend fun addAdjustment(
        amountCents: Long,
        occurredAt: Instant,
        reason: String,
        accountId: String? = null,
        classification: MovementClassification = MovementClassification.BUSINESS,
        id: UUID = newId(),
    ): UUID {
        require(classification != MovementClassification.EXCLUDED)
        validateAccount(accountId)
        val movement = MovementFactory.adjustment(
            id = id,
            amountCents = amountCents,
            occurredAt = occurredAt,
            reason = reason,
            now = now(),
        ).copy(accountId = accountId, classification = classification)
        insertIdempotent(movement)
        return movement.id
    }

    /**
     * Un mismo intento puede llegar dos veces tras una recreación. `IGNORE` evita sobrescribir
     * una fila previa. Si el id ya existe, solo se recupera el éxito cuando el contenido canónico
     * coincide; con otros datos se devuelve un conflicto explícito.
     */
    private suspend fun insertIdempotent(movement: Movement) {
        val rowId = dao.insertIfAbsent(movement.toEntity())
        if (rowId == -1L) {
            val stored = dao.getById(movement.id)
                ?: throw IllegalStateException("No se pudo registrar el movimiento.")
            if (stored.toDomain().attemptContent() != movement.attemptContent()) {
                throw MovementAttemptConflictException(
                    "Este intento ya se guardó con otros datos. Revisa Movimientos antes de crear otro.",
                )
            }
        }
    }

    override suspend fun saveManual(
        amountCents: Long,
        occurredAt: Instant,
        label: String?,
        accountId: String?,
        classification: MovementClassification,
        id: UUID,
    ): UUID = addManual(
        amountCents = amountCents,
        occurredAt = occurredAt,
        label = label,
        accountId = accountId,
        classification = classification,
        id = id,
    )

    override suspend fun saveAdjustment(
        amountCents: Long,
        occurredAt: Instant,
        reason: String,
        accountId: String?,
        classification: MovementClassification,
        id: UUID,
    ): UUID = addAdjustment(
        amountCents = amountCents,
        occurredAt = occurredAt,
        reason = reason,
        accountId = accountId,
        classification = classification,
        id = id,
    )

    suspend fun classify(id: UUID, classification: MovementClassification): Boolean =
        mutate(id) { movement -> MovementEdits.classify(movement, classification, now()) }

    suspend fun exclude(id: UUID): Boolean =
        mutate(id) { movement -> MovementEdits.exclude(movement, now()) }

    suspend fun restore(id: UUID): Boolean =
        mutate(id) { movement -> MovementEdits.restore(movement, now()) }

    /**
     * Edita con identidad de intento y revisión esperada. La comprobación y la escritura son una
     * sola transacción: un reenvío antiguo no puede sobrescribir una edición posterior.
     */
    suspend fun edit(
        id: UUID,
        amountCents: Long,
        occurredAt: Instant,
        senderName: String?,
        description: String?,
        adjustmentReason: String?,
        expectedVersion: Long,
        editAttemptId: UUID,
        accountId: String? = null,
        updateAccount: Boolean = false,
    ): MovementEditResult {
        val requestedSender = senderName.normalizedAttemptText()
        val requestedDescription = description.normalizedAttemptText()
        val requestedReason = adjustmentReason.normalizedAttemptText()

        return dao.editAtomically(
            id = id,
            expectedVersion = expectedVersion,
            attemptId = editAttemptId,
            matches = { current ->
                val movement = current.toDomain()
                movement.amountCents == amountCents &&
                    movement.occurredAt.toPersistedPrecision() == occurredAt.toPersistedPrecision() &&
                    movement.senderName == requestedSender &&
                    movement.description == requestedDescription &&
                    (
                        movement.source != MovementSource.ADJUSTMENT ||
                            movement.adjustmentReason == requestedReason
                        ) &&
                    (!updateAccount || movement.accountId == accountId)
            },
            apply = { current ->
                val timestamp = now()
                MovementEdits.edit(
                    movement = current.toDomain(),
                    amountCents = amountCents,
                    occurredAt = occurredAt,
                    senderName = senderName,
                    description = description,
                    adjustmentReason = adjustmentReason,
                    now = timestamp,
                ).let { edited ->
                    if (updateAccount && edited.accountId != accountId) {
                        edited.copy(accountId = accountId, isEdited = true, updatedAt = timestamp)
                    } else {
                        edited
                    }
                }.copy(
                    editVersion = current.editVersion + 1L,
                    lastEditAttemptId = editAttemptId,
                ).toEntity()
            },
        )
    }

    override suspend fun saveEdit(
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
    ): MovementEditResult = edit(
        id = id,
        amountCents = amountCents,
        occurredAt = occurredAt,
        senderName = senderName,
        description = description,
        adjustmentReason = adjustmentReason,
        expectedVersion = expectedVersion,
        editAttemptId = editAttemptId,
        accountId = accountId,
        updateAccount = updateAccount,
    )

    /** Cierra el día: marca como revisados los pendientes de la fecha local. Devuelve cuántos. */
    suspend fun closeDay(date: LocalDate): Int = markReviewed(DateRange.day(date), reviewed = true)

    /** Reabre el día sin tocar clasificaciones ni ediciones. Devuelve cuántos volvieron a pendiente. */
    suspend fun reopenDay(date: LocalDate): Int =
        markReviewed(DateRange.day(date), reviewed = false)

    private suspend fun markReviewed(range: DateRange, reviewed: Boolean): Int {
        val start = range.start.toEpochMilli()
        val end = range.endExclusive.toEpochMilli()
        val timestamp = now().toEpochMilli()
        return if (reviewed) {
            dao.markReviewedInRange(start, end, timestamp)
        } else {
            dao.reopenInRange(start, end, timestamp)
        }
    }

    private suspend fun mutate(id: UUID, transform: (Movement) -> Movement): Boolean {
        return dao.mutateAtomically(id) { transform(it.toDomain()).toEntity() }
    }

    private suspend fun validateAccount(accountId: String?) {
        if (accountId != null && !dao.accountExists(accountId)) {
            throw AccountUnavailableException("La cuenta ya no existe. Elige otra.")
        }
    }
}

/** Ventana acotada; las páginas lejanas se descartan, no se acumulan al desplazarse. */
internal val HISTORY_PAGING_CONFIG = PagingConfig(
    pageSize = 50,
    initialLoadSize = 50,
    prefetchDistance = 10,
    maxSize = 150,
    enablePlaceholders = false,
)
