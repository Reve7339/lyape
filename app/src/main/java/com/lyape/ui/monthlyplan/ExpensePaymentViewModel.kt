package com.lyape.ui.monthlyplan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lyape.data.AccountsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.YearMonth
import java.util.UUID

internal data class PaymentSubmission(
    val saving: Boolean = false,
    val saved: Boolean = false,
    val error: String? = null,
)

/** La operación sobrevive a rotación; no se vuelve a enviar al reconstruir el formulario. */
internal class ExpensePaymentViewModel(private val repository: AccountsRepository) : ViewModel() {
    private val mutableSubmission = MutableStateFlow(PaymentSubmission())
    val submission = mutableSubmission.asStateFlow()

    fun save(expenseId: UUID, month: YearMonth, accountId: String, amountCents: Long) {
        if (mutableSubmission.value.saving || mutableSubmission.value.saved) return
        mutableSubmission.value = PaymentSubmission(saving = true)
        viewModelScope.launch {
            try {
                repository.payExpense(expenseId, month, accountId, amountCents)
                mutableSubmission.value = PaymentSubmission(saved = true)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: IllegalArgumentException) {
                mutableSubmission.value = PaymentSubmission(error = error.message ?: "Revisa el importe y la cuenta.")
            } catch (error: IllegalStateException) {
                mutableSubmission.value = PaymentSubmission(error = error.message ?: "El gasto cambió. Revisa el pago.")
            } catch (_: Exception) {
                mutableSubmission.value = PaymentSubmission(error = "No se pudo registrar el pago. Vuelve a intentarlo.")
            }
        }
    }

    fun acknowledgeSuccess() {
        if (mutableSubmission.value.saved) mutableSubmission.value = PaymentSubmission()
    }
}
