package com.lyape.notification

import com.lyape.domain.Movement
import java.time.Duration
import java.time.Instant

/**
 * Detecta duplicados de forma pura, sin tocar Room ni Android.
 *
 * Reglas, en orden:
 * 1. Clave de Android + mismo `postedAt`: es la **misma entrega** de la misma notificación, así
 *    que es duplicado. La clave por sí sola no identifica un pago para siempre: dos pagos con la
 *    misma clave y horas distintas son movimientos distintos y se guardan.
 * 2. Respaldo por huella: solo cuando **falta una clave**. Una huella igual (paquete, monto y texto
 *    normalizado) dentro de [window] es duplicado. Si ambos tienen clave, no se descarta ninguno
 *    por compartir huella.
 *
 * [window] es pública para que el repositorio acote su consulta a la misma ventana.
 */
class DuplicateDetector(val window: Duration = DEFAULT_WINDOW) {

    init {
        require(!window.isNegative) { "La ventana de deduplicación no puede ser negativa." }
    }

    fun isDuplicate(candidate: Movement, existing: Iterable<Movement>): Boolean =
        existing.any { stored -> isDuplicate(candidate, stored) }

    private fun isDuplicate(candidate: Movement, stored: Movement): Boolean =
        sameKeyAndSameInstant(candidate, stored) || sameFingerprintWithinWindow(candidate, stored)

    private fun sameKeyAndSameInstant(candidate: Movement, stored: Movement): Boolean {
        val key = candidate.notificationKey ?: return false
        return stored.notificationKey == key && stored.occurredAt == candidate.occurredAt
    }

    private fun sameFingerprintWithinWindow(candidate: Movement, stored: Movement): Boolean {
        val hash = candidate.deduplicationHash ?: return false
        // Con clave en ambos lados, la huella no decide: podrían ser dos pagos distintos.
        if (candidate.notificationKey != null && stored.notificationKey != null) return false
        return stored.deduplicationHash == hash &&
            withinWindow(candidate.occurredAt, stored.occurredAt)
    }

    private fun withinWindow(first: Instant, second: Instant): Boolean =
        Duration.between(first, second).abs() <= window

    companion object {
        /** Ventana explícita por defecto para el respaldo por huella. */
        val DEFAULT_WINDOW: Duration = Duration.ofMinutes(5)
    }
}
