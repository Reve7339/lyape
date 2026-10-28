package com.lyape.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth
import java.util.UUID

class CashTest {
    @Test
    fun annualRemainingSharesAlwaysSumExactly() {
        for (paid in listOf(0L, 1L, 119L, 1200L, 1201L)) {
            val parts = (1..12).map { month ->
                expensePaymentProgress(MonthlyExpense(UUID.randomUUID(), YearMonth.of(2026, month),
                    "Anual", annualExpenseShare(1201, month), ExpenseSchedule.ANNUAL_FIXED, 1201), paid)
            }
            assertEquals(1201 - paid, parts.sumOf { it.monthlyUnpaidCents })
            assertTrue(parts.all { it.remainingCents == 1201 - paid && it.budgetCents == 1201L })
        }
    }

    @Test
    fun monthlyPaymentDoesNotChangeBudget() {
        val expense = MonthlyExpense(UUID.randomUUID(), YearMonth.of(2026, 10), "Luz", 15000)
        val result = expensePaymentProgress(expense, 5000)
        assertEquals(15000L, result.budgetCents)
        assertEquals(10000L, result.monthlyUnpaidCents)
        assertEquals(15000L, expense.amountCents)
    }

    @Test
    fun annualPeriodCannotBePaidAgainInAnotherMonthOfSameYear() {
        assertEquals("2026", expensePaymentPeriod(YearMonth.of(2026, 1), ExpenseSchedule.ANNUAL_FIXED))
        assertEquals("2026", expensePaymentPeriod(YearMonth.of(2026, 12), ExpenseSchedule.ANNUAL_FIXED))
        assertEquals("2026-12", expensePaymentPeriod(YearMonth.of(2026, 12), ExpenseSchedule.MONTHLY_FIXED))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsOverpaidBudget() {
        expensePaymentProgress(MonthlyExpense(UUID.randomUUID(), YearMonth.of(2026, 10), "Luz", 10), 11)
    }

    @Test
    fun businessAndPersonalAreSeparateAndUnconfiguredIsExplicit() {
        val summary = CashSummary(listOf(
            AccountBalance("yape", "Yape", AccountKind.YAPE, true, 10000, 2000, null),
            AccountBalance("cash", "Efectivo", AccountKind.CASH, false, 0, 0, null),
        ))
        assertEquals(10000L, summary.businessCents)
        assertEquals(2000L, summary.personalCents)
        assertEquals(12000L, summary.totalCents)
        assertTrue(summary.hasUnconfiguredAccounts)
    }

    @Test(expected = ArithmeticException::class)
    fun overflowNeverWrapsIntoNegativeMoney() {
        AccountBalance("yape", "Yape", AccountKind.YAPE, true, Long.MAX_VALUE, 1, null).totalCents
    }

    @Test
    fun intermediateOverflowDoesNotRejectRepresentableFinalBalance() {
        assertEquals(Long.MAX_VALUE, sumCashExact(Long.MAX_VALUE, 1L, -1L))
        assertEquals(Long.MIN_VALUE, sumCashExact(Long.MIN_VALUE, -1L, 1L))
        assertEquals(0L, sumCashExact())
    }

    @Test(expected = ArithmeticException::class)
    fun rejectsPositiveFinalSumOverflow() {
        sumCashExact(Long.MAX_VALUE, 1L)
    }

    @Test(expected = ArithmeticException::class)
    fun rejectsNegativeFinalSumOverflow() {
        sumCashExact(Long.MIN_VALUE, -1L)
    }

    @Test
    fun summaryDoesNotDependOnAccountOrderWhenSignedBalancesCancel() {
        val values = listOf(Long.MAX_VALUE, 1L, -1L).mapIndexed { index, value ->
            AccountBalance(index.toString(), "Cuenta", AccountKind.BANK, true, value, 0, null)
        }
        assertEquals(Long.MAX_VALUE, CashSummary(values).businessCents)
        assertEquals(Long.MAX_VALUE, CashSummary(values.reversed()).totalCents)
    }
}
