package com.lyape.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class MonthlyChartCalculatorTest {

    private val lima = ZoneId.of("America/Lima")

    @Test
    fun `el acumulado suma con signo y respeta el ajuste negativo intermedio`() {
        val series = MonthlyChartCalculator.calculate(
            bars = bars(1_000, 2_000, -500, 3_000),
            expensesCents = null,
            goalCents = null,
        )

        assertEquals(listOf(1_000L, 3_000L, 2_500L, 5_500L), series.points.map { it.cumulativeCents })
        assertEquals(0L, series.minCents)
        assertEquals(5_500L, series.maxCents)
    }

    @Test
    fun `incluye gastos y meta como referencias visibles`() {
        val series = MonthlyChartCalculator.calculate(
            bars = bars(1_000, 2_000),
            expensesCents = 2_500L,
            goalCents = 5_000L,
        )

        assertEquals(
            listOf(MonthlyChartThresholdKind.EXPENSES, MonthlyChartThresholdKind.GOAL),
            series.thresholds.map { it.kind },
        )
        assertEquals(listOf(2_500L, 5_000L), series.thresholds.map { it.amountCents })
    }

    @Test
    fun `solo gastos solo meta y ninguno dejan las referencias correctas`() {
        val onlyExpenses = MonthlyChartCalculator.calculate(bars(1_000), 2_000L, null)
        assertEquals(listOf(MonthlyChartThresholdKind.EXPENSES), onlyExpenses.thresholds.map { it.kind })

        val onlyGoal = MonthlyChartCalculator.calculate(bars(1_000), null, 2_000L)
        assertEquals(listOf(MonthlyChartThresholdKind.GOAL), onlyGoal.thresholds.map { it.kind })

        val none = MonthlyChartCalculator.calculate(bars(1_000), null, null)
        assertTrue(none.thresholds.isEmpty())
    }

    @Test
    fun `gastos y meta iguales o muy cercanos se apilan sin superponerse`() {
        val equal = MonthlyChartCalculator.calculate(bars(4_000), 5_000L, 5_000L)
        val equalLabels = MonthlyChartCalculator.stackedLabelFractions(equal, minimumGap = 0.16f)

        assertEquals(2, equalLabels.size)
        assertTrue(equalLabels.all { it in 0f..1f })
        assertGapAtLeast(equalLabels, 0.16f)

        val close = MonthlyChartCalculator.calculate(bars(4_000), 5_000L, 5_100L)
        val closeLabels = MonthlyChartCalculator.stackedLabelFractions(close, minimumGap = 0.16f)

        assertEquals(2, closeLabels.size)
        assertTrue(closeLabels.all { it in 0f..1f })
        assertGapAtLeast(closeLabels, 0.16f)
    }

    @Test
    fun `el acumulado debajo exactamente en y por encima de cada umbral se ordena`() {
        val series = MonthlyChartCalculator.calculate(
            bars = bars(3_000, 2_000, 3_000, 1_000),
            expensesCents = 5_000L,
            goalCents = 8_000L,
        )

        val belowExpenses = series.fractionOf(3_000L)
        val atExpenses = series.fractionOf(5_000L)
        val atGoal = series.fractionOf(8_000L)
        val aboveGoal = series.fractionOf(9_000L)

        assertTrue(belowExpenses < atExpenses)
        assertEquals(atExpenses, series.fractionOf(5_000L), 0.0001f)
        assertEquals(atGoal, series.fractionOf(8_000L), 0.0001f)
        assertTrue(aboveGoal > atGoal)
    }

    @Test
    fun `el acumulado negativo queda por debajo de cero`() {
        val series = MonthlyChartCalculator.calculate(
            bars = bars(-1_000, -2_000),
            expensesCents = null,
            goalCents = null,
        )

        assertEquals(listOf(-1_000L, -3_000L), series.points.map { it.cumulativeCents })
        assertEquals(-3_000L, series.minCents)
        assertEquals(0L, series.maxCents)
        assertEquals(0f, series.fractionOf(-3_000L), 0.0001f)
        assertEquals(1f, series.fractionOf(0L), 0.0001f)
        assertTrue(series.fractionOf(-500L) < series.fractionOf(0L))
    }

    @Test
    fun `la suma saturada evita desbordar con extremos de Long`() {
        val positive = MonthlyChartCalculator.calculate(
            bars = bars(Long.MAX_VALUE, Long.MAX_VALUE),
            expensesCents = null,
            goalCents = null,
        )
        assertEquals(Long.MAX_VALUE, positive.points.last().cumulativeCents)

        val negative = MonthlyChartCalculator.calculate(
            bars = bars(Long.MIN_VALUE, Long.MIN_VALUE),
            expensesCents = null,
            goalCents = null,
        )
        assertEquals(Long.MIN_VALUE, negative.points.last().cumulativeCents)
    }

    @Test
    fun `la escala es segura con Long MIN VALUE y MAX VALUE sin NaN ni infinito`() {
        // Acumulado mínimo extremo y umbral máximo extremo fuerzan la escala completa.
        val series = MonthlyChartCalculator.calculate(
            bars = bars(Long.MIN_VALUE),
            expensesCents = Long.MAX_VALUE,
            goalCents = null,
        )

        assertEquals(Long.MIN_VALUE, series.minCents)
        assertEquals(Long.MAX_VALUE, series.maxCents)

        val atMin = series.fractionOf(Long.MIN_VALUE)
        val atZero = series.fractionOf(0L)
        val atMax = series.fractionOf(Long.MAX_VALUE)

        assertFalse(atMin.isNaN())
        assertFalse(atZero.isNaN())
        assertFalse(atMax.isNaN())
        assertFalse(atMin.isInfinite())
        assertFalse(atMax.isInfinite())
        assertEquals(0f, atMin, 0.0001f)
        assertEquals(1f, atMax, 0.0001f)
        assertTrue(atZero in 0f..1f)
    }

    @Test
    fun `regresion dia conserva seis barras semana siete y mes acumula la trayectoria`() {
        val day = PeriodReportCalculator.calculate(emptyList(), ReportPeriod.DAY, LocalDate.of(2026, 10, 15), lima)
        val week = PeriodReportCalculator.calculate(emptyList(), ReportPeriod.WEEK, LocalDate.of(2026, 10, 15), lima)
        val month = PeriodReportCalculator.calculate(emptyList(), ReportPeriod.MONTH, LocalDate.of(2026, 10, 15), lima)

        assertEquals(6, day.bars.size)
        assertEquals(7, week.bars.size)
        assertEquals(31, month.bars.size)

        val monthly = MonthlyChartCalculator.calculate(
            bars = listOf(
                PeriodBar("1", 1_000L),
                PeriodBar("2", 2_000L),
                PeriodBar("3", -500L),
            ),
            expensesCents = null,
            goalCents = null,
        )
        assertEquals(listOf(1_000L, 3_000L, 2_500L), monthly.points.map { it.cumulativeCents })
        assertEquals(listOf("1", "2", "3"), monthly.points.map { it.label })
    }

    private fun bars(vararg amounts: Long): List<PeriodBar> =
        amounts.mapIndexed { index, amount -> PeriodBar((index + 1).toString(), amount) }

    /** El mínimo de separación es exacto salvo por el redondeo de `Float`. */
    private fun assertGapAtLeast(fractions: List<Float>, minimumGap: Float) {
        val tolerance = 1e-3f
        fractions.sorted().zipWithNext().forEach { (lower, upper) ->
            assertTrue(upper - lower >= minimumGap - tolerance)
        }
    }
}
