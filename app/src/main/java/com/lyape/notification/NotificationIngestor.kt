package com.lyape.notification

import com.lyape.data.MovementRepository
import com.lyape.domain.Movement
import com.lyape.domain.MovementClassification
import com.lyape.domain.MovementSource
import com.lyape.preferences.AppPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.util.UUID

/** Resultado de una captura. Nunca se crea un movimiento salvo en [STORED]. */
enum class IngestResult {
    /** Se guardó un movimiento nuevo. */
    STORED,

    /** Ya existía (misma clave o misma huella dentro de la ventana). */
    DUPLICATE,

    /** Formato no reconocido: no altera nada. */
    UNRECOGNIZED,

    /** La captura está pausada por el usuario. */
    PAUSED,
}

/**
 * Recorrido de captura: parser → deduplicación → Room.
 *
 * La sección comprobar/insertar se serializa con un [Mutex], de modo que dos entregas concurrentes
 * de la misma notificación producen exactamente una fila. La consulta de candidatos está acotada
 * por clave y ventana temporal: no se carga la tabla completa.
 */
class NotificationIngestor(
    private val repository: MovementRepository,
    private val preferences: AppPreferences,
    private val detector: DuplicateDetector = DuplicateDetector(),
    private val now: () -> Instant = Instant::now,
    private val newId: () -> UUID = UUID::randomUUID,
) {

    private val mutex = Mutex()

    suspend fun ingest(input: NotificationInput): IngestResult {
        if (preferences.capturePaused.first()) return IngestResult.PAUSED

        val parsed = NotificationParser.parse(input)
        if (parsed !is ParseResult.Parsed) return IngestResult.UNRECOGNIZED

        return mutex.withLock {
            val timestamp = now()
            val candidate = Movement(
                id = newId(),
                amountCents = parsed.amountCents,
                occurredAt = input.postedAt,
                capturedAt = timestamp,
                source = MovementSource.NOTIFICATION,
                classification = MovementClassification.BUSINESS,
                reviewed = false,
                createdAt = timestamp,
                updatedAt = timestamp,
                senderName = parsed.senderName,
                notificationKey = input.key,
                deduplicationHash = parsed.fingerprint,
            )

            val existing = repository.findDedupCandidates(candidate, detector.window)
            if (detector.isDuplicate(candidate, existing)) {
                IngestResult.DUPLICATE
            } else {
                repository.add(candidate)
                IngestResult.STORED
            }
        }
    }
}
