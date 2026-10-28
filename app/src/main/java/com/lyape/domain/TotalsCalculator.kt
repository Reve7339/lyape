package com.lyape.domain

import java.math.BigInteger

/** Resumen de un conjunto de movimientos. Todos los importes van en céntimos. */
data class Totals(
    val businessCents: Long,
    val personalCents: Long,
    val pendingCount: Int,
    val movementCount: Int,
    /**
     * La suma exacta no cabe en `Long`. Los importes mostrados no son utilizables y la interfaz
     * debe presentar un error local; nunca se saturan ni se truncan.
     */
    val calculationError: Boolean = false,
) {
    companion object {
        val Empty = Totals(
            businessCents = 0L,
            personalCents = 0L,
            pendingCount = 0,
            movementCount = 0,
        )
    }
}

/**
 * Regla única de totales del MVP.
 *
 * El total es la suma con signo de los movimientos activos del negocio: los ingresos suman y los
 * ajustes negativos restan. Un movimiento personal o anulado no participa.
 *
 * La suma se hace con precisión arbitraria y solo se convierte al final. Así una secuencia como
 * `MAX + 1 - 1` es representable sin depender del orden, mientras que un resultado fuera de rango
 * se marca como error en vez de saturarse.
 */
object TotalsCalculator {

    fun totalFor(movements: Iterable<Movement>): Totals {
        var business = BigInteger.ZERO
        var personal = BigInteger.ZERO
        var pending = 0
        var count = 0

        for (movement in movements) {
            count++
            when (movement.classification) {
                MovementClassification.BUSINESS -> business += BigInteger.valueOf(movement.amountCents)
                MovementClassification.PERSONAL -> personal += BigInteger.valueOf(movement.amountCents)
                MovementClassification.EXCLUDED -> Unit
            }
            if (!movement.reviewed) pending++
        }

        val businessCents = business.toLongExactOrNull()
        val personalCents = personal.toLongExactOrNull()
        if (businessCents == null || personalCents == null) {
            return Totals(
                businessCents = 0L,
                personalCents = 0L,
                pendingCount = pending,
                movementCount = count,
                calculationError = true,
            )
        }

        return Totals(
            businessCents = businessCents,
            personalCents = personalCents,
            pendingCount = pending,
            movementCount = count,
        )
    }

    /** Total exacto de negocio; lanza si el resultado no cabe en `Long`. */
    fun businessTotalCents(movements: Iterable<Movement>): Long =
        movements
            .filter { it.participatesInTotals }
            .fold(BigInteger.ZERO) { sum, movement ->
                sum + BigInteger.valueOf(movement.amountCents)
            }
            .toLongExact()
}

internal fun BigInteger.toLongExactOrNull(): Long? =
    if (this < LONG_MIN || this > LONG_MAX) null else toLong()

private fun BigInteger.toLongExact(): Long =
    toLongExactOrNull() ?: throw ArithmeticException("El total no cabe en Long.")

private val LONG_MIN: BigInteger = BigInteger.valueOf(Long.MIN_VALUE)
private val LONG_MAX: BigInteger = BigInteger.valueOf(Long.MAX_VALUE)
