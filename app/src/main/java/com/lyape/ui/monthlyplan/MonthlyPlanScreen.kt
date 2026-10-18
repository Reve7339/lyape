package com.lyape.ui.monthlyplan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lyape.domain.MonthlyExpense
import com.lyape.domain.MonthlyPlan
import com.lyape.domain.MonthlyProgressCalculator
import com.lyape.domain.ExpenseSchedule
import com.lyape.domain.GoalMode
import com.lyape.ui.components.LyapeCard
import com.lyape.ui.components.SectionLabel
import com.lyape.ui.format.AmountParse
import com.lyape.ui.format.formatAmountInput
import com.lyape.ui.format.formatCents
import com.lyape.ui.format.formatMonthLabel
import com.lyape.ui.format.parseAmountCents
import com.lyape.ui.rememberMonthlyPlanRepository
import com.lyape.ui.theme.PillShape
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.time.YearMonth

@Composable
fun MonthlyPlanScreen(
    initialMonth: String,
    modifier: Modifier = Modifier,
) {
    val repository = rememberMonthlyPlanRepository()
    val scope = rememberCoroutineScope()
    val parsedInitial = remember(initialMonth) {
        runCatching { YearMonth.parse(initialMonth) }.getOrElse { YearMonth.now() }
    }
    var monthValue by rememberSaveable(initialMonth) { mutableStateOf(parsedInitial.toString()) }
    val month = remember(monthValue) { YearMonth.parse(monthValue) }
    val plan by remember(repository, month) { repository.observePlan(month) }
        .collectAsStateWithLifecycle(initialValue = MonthlyPlan.empty(month))
    val totalExpenses = remember(plan) {
        MonthlyProgressCalculator.calculate(0L, plan).totalExpensesCents
    }

    var addingExpense by remember { mutableStateOf(false) }
    var editingExpense by remember { mutableStateOf<MonthlyExpense?>(null) }
    var deletingExpense by remember { mutableStateOf<MonthlyExpense?>(null) }
    var goalDialogMode by remember { mutableStateOf<GoalMode?>(null) }
    var removingGoal by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var actionError by remember { mutableStateOf<String?>(null) }

    fun save(action: suspend () -> Unit, onSuccess: () -> Unit) {
        if (busy) return
        busy = true
        actionError = null
        scope.launch {
            val succeeded = try {
                action()
                true
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                actionError = "No pudimos guardar el plan. Vuelve a intentarlo."
                false
            }
            busy = false
            if (succeeded) onSuccess()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 17.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Los gastos no aparecen como movimientos.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(12.dp))
        MonthSelector(
            month = month,
            showThisMonth = month != YearMonth.now(),
            onThisMonth = { monthValue = YearMonth.now().toString() },
            onPrevious = { monthValue = month.minusMonths(1).toString() },
            onNext = { monthValue = month.plusMonths(1).toString() },
        )

        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SectionLabel(text = "Gastos previstos")
            Text(
                text = formatCents(totalExpenses),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Spacer(Modifier.height(8.dp))

        if (plan.expenses.isEmpty()) {
            LyapeCard {
                Text(
                    text = "Todavía no agregaste gastos para este mes.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LyapeCard {
                plan.expenses.forEachIndexed { index, expense ->
                    ExpenseRow(
                        expense = expense,
                        selectedMonth = month,
                        enabled = !busy,
                        onEdit = { editingExpense = expense },
                        onDelete = { deletingExpense = expense },
                    )
                    if (index != plan.expenses.lastIndex) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = { addingExpense = true },
            enabled = !busy,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp),
            shape = PillShape,
        ) {
            Text("Agregar gasto fijo o extra")
        }

        Spacer(Modifier.height(20.dp))
        SectionLabel(text = "Meta de ingresos")
        Spacer(Modifier.height(8.dp))
        LyapeCard {
            val goal = plan.goal
            Text(
                text = "Tipo de meta",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GoalModeChip(
                    label = "Fija mensual",
                    selected = plan.goalMode == GoalMode.FIXED_MONTHLY,
                    enabled = !busy,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        save(
                            action = { repository.setGoalMode(GoalMode.FIXED_MONTHLY) },
                            onSuccess = {},
                        )
                    },
                )
                GoalModeChip(
                    label = "Por mes",
                    selected = plan.goalMode == GoalMode.BY_MONTH,
                    enabled = !busy,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        save(
                            action = { repository.setGoalMode(GoalMode.BY_MONTH) },
                            onSuccess = {},
                        )
                    },
                )
            }
            Spacer(Modifier.height(12.dp))
            if (goal == null) {
                Text(
                    text = if (plan.goalMode == GoalMode.FIXED_MONTHLY) {
                        "Todavía no definiste la meta fija que se usará todos los meses."
                    } else {
                        "Sin meta para ${formatMonthLabel(month)}."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    text = formatCents(goal.amountCents),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (plan.goalMode == GoalMode.BY_MONTH) {
                    Text(
                        text = "Solo se aplica a ${formatMonthLabel(month)}.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (plan.expenses.isNotEmpty() && goal.amountCents < totalExpenses) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Esta meta es menor que tus gastos previstos y no alcanza a cubrirlos.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { goalDialogMode = plan.goalMode },
                    enabled = !busy,
                    modifier = Modifier.weight(1f),
                    shape = PillShape,
                ) {
                    Text(if (goal == null) "Definir meta" else "Editar meta")
                }
                if (goal != null) {
                    TextButton(
                        onClick = { removingGoal = true },
                        enabled = !busy,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Quitar meta", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }

        if (actionError != null) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = actionError.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        Spacer(Modifier.height(28.dp))
    }

    if (addingExpense || editingExpense != null) {
        val current = editingExpense
        ExpenseDialog(
            expense = current,
            busy = busy,
            onDismiss = {
                addingExpense = false
                editingExpense = null
            },
            onSave = { schedule, name, amount ->
                save(
                    action = {
                        repository.saveExpense(
                            id = current?.id,
                            selectedMonth = month,
                            schedule = schedule,
                            name = name,
                            amountCents = amount,
                        )
                    },
                    onSuccess = {
                        addingExpense = false
                        editingExpense = null
                    },
                )
            },
        )
    }

    deletingExpense?.let { expense ->
        AlertDialog(
            onDismissRequest = { if (!busy) deletingExpense = null },
            title = { Text("Quitar gasto") },
            text = {
                Text(
                    if (expense.schedule == ExpenseSchedule.MONTHLY_EXTRA) {
                        "¿Quitar “${expense.name}” del plan de ${formatMonthLabel(month)}?"
                    } else {
                        "¿Quitar “${expense.name}” de todos los meses?"
                    },
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !busy,
                    onClick = {
                        save(
                            action = { repository.removeExpense(expense) },
                            onSuccess = { deletingExpense = null },
                        )
                    },
                ) {
                    Text("Quitar", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingExpense = null }, enabled = !busy) {
                    Text("Cancelar")
                }
            },
        )
    }

    if (removingGoal) {
        AlertDialog(
            onDismissRequest = { if (!busy) removingGoal = false },
            title = { Text("¿Quitar esta meta?") },
            text = {
                Text(
                    if (plan.goalMode == GoalMode.FIXED_MONTHLY) {
                        "Se quitará la meta fija que se usa todos los meses."
                    } else {
                        "Se quitará la meta de ${formatMonthLabel(month)}."
                    },
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !busy,
                    onClick = {
                        save(
                            action = { repository.clearAppliedGoal(month, plan.goalMode) },
                            onSuccess = { removingGoal = false },
                        )
                    },
                ) {
                    Text("Quitar", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { removingGoal = false }, enabled = !busy) {
                    Text("Cancelar")
                }
            },
        )
    }

    goalDialogMode?.let { mode ->
        GoalDialog(
            mode = mode,
            currentAmount = plan.goal?.amountCents,
            busy = busy,
            onDismiss = { goalDialogMode = null },
            onSave = { amount ->
                save(
                    action = {
                        if (mode == GoalMode.FIXED_MONTHLY) {
                            repository.setFixedGoal(amount)
                        } else {
                            repository.setMonthlyGoal(month, amount)
                        }
                    },
                    onSuccess = { goalDialogMode = null },
                )
            },
        )
    }
}

@Composable
private fun MonthSelector(
    month: YearMonth,
    showThisMonth: Boolean,
    onThisMonth: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPrevious) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Mes anterior")
        }
        Text(
            text = formatMonthLabel(month),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onNext) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Mes siguiente")
        }
        if (showThisMonth) {
            TextButton(
                onClick = onThisMonth,
                contentPadding = PaddingValues(horizontal = 8.dp),
            ) {
                Text("Este mes", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun ExpenseRow(
    expense: MonthlyExpense,
    selectedMonth: YearMonth,
    enabled: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(modifier = Modifier.padding(vertical = 10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = expense.name,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            Text(text = formatCents(expense.amountCents), style = MaterialTheme.typography.labelLarge)
        }
        Text(
            text = when (expense.schedule) {
                ExpenseSchedule.MONTHLY_FIXED -> "Fijo mensual"
                ExpenseSchedule.ANNUAL_FIXED ->
                    "Fijo anual: ${formatCents(expense.plannedAmountCents)} al año · " +
                        "${formatCents(expense.amountCents)} en ${formatMonthLabel(selectedMonth)}"
                ExpenseSchedule.MONTHLY_EXTRA -> "Extra de ${formatMonthLabel(selectedMonth)}"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onEdit, enabled = enabled) { Text("Editar") }
            TextButton(onClick = onDelete, enabled = enabled) {
                Text("Quitar", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun ExpenseDialog(
    expense: MonthlyExpense?,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSave: (ExpenseSchedule, String, Long) -> Unit,
) {
    var schedule by remember(expense?.id) {
        mutableStateOf(expense?.schedule ?: ExpenseSchedule.MONTHLY_FIXED)
    }
    var name by remember(expense?.id) { mutableStateOf(expense?.name.orEmpty()) }
    var amountText by remember(expense?.id) {
        mutableStateOf(expense?.plannedAmountCents?.let(::formatAmountInput).orEmpty())
    }
    var nameError by remember { mutableStateOf<String?>(null) }
    var amountError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(if (expense == null) "Agregar gasto" else "Editar gasto") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = "Frecuencia",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // Mismo contenedor `surface` que el resto de chips: así el borde fuerte descansa
                // sobre una superficie donde alcanza 3:1 en lugar del contenedor del diálogo.
                ExpenseSchedule.entries.forEachIndexed { index, option ->
                    if (index > 0) Spacer(Modifier.height(8.dp))
                    FilterChip(
                        selected = schedule == option,
                        onClick = { schedule = option },
                        enabled = !busy,
                        label = { Text(expenseScheduleLabel(option)) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = lyapeChipColors(),
                    )
                }
                if (schedule == ExpenseSchedule.ANNUAL_FIXED) {
                    Text(
                        text = "Escribe el total anual. LYape lo divide entre los 12 meses.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; nameError = null },
                    label = { Text("Nombre") },
                    singleLine = true,
                    enabled = !busy,
                    isError = nameError != null,
                    supportingText = nameError?.let { message -> { Text(message) } },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it; amountError = null },
                    label = {
                        Text(
                            if (schedule == ExpenseSchedule.ANNUAL_FIXED) {
                                "Total anual (S/)"
                            } else {
                                "Monto mensual (S/)"
                            },
                        )
                    },
                    singleLine = true,
                    enabled = !busy,
                    isError = amountError != null,
                    supportingText = amountError?.let { message -> { Text(message) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                enabled = !busy,
                onClick = {
                    nameError = if (name.isBlank()) "Escribe un nombre." else null
                    val amount = positiveAmountOrNull(amountText) { amountError = it }
                    if (nameError == null && amount != null) onSave(schedule, name, amount)
                },
                shape = PillShape,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            ) {
                Text(if (busy) "Guardando…" else "Guardar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancelar") }
        },
    )
}

@Composable
private fun GoalDialog(
    mode: GoalMode,
    currentAmount: Long?,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSave: (Long) -> Unit,
) {
    var amountText by remember(currentAmount) {
        mutableStateOf(currentAmount?.let(::formatAmountInput).orEmpty())
    }
    var amountError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = {
            Text(
                if (mode == GoalMode.FIXED_MONTHLY) {
                    "Meta fija mensual"
                } else {
                    "Meta de este mes"
                },
            )
        },
        text = {
            Column {
                Text(
                    text = "Es una meta de ingresos registrados, no de ganancia neta.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (mode == GoalMode.FIXED_MONTHLY) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Se aplica automáticamente a todos los meses.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it; amountError = null },
                    label = { Text("Meta (S/)") },
                    singleLine = true,
                    enabled = !busy,
                    isError = amountError != null,
                    supportingText = amountError?.let { message -> { Text(message) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                enabled = !busy,
                onClick = {
                    val amount = positiveAmountOrNull(amountText) { amountError = it }
                    if (amount != null) onSave(amount)
                },
                shape = PillShape,
            ) {
                Text(if (busy) "Guardando…" else "Guardar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancelar") }
        },
    )
}

@Composable
private fun GoalModeChip(
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
        modifier = modifier,
        colors = lyapeChipColors(),
    )
}

/**
 * Colores de chip de LYape: contenedor `surface` para que el borde de control se lea sobre una
 * superficie donde supera 3:1, etiqueta en tinta suave y selección en morado uva.
 */
@Composable
private fun lyapeChipColors() = FilterChipDefaults.filterChipColors(
    containerColor = MaterialTheme.colorScheme.surface,
    labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
    selectedContainerColor = MaterialTheme.colorScheme.primary,
    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
)

private fun expenseScheduleLabel(schedule: ExpenseSchedule): String = when (schedule) {
    ExpenseSchedule.MONTHLY_FIXED -> "Fijo mensual · todos los meses"
    ExpenseSchedule.ANNUAL_FIXED -> "Fijo anual · dividido entre 12"
    ExpenseSchedule.MONTHLY_EXTRA -> "Extra · solo este mes"
}

private fun positiveAmountOrNull(text: String, setError: (String) -> Unit): Long? =
    when (val parsed = parseAmountCents(text)) {
        AmountParse.Empty -> null.also { setError("Ingresa un monto.") }
        AmountParse.Malformed -> null.also { setError("Usa un monto como 350.00.") }
        AmountParse.TooLarge -> null.also { setError("El monto es demasiado grande.") }
        is AmountParse.Valid -> if (parsed.cents > 0L) {
            parsed.cents
        } else {
            null.also { setError("El monto debe ser mayor que cero.") }
        }
    }
