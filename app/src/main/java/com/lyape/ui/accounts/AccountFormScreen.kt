package com.lyape.ui.accounts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lyape.domain.AccountBalance
import com.lyape.domain.AccountKind
import com.lyape.domain.CashSummary
import com.lyape.domain.MovementClassification
import com.lyape.ui.format.formatCents
import com.lyape.ui.rememberAccountsRepository

private val AccountFormSaver = listSaver<AccountFormValues, String>(
    save = { it.saved() },
    restore = { restoreAccountForm(it) },
)

/** La barra y la confirmación al salir pertenecen a LyapeApp. */
@Composable
fun AccountFormScreen(
    mode: String,
    onSaved: () -> Unit,
    modifier: Modifier = Modifier,
    accountId: String? = null,
    onDirtyChange: (Boolean) -> Unit = {},
    onSavingChange: (Boolean) -> Unit = {},
) {
    val repository = rememberAccountsRepository()
    var retry by remember { mutableIntStateOf(0) }
    val state by remember(repository, retry) { repository.observeSummary().accountLoadState() }
        .collectAsStateWithLifecycle(initialValue = AccountLoadState.Loading)
    val model: AccountsFormViewModel = viewModel(key = "account-form-$mode-$accountId")
    val savingState by model.state.collectAsStateWithLifecycle()
    var values by rememberSaveable(mode, accountId, stateSaver = AccountFormSaver) { mutableStateOf(AccountFormValues()) }
    var initial by rememberSaveable(mode, accountId, stateSaver = AccountFormSaver) { mutableStateOf(AccountFormValues()) }
    var loaded by rememberSaveable(mode, accountId) { mutableStateOf(false) }
    var validation by remember { mutableStateOf(AccountFormResult()) }
    var confirmBalance by rememberSaveable { mutableStateOf(false) }
    val accounts = (state as? AccountLoadState.Ready<CashSummary>)?.value?.accounts.orEmpty()
    val balanceAccount = accounts.firstOrNull { it.id == accountId }

    LaunchedEffect(model) { model.clearError() }
    LaunchedEffect(state, loaded) {
        if (loaded || state !is AccountLoadState.Ready) return@LaunchedEffect
        val ready = accounts.filter { it.initialized }
        val from = ready.firstOrNull { it.id == accountId } ?: ready.firstOrNull()
        val defaults = AccountFormValues(
            fromId = from?.id.orEmpty(),
            toId = ready.firstOrNull { it.id != from?.id }?.id.orEmpty(),
            classification = if (mode == "withdraw") MovementClassification.PERSONAL else MovementClassification.BUSINESS,
        )
        initial = defaults
        values = defaults
        loaded = true
    }
    LaunchedEffect(loaded, values, initial) { onDirtyChange(loaded && values != initial) }
    LaunchedEffect(savingState.saving) { onSavingChange(savingState.saving) }
    LaunchedEffect(savingState.saved) {
        if (savingState.saved) {
            model.consumeSaved()
            onDirtyChange(false)
            onSavingChange(false)
            onSaved()
        }
    }
    val update: (AccountFormValues) -> Unit = {
        values = it
        validation = AccountFormResult()
        model.clearError()
    }
    val save: () -> Unit = {
        val snapshot = values
        val result = validateAccountForm(mode, snapshot, accounts)
        validation = result
        if (result.valid && !savingState.saving) {
            onSavingChange(true)
            model.submit {
                when (mode) {
                    "add" -> repository.addAccount(snapshot.name.trim(), AccountKind.BANK)
                    "balance" -> repository.setBalance(
                        requireNotNull(accountId), requireNotNull(result.businessCents), requireNotNull(result.personalCents),
                    )
                    "transfer" -> repository.transfer(
                        snapshot.fromId, snapshot.toId, requireNotNull(result.amountCents), snapshot.classification,
                        snapshot.note.trim().takeIf { it.isNotEmpty() },
                    )
                    "withdraw" -> repository.withdraw(
                        snapshot.fromId, requireNotNull(result.amountCents), snapshot.classification,
                        snapshot.note.trim().takeIf { it.isNotEmpty() },
                    )
                }
            }
        }
    }

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        when (state) {
            AccountLoadState.Loading -> Text("Cargando cuentas…")
            AccountLoadState.Error -> AccountsReadError { retry++ }
            is AccountLoadState.Ready -> if (!loaded) {
                Text("Cargando cuentas…")
            } else if (mode == "balance" && balanceAccount == null) {
                AccountError("No encontramos esta cuenta.")
            } else {
                val enabled = !savingState.saving
                when (mode) {
                    "add" -> {
                        AccountNote("Registra otra cuenta bancaria. No se conecta con tu banco.")
                        FormTextField("Nombre de la cuenta", values.name, { update(values.copy(name = it)) }, enabled,
                            validation.errors["name"])
                        AccountNote("Después podrás configurar su saldo actual.")
                    }
                    "balance" -> {
                        Text(balanceAccount?.name.orEmpty(), style = MaterialTheme.typography.titleLarge)
                        AccountNote("Ingresa cuánto tienes ahora, separado entre negocio y personal.")
                        FormTextField("Negocio (S/)", values.business, { update(values.copy(business = it)) }, enabled,
                            validation.errors["business"], money = true)
                        FormTextField("Personal (S/)", values.personal, { update(values.copy(personal = it)) }, enabled,
                            validation.errors["personal"], money = true)
                        AccountNote("Este saldo ya incluye los cobros anteriores. No cambia los ingresos registrados.")
                    }
                    "transfer", "withdraw" -> {
                        AccountNote(if (mode == "transfer") "Registra dinero que moviste entre tus cuentas." else
                            "Registra una salida que ya hiciste. Para pagar un gasto previsto, usa Plan del mes.")
                        val ready = accounts.filter { it.initialized }
                        if (ready.isEmpty()) AccountNote("Configura el saldo de una cuenta antes de registrar operaciones.")
                        AccountPicker("Desde", values.fromId, ready, enabled, validation.errors["from"]) {
                            update(values.copy(fromId = it))
                        }
                        if (mode == "transfer") {
                            AccountPicker("Hacia", values.toId, ready, enabled, validation.errors["to"]) {
                                update(values.copy(toId = it))
                            }
                            if (ready.size < 2) AccountNote("Para transferir necesitas dos cuentas con saldo configurado.")
                        }
                        ClassificationPicker(values.classification, enabled) { update(values.copy(classification = it)) }
                        validation.errors["classification"]?.let { AccountError(it) }
                        val selected = ready.firstOrNull { it.id == values.fromId }
                        selected?.let {
                            val balance = if (values.classification == MovementClassification.PERSONAL) it.personalCents else it.businessCents
                            AccountNote("Disponible: ${formatCents(balance)}")
                        }
                        FormTextField("Importe (S/)", values.amount, { update(values.copy(amount = it)) }, enabled,
                            validation.errors["amount"], money = true)
                        FormTextField("Nota (opcional)", values.note, { update(values.copy(note = it)) }, enabled,
                            validation.errors["note"], singleLine = false)
                    }
                    else -> AccountError("No encontramos este formulario.")
                }
                validation.errors["form"]?.let { AccountError(it) }
                savingState.error?.let { AccountError(it) }
                if (mode in listOf("add", "balance", "transfer", "withdraw")) {
                    Button(
                        enabled = enabled,
                        onClick = {
                            if (savingState.saving) return@Button
                            val result = validateAccountForm(mode, values, accounts)
                            validation = result
                            if (!result.valid) return@Button
                            if (mode == "balance" && balanceAccount?.initialized == true) confirmBalance = true else save()
                        },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    ) {
                        Text(if (savingState.saving) "Guardando…" else when (mode) {
                            "add" -> "Agregar cuenta"
                            "balance" -> "Guardar saldo"
                            "transfer" -> "Registrar transferencia"
                            else -> "Guardar salida"
                        })
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
    if (confirmBalance) {
        AlertDialog(
            onDismissRequest = { confirmBalance = false },
            title = { Text("¿Actualizar el saldo?") },
            text = { Text("Se usará el importe que ingresaste como saldo actual. El historial y los ingresos no cambian.") },
            confirmButton = { TextButton(onClick = { confirmBalance = false; save() }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Actualizar") } },
            dismissButton = { TextButton(onClick = { confirmBalance = false }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun FormTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    error: String?,
    money: Boolean = false,
    singleLine: Boolean = true,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        enabled = enabled,
        singleLine = singleLine,
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = if (money) KeyboardType.Decimal else KeyboardType.Text),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun AccountPicker(
    label: String,
    id: String,
    accounts: List<AccountBalance>,
    enabled: Boolean,
    error: String?,
    onChange: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val name = accounts.firstOrNull { it.id == id }?.name ?: "Elegir cuenta"
    Column {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        OutlinedButton(
            onClick = { expanded = true },
            enabled = enabled && accounts.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics { contentDescription = "$label: $name" },
        ) { Text(name) }
        DropdownMenu(expanded = expanded && enabled, onDismissRequest = { expanded = false }) {
            accounts.forEach { account ->
                DropdownMenuItem(
                    text = { Text(account.name) },
                    onClick = { expanded = false; onChange(account.id) },
                    modifier = Modifier.heightIn(min = 48.dp),
                )
            }
        }
        error?.let { AccountError(it) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ClassificationPicker(
    selected: MovementClassification,
    enabled: Boolean,
    onSelect: (MovementClassification) -> Unit,
) {
    Column {
        Text("Dinero de", style = MaterialTheme.typography.bodyMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(MovementClassification.BUSINESS, MovementClassification.PERSONAL).forEach { classification ->
                FilterChip(
                    selected = selected == classification,
                    onClick = { onSelect(classification) },
                    label = { Text(if (classification == MovementClassification.BUSINESS) "Negocio" else "Personal") },
                    enabled = enabled,
                    modifier = Modifier.heightIn(min = 48.dp),
                )
            }
        }
    }
}
