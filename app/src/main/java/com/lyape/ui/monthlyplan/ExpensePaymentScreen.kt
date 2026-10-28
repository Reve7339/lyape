package com.lyape.ui.monthlyplan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lyape.ui.components.InfoRow
import com.lyape.ui.components.LyapeCard
import com.lyape.ui.dashboard.ReadFeedback
import com.lyape.ui.dashboard.ScreenRead
import com.lyape.ui.dashboard.asScreenRead
import com.lyape.ui.format.formatAmountInput
import com.lyape.ui.format.formatCents
import com.lyape.ui.format.formatMonthLabel
import com.lyape.ui.rememberAccountsRepository
import com.lyape.ui.theme.PillShape
import java.time.YearMonth
import java.util.UUID

@Composable
fun ExpensePaymentScreen(
    expenseId: String,
    month: String,
    onSaved: () -> Unit,
    onDirtyChange: (Boolean) -> Unit,
    onSavingChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val repository = rememberAccountsRepository()
    val period = remember(month) { runCatching { YearMonth.parse(month) }.getOrNull() }
    val id = remember(expenseId) { runCatching { UUID.fromString(expenseId) }.getOrNull() }
    if (period == null || id == null) {
        Text("No se encontró este gasto.", modifier.padding(17.dp))
        return
    }
    val paymentModel: ExpensePaymentViewModel = viewModel(
        key = "expense-payment:$expenseId:$month",
        factory = remember(repository) {
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = ExpensePaymentViewModel(repository) as T
            }
        },
    )
    val submission by paymentModel.submission.collectAsStateWithLifecycle()
    var retry by remember { mutableStateOf(0) }
    val cashReading by remember(repository, retry) {
        repository.observeSummary().asScreenRead()
    }.collectAsStateWithLifecycle(initialValue = ScreenRead.Loading)
    val paymentsReading by remember(repository, period, retry) {
        repository.observeExpensePayments(period).asScreenRead()
    }.collectAsStateWithLifecycle(initialValue = ScreenRead.Loading)
    val cash = (cashReading as? ScreenRead.Ready)?.value
    val payments = (paymentsReading as? ScreenRead.Ready)?.value
    val expense = payments?.firstOrNull { it.expenseId == id }

    var amountText by rememberSaveable(expenseId, month) { mutableStateOf("") }
    var accountId by rememberSaveable(expenseId, month) { mutableStateOf("") }
    var initialAmount by rememberSaveable(expenseId, month) { mutableStateOf("") }
    var initialAccount by rememberSaveable(expenseId, month) { mutableStateOf("") }
    var initialized by rememberSaveable(expenseId, month) { mutableStateOf(false) }
    var accountInitialized by rememberSaveable(expenseId, month) { mutableStateOf(false) }
    var showValidation by rememberSaveable(expenseId, month) { mutableStateOf(false) }
    LaunchedEffect(expense, cash) {
        if (!initialized && expense != null) {
            initialAmount = formatAmountInput(expense.remainingCents)
            amountText = initialAmount
            initialized = true
        }
        if (!accountInitialized && cash != null) {
            initialAccount = cash.accounts.firstOrNull { it.initialized }?.id.orEmpty()
            accountId = initialAccount
            accountInitialized = true
        }
    }
    val dirty = initialized && (amountText != initialAmount || accountId != initialAccount)
    LaunchedEffect(dirty) { onDirtyChange(dirty) }
    LaunchedEffect(submission.saving) { onSavingChange(submission.saving) }
    LaunchedEffect(submission.saved) {
        if (submission.saved) {
            onDirtyChange(false)
            onSavingChange(false)
            paymentModel.acknowledgeSuccess()
            onSaved()
        }
    }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 17.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (payments == null) {
            ReadFeedback(
                failed = paymentsReading is ScreenRead.Failed,
                message = "No se pudo leer el gasto.",
                onRetry = { retry++ },
            )
            return@Column
        }
        if (expense == null) {
            Text("Este gasto ya no está en el plan.", style = MaterialTheme.typography.bodyMedium)
            return@Column
        }
        Text(expense.name, style = MaterialTheme.typography.headlineMedium)
        Text(
            if (expense.annual) "Gasto anual · ${period.year}" else formatMonthLabel(period),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(expenseRemainingLabel(expense), style = MaterialTheme.typography.titleMedium)
        Text(
            "Registra un pago que ya hiciste. LYape no mueve dinero.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (expense.annual) {
            Text(
                "Pagar reduce el saldo de la cuenta. El coste anual sigue repartido entre los 12 meses.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (cash == null) {
            ReadFeedback(
                failed = cashReading is ScreenRead.Failed,
                message = "No se pudieron leer los saldos. El pago no se puede registrar hasta reintentar.",
                onRetry = { retry++ },
            )
        }
        Text("Pagar desde", style = MaterialTheme.typography.titleMedium)
        val accounts = cash?.accounts.orEmpty().filter { it.initialized }
        if (accounts.isEmpty()) {
            if (cash != null) {
                Text("Configura el saldo de una cuenta en Cuentas y vuelve aquí.")
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                accounts.forEach { account ->
                    FilterChip(
                        selected = account.id == accountId,
                        onClick = { accountId = account.id; showValidation = false },
                        enabled = !submission.saving,
                        label = { Text("${account.name} · ${formatCents(account.businessCents)} de negocio") },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    )
                }
            }
        }
        val account = accounts.firstOrNull { it.id == accountId }
        val check = validateExpensePayment(amountText, account, expense)
        val amountError = (check as? PaymentCheck.Invalid)?.message?.takeIf { showValidation }
        OutlinedTextField(
            value = amountText,
            onValueChange = { amountText = it; showValidation = false },
            label = { Text("Importe (S/)") },
            singleLine = true,
            enabled = !submission.saving && cash != null && expense.remainingCents > 0L,
            isError = amountError != null,
            supportingText = amountError?.let { message -> { Text(message) } },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )
        if (check is PaymentCheck.Ready) {
            LyapeCard {
                InfoRow("Saldo del negocio después", formatCents(check.businessAfterCents))
                InfoRow("Resta por pagar", formatCents(check.remainingAfterCents))
            }
        }
        submission.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        }
        Button(
            onClick = {
                showValidation = true
                if (check is PaymentCheck.Ready && account != null) {
                    onSavingChange(true)
                    paymentModel.save(id, period, account.id, check.amountCents)
                }
            },
            enabled = !submission.saving && !submission.saved && cash != null &&
                accounts.isNotEmpty() && expense.remainingCents > 0L,
            shape = PillShape,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) {
            Text(if (submission.saving) "Guardando…" else "Registrar pago")
        }
        Spacer(Modifier.height(16.dp))
    }
}
