package com.lyape.data

import com.lyape.domain.DateRange
import com.lyape.domain.Movement
import com.lyape.domain.MovementClassification
import com.lyape.domain.MovementEdits
import com.lyape.domain.MovementFactory
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
class MovementRepository(
    private val dao: MovementDao,
    private val now: () -> Instant = Instant::now,
    private val newId: () -> UUID = UUID::randomUUID,
) {

    fun observeAll(): Flow<List<Movement>> =
        dao.observeAll().map { entities -> entities.map(MovementEntity::toDomain) }

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
    suspend fun findDedupCandidates(candidate: Movement, window: Duration): List<Movement> {
        val center = candidate.occurredAt.toEpochMilli()
        val delta = window.toMillis()
        return dao.findDedupCandidates(
            notificationKey = candidate.notificationKey,
            deduplicationHash = candidate.deduplicationHash,
            fromMillis = center - delta,
            toMillis = center + delta,
        ).map(MovementEntity::toDomain)
    }

    suspend fun add(movement: Movement) = dao.insert(movement.toEntity())

    suspend fun addAll(movements: List<Movement>) =
        dao.insertAll(movements.map(Movement::toEntity))

    suspend fun update(movement: Movement) = dao.update(movement.toEntity())

    // --- Conciliación ---

    /** Registra un pago perdido. Devuelve el identificador del movimiento creado. */
    suspend fun addManual(
        amountCents: Long,
        occurredAt: Instant,
        label: String?,
    ): UUID {
        val movement = MovementFactory.manual(
            id = newId(),
            amountCents = amountCents,
            occurredAt = occurredAt,
            label = label,
            now = now(),
        )
        dao.insert(movement.toEntity())
        return movement.id
    }

    /** Registra un ajuste positivo o negativo con motivo obligatorio. */
    suspend fun addAdjustment(
        amountCents: Long,
        occurredAt: Instant,
        reason: String,
    ): UUID {
        val movement = MovementFactory.adjustment(
            id = newId(),
            amountCents = amountCents,
            occurredAt = occurredAt,
            reason = reason,
            now = now(),
        )
        dao.insert(movement.toEntity())
        return movement.id
    }

    suspend fun classify(id: UUID, classification: MovementClassification): Boolean =
        mutate(id) { movement -> MovementEdits.classify(movement, classification, now()) }

    suspend fun exclude(id: UUID): Boolean =
        mutate(id) { movement -> MovementEdits.exclude(movement, now()) }

    suspend fun restore(id: UUID): Boolean =
        mutate(id) { movement -> MovementEdits.restore(movement, now()) }

    suspend fun edit(
        id: UUID,
        amountCents: Long,
        occurredAt: Instant,
        senderName: String?,
        description: String?,
        adjustmentReason: String?,
    ): Boolean = mutate(id) { movement ->
        MovementEdits.edit(
            movement = movement,
            amountCents = amountCents,
            occurredAt = occurredAt,
            senderName = senderName,
            description = description,
            adjustmentReason = adjustmentReason,
            now = now(),
        )
    }

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
        val current = dao.getById(id)?.toDomain() ?: return false
        val updated = transform(current)
        if (updated != current) {
            dao.update(updated.toEntity())
        }
        return true
    }
}
