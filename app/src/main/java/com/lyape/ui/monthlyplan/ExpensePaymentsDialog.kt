package com.lyape.ui.monthlyplan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lyape.domain.CashOperation
import com.lyape.domain.MonthlyExpense
import com.lyape.ui.dashboard.ReadFeedback
import com.lyape.ui.dashboard.ScreenRead
import com.lyape.ui.dashboard.asScreenRead
import com.lyape.ui.format.formatCents
import com.lyape.ui.rememberAccountsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** El mismo historial que Cuentas, filtrado por gasto e incluyendo todos sus periodos. */
@Composable
internal fun ExpensePaymentsDialog(expense: MonthlyExpense, onDismiss: () -> Unit) {
    val repository = rememberAccountsRepository()
    val scope = rememberCoroutineScope()
    var limit by remember(expense.id) { mutableIntStateOf(25) }
    var retry by remember { mutableIntStateOf(0) }
    var selected by remember { mutableStateOf<CashOperation?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val reading by remember(repository, expense.id, limit, retry) {
        combine(repository.observeExpenseOperations(expense.id, limit + 1), repository.observeSelectableAccounts()) { payments, accounts ->
            payments to accounts.associate { it.id to it.name }
        }.asScreenRead()
    }.collectAsStateWithLifecycle(initialValue = ScreenRead.Loading)
    val content = (reading as? ScreenRead.Ready)?.value
    val dateFormat = remember { DateTimeFormatter.ofPattern("d MMM yyyy · HH:mm", Locale("es", "PE")) }

    if (selected == null) AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Pagos de ${expense.name}") },
        text = {
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 400.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (content == null) {
                    item { ReadFeedback(reading is ScreenRead.Failed, "No se pudieron cargar los pagos.", { retry++ }) }
                } else {
                    if (content.first.isEmpty()) item { Text("Este gasto aún no tiene pagos registrados.") }
                    items(content.first.take(limit), key = { it.id.toString() }) { payment ->
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(formatCents(payment.amountCents), style = MaterialTheme.typography.titleMedium)
                            Text(content.second[payment.fromAccountId] ?: "Cuenta no disponible")
                            Text(dateFormat.format(payment.occurredAt.atZone(ZoneId.systemDefault())), style = MaterialTheme.typography.bodySmall)
                            payment.expensePeriod?.let { Text("Periodo: $it", style = MaterialTheme.typography.bodySmall) }
                            if (payment.voided) {
                                Text("Pago anulado", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            } else {
                                TextButton(onClick = { selected = payment; error = null }, enabled = !busy,
                                    modifier = Modifier.heightIn(min = 48.dp)) { Text("Anular pago") }
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                    if (content.first.size > limit) item {
                        TextButton(onClick = { limit += 25 }, enabled = !busy) { Text("Ver más pagos") }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Listo") } },
    )
    selected?.let { payment ->
        AlertDialog(
            onDismissRequest = { if (!busy) selected = null },
            title = { Text("¿Anular pago de ${formatCents(payment.amountCents)}?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("El importe volverá a quedar pendiente. Si actualizaste el saldo después del pago, ese saldo se conserva; si no, se devuelve a la cuenta.")
                    Text("Solo cambia el registro en LYape, no el pago real.", style = MaterialTheme.typography.bodySmall)
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                TextButton(enabled = !busy, onClick = {
                    busy = true
                    scope.launch {
                        try {
                            repository.undoOperation(payment.id)
                            selected = null
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (invalid: IllegalArgumentException) {
                            error = invalid.message ?: "No se pudo anular el pago."
                        } catch (_: Exception) {
                            error = "No se pudo anular. Vuelve a intentarlo."
                        } finally { busy = false }
                    }
                }) { Text(if (busy) "Anulando…" else "Anular pago", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { selected = null }, enabled = !busy) { Text("Cancelar") } },
        )
    }
}
