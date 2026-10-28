package com.lyape.ui.monthlyplan

import com.lyape.domain.AccountBalance
import com.lyape.domain.AccountKind
import com.lyape.domain.ExpensePaymentProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.util.UUID

class ExpensePaymentValidationTest {
    private val account = AccountBalance("yape", "Yape", AccountKind.YAPE, true, 20_000L, 90_000L, Instant.EPOCH)
    private val expense = ExpensePaymentProgress(UUID.randomUUID(), "Luz", 0L, 15_000L, 15_000L, false, 15_000L)

    @Test fun partialPaymentPreviewUsesOnlyBusinessMoney() {
        assertEquals(PaymentCheck.Ready(5_000L, 15_000L, 10_000L), validateExpensePayment("50.00", account, expense))
    }

    @Test fun exactPaymentLeavesNoExpensePending() {
        assertEquals(PaymentCheck.Ready(15_000L, 5_000L, 0L), validateExpensePayment("150", account, expense))
    }

    @Test fun personalBalanceCannotPayBusinessExpense() {
        val result = validateExpensePayment("150", account.copy(businessCents = 100L), expense)
        assertTrue((result as PaymentCheck.Invalid).message.contains("negocio insuficiente"))
    }

    @Test fun rejectsMissingAndUninitializedAccounts() {
        assertTrue(validateExpensePayment("50", null, expense) is PaymentCheck.Invalid)
        assertTrue(validateExpensePayment("50", account.copy(initialized = false), expense) is PaymentCheck.Invalid)
    }

    @Test fun rejectsAmountsAboveOutstanding() {
        assertTrue((validateExpensePayment("150.01", account, expense) as PaymentCheck.Invalid).message.contains("supera lo pendiente"))
    }

    @Test fun rejectsBlankZeroMalformedAndOverflow() {
        listOf("", "0", "35.505", "1,284.50", "9223372036854775808").forEach { input ->
            assertTrue(input, validateExpensePayment(input, account, expense) is PaymentCheck.Invalid)
        }
    }

    @Test fun paidExpenseCannotBeSubmittedAgain() {
        assertEquals(
            PaymentCheck.Invalid("Este gasto ya está pagado."),
            validateExpensePayment("1", account, expense.copy(remainingCents = 0L, paidCents = 15_000L)),
        )
    }

    @Test fun annualPaymentUsesAnnualRemainingNotMonthlyShare() {
        val annual = expense.copy(annual = true, budgetCents = 120_000L, remainingCents = 120_000L, monthlyUnpaidCents = 10_000L)
        assertEquals(PaymentCheck.Ready(15_000L, 5_000L, 105_000L), validateExpensePayment("150", account, annual))
        assertEquals("Por pagar en el año: S/ 1,200.00", expenseRemainingLabel(annual))
        assertEquals("Año pagado", expenseRemainingLabel(annual.copy(remainingCents = 0L)))
    }
}
