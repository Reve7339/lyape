package com.lyape.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.paging.PagingSource
import com.lyape.domain.MovementClassification
import com.lyape.domain.MovementCounts
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/**
 * Acceso a `movements`.
 *
 * Las consultas por periodo reciben instantes absolutos que provienen de
 * [com.lyape.domain.DateRange]; el cálculo de totales se hace en el dominio con
 * [com.lyape.domain.TotalsCalculator] para no duplicar la regla.
 */
@Dao
interface MovementDao {
    @androidx.room.Transaction
    suspend fun mutateAtomically(id: UUID, transform: (MovementEntity) -> MovementEntity): Boolean {
        val current = getById(id) ?: return false
        val updated = transform(current)
        if (updated.accountId != current.accountId && updated.accountId != null && !accountExists(updated.accountId)) {
            throw AccountUnavailableException("La cuenta ya no existe. Elige otra.")
        }
        if (updated != current) update(updated)
        return true
    }

    /**
     * Aplica una edición solo si la revisión guardada sigue siendo la esperada.
     *
     * Toda la comprobación, la comparación de contenido y la escritura ocurren en la misma
     * transacción. Un reenvío del mismo intento se reconoce como [MovementEditResult.AlreadyApplied];
     * una revisión posterior con otro intento devuelve [MovementEditResult.Conflict] sin escribir.
     */
    @androidx.room.Transaction
    suspend fun editAtomically(
        id: UUID,
        expectedVersion: Long,
        attemptId: UUID,
        matches: (MovementEntity) -> Boolean,
        apply: (MovementEntity) -> MovementEntity,
    ): MovementEditResult {
        val current = getById(id) ?: return MovementEditResult.NotFound
        if (current.editVersion != expectedVersion) {
            return if (current.lastEditAttemptId == attemptId && matches(current)) {
                MovementEditResult.AlreadyApplied
            } else {
                MovementEditResult.Conflict
            }
        }
        if (matches(current)) return MovementEditResult.AlreadyApplied

        val updated = apply(current)
        if (updated.editVersion != expectedVersion + 1L) {
            throw IllegalStateException("La revisión de edición no avanzó.")
        }
        if (updated.accountId != current.accountId &&
            updated.accountId != null &&
            !accountExists(updated.accountId)
        ) {
            throw AccountUnavailableException("La cuenta ya no existe. Elige otra.")
        }
        update(updated)
        return MovementEditResult.Applied
    }

    @Query("SELECT EXISTS(SELECT 1 FROM cash_accounts WHERE id=:id)")
    suspend fun accountExists(id: String): Boolean

    // Room añade LIMIT/OFFSET: cada carga materializa solo la página solicitada.
    @Query(
        """
        SELECT * FROM movements
        WHERE (:classification IS NULL OR classification = :classification)
          AND (:query = '' OR instr(searchTitle, :query) > 0
               OR instr(searchDescription, :query) > 0 OR instr(searchReason, :query) > 0)
        ORDER BY occurredAt DESC, createdAt DESC, id DESC
        """,
    )
    fun pageHistory(classification: MovementClassification?, query: String): PagingSource<Int, MovementEntity>

    @Query(
        """
        SELECT COUNT(*) AS total,
               COUNT(CASE WHEN classification = 'BUSINESS' THEN 1 END) AS business,
               COUNT(CASE WHEN classification = 'PERSONAL' THEN 1 END) AS personal,
               COUNT(CASE WHEN classification = 'EXCLUDED' THEN 1 END) AS excluded
        FROM movements
        """,
    )
    fun observeCounts(): Flow<MovementCounts>

    @Query(
        """
        SELECT * FROM movements
        WHERE occurredAt >= :start AND occurredAt < :endExclusive
        ORDER BY occurredAt DESC, createdAt DESC
        """,
    )
    fun observeInRange(start: Long, endExclusive: Long): Flow<List<MovementEntity>>

    @Query(
        """
        SELECT * FROM movements
        WHERE occurredAt >= :start AND occurredAt < :endExclusive
        ORDER BY occurredAt DESC, createdAt DESC
        """,
    )
    suspend fun getInRange(start: Long, endExclusive: Long): List<MovementEntity>

    @Query("SELECT * FROM movements WHERE id = :id LIMIT 1")
    suspend fun getById(id: UUID): MovementEntity?

    @Query("SELECT * FROM movements WHERE id = :id LIMIT 1")
    fun observeById(id: UUID): Flow<MovementEntity?>

    @Query(
        """
        SELECT * FROM movements
        WHERE occurredAt >= :start AND occurredAt < :endExclusive
          AND reviewed = 0 AND classification != 'EXCLUDED'
        ORDER BY occurredAt DESC, createdAt DESC
        """,
    )
    fun observePendingInRange(start: Long, endExclusive: Long): Flow<List<MovementEntity>>

    /**
     * Marca como revisados los movimientos pendientes del rango. Una sola sentencia `UPDATE` es
     * atómica, así que el cierre del día no puede quedar a medias.
     */
    @Query(
        """
        UPDATE movements SET reviewed = 1, updatedAt = :updatedAt
        WHERE occurredAt >= :start AND occurredAt < :endExclusive
          AND reviewed = 0 AND classification != 'EXCLUDED'
        """,
    )
    suspend fun markReviewedInRange(start: Long, endExclusive: Long, updatedAt: Long): Int

    /** Reabre el día: devuelve a pendiente los movimientos revisados del rango. */
    @Query(
        """
        UPDATE movements SET reviewed = 0, updatedAt = :updatedAt
        WHERE occurredAt >= :start AND occurredAt < :endExclusive
          AND reviewed = 1 AND classification != 'EXCLUDED'
        """,
    )
    suspend fun reopenInRange(start: Long, endExclusive: Long, updatedAt: Long): Int

    /**
     * Candidatos acotados para la deduplicación de una captura.
     *
     * Se limita a la ventana temporal (`occurredAt` está indexado) y dentro de ella devuelve las
     * filas que comparten la clave de Android o la huella. No carga la tabla completa y no decide:
     * la regla vive en [com.lyape.notification.DuplicateDetector]. Cualquier duplicado posible cae
     * dentro de la ventana, porque la coincidencia por clave exige el mismo instante.
     */
    @Query(
        """
        SELECT * FROM movements
        WHERE occurredAt >= :fromMillis AND occurredAt <= :toMillis
          AND (:medium IS NULL OR medium = :medium)
          AND (
              (:notificationKey IS NOT NULL AND notificationKey = :notificationKey)
              OR (:deduplicationHash IS NOT NULL AND deduplicationHash = :deduplicationHash)
          )
        """,
    )
    suspend fun findDedupCandidates(
        notificationKey: String?,
        deduplicationHash: String?,
        medium: String?,
        fromMillis: Long,
        toMillis: Long,
    ): List<MovementEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(movement: MovementEntity)

    /** Inserción idempotente: no reemplaza una fila existente con el mismo id. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(movement: MovementEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(movements: List<MovementEntity>)

    @Update
    suspend fun update(movement: MovementEntity)
}
