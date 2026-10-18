package com.lyape.ui.movementform

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lyape.domain.Movement
import com.lyape.domain.MovementSource
import com.lyape.ui.components.LyapeCard
import com.lyape.ui.components.SectionLabel
import com.lyape.ui.format.formatAmountInput
import com.lyape.ui.format.formatDateInput
import com.lyape.ui.format.formatTimeInput
import com.lyape.ui.model.sourceLabel
import com.lyape.ui.rememberMovementRepository
import com.lyape.ui.theme.PillShape
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

/** Serializa el formulario para sobrevivir a rotaciones y recreaciones de la actividad. */
private val MovementFormValuesSaver: Saver<MovementFormValues, Any> = listSaver(
    save = { it.toSavedValues() },
    restore = { movementFormValuesFromSaved(it) },
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
    val scope = rememberCoroutineScope()
    val zone = remember { ZoneId.systemDefault() }

    val initialValues = rememberSaveable(saver = MovementFormValuesSaver) {
        MovementFormValues(
            kind = MovementFormKind.PAYMENT,
            amountText = "",
            dateText = formatDateInput(LocalDate.now()),
            timeText = formatTimeInput(LocalTime.now().withSecond(0).withNano(0)),
            labelText = "",
        )
    }
    var values by rememberSaveable(stateSaver = MovementFormValuesSaver) {
        mutableStateOf(initialValues)
    }
    var result by remember { mutableStateOf<MovementFormResult?>(null) }
    var saving by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf<String?>(null) }

    val hasChanges = values.hasChangesFrom(initialValues)
    LaunchedEffect(hasChanges) { onDirtyChange(hasChanges) }

    ScreenColumn(modifier = modifier) {
        LyapeCard {
            FormFields(
                values = values,
                enabled = !saving,
                result = result,
                onValuesChange = { updated ->
                    values = updated
                    result = null
                    saveError = null
                },
            )
        }

        Spacer(Modifier.height(16.dp))
        SaveError(message = saveError)
        SubmitButton(
            saving = saving,
            label = "Guardar movimiento",
            onClick = {
                if (saving) return@SubmitButton
                val validation = MovementFormValidator.validate(values, zone)
                result = validation
                val amount = validation.amountCents ?: return@SubmitButton
                val occurredAt = validation.occurredAt ?: return@SubmitButton
                saving = true
                onSavingChange(true)
                saveError = null
                scope.launch {
                    val saved = try {
                        if (values.kind == MovementFormKind.ADJUSTMENT) {
                            repository.addAdjustment(amount, occurredAt, validation.label.orEmpty())
                        } else {
                            repository.addManual(amount, occurredAt, validation.label)
                        }
                        true
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        saveError = "No pudimos guardar el movimiento. Vuelve a intentarlo."
                        false
                    }
                    saving = false
                    onSavingChange(false)
                    if (saved) onSaved()
                }
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
    val scope = rememberCoroutineScope()
    val zone = remember { ZoneId.systemDefault() }
    val id = remember(movementId) { runCatching { UUID.fromString(movementId) }.getOrNull() }

    val state by remember(id, repository) {
        val flow: Flow<EditState> = if (id == null) {
            flowOf(EditState.Missing)
        } else {
            repository.observeById(id).map { movement ->
                if (movement == null) EditState.Missing else EditState.Ready(movement)
            }
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
    var result by remember { mutableStateOf<MovementFormResult?>(null) }
    var saving by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(state, loaded) {
        if (loaded) return@LaunchedEffect
        val ready = state as? EditState.Ready ?: return@LaunchedEffect
        val filled = formValuesFor(ready.movement, zone)
        initialValues = filled
        values = filled
        loaded = true
    }

    val hasChanges = loaded && values.hasChangesFrom(initialValues)
    LaunchedEffect(hasChanges) { onDirtyChange(hasChanges) }

    ScreenColumn(modifier = modifier) {
        when (val current = state) {
            EditState.Loading -> Message("Cargando movimiento…")
            EditState.Missing -> Message("No encontramos este movimiento.")
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
                                result = null
                                saveError = null
                            },
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    SaveError(message = saveError)
                    SubmitButton(
                        saving = saving,
                        label = "Guardar cambios",
                        onClick = {
                            if (saving) return@SubmitButton
                            val validation = MovementFormValidator.validate(values, zone)
                            result = validation
                            val amount = validation.amountCents ?: return@SubmitButton
                            val occurredAt = validation.occurredAt ?: return@SubmitButton
                            saving = true
                            onSavingChange(true)
                            saveError = null
                            scope.launch {
                                val saved = try {
                                    val updated = repository.edit(
                                        id = current.movement.id,
                                        amountCents = amount,
                                        occurredAt = occurredAt,
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
                                    )
                                    if (!updated) {
                                        throw IllegalStateException("El movimiento ya no existe")
                                    }
                                    true
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (_: Exception) {
                                    saveError = "No pudimos guardar los cambios. Vuelve a intentarlo."
                                    false
                                }
                                saving = false
                                onSavingChange(false)
                                if (saved) onSaved()
                            }
                        },
                    )
                }
            }
        }
    }
}

private sealed interface EditState {
    data object Loading : EditState
    data object Missing : EditState
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
private fun SaveError(message: String?) {
    if (message == null) return
    Text(
        text = message,
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

    if (kindLocked) {
        ReadOnlyRow(label = "Tipo", value = kindLabel(values.kind))
    } else {
        SectionLabel(text = "Tipo")
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FormChip(
                label = "Pago",
                selected = values.kind == MovementFormKind.PAYMENT,
                enabled = enabled,
                modifier = Modifier.weight(1f),
                onClick = { onValuesChange(values.copy(kind = MovementFormKind.PAYMENT)) },
            )
            FormChip(
                label = "Ajuste",
                selected = values.kind == MovementFormKind.ADJUSTMENT,
                enabled = enabled,
                modifier = Modifier.weight(1f),
                onClick = { onValuesChange(values.copy(kind = MovementFormKind.ADJUSTMENT)) },
            )
        }
    }

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
