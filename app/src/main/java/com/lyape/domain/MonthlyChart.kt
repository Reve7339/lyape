package com.lyape.domain

/** Punto del acumulado mensual: etiqueta del día y suma acumulada con signo. */
data class MonthlyChartPoint(
    val label: String,
    val cumulativeCents: Long,
)

/** Referencia horizontal del gráfico mensual. */
enum class MonthlyChartThresholdKind {
    /** Gastos previstos del mes ([MonthlyProgress.totalExpensesCents]). */
    EXPENSES,

    /** Meta de ingresos resuelta por el bloque 6. */
    GOAL,
}

data class MonthlyChartThreshold(
    val kind: MonthlyChartThresholdKind,
    val amountCents: Long,
)

/**
 * Serie acumulada del mes lista para dibujar.
 *
 * [minCents] y [maxCents] siempre incluyen cero, todos los acumulados y los umbrales visibles, de
 * modo que la escala nunca recorta un valor. Los importes siguen en céntimos (`Long`): la
 * conversión a `Double` ocurre solo al calcular fracciones de dibujo.
 */
data class MonthlyChartSeries(
    val points: List<MonthlyChartPoint>,
    val thresholds: List<MonthlyChartThreshold>,
    val minCents: Long,
    val maxCents: Long,
) {
    /** Posición vertical de [valueCents] en `[0,1]`: 0 abajo (mínimo) y 1 arriba (máximo). */
    fun fractionOf(valueCents: Long): Float {
        val span = maxCents.toDouble() - minCents.toDouble()
        if (span <= 0.0) return 0f
        // Resta en Double: negar Long.MIN_VALUE directamente desbordaría.
        val offset = valueCents.toDouble() - minCents.toDouble()
        val fraction = offset / span
        return fraction.toFloat().coerceIn(0f, 1f)
    }
}

/**
 * Cálculo puro del acumulado mensual para el gráfico del resumen.
 *
 * La suma es saturada para no desbordar con valores extremos de `Long`; el resultado sigue siendo
 * exacto para cualquier monto real.
 */
object MonthlyChartCalculator {

    fun calculate(
        bars: List<PeriodBar>,
        expensesCents: Long?,
        goalCents: Long?,
    ): MonthlyChartSeries {
        var running = 0L
        val points = bars.map { bar ->
            running = saturatedAdd(running, bar.amountCents)
            MonthlyChartPoint(label = bar.label, cumulativeCents = running)
        }

        val thresholds = buildList {
            if (expensesCents != null) {
                add(MonthlyChartThreshold(MonthlyChartThresholdKind.EXPENSES, expensesCents))
            }
            if (goalCents != null) {
                add(MonthlyChartThreshold(MonthlyChartThresholdKind.GOAL, goalCents))
            }
        }

        var min = 0L
        var max = 0L
        points.forEach { point ->
            if (point.cumulativeCents < min) min = point.cumulativeCents
            if (point.cumulativeCents > max) max = point.cumulativeCents
        }
        thresholds.forEach { threshold ->
            if (threshold.amountCents < min) min = threshold.amountCents
            if (threshold.amountCents > max) max = threshold.amountCents
        }

        return MonthlyChartSeries(
            points = points,
            thresholds = thresholds,
            minCents = min,
            maxCents = max,
        )
    }

    /**
     * Posiciones verticales de las etiquetas de umbral en `[0,1]`, en el mismo orden que
     * [MonthlyChartSeries.thresholds], separadas al menos [minimumGap] cuando es posible.
     *
     * Si gastos y meta coinciden o quedan muy cerca, la etiqueta inferior se empuja hacia abajo
     * para que ambas sigan siendo legibles.
     */
    fun stackedLabelFractions(series: MonthlyChartSeries, minimumGap: Float): List<Float> {
        if (series.thresholds.isEmpty()) return emptyList()

        val fractions = series.thresholds.map { series.fractionOf(it.amountCents) }.toMutableList()
        val order = fractions.indices.sortedByDescending { fractions[it] }

        var previous = Float.POSITIVE_INFINITY
        order.forEach { index ->
            var value = fractions[index]
            if (previous - value < minimumGap) value = previous - minimumGap
            fractions[index] = value
            previous = value
        }

        val lowest = fractions.min()
        if (lowest < 0f) {
            for (index in fractions.indices) fractions[index] -= lowest
        }
        return fractions
    }

    private fun saturatedAdd(left: Long, right: Long): Long = try {
        Math.addExact(left, right)
    } catch (_: ArithmeticException) {
        if (left >= 0L && right >= 0L) Long.MAX_VALUE else Long.MIN_VALUE
    }
}
