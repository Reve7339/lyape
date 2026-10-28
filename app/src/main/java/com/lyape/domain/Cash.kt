package com.lyape.domain

import java.time.Instant
import java.math.BigInteger
import java.time.YearMonth
import java.util.UUID

enum class AccountKind { YAPE, CASH, BANK }

data class AccountBalance(
    val id: String,
    val name: String,
    val kind: AccountKind,
    val initialized: Boolean,
    val businessCents: Long,
    val personalCents: Long,
    val baselineAt: Instant?,
) {
    val totalCents: Long get() = Math.addExact(businessCents, personalCents)
}

data class CashSummary(val accounts: List<AccountBalance>) {
    val businessCents: Long get() = accounts.fold(BigInteger.ZERO) { sum, a -> sum + BigInteger.valueOf(a.businessCents) }.toCashLongExact()
    val personalCents: Long get() = accounts.fold(BigInteger.ZERO) { sum, a -> sum + BigInteger.valueOf(a.personalCents) }.toCashLongExact()
    val totalCents: Long get() = Math.addExact(businessCents, personalCents)
    val hasUnconfiguredAccounts: Boolean get() = accounts.any { !it.initialized }
}

enum class CashOperationKind { TRANSFER, WITHDRAWAL, EXPENSE_PAYMENT }

data class CashOperation(
    val id: UUID,
    val kind: CashOperationKind,
    val fromAccountId: String,
    val toAccountId: String?,
    val classification: MovementClassification,
    val amountCents: Long,
    val note: String?,
    val occurredAt: Instant,
    val voided: Boolean,
    val expenseName: String?,
    val expensePeriod: String? = null,
)

data class ExpensePaymentProgress(
    val expenseId: UUID,
    val name: String,
    val paidCents: Long,
    val remainingCents: Long,
    val budgetCents: Long,
    val annual: Boolean,
    val monthlyUnpaidCents: Long,
)

/** El pago reduce deuda y caja, nunca el costo presupuestado ni los ingresos. */
fun expensePaymentProgress(expense: MonthlyExpense, paidCents: Long): ExpensePaymentProgress {
    val annual = expense.schedule == ExpenseSchedule.ANNUAL_FIXED
    val budget = if (annual) expense.plannedAmountCents else expense.amountCents
    require(paidCents in 0L..budget) { "Los pagos no coinciden con el gasto." }
    val remaining = budget - paidCents
    return ExpensePaymentProgress(
        expense.id, expense.name, paidCents, remaining, budget, annual,
        if (annual && remaining > 0L) annualExpenseShare(remaining, expense.month.monthValue) else remaining,
    )
}

fun expensePaymentPeriod(month: YearMonth, schedule: ExpenseSchedule): String =
    if (schedule == ExpenseSchedule.ANNUAL_FIXED) month.year.toString() else month.toString()

internal fun requireCashAmount(amount: Long, available: Long) {
    require(amount > 0L) { "El importe debe ser mayor que cero." }
    require(amount <= available) { "Saldo insuficiente. Reduce el importe o elige otra cuenta." }
}

/** Intermedios exactos; solo el resultado final debe caber en céntimos Long. */
internal fun sumCashExact(vararg amounts: Long): Long = amounts
    .fold(BigInteger.ZERO) { sum, amount -> sum + BigInteger.valueOf(amount) }
    .toCashLongExact()

/** BigInteger.longValueExact requiere API 31; esta comprobación funciona desde minSdk 26. */
private fun BigInteger.toCashLongExact(): Long {
    if (bitLength() > 63) throw ArithmeticException("El importe supera el rango de Long.")
    return toLong()
}
