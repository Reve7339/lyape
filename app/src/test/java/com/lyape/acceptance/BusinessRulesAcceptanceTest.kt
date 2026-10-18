package com.lyape.acceptance

import com.lyape.domain.MonthlyChartCalculator
import com.lyape.domain.MonthlyExpense
import com.lyape.domain.MonthlyGoal
import com.lyape.domain.MonthlyPlan
import com.lyape.domain.MonthlyProgressCalculator
import com.lyape.domain.Movement
import com.lyape.domain.MovementClassification
import com.lyape.domain.MovementEdits
import com.lyape.domain.MovementFactory
import com.lyape.domain.PeriodReportCalculator
import com.lyape.domain.ReportPeriod
import com.lyape.domain.TotalsCalculator
import com.lyape.domain.annualExpenseShare
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.UUID

/** Reglas de negocio como ejemplos ejecutables Dado/Cuando/Entonces. */
class BusinessRulesAcceptanceTest {
    private val zone = ZoneId.of("America/Lima")
    private val date = LocalDate.of(2026, 10, 15)
    private val month = YearMonth.from(date)
    private val now = Instant.parse("2026-10-15T17:00:00Z")

    @Test
    fun bdd01PersonalAndExcludedDoNotInflateIncome() {
        // Dado un ingreso de S/ 100, un personal de S/ 50 y un anulado de S/ 70.
        val movements = listOf(
            payment(10_000),
            MovementEdits.classify(payment(5_000), MovementClassification.PERSONAL, now),
            MovementEdits.exclude(payment(7_000), now),
            refund(-2_000),
        )
        // Cuando consulto el mes, después de una devolución de S/ 20.
        val report = PeriodReportCalculator.calculate(movements, ReportPeriod.MONTH, date, zone)
        // Entonces solo el negocio neto suma S/ 80 y coincide con el gráfico.
        assertEquals(8_000L, report.totals.businessCents)
        assertEquals(5_000L, report.totals.personalCents)
        assertEquals(8_000L, report.bars.sumOf { it.amountCents })
        assertEquals(3, report.pendingActiveCount)
    }

    @Test
    fun bdd02ExpensesAndGoalAreIndependent() {
        // Dado un plan de gastos S/ 100 y meta de ingresos S/ 200.
        val plan = plan(expenses = 10_000, goal = 20_000)
        // Cuando paso de S/ 80 a S/ 120 y finalmente a S/ 220.
        val below = MonthlyProgressCalculator.calculate(8_000, plan)
        val profitable = MonthlyProgressCalculator.calculate(12_000, plan)
        val exceeded = MonthlyProgressCalculator.calculate(22_000, plan)
        // Entonces faltantes, ganancia y excedente son independientes y exactos.
        assertEquals(2_000L, below.expensesRemainingCents)
        assertEquals(12_000L, below.goalRemainingCents)
        assertNull(below.estimatedProfitCents)
        assertEquals(2_000L, profitable.estimatedProfitCents)
        assertEquals(8_000L, profitable.goalRemainingCents)
        assertTrue(exceeded.expensesCovered)
        assertTrue(exceeded.goalReached)
        assertEquals(12_000L, exceeded.estimatedProfitCents)
        assertEquals(2_000L, exceeded.goalExceededCents)
    }

    @Test
    fun bdd03AnnualCentsAreNotLost() {
        // Dado un gasto anual S/ 100.01 que no divide exactamente entre 12.
        val annual = 10_001L
        // Cuando reservo su parte para cada mes.
        val shares = (1..12).map { annualExpenseShare(annual, it) }
        // Entonces no se pierde ningún céntimo y los sobrantes van desde enero.
        assertEquals(annual, shares.sum())
        assertEquals(List(5) { 834L } + List(7) { 833L }, shares)
    }

    @Test
    fun bdd04RefundLowersMonthlyCurve() {
        // Dado S/ 100 el día 1 y una devolución S/ 30 el día 2.
        val movements = listOf(
            payment(10_000, month.atDay(1).atStartOfDay(zone).toInstant()),
            refund(-3_000, month.atDay(2).atStartOfDay(zone).toInstant()),
        )
        // Cuando dibujo el acumulado con gastos S/ 50 y meta S/ 200.
        val report = PeriodReportCalculator.calculate(movements, ReportPeriod.MONTH, date, zone)
        val chart = MonthlyChartCalculator.calculate(report.bars, 5_000, 20_000)
        // Entonces la curva baja, termina en el ingreso neto y conserva ambas referencias.
        assertEquals(10_000L, chart.points[0].cumulativeCents)
        assertEquals(7_000L, chart.points[1].cumulativeCents)
        assertEquals(report.totals.businessCents, chart.points.last().cumulativeCents)
        assertEquals(listOf(5_000L, 20_000L), chart.thresholds.map { it.amountCents })
    }

    @Test
    fun bdd05MonthBoundaryUsesLocalTime() {
        // Dado un pago al último milisegundo de octubre en Lima y otro al iniciar noviembre.
        val boundary = LocalDate.of(2026, 11, 1).atStartOfDay(zone).toInstant()
        val movements = listOf(payment(1_000, boundary.minusMillis(1)), payment(2_000, boundary))
        // Cuando consulto octubre.
        val report = PeriodReportCalculator.calculate(movements, ReportPeriod.MONTH, date, zone)
        // Entonces noviembre no infla el ingreso de octubre aunque ambos instantes sean UTC.
        assertEquals(1_000L, report.totals.businessCents)
        assertEquals(1, report.businessMovementCount)
        assertFalse(report.range.contains(boundary))
    }

    @Test
    fun bdd06PersonalRestoresAsPersonal() {
        // Dado un pago personal.
        val original = MovementEdits.classify(payment(5_000), MovementClassification.PERSONAL, now)
        // Cuando lo anulo y lo restauro.
        val restored = MovementEdits.restore(MovementEdits.exclude(original, now), now)
        // Entonces no se transforma en ingreso de negocio ni cambia su identidad.
        assertEquals(MovementClassification.PERSONAL, restored.classification)
        assertEquals(original.id, restored.id)
        assertEquals(original.capturedAt, restored.capturedAt)
        assertEquals(0L, TotalsCalculator.totalFor(listOf(restored)).businessCents)
    }

    @Test
    fun bdd07NegativeAdjustmentRequiresReason() {
        // Dado un ajuste negativo que alguien intenta registrar sin motivo.
        // Cuando se valida, entonces se rechaza en vez de crear una corrección inexplicable.
        assertThrows(IllegalArgumentException::class.java) {
            MovementFactory.adjustment(UUID.randomUUID(), -500, now, "  ", now)
        }
        val valid = refund(-500)
        assertEquals("Devolución", valid.adjustmentReason)
        assertEquals(-500L, valid.amountCents)
    }

    private fun payment(amount: Long, at: Instant = now): Movement =
        MovementFactory.manual(UUID.randomUUID(), amount, at, "Cliente de prueba", now)

    private fun refund(amount: Long, at: Instant = now): Movement =
        MovementFactory.adjustment(UUID.randomUUID(), amount, at, "Devolución", now)

    private fun plan(expenses: Long, goal: Long) = MonthlyPlan(
        month = month,
        expenses = listOf(MonthlyExpense(UUID.randomUUID(), month, "Alquiler", expenses)),
        goal = MonthlyGoal(month, goal),
    )
}
