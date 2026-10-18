package com.lyape.domain

/** Resumen de un conjunto de movimientos. Todos los importes van en céntimos. */
data class Totals(
    val businessCents: Long,
    val personalCents: Long,
    val pendingCount: Int,
    val movementCount: Int,
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
 */
object TotalsCalculator {

    fun totalFor(movements: Iterable<Movement>): Totals {
        var business = 0L
        var personal = 0L
        var pending = 0
        var count = 0

        for (movement in movements) {
            count++
            when (movement.classification) {
                MovementClassification.BUSINESS -> business += movement.amountCents
                MovementClassification.PERSONAL -> personal += movement.amountCents
                MovementClassification.EXCLUDED -> Unit
            }
            if (!movement.reviewed) pending++
        }

        return Totals(
            businessCents = business,
            personalCents = personal,
            pendingCount = pending,
            movementCount = count,
        )
    }

    fun businessTotalCents(movements: Iterable<Movement>): Long =
        movements.filter { it.participatesInTotals }.sumOf { it.amountCents }
}
