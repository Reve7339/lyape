package com.lyape.domain

import java.time.YearMonth
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test

class ProfitGoalTest {
    private val month = YearMonth.of(2026, 10)
    private fun plan(expense: Long = 95_000L, goal: Long = 50_000L, type: GoalType = GoalType.PROFIT) = MonthlyPlan(
        month, listOf(MonthlyExpense(UUID.randomUUID(), month, "Gastos", expense)), MonthlyGoal(month, goal, type),
    )
    @Test fun `ganancia 500 gastos 950 e ingresos 1200 requieren 1450`() {
        val p = MonthlyProgressCalculator.calculate(120_000L, plan())
        assertEquals(145_000L, p.requiredIncomeCents)
        assertEquals(25_000L, p.signedProfitCents)
        assertEquals(25_000L, p.goalRemainingCents)
        assertFalse(p.goalReached)
    }
    @Test fun `un extra eleva el objetivo pero pagar no cambia el presupuesto`() {
        val original = plan()
        val before = MonthlyProgressCalculator.calculate(120_000L, original)
        // El cálculo no recibe ni caja ni pagos: registrar un pago conserva exactamente el plan.
        assertEquals(before, MonthlyProgressCalculator.calculate(120_000L, original))
        val extra = MonthlyExpense(UUID.randomUUID(), month, "Extra", 10_000L)
        val after = MonthlyProgressCalculator.calculate(120_000L, original.copy(expenses = original.expenses + extra))
        assertEquals(155_000L, after.requiredIncomeCents)
        assertEquals(15_000L, after.signedProfitCents)
    }
    @Test fun `meta de ingresos no suma gastos a su importe`() {
        val p = MonthlyProgressCalculator.calculate(120_000L, plan(type = GoalType.INCOME))
        assertEquals(50_000L, p.requiredIncomeCents)
        assertTrue(p.goalReached)
        assertEquals(70_000L, p.goalExceededCents)
    }
    @Test fun `sin gastos la ganancia coincide con ingresos`() {
        val p = MonthlyProgressCalculator.calculate(20_000L, plan().copy(expenses = emptyList()))
        assertEquals(50_000L, p.requiredIncomeCents)
        assertEquals(20_000L, p.signedProfitCents)
        assertEquals(30_000L, p.goalRemainingCents)
    }
    @Test fun `la perdida mantiene el signo y aumenta lo que falta`() {
        val p = MonthlyProgressCalculator.calculate(-5_000L, plan())
        assertEquals(-100_000L, p.signedProfitCents)
        assertEquals(150_000L, p.goalRemainingCents)
    }
    @Test fun `coste anual conserva centimos y cambia el objetivo mensual`() {
        val annual = (1..12).map { annualExpenseShare(1_201, it) }
        assertEquals(1_201L, annual.sum())
        assertEquals(50_101L, MonthlyProgressCalculator.calculate(0, plan(expense = annual[0])).requiredIncomeCents)
        assertEquals(50_100L, MonthlyProgressCalculator.calculate(0, plan(expense = annual[1])).requiredIncomeCents)
    }
    @Test fun `extremos no producen meta alcanzada ni objetivo saturado`() {
        listOf(
            MonthlyProgressCalculator.calculate(Long.MAX_VALUE, plan(Long.MAX_VALUE, 1)),
            MonthlyProgressCalculator.calculate(Long.MIN_VALUE, plan()),
            MonthlyProgressCalculator.calculate(Long.MIN_VALUE, plan(0, Long.MAX_VALUE)),
        ).forEach { assertTrue(it.calculationError); assertFalse(it.goalReached); assertNull(it.requiredIncomeCents) }
    }
    @Test fun `meta antigua sin tipo conserva ingresos`() {
        assertEquals(GoalType.INCOME, MonthlyGoal(month, 500).type)
    }
}
