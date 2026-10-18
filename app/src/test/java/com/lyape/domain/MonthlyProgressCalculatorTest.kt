package com.lyape.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth
import java.util.UUID

class MonthlyProgressCalculatorTest {

    private val month = YearMonth.of(2026, 10)

    @Test
    fun `sin configuracion solo conserva los ingresos`() {
        val result = calculate(income = 5_000)

        assertEquals(5_000L, result.incomeCents)
        assertEquals(0L, result.totalExpensesCents)
        assertFalse(result.hasExpenses)
        assertFalse(result.hasGoal)
        assertNull(result.expensesRemainingCents)
        assertNull(result.goalRemainingCents)
    }

    @Test
    fun `debajo de gastos muestra el faltante exacto`() {
        val result = calculate(income = 4_000, expenses = listOf(3_000, 2_500))

        assertEquals(5_500L, result.totalExpensesCents)
        assertEquals(1_500L, result.expensesRemainingCents)
        assertFalse(result.expensesCovered)
        assertNull(result.estimatedProfitCents)
    }

    @Test
    fun `al cubrir exactamente los gastos la ganancia es cero`() {
        val result = calculate(income = 5_500, expenses = listOf(3_000, 2_500))

        assertTrue(result.expensesCovered)
        assertEquals(0L, result.expensesRemainingCents)
        assertEquals(0L, result.estimatedProfitCents)
    }

    @Test
    fun `por encima de gastos muestra ganancia estimada`() {
        val result = calculate(income = 7_000, expenses = listOf(3_000, 2_500))

        assertEquals(0L, result.expensesRemainingCents)
        assertEquals(1_500L, result.estimatedProfitCents)
    }

    @Test
    fun `meta sola muestra faltante y luego excedente`() {
        val below = calculate(income = 4_000, goal = 6_000)
        assertEquals(2_000L, below.goalRemainingCents)
        assertNull(below.goalExceededCents)
        assertFalse(below.goalReached)

        val reached = calculate(income = 7_500, goal = 6_000)
        assertEquals(0L, reached.goalRemainingCents)
        assertEquals(1_500L, reached.goalExceededCents)
        assertTrue(reached.goalReached)
    }

    @Test
    fun `gastos y meta se calculan de forma independiente`() {
        val result = calculate(income = 6_000, expenses = listOf(5_000), goal = 8_000)

        assertEquals(1_000L, result.estimatedProfitCents)
        assertEquals(2_000L, result.goalRemainingCents)
    }

    @Test
    fun `ingreso negativo aumenta el faltante`() {
        val result = calculate(income = -1_000, expenses = listOf(5_000), goal = 8_000)

        assertEquals(6_000L, result.expensesRemainingCents)
        assertEquals(9_000L, result.goalRemainingCents)
    }

    @Test
    fun `rechaza gastos negativos y metas no positivas`() {
        assertThrows(IllegalArgumentException::class.java) {
            calculate(income = 0, expenses = listOf(-1))
        }
        assertThrows(IllegalArgumentException::class.java) {
            calculate(income = 0, goal = -1)
        }
    }

    @Test
    fun `acepta una parte mensual cero de un gasto anual menor a doce centimos`() {
        val result = calculate(income = 0, expenses = listOf(0))

        assertTrue(result.hasExpenses)
        assertEquals(0L, result.totalExpensesCents)
        assertTrue(result.expensesCovered)
    }

    @Test
    fun `gasto anual divisible se reparte igual en los doce meses`() {
        val shares = (1..12).map { annualExpenseShare(120_000L, it) }

        assertEquals(List(12) { 10_000L }, shares)
        assertEquals(120_000L, shares.sum())
    }

    @Test
    fun `centimos sobrantes del anual se reparten sin cambiar el total`() {
        val shares = (1..12).map { annualExpenseShare(100L, it) }

        assertEquals(List(4) { 9L } + List(8) { 8L }, shares)
        assertEquals(100L, shares.sum())
    }

    @Test
    fun `reparto anual rechaza total y mes invalidos`() {
        assertThrows(IllegalArgumentException::class.java) {
            annualExpenseShare(0L, 1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            annualExpenseShare(1_200L, 13)
        }
    }

    @Test
    fun `satura sumas y faltantes extremos sin desbordar`() {
        val result = calculate(
            income = Long.MIN_VALUE,
            expenses = listOf(Long.MAX_VALUE, 1),
        )

        assertEquals(Long.MAX_VALUE, result.totalExpensesCents)
        assertEquals(Long.MAX_VALUE, result.expensesRemainingCents)
    }

    private fun calculate(
        income: Long,
        expenses: List<Long> = emptyList(),
        goal: Long? = null,
    ): MonthlyProgress {
        val plan = MonthlyPlan(
            month = month,
            expenses = expenses.mapIndexed { index, amount ->
                MonthlyExpense(
                    id = UUID.nameUUIDFromBytes("expense-$index".toByteArray()),
                    month = month,
                    name = "Gasto $index",
                    amountCents = amount,
                )
            },
            goal = goal?.let { MonthlyGoal(month, it) },
        )
        return MonthlyProgressCalculator.calculate(income, plan)
    }
}
