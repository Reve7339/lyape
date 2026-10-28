package com.lyape.ui.accounts

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lyape.R
import com.lyape.domain.AccountBalance
import com.lyape.domain.CaptureConfiguration
import com.lyape.domain.CaptureProvider
import com.lyape.domain.CashOperation
import com.lyape.domain.CashOperationKind
import com.lyape.domain.CashSummary
import com.lyape.domain.MovementClassification
import com.lyape.ui.components.InfoRow
import com.lyape.ui.components.LyapeCard
import com.lyape.ui.dashboard.ReadFeedback
import com.lyape.ui.format.formatCents
import com.lyape.ui.rememberAccountsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

internal sealed interface AccountLoadState<out T> {
    data object Loading : AccountLoadState<Nothing>
    data object Error : AccountLoadState<Nothing>
    data class Ready<T>(val value: T) : AccountLoadState<T>
}

internal fun <T> Flow<T>.accountLoadState(): Flow<AccountLoadState<T>> =
    map<T, AccountLoadState<T>> { AccountLoadState.Ready(it) }
        .catch { emit(AccountLoadState.Error) }

/** Solo una asociación existente cuya cuenta ya no está disponible requiere reparación. */
internal fun List<CaptureConfiguration>.brokenCaptureLinks(): List<CaptureConfiguration> =
    filter { it.link != null && it.link.account == null }

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AccountsScreen(
    onOpenAccount: (String) -> Unit,
    onAddAccount: () -> Unit,
    onTransfer: () -> Unit,
    onWithdraw: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val repository = rememberAccountsRepository()
    var retry by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val state by remember(repository, retry) { repository.observeSummary().accountLoadState() }
        .collectAsStateWithLifecycle(initialValue = AccountLoadState.Loading)
    var captureRetry by remember { mutableIntStateOf(0) }
    val captureState by remember(repository, captureRetry) {
        combine(
            repository.observeCaptureConfiguration(CaptureProvider.YAPE),
            repository.observeCaptureConfiguration(CaptureProvider.INTERBANK_PLIN),
        ) { yape, plin -> listOf(yape, plin) }
            .accountLoadState()
    }.collectAsStateWithLifecycle(initialValue = AccountLoadState.Loading)
    var repairing by remember { mutableStateOf<CaptureProvider?>(null) }
    var repairError by remember { mutableStateOf<String?>(null) }
    val captureConfigurations = (captureState as? AccountLoadState.Ready)?.value.orEmpty()
    val brokenLinks = captureConfigurations.brokenCaptureLinks()

    fun repair(provider: CaptureProvider) {
        if (repairing != null) return
        repairing = provider
        repairError = null
        scope.launch {
            try {
                repository.repairCaptureLink(provider)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: IllegalArgumentException) {
                repairError = error.message ?: "No se pudo restablecer el vínculo."
            } catch (_: Exception) {
                repairError = "No se pudo restablecer el vínculo. Vuelve a intentarlo."
            } finally {
                repairing = null
            }
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { Text("Cuentas", style = MaterialTheme.typography.headlineMedium) }
        when (captureState) {
            AccountLoadState.Loading -> Unit
            AccountLoadState.Error -> item {
                CaptureCheckError { captureRetry++ }
            }
            is AccountLoadState.Ready -> if (brokenLinks.isNotEmpty()) {
                item {
                    CaptureLinkNotice(
                        configurations = brokenLinks,
                        repairing = repairing,
                        error = repairError,
                        onRepair = ::repair,
                    )
                }
            }
        }
        when (val current = state) {
            AccountLoadState.Loading -> item { Text("Cargando cuentas…") }
            AccountLoadState.Error -> item { AccountsReadError { retry++ } }
            is AccountLoadState.Ready -> {
                val summary = current.value
                item { CashSummaryCard(summary) }
                items(summary.accounts, key = { it.id }) { account ->
                    AccountBalanceCard(account, onClick = { onOpenAccount(account.id) })
                }
                item {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(onClick = onTransfer, modifier = Modifier.heightIn(min = 48.dp)) { Text("Transferir") }
                        OutlinedButton(onClick = onWithdraw, modifier = Modifier.heightIn(min = 48.dp)) { Text("Registrar salida") }
                    }
                    TextButton(onClick = onAddAccount, modifier = Modifier.heightIn(min = 48.dp).padding(top = 8.dp)) {
                        Text("Agregar cuenta")
                    }
                    AccountNote("Saldos según tus registros.")
                }
            }
        }
    }
}

@Composable
private fun CaptureLinkNotice(
    configurations: List<CaptureConfiguration>,
    repairing: CaptureProvider?,
    error: String?,
    onRepair: (CaptureProvider) -> Unit,
) {
    LyapeCard {
        Text("Captura detenida", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        configurations.forEach { configuration ->
            val providerName = when (configuration.provider) {
                CaptureProvider.YAPE -> "Yape"
                CaptureProvider.INTERBANK_PLIN -> "Plin"
            }
            Text(
                text = "$providerName: la cuenta asociada ya no está disponible. " +
                    "Las capturas nuevas no se guardarán hasta restablecer el vínculo.",
                style = MaterialTheme.typography.bodyMedium,
            )
            TextButton(
                onClick = { onRepair(configuration.provider) },
                enabled = repairing == null,
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Text(
                    if (repairing == configuration.provider) {
                        "Restableciendo…"
                    } else {
                        "Restablecer vínculo local"
                    },
                )
            }
        }
        AccountNote(
            "Se usará el registro local del proveedor. No se moverán movimientos ni saldos históricos.",
        )
        error?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
}

@Composable
fun CashSummaryCard(summary: CashSummary, modifier: Modifier = Modifier) {
    LyapeCard(modifier) {
        Text("Total registrado", style = MaterialTheme.typography.bodyMedium)
        Text(formatCents(summary.totalCents), style = MaterialTheme.typography.headlineMedium)
        InfoRow("Negocio", formatCents(summary.businessCents))
        InfoRow("Personal", formatCents(summary.personalCents))
        val unconfigured = summary.accounts.count { !it.initialized }
        if (unconfigured > 0) AccountNote(unconfiguredAccountsLabel(unconfigured))
    }
}

@Composable
fun AccountBalanceCard(account: AccountBalance, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.clickable(role = Role.Button, onClickLabel = "Ver ${account.name}", onClick = onClick)
                .heightIn(min = 48.dp).padding(16.dp),
        ) {
            Text(account.name, style = MaterialTheme.typography.titleMedium)
            if (account.initialized) {
                Text(formatCents(account.totalCents), style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                Text("Negocio ${formatCents(account.businessCents)}", style = MaterialTheme.typography.bodyMedium)
                Text("Personal ${formatCents(account.personalCents)}", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Text("Configurar saldo", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AccountDetailScreen(
    accountId: String,
    onSetBalance: () -> Unit,
    onTransfer: () -> Unit,
    onWithdraw: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val repository = rememberAccountsRepository()
    var retry by remember { mutableIntStateOf(0) }
    // El catálogo y los saldos se leen aparte de las operaciones: un fallo en el historial no
    // debe ocultar la cuenta ni convertirse en «no encontrada».
    val summaryState by remember(repository, retry) {
        repository.observeSummary().accountLoadState()
    }.collectAsStateWithLifecycle(initialValue = AccountLoadState.Loading)
    val operationsState by remember(repository, accountId, retry) {
        repository.observeOperations(accountId).accountLoadState()
    }.collectAsStateWithLifecycle(initialValue = AccountLoadState.Loading)
    var pendingUndo by remember(accountId) { mutableStateOf<CashOperation?>(null) }
    var undoing by remember { mutableStateOf(false) }
    var undoError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        when (summaryState) {
            AccountLoadState.Loading -> item { Text("Cargando cuenta…") }
            AccountLoadState.Error -> item { AccountsReadError { retry++ } }
            is AccountLoadState.Ready -> {
                val summary = (summaryState as AccountLoadState.Ready<CashSummary>).value
                val account = summary.accounts.firstOrNull { it.id == accountId }
                if (account == null) {
                    item { Text("No encontramos esta cuenta.") }
                } else {
                    item {
                        Text(account.name, style = MaterialTheme.typography.headlineMedium)
                        Spacer(Modifier.height(16.dp))
                        if (account.initialized) {
                            LyapeCard {
                                Text("Saldo registrado", style = MaterialTheme.typography.bodyMedium)
                                Text(formatCents(account.totalCents), style = MaterialTheme.typography.headlineMedium)
                                InfoRow("Negocio", formatCents(account.businessCents))
                                InfoRow("Personal", formatCents(account.personalCents))
                            }
                            Spacer(Modifier.height(16.dp))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = onWithdraw, modifier = Modifier.heightIn(min = 48.dp)) { Text("Registrar salida") }
                                OutlinedButton(onClick = onTransfer, modifier = Modifier.heightIn(min = 48.dp)) { Text("Transferir") }
                            }
                            TextButton(onClick = onSetBalance, modifier = Modifier.heightIn(min = 48.dp).padding(top = 8.dp)) { Text("Actualizar saldo") }
                            AccountNote("La caja incluye solo dinero del negocio.")
                        } else {
                            AccountNote("Ingresa cuánto tienes ahora para empezar a calcular el saldo.")
                            Spacer(Modifier.height(12.dp))
                            Button(onClick = onSetBalance, modifier = Modifier.heightIn(min = 48.dp)) { Text("Configurar saldo") }
                        }
                    }
                    item {
                        Text("Operaciones de caja", style = MaterialTheme.typography.titleLarge)
                        AccountNote("Los cobros están en Movimientos.")
                    }
                    val operations = (operationsState as? AccountLoadState.Ready<List<CashOperation>>)?.value
                    if (operations == null) {
                        item {
                            ReadFeedback(
                                failed = operationsState is AccountLoadState.Error,
                                message = "No se pudieron leer las operaciones de caja.",
                                onRetry = { retry++ },
                            )
                        }
                    } else {
                        if (operations.isEmpty()) item { AccountNote("Aún no hay salidas, transferencias ni pagos de gastos.") }
                        items(operations, key = { it.id.toString() }) { operation ->
                            CashOperationRow(operation, accountId, summary.accounts, enabled = !undoing) {
                                undoError = null
                                pendingUndo = operation
                            }
                        }
                        if (operations.size == 100) item { AccountNote("Se muestran las últimas 100 operaciones.") }
                    }
                }
            }
        }
    }
    pendingUndo?.let { operation ->
        AlertDialog(
            onDismissRequest = { if (!undoing) pendingUndo = null },
            title = { Text("¿Anular operación?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(if (operation.kind == CashOperationKind.EXPENSE_PAYMENT)
                        "El gasto volverá a quedar pendiente. Si actualizaste el saldo después, ese saldo se conserva; si no, se devuelve el importe. Quedará en el historial."
                    else "Se revertirá su efecto en los saldos. Quedará en el historial.")
                    undoError?.let { AccountError(it) }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !undoing,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    onClick = {
                        if (undoing) return@TextButton
                        undoing = true
                        scope.launch {
                            try {
                                repository.undoOperation(operation.id)
                                pendingUndo = null
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (invalid: IllegalArgumentException) {
                                undoError = invalid.message ?: "No se pudo anular esta operación."
                            } catch (_: Exception) {
                                undoError = "No se pudo anular. Vuelve a intentarlo."
                            } finally {
                                undoing = false
                            }
                        }
                    },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) { Text(if (undoing) "Anulando…" else "Anular") }
            },
            dismissButton = {
                TextButton(onClick = { pendingUndo = null }, enabled = !undoing, modifier = Modifier.heightIn(min = 48.dp)) { Text("Cancelar") }
            },
        )
    }
}

@Composable
private fun CashOperationRow(
    operation: CashOperation,
    accountId: String,
    accounts: List<AccountBalance>,
    enabled: Boolean,
    onUndo: () -> Unit,
) {
    val formatter = remember { DateTimeFormatter.ofPattern("d MMM yyyy · HH:mm", Locale("es", "PE")) }
    val title = when (operation.kind) {
        CashOperationKind.TRANSFER -> "Transferencia"
        CashOperationKind.WITHDRAWAL -> "Salida"
        CashOperationKind.EXPENSE_PAYMENT -> operation.expenseName ?: "Pago de gasto"
    }
    val incoming = operation.toAccountId == accountId
    val otherId = if (incoming) operation.fromAccountId else operation.toAccountId
    val otherAccount = accounts.firstOrNull { it.id == otherId }?.name
    LyapeCard {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(
            "${if (incoming) "+" else "−"} ${formatCents(operation.amountCents)}",
            style = MaterialTheme.typography.titleLarge,
            color = if (operation.voided) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(8.dp))
        val owner = if (operation.classification == MovementClassification.PERSONAL) "Personal" else "Negocio"
        Text(if (otherAccount != null) "$owner · ${if (incoming) "Desde" else "Hacia"} $otherAccount" else owner,
            style = MaterialTheme.typography.bodyMedium)
        AccountNote(formatter.format(operation.occurredAt.atZone(ZoneId.systemDefault())))
        operation.note?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        if (operation.voided) {
            Text("Anulada", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            TextButton(onClick = onUndo, enabled = enabled, modifier = Modifier.heightIn(min = 48.dp).padding(top = 8.dp)) { Text("Anular") }
        }
    }
}

@Composable
internal fun CaptureCheckError(onRetry: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AccountError(stringResource(R.string.accounts_capture_check_error))
        OutlinedButton(onClick = onRetry, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(R.string.accounts_capture_check_retry))
        }
    }
}

@Composable
internal fun AccountsReadError(onRetry: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AccountError("No se pudieron cargar las cuentas.")
        OutlinedButton(onClick = onRetry, modifier = Modifier.heightIn(min = 48.dp)) { Text("Reintentar") }
    }
}

@Composable
internal fun AccountNote(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
internal fun AccountError(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
}
