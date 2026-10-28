package com.lyape.ui.dashboard

import com.lyape.domain.ExpensePaymentProgress
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.UUID

class BalancedHomeCalculationsTest {
    private fun expense(remaining: Long, monthlyShare: Long = remaining, annual: Boolean = false) =
        ExpensePaymentProgress(UUID.randomUUID(), "Gasto", 0L, remaining, remaining, annual, monthlyShare)

    @Test fun monthlyOutstandingIncludesOnlyAnnualMonthlyShare() {
        assertEquals(20_000L, monthlyUnpaidTotal(listOf(expense(15_000L), expense(60_000L, 5_000L, true))))
    }

    @Test fun payingLowersPendingButDoesNotIncreaseProfit() {
        val unpaid = listOf(expense(15_000L))
        val paid = unpaid.map { it.copy(paidCents = 15_000L, remainingCents = 0L, monthlyUnpaidCents = 0L) }
        assertEquals(15_000L, monthlyUnpaidTotal(unpaid))
        assertEquals(0L, monthlyUnpaidTotal(paid))
        assertEquals(88_000L, estimatedMonthlyProfit(248_000L, 160_000L))
    }

    @Test fun lossIsDisplayedAsNegativeNotAsZeroProfit() {
        assertEquals(-60_000L, estimatedMonthlyProfit(100_000L, 160_000L))
    }

    @Test fun emptyPlanHasNoPendingExpenses() {
        assertEquals(0L, monthlyUnpaidTotal(emptyList()))
        assertEquals(100L, estimatedMonthlyProfit(100L, 0L))
    }

    @Test fun extremeAmountsDoNotWrapToWrongSign() {
        assertEquals(Long.MAX_VALUE, monthlyUnpaidTotal(listOf(expense(Long.MAX_VALUE), expense(1L))))
        assertEquals(Long.MIN_VALUE, estimatedMonthlyProfit(Long.MIN_VALUE, 1L))
    }
}
