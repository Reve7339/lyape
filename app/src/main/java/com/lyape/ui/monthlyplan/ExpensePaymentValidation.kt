package com.lyape.ui.monthlyplan

import com.lyape.domain.AccountBalance
import com.lyape.domain.ExpensePaymentProgress
import com.lyape.ui.format.AmountParse
import com.lyape.ui.format.formatCents
import com.lyape.ui.format.parseAmountCents

internal sealed interface PaymentCheck {
    data class Ready(
        val amountCents: Long,
        val businessAfterCents: Long,
        val remainingAfterCents: Long,
    ) : PaymentCheck
    data class Invalid(val message: String) : PaymentCheck
}

internal fun validateExpensePayment(
    amountText: String,
    account: AccountBalance?,
    expense: ExpensePaymentProgress,
): PaymentCheck {
    if (expense.remainingCents == 0L) return PaymentCheck.Invalid("Este gasto ya está pagado.")
    if (account == null || !account.initialized) {
        return PaymentCheck.Invalid("Configura el saldo de una cuenta antes de registrar el pago.")
    }
    val amount = when (val parsed = parseAmountCents(amountText)) {
        AmountParse.Empty -> return PaymentCheck.Invalid("Ingresa un importe.")
        AmountParse.Malformed -> return PaymentCheck.Invalid("Usa un importe como 150.00.")
        AmountParse.TooLarge -> return PaymentCheck.Invalid("El importe es demasiado grande.")
        is AmountParse.Valid -> parsed.cents
    }
    if (amount <= 0L) return PaymentCheck.Invalid("El importe debe ser mayor que cero.")
    if (amount > expense.remainingCents) {
        return PaymentCheck.Invalid("El importe supera lo pendiente. Ingresa hasta ${formatCents(expense.remainingCents)}.")
    }
    if (amount > account.businessCents) {
        return PaymentCheck.Invalid("Saldo de negocio insuficiente. Elige otra cuenta o reduce el importe.")
    }
    return PaymentCheck.Ready(amount, account.businessCents - amount, expense.remainingCents - amount)
}

internal fun expenseRemainingLabel(payment: ExpensePaymentProgress): String = when {
    payment.remainingCents == 0L -> if (payment.annual) "Año pagado" else "Pagado"
    payment.annual -> "Por pagar en el año: ${formatCents(payment.remainingCents)}"
    else -> "Por pagar: ${formatCents(payment.remainingCents)}"
}
