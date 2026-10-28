package com.lyape.ui.movementform

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lyape.data.MovementFormStore
import com.lyape.domain.Movement
import com.lyape.domain.MovementSource
import com.lyape.domain.CaptureAccount
import com.lyape.domain.MovementClassification
import com.lyape.ui.components.LyapeCard
import com.lyape.ui.components.SectionLabel
import com.lyape.ui.format.formatAmountInput
import com.lyape.ui.format.formatDateInput
import com.lyape.ui.format.formatTimeInput
import com.lyape.ui.model.sourceLabel
import com.lyape.ui.rememberMovementRepository
import com.lyape.ui.rememberAccountsRepository
import com.lyape.ui.theme.PillShape
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.catch
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

/** Serializa el formulario para sobrevivir a rotaciones y recreaciones de la actividad. */
private val MovementFormValuesSaver: Saver<MovementFormValues, Any> = listSaver(
    save = { it.toSavedValues() },
    restore = { movementFormValuesFromSaved(it) },
)

private const val NULL_FORM_RESULT = "__null__"

private val MovementFormResultSaver: Saver<MovementFormResult?, Any> = listSaver(
    save = {
        if (it == null) {
            listOf(NULL_FORM_RESULT)
        } else {
            listOf(
                it.amountError.orEmpty(),
                it.dateError.orEmpty(),
                it.timeError.orEmpty(),
                it.labelError.orEmpty(),
                it.amountCents?.toString().orEmpty(),
                it.occurredAt?.toEpochMilli()?.toString().orEmpty(),
                it.label.orEmpty(),
            )
        }
    },
    restore = { saved ->
        if (saved.firstOrNull() == NULL_FORM_RESULT) {
            null
        } else {
            MovementFormResult(
                amountError = saved.getOrNull(0)?.takeIf(String::isNotEmpty),
                dateError = saved.getOrNull(1)?.takeIf(String::isNotEmpty),
                timeError = saved.getOrNull(2)?.takeIf(String::isNotEmpty),
                labelError = saved.getOrNull(3)?.takeIf(String::isNotEmpty),
                amountCents = saved.getOrNull(4)?.toLongOrNull(),
                occurredAt = saved.getOrNull(5)?.toLongOrNull()?.let(Instant::ofEpochMilli),
                label = saved.getOrNull(6)?.takeIf(String::isNotEmpty),
            )
        }
    },
)

/** Valores neutros usados antes de cargar un movimiento en la edición. */
private val EmptyFormValues = MovementFormValues(
    kind = MovementFormKind.PAYMENT,
    amountText = "",
    dateText = "",
    timeText = "",
    labelText = "",
)

/** Alta manual: un pago perdido (positivo) o un ajuste positivo o negativo con motivo. */
@Composable
fun AddMovementScreen(
    onSaved: () -> Unit,
    onDirtyChange: (Boolean) -> Unit,
    onSavingChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val repository = rememberMovementRepository()
    val zone = remember { ZoneId.systemDefault() }
    val formSession by rememberSaveable { mutableStateOf(UUID.randomUUID().toString()) }
    val formViewModel = rememberMovementFormViewModel("add-movement:$formSession", repository)
    val submission by formViewModel.submission.collectAsStateWithLifecycle()
    val attemptId by rememberSaveable { mutableStateOf(UUID.randomUUID().toString()) }
    val initialTimestamp by rememberSaveable { mutableStateOf(Instant.now().toEpochMilli()) }

    val initialValues = rememberSaveable(saver = MovementFormValuesSaver) {
        MovementFormValues(
            kind = MovementFormKind.PAYMENT,
            amountText = "",
            dateText = formatDateInput(Instant.ofEpochMilli(initialTimestamp).atZone(zone).toLocalDate()),
            timeText = formatTimeInput(Instant.ofEpochMilli(initialTimestamp).atZone(zone).toLocalTime()),
            labelText = "",
        )
    }
    var values by rememberSaveable(stateSaver = MovementFormValuesSaver) {
        mutableStateOf(initialValues)
    }
    var result by rememberSaveable(stateSaver = MovementFormResultSaver) {
        mutableStateOf<MovementFormResult?>(null)
    }
    val saving = submission.saving

    val hasChanges = values.hasChangesFrom(initialValues)
    LaunchedEffect(hasChanges) { onDirtyChange(hasChanges) }
    LaunchedEffect(submission.saving) { onSavingChange(submission.saving) }
    LaunchedEffect(submission.saved) {
        if (submission.saved) {
            onDirtyChange(false)
            formViewModel.acknowledgeSuccess()
            onSaved()
        }
    }

    ScreenColumn(modifier = modifier) {
        LyapeCard {
            FormFields(
                values = values,
                enabled = !saving,
                result = result,
                onValuesChange = { updated ->
                    values = updated
                    onDirtyChange(updated.hasChangesFrom(initialValues))
                    result = null
                    formViewModel.clearError()
                },
            )
        }

        Spacer(Modifier.height(16.dp))
        SaveError(message = submission.error, errorRes = submission.errorRes)
        SubmitButton(
            saving = saving,
            label = "Guardar movimiento",
            onClick = {
                if (saving || submission.saved) return@SubmitButton
                val validation = MovementFormValidator.validate(values, zone)
                result = validation
                val amount = validation.amountCents ?: return@SubmitButton
                val occurredAt = validation.occurredAt ?: return@SubmitButton
                if (validation.hasErrors) return@SubmitButton
                val timestamp = preserveTimestampPrecision(values, initialValues, occurredAt, Instant.ofEpochMilli(initialTimestamp))
                formViewModel.clearError()
                onSavingChange(true)
                formViewModel.saveAdd(
                    attemptId = UUID.fromString(attemptId),
                    amountCents = amount,
                    occurredAt = timestamp,
                    label = validation.label,
                    accountId = values.accountId,
                    classification = values.classification,
                    isAdjustment = values.kind == MovementFormKind.ADJUSTMENT,
                )
            },
        )
    }
}

/** Edición de los campos conciliables de un movimiento existente. */
@Composable
fun EditMovementScreen(
    movementId: String,
    onSaved: () -> Unit,
    onDirtyChange: (Boolean) -> Unit,
    onSavingChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val repository = rememberMovementRepository()
    val zone = remember { ZoneId.systemDefault() }
    val id = remember(movementId) { runCatching { UUID.fromString(movementId) }.getOrNull() }
    val formSession by rememberSaveable { mutableStateOf(UUID.randomUUID().toString()) }
    val formViewModel = rememberMovementFormViewModel(
        "edit-movement:$movementId:$formSession",
        repository,
    )
    val submission by formViewModel.submission.collectAsStateWithLifecycle()
    val editAttemptId by rememberSaveable(id) { mutableStateOf(UUID.randomUUID().toString()) }
    var expectedVersion by rememberSaveable(id) { mutableStateOf(-1L) }

    val state by remember(id, repository) {
        val flow: Flow<EditState> = if (id == null) {
            flowOf(EditState.Missing)
        } else {
            repository.observeById(id).map { movement ->
                if (movement == null) EditState.Missing else EditState.Ready(movement)
            }.catch { emit(EditState.Error) }
        }
        flow
    }.collectAsStateWithLifecycle(initialValue = EditState.Loading)

    var values by rememberSaveable(id, stateSaver = MovementFormValuesSaver) {
        mutableStateOf(EmptyFormValues)
    }
    var initialValues by rememberSaveable(id, stateSaver = MovementFormValuesSaver) {
        mutableStateOf(EmptyFormValues)
    }
    var loaded by rememberSaveable(id) { mutableStateOf(false) }
    var result by rememberSaveable(stateSaver = MovementFormResultSaver) {
        mutableStateOf<MovementFormResult?>(null)
    }
    val saving = submission.saving

    LaunchedEffect(state, loaded) {
        if (loaded) return@LaunchedEffect
        val ready = state as? EditState.Ready ?: return@LaunchedEffect
        val filled = formValuesFor(ready.movement, zone)
        initialValues = filled
        values = filled
        expectedVersion = ready.movement.editVersion
        loaded = true
    }

    val hasChanges = loaded && values.hasChangesFrom(initialValues)
    LaunchedEffect(hasChanges) { onDirtyChange(hasChanges) }
    LaunchedEffect(submission.saving) { onSavingChange(submission.saving) }
    LaunchedEffect(submission.saved) {
        if (submission.saved) {
            onDirtyChange(false)
            formViewModel.acknowledgeSuccess()
            onSaved()
        }
    }

    ScreenColumn(modifier = modifier) {
        when (val current = state) {
            EditState.Loading -> Message("Cargando movimiento…")
            EditState.Missing -> Message("No encontramos este movimiento.")
            EditState.Error -> Message("No pudimos cargar el movimiento. Vuelve a abrirlo.")
            is EditState.Ready -> {
                if (!loaded) {
                    Message("Cargando movimiento…")
                } else {
                    ReadOnlyRow(label = "Origen", value = sourceLabel(current.movement.source))
                    Spacer(Modifier.height(12.dp))
                    LyapeCard {
                        FormFields(
                            values = values,
                            enabled = !saving,
                            result = result,
                            kindLocked = true,
                            onValuesChange = { updated ->
                                values = updated
                                onDirtyChange(updated.hasChangesFrom(initialValues))
                                result = null
                                formViewModel.clearError()
                            },
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    SaveError(message = submission.error, errorRes = submission.errorRes)
                    SubmitButton(
                        saving = saving,
                        label = "Guardar cambios",
                        onClick = {
                            if (saving || submission.saved) return@SubmitButton
                            val validation = MovementFormValidator.validate(values, zone)
                            result = validation
                            val amount = validation.amountCents ?: return@SubmitButton
                            val occurredAt = validation.occurredAt ?: return@SubmitButton
                            if (validation.hasErrors) return@SubmitButton
                            formViewModel.clearError()
                            onSavingChange(true)
                            formViewModel.saveEdit(
                                movementId = current.movement.id,
                                amountCents = amount,
                                occurredAt = preserveTimestampPrecision(values, initialValues, occurredAt, current.movement.occurredAt),
                                accountId = values.accountId,
                                senderName = if (values.kind == MovementFormKind.ADJUSTMENT) {
                                    current.movement.senderName
                                } else {
                                    validation.label
                                },
                                description = current.movement.description,
                                adjustmentReason = if (values.kind == MovementFormKind.ADJUSTMENT) {
                                    validation.label
                                } else {
                                    current.movement.adjustmentReason
                                },
                                expectedVersion = expectedVersion,
                                editAttemptId = UUID.fromString(editAttemptId),
                            )
                        },
                    )
                }
            }
        }
    }
}

/** Mantiene la operación en un ViewModel y la identidad del alta en el estado guardado. */
@Composable
private fun rememberMovementFormViewModel(
    key: String,
    store: MovementFormStore,
): MovementFormViewModel {
    val factory = remember(store) {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                MovementFormViewModel(store) as T
        }
    }
    return viewModel(key = key, factory = factory)
}

private sealed interface EditState {
    data object Loading : EditState
    data object Missing : EditState
    data object Error : EditState
    data class Ready(val movement: Movement) : EditState
}

private fun formValuesFor(movement: Movement, zone: ZoneId): MovementFormValues {
    val local = movement.occurredAt.atZone(zone)
    val isAdjustment = movement.source == MovementSource.ADJUSTMENT
    return MovementFormValues(
        kind = if (isAdjustment) MovementFormKind.ADJUSTMENT else MovementFormKind.PAYMENT,
        amountText = formatAmountInput(movement.amountCents),
        dateText = formatDateInput(local.toLocalDate()),
        timeText = formatTimeInput(local.toLocalTime()),
        labelText = (if (isAdjustment) movement.adjustmentReason else movement.senderName).orEmpty(),
        adjustmentSign = if (movement.amountCents < 0L) {
            AdjustmentSign.NEGATIVE
        } else {
            AdjustmentSign.POSITIVE
        },
        accountId = movement.accountId,
        classification = movement.classification,
    )
}

@Composable
private fun ScreenColumn(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 17.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        content()
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun Message(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Fila compacta de solo lectura: `Origen — Detectado` o `Tipo — Pago`. */
@Composable
private fun ReadOnlyRow(label: String, value: String) {
    Text(
        text = "$label — $value",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 24.dp)
            .padding(vertical = 2.dp),
    )
}

private fun kindLabel(kind: MovementFormKind): String = when (kind) {
    MovementFormKind.PAYMENT -> "Pago"
    MovementFormKind.ADJUSTMENT -> "Ajuste"
}

@Composable
private fun SaveError(message: String?, errorRes: Int?) {
    val text = when {
        message != null -> message
        errorRes != null -> stringResource(errorRes)
        else -> null
    }
    if (text == null) return
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
    )
    Spacer(Modifier.height(10.dp))
}

@Composable
private fun SubmitButton(
    saving: Boolean,
    label: String,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = !saving,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
        shape = PillShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
    ) {
        Text(text = if (saving) "Guardando…" else label, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun FormFields(
    values: MovementFormValues,
    enabled: Boolean,
    result: MovementFormResult?,
    onValuesChange: (MovementFormValues) -> Unit,
    kindLocked: Boolean = false,
) {
    val amountError = result?.amountError
    val dateError = result?.dateError
    val timeError = result?.timeError
    val labelError = result?.labelError

    if (values.kind == MovementFormKind.ADJUSTMENT) {
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FormChip(
                label = "+ Suma",
                selected = values.adjustmentSign == AdjustmentSign.POSITIVE,
                enabled = enabled,
                modifier = Modifier.weight(1f),
                onClick = { onValuesChange(values.copy(adjustmentSign = AdjustmentSign.POSITIVE)) },
            )
            FormChip(
                label = "− Resta",
                selected = values.adjustmentSign == AdjustmentSign.NEGATIVE,
                enabled = enabled,
                modifier = Modifier.weight(1f),
                onClick = { onValuesChange(values.copy(adjustmentSign = AdjustmentSign.NEGATIVE)) },
            )
        }
    }

    Spacer(Modifier.height(12.dp))
    OutlinedTextField(
        value = values.amountText,
        onValueChange = { onValuesChange(values.copy(amountText = it)) },
        label = { Text("Monto (S/)") },
        singleLine = true,
        enabled = enabled,
        isError = amountError != null,
        supportingText = if (amountError != null) {
            { Text(amountError) }
        } else {
            null
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth(),
    )

    Spacer(Modifier.height(12.dp))
    MovementAccountField(values.accountId, enabled) { onValuesChange(values.copy(accountId = it)) }
    if (!kindLocked) {
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FormChip("Negocio", values.classification == MovementClassification.BUSINESS, enabled,
                { onValuesChange(values.copy(classification = MovementClassification.BUSINESS)) }, Modifier.weight(1f))
            FormChip("Personal", values.classification == MovementClassification.PERSONAL, enabled,
                { onValuesChange(values.copy(classification = MovementClassification.PERSONAL)) }, Modifier.weight(1f))
        }
        androidx.compose.material3.TextButton(onClick = {
            onValuesChange(values.copy(kind = if (values.kind == MovementFormKind.PAYMENT) MovementFormKind.ADJUSTMENT else MovementFormKind.PAYMENT))
        }, enabled = enabled) { Text(if (values.kind == MovementFormKind.PAYMENT) "Registrar un ajuste" else "Registrar un pago") }
    } else ReadOnlyRow("Tipo", kindLabel(values.kind))

    Spacer(Modifier.height(12.dp))
    SegmentedDateTimeFields(
        dateText = values.dateText,
        timeText = values.timeText,
        enabled = enabled,
        dateError = dateError,
        timeError = timeError,
        onDateChange = { onValuesChange(values.copy(dateText = it)) },
        onTimeChange = { onValuesChange(values.copy(timeText = it)) },
    )

    Spacer(Modifier.height(12.dp))
    OutlinedTextField(
        value = values.labelText,
        onValueChange = { onValuesChange(values.copy(labelText = it)) },
        label = {
            Text(
                if (values.kind == MovementFormKind.ADJUSTMENT) {
                    "Motivo (obligatorio)"
                } else {
                    "Remitente o descripción (opcional)"
                },
            )
        },
        singleLine = true,
        enabled = enabled,
        isError = labelError != null,
        supportingText = if (labelError != null) {
            { Text(labelError) }
        } else {
            null
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun MovementAccountField(selectedId: String?, enabled: Boolean, onSelect: (String?) -> Unit) {
    val repository = rememberAccountsRepository()
    var failed by remember { mutableStateOf(false) }
    var retryKey by remember { mutableStateOf(0) }
    val accounts by remember(repository, retryKey) {
        repository.observeSelectableAccounts().map<List<CaptureAccount>, List<CaptureAccount>?> { it }
            .catch { failed = true; emit(null) }
    }.collectAsStateWithLifecycle(initialValue = null)
    var expanded by remember { mutableStateOf(false) }
    SectionLabel("Cuenta")
    Box {
        OutlinedButton(
            onClick = { expanded = true },
            enabled = enabled && !failed && accounts != null,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) {
            Text(when {
                failed -> "No pudimos cargar las cuentas"
                accounts == null -> "Cargando cuentas…"
                selectedId == null -> "Sin cuenta"
                else -> accounts?.find { it.id == selectedId }?.name ?: "Cuenta no disponible"
            })
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text("Sin cuenta") }, onClick = { onSelect(null); expanded = false })
            accounts?.forEach { account ->
                DropdownMenuItem(text = { Text(account.name) }, onClick = { onSelect(account.id); expanded = false })
            }
        }
    }
    if (failed) {
        androidx.compose.material3.TextButton(onClick = { failed = false; retryKey++ }) { Text("Reintentar") }
    } else if (selectedId == null) {
        Text("No cambia el saldo de ninguna cuenta.", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun FormChip(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        enabled = enabled,
        label = { Text(label) },
        shape = PillShape,
        colors = FilterChipDefaults.filterChipColors(
            containerColor = MaterialTheme.colorScheme.surface,
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
        ),
        modifier = modifier,
    )
}
