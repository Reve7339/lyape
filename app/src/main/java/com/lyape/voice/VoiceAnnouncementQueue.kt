package com.lyape.voice

/**
 * Cola serial acotada de anuncios de voz.
 *
 * Comportamiento:
 * - Máximo [maxPending] anuncios pendientes (default 10).
 * - Caducidad de [ttlMillis] desde la creación (default 30 s): los viejos no se devuelven.
 * - Un identificador ya en la cola se considera duplicado y se descarta; esto evita
 *   reencolados del mismo movimiento. El identificador no contiene el anuncio en sí.
 * - La cola no guarda ni transmite ninguna notificación completa, nombre, código o cuenta.
 *
 * La fecha de validación (y el resultado) se determina con el tiempo actual pasado en cada
 * operación, para facilitar la prueba unitaria.
 */
class VoiceAnnouncementQueue(
    private val maxPending: Int = VoiceAnnouncement.VOICE_MAX_PENDING,
    private val ttlMillis: Long = VoiceAnnouncement.VOICE_TTL_MILLIS,
    private val maxRememberedIds: Int = 64,
) {
    init {
        require(maxPending >= 1) { "maxPending debe ser al menos 1" }
        require(ttlMillis > 0) { "ttlMillis debe ser positivo" }
        require(maxRememberedIds >= maxPending) { "maxRememberedIds no puede ser menor que maxPending" }
    }

    private val pending = ArrayDeque<VoiceAnnouncement>()
    private val seenIds = LinkedHashSet<String>(maxRememberedIds.coerceAtLeast(maxPending))

    /** El resultado de intentar añadir un anuncio a la cola. */
    enum class Outcome {
        /** Añadido a la cola y disponible para ser reproducido. */
        ENQUEUED,
        /** Ya estaba en la cola con el mismo identificador. */
        DUPLICATE,
        /** Caducó antes de poder añadirse. */
        EXPIRED,
        /** Cola llena: el exceso queda registrado en esta cola sin ser reproducido. */
        FULL,
    }

    /** Añade un anuncio si cabe, caduca o ya está pendiente. No reproduce ni contiene notificaciones. */
    @Synchronized
    fun enqueue(item: VoiceAnnouncement, nowMillis: Long): Outcome {
        purgeExpired(nowMillis)

        if (item.isExpired(nowMillis, ttlMillis)) {
            return Outcome.EXPIRED
        }
        if (item.id in seenIds) {
            return Outcome.DUPLICATE
        }
        if (pending.size >= maxPending) {
            return Outcome.FULL
        }
        pending.addLast(item)
        seenIds.add(item.id)
        trimSeenIds()
        return Outcome.ENQUEUED
    }

    /** Saca el siguiente anuncio, eliminando primero los que ya expiraron. */
    @Synchronized
    fun takeNext(nowMillis: Long): VoiceAnnouncement? {
        purgeExpired(nowMillis)
        return pending.removeFirstOrNull()
    }

    /** Vacía pendientes y olvida sus identificadores, permitiendo nuevos anuncios. */
    @Synchronized
    fun clear() {
        pending.clear()
        seenIds.clear()
    }

    /** Retira solo los anuncios que cumplen el predicado; no toca el resto de la cola. */
    @Synchronized
    fun remove(predicate: (VoiceAnnouncement) -> Boolean): Int {
        val removed = pending.filter(predicate)
        if (removed.isEmpty()) return 0
        pending.removeAll(predicate)
        removed.forEach { seenIds.remove(it.id) }
        return removed.size
    }

    @Synchronized
    fun currentCount(): Int {
        purgeExpired(currentNowMillis())
        return pending.size
    }

    /**
     * Cuenta de anuncios pendientes válidos a un instante dado. Útil para pruebas.
     */
    @Synchronized
    internal fun countAt(nowMillis: Long): Int {
        purgeExpired(nowMillis)
        return pending.size
    }

    private fun purgeExpired(nowMillis: Long) {
        while (pending.isNotEmpty() && pending.first().isExpired(nowMillis, ttlMillis)) {
            seenIds.remove(pending.removeFirst().id)
        }
    }

    private fun trimSeenIds() {
        while (seenIds.size > maxRememberedIds) {
            seenIds.remove(seenIds.first())
        }
    }

    companion object {
        private var nowSource: (() -> Long)? = null

        internal fun currentNowMillis(): Long =
            nowSource?.invoke() ?: (System.nanoTime() / 1_000_000)

        @Suppress("ktlint:standard:function-naming")
        internal fun setCurrentTimeSource(source: () -> Long) {
            nowSource = source
        }

        internal fun resetTimeSource() {
            nowSource = null
        }
    }
}
