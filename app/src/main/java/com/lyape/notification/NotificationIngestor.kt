package com.lyape.notification

import com.lyape.data.AccountUnavailableException
import com.lyape.data.CaptureAccountResolver
import com.lyape.data.CaptureMovementStore
import com.lyape.domain.CaptureProvider
import com.lyape.domain.CapturedPayment
import com.lyape.domain.Movement
import com.lyape.domain.MovementClassification
import com.lyape.domain.MovementSource
import com.lyape.preferences.AppPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.util.UUID

/** Fallo identificable de captura; no se confunde con un formato desconocido. */
enum class IngestFailure {
    PREFERENCE_READ,
    PARSE,
    DEDUPLICATION_READ,
    ACCOUNT_READ,
    PERSISTENCE,
    UNEXPECTED,
}

/**
 * Resultado tipado de una captura.
 *
 * Solo [Stored] lleva un evento de voz y solo se construye después de que Room confirmó la
 * inserción. Duplicado, desconocido, pausado, sin cuenta y fallo no producen anuncio.
 */
sealed interface IngestResult {
    data class Stored(val payment: CapturedPayment) : IngestResult
    data object Duplicate : IngestResult
    data object Unrecognized : IngestResult
    data object Paused : IngestResult
    data class Unconfigured(val provider: CaptureProvider) : IngestResult
    data class Failed(val failure: IngestFailure) : IngestResult
}

/**
 * Recorrido de captura: parser → deduplicación → Room.
 *
 * La sección comprobar/insertar se serializa con un [Mutex], de modo que dos entregas concurrentes
 * de la misma notificación producen exactamente una fila. La consulta de candidatos está acotada
 * por clave y ventana temporal: no se carga la tabla completa.
 */
class NotificationIngestor(
    private val repository: CaptureMovementStore,
    private val preferences: AppPreferences,
    private val accountResolver: CaptureAccountResolver,
    private val detector: DuplicateDetector = DuplicateDetector(),
    private val now: () -> Instant = Instant::now,
    private val newId: () -> UUID = UUID::randomUUID,
) {

    private val mutex = Mutex()

    /**
     * Captura una notificación de [source] solo si el proveedor acepta su paquete y su cuenta
     * receptora se resuelve de forma segura. El parser pertenece al proveedor; el medio y la
     * cuenta son explícitos. Un duplicado no necesita resolver la cuenta.
     */
    suspend fun ingest(input: NotificationInput, source: CaptureProviderSpec): IngestResult {
        // Defensa adicional: el listener ya filtró antes de leer extras.
        if (!source.acceptsPackage(input.packageName)) return IngestResult.Unrecognized

        val paused = try {
            preferences.capturePaused.first()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return IngestResult.Failed(IngestFailure.PREFERENCE_READ)
        }
        if (paused) return IngestResult.Paused

        val parsed = try {
            source.parse(input)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return IngestResult.Failed(IngestFailure.PARSE)
        }
        if (parsed !is ParseResult.Parsed) return IngestResult.Unrecognized
        // Defensa independiente del parser: una captura nunca puede restar dinero.
        if (parsed.amountCents <= 0L) return IngestResult.Unrecognized
        val provider = source.provider

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
                medium = provider.medium,
            )

            val existing = try {
                repository.findDedupCandidates(candidate, detector.window)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                return@withLock IngestResult.Failed(IngestFailure.DEDUPLICATION_READ)
            }

            if (detector.isDuplicate(candidate, existing)) {
                IngestResult.Duplicate
            } else {
                val accountId = try {
                    accountResolver.accountIdFor(provider)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    return@withLock IngestResult.Failed(IngestFailure.ACCOUNT_READ)
                } ?: return@withLock IngestResult.Unconfigured(provider)

                try {
                    repository.add(candidate.copy(accountId = accountId))
                    IngestResult.Stored(
                        CapturedPayment(
                            id = candidate.id,
                            medium = provider.medium,
                            amountCents = candidate.amountCents,
                            occurredAt = candidate.occurredAt,
                        ),
                    )
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: AccountUnavailableException) {
                    // La asociación pudo invalidarse entre la resolución y la inserción.
                    IngestResult.Unconfigured(provider)
                } catch (_: Exception) {
                    IngestResult.Failed(IngestFailure.PERSISTENCE)
                }
            }
        }
    }
}
