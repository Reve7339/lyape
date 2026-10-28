package com.lyape.ui.monthlyplan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.LinearProgressIndicator
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
import com.lyape.domain.ExpensePaymentProgress
import com.lyape.domain.MonthlyProgressCalculator
import com.lyape.domain.ExpenseSchedule
import com.lyape.domain.GoalMode
import com.lyape.domain.GoalType
import com.lyape.ui.components.LyapeCard
import com.lyape.ui.components.SectionLabel
import com.lyape.ui.format.AmountParse
import com.lyape.ui.format.formatAmountInput
import com.lyape.ui.format.formatCents
import com.lyape.ui.format.formatMonthLabel
import com.lyape.ui.format.parseAmountCents
import com.lyape.ui.rememberMonthlyPlanRepository
import com.lyape.ui.theme.PillShape
import com.lyape.ui.rememberAccountsRepository
import com.lyape.ui.dashboard.ReadFeedback
import com.lyape.ui.dashboard.ScreenRead
import com.lyape.ui.dashboard.asScreenRead
import com.lyape.ui.dashboard.monthlyUnpaidTotal
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.time.YearMonth

@Composable
fun MonthlyPlanScreen(
    initialMonth: String,
    modifier: Modifier = Modifier,
    onPayExpense: (String, String) -> Unit = { _, _ -> },
) {
    val repository = rememberMonthlyPlanRepository()
    val accountsRepository = rememberAccountsRepository()
    val scope = rememberCoroutineScope()
    val parsedInitial = remember(initialMonth) {
        runCatching { YearMonth.parse(initialMonth) }.getOrElse { YearMonth.now() }
    }
    var monthValue by rememberSaveable(initialMonth) { mutableStateOf(parsedInitial.toString()) }
    val month = remember(monthValue) { YearMonth.parse(monthValue) }
    var retry by remember { mutableStateOf(0) }
    val planReading by remember(repository, month, retry) {
        repository.observePlan(month).asScreenRead()
    }.collectAsStateWithLifecycle(initialValue = ScreenRead.Loading)
    val paymentsReading by remember(accountsRepository, month, retry) {
        accountsRepository.observeExpensePayments(month).asScreenRead()
    }.collectAsStateWithLifecycle(initialValue = ScreenRead.Loading)
    val planReady = planReading is ScreenRead.Ready
    val plan = (planReading as? ScreenRead.Ready)?.value ?: MonthlyPlan.empty(month)
    val paymentsAvailable = paymentsReading is ScreenRead.Ready
    val payments = (paymentsReading as? ScreenRead.Ready)?.value.orEmpty()
    val paymentsByExpense = payments.associateBy { it.expenseId }
    val progress = remember(plan) { MonthlyProgressCalculator.calculate(0L, plan) }
    val budget = remember(plan) { MonthlyProgressCalculator.calculate(0L, plan.copy(goal = null)) }
    val totalExpenses = budget.totalExpensesCents

    var addingExpense by remember { mutableStateOf(false) }
    var editingExpense by remember { mutableStateOf<MonthlyExpense?>(null) }
    var deletingExpense by remember { mutableStateOf<MonthlyExpense?>(null) }
    var viewingPayments by remember { mutableStateOf<MonthlyExpense?>(null) }
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
            } catch (error: IllegalArgumentException) {
                actionError = error.message ?: "Revisa los datos del plan."
                false
            } catch (error: IllegalStateException) {
                actionError = error.message ?: "No se puede cambiar este gasto."
                false
            } catch (_: Exception) {
                actionError = "No pudimos guardar el plan. Vuelve a intentarlo."
                false
            } finally {
                busy = false
            }
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
        MonthSelector(
            month = month,
            showThisMonth = month != YearMonth.now(),
            onThisMonth = { monthValue = YearMonth.now().toString() },
            onPrevious = { monthValue = month.minusMonths(1).toString() },
            onNext = { monthValue = month.plusMonths(1).toString() },
        )

        if (!planReady) {
            ReadFeedback(
                failed = planReading is ScreenRead.Failed,
                message = "No se pudo leer el plan de este mes.",
                onRetry = { retry++ },
            )
            return@Column
        }
        if (!paymentsAvailable) {
            ReadFeedback(
                failed = paymentsReading is ScreenRead.Failed,
                message = "No se pudieron leer los pagos. Puedes ver el plan, pero no editar ni quitar gastos hasta reintentar.",
                onRetry = { retry++ },
            )
        }
        val unpaid = if (paymentsAvailable) monthlyUnpaidTotal(payments) else null
        Spacer(Modifier.height(12.dp))
        Text("Por pagar del mes", style = MaterialTheme.typography.bodyMedium)
        Text(
            text = unpaid?.let(::formatCents) ?: "No disponible",
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(
            if (budget.calculationError) "Los gastos superan el límite de cálculo. Revisa los importes." else "Gastos previstos ${formatCents(totalExpenses)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (unpaid != null && totalExpenses > 0L) {
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { ((totalExpenses - unpaid).toDouble() / totalExpenses.toDouble()).toFloat().coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
                drawStopIndicator = {},
            )
        }
        if (paymentsAvailable && payments.any { it.annual }) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Los anuales muestran aquí su parte mensual. El pago se registra por el año completo o en abonos.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SectionLabel(text = "Gastos previstos")
            Text(
                text = if (budget.calculationError) "No disponible" else formatCents(totalExpenses),
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
                        enabled = !busy && paymentsAvailable,
                        paymentsAvailable = paymentsAvailable,
                        paymentsFailed = paymentsReading is ScreenRead.Failed,
                        payment = if (paymentsAvailable) paymentsByExpense[expense.id] else null,
                        onPay = { onPayExpense(expense.id.toString(), month.toString()) },
                        onPayments = { viewingPayments = expense },
                        onEdit = { actionError = null; editingExpense = expense },
                        onDelete = { actionError = null; deletingExpense = expense },
                    )
                    if (index != plan.expenses.lastIndex) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = { actionError = null; addingExpense = true },
            enabled = !busy,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp),
            shape = PillShape,
        ) {
            Text("Agregar gasto fijo o extra")
        }

        Spacer(Modifier.height(20.dp))
        SectionLabel(text = "Meta")
        Spacer(Modifier.height(8.dp))
        LyapeCard {
            val goal = plan.goal
            Text(
                text = if (goal?.type == GoalType.PROFIT) "Meta de ganancia" else if (goal != null) "Meta de ingresos" else "Sin meta",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            if (goal == null) {
                Text(
                    text = if (plan.goalMode == GoalMode.FIXED_MONTHLY) {
                        "Define cuánto quieres ganar cada mes."
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
                Text(
                        text = if (plan.goalMode == GoalMode.BY_MONTH) formatMonthLabel(month) else "Todos los meses",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                if (progress.calculationError) {
                    Text("Los importes superan el límite de cálculo. Revisa gastos y meta.", color = MaterialTheme.colorScheme.error)
                } else if (goal.type == GoalType.PROFIT) {
                    Text("Ingresos necesarios: ${formatCents(requireNotNull(progress.requiredIncomeCents))}", style = MaterialTheme.typography.bodySmall)
                } else if (plan.expenses.isNotEmpty() && goal.amountCents < totalExpenses) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "La meta no cubre los gastos del mes.",
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
            error = actionError,
            onDismiss = {
                addingExpense = false
                editingExpense = null
                actionError = null
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
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        if (expense.schedule == ExpenseSchedule.MONTHLY_EXTRA) {
                            "¿Quitar “${expense.name}” del plan de ${formatMonthLabel(month)}?"
                        } else {
                            "¿Quitar “${expense.name}” de todos los meses?"
                        },
                    )
                    actionError?.let {
                        Text(it, color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = { deletingExpense = null; viewingPayments = expense; actionError = null }, enabled = !busy) {
                            Text("Ver pagos")
                        }
                    }
                }
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

    viewingPayments?.let { expense ->
        ExpensePaymentsDialog(expense, onDismiss = { viewingPayments = null })
    }

    goalDialogMode?.let { mode ->
        GoalDialog(
            initialMode = mode,
            plan = plan,
            busy = busy,
            saveError = actionError,
            onDismiss = { goalDialogMode = null },
            onSave = { amount, type, selectedMode ->
                save(
                    action = {
                        if (selectedMode == GoalMode.FIXED_MONTHLY) {
                            repository.setFixedGoal(amount, type)
                        } else {
                            repository.setMonthlyGoal(month, amount, type)
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
@OptIn(ExperimentalLayoutApi::class)
private fun ExpenseRow(
    expense: MonthlyExpense,
    selectedMonth: YearMonth,
    enabled: Boolean,
    paymentsAvailable: Boolean,
    paymentsFailed: Boolean,
    payment: ExpensePaymentProgress?,
    onPay: () -> Unit,
    onPayments: () -> Unit,
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
        if (!paymentsAvailable) {
            if (paymentsFailed) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "No se pudieron comprobar los pagos de este gasto.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        } else if (payment != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = expenseRemainingLabel(payment),
                style = MaterialTheme.typography.bodyMedium,
                color = if (payment.remainingCents == 0L) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
            if (payment.paidCents > 0L && payment.remainingCents > 0L) {
                Text(
                    "Abonado ${formatCents(payment.paidCents)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (payment != null && payment.remainingCents > 0L) {
                TextButton(onClick = onPay, enabled = enabled) { Text("Pagar") }
            }
            TextButton(onClick = onPayments, enabled = enabled) { Text("Ver pagos") }
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
    error: String?,
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
                error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
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
@OptIn(ExperimentalLayoutApi::class)
private fun GoalDialog(
    initialMode: GoalMode,
    plan: MonthlyPlan,
    busy: Boolean,
    saveError: String?,
    onDismiss: () -> Unit,
    onSave: (Long, GoalType, GoalMode) -> Unit,
) {
    var mode by rememberSaveable { mutableStateOf(initialMode) }
    val savedGoal = if (mode == GoalMode.FIXED_MONTHLY) plan.fixedGoal else plan.monthlyGoal
    var type by rememberSaveable(mode) { mutableStateOf(savedGoal?.type ?: GoalType.PROFIT) }
    var amountText by rememberSaveable(mode) {
        mutableStateOf(savedGoal?.amountCents?.let(::formatAmountInput).orEmpty())
    }
    var amountError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = {
            Text("Definir meta")
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Tipo", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    GoalModeChip("Ganancia", type == GoalType.PROFIT, !busy, { type = GoalType.PROFIT; amountError = null })
                    GoalModeChip("Ingresos", type == GoalType.INCOME, !busy, { type = GoalType.INCOME; amountError = null })
                }
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it; amountError = null },
                    label = { Text(if (type == GoalType.PROFIT) "Ganancia deseada (S/)" else "Ingresos deseados (S/)") },
                    singleLine = true,
                    enabled = !busy,
                    isError = amountError != null,
                    supportingText = amountError?.let { message -> { Text(message) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Aplicar", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    GoalModeChip("Todos los meses", mode == GoalMode.FIXED_MONTHLY, !busy, { mode = GoalMode.FIXED_MONTHLY; amountError = null })
                    GoalModeChip("Por mes", mode == GoalMode.BY_MONTH, !busy, { mode = GoalMode.BY_MONTH; amountError = null })
                }
                Text(
                    if (type == GoalType.PROFIT) "La ganancia estimada descuenta los gastos del plan." else "Ingresos del negocio antes de descontar gastos.",
                    style = MaterialTheme.typography.bodySmall,
                )
                val amount = (parseAmountCents(amountText) as? AmountParse.Valid)?.cents
                if (type == GoalType.PROFIT && amount != null && amount > 0L) {
                    val preview = MonthlyProgressCalculator.calculate(0L, plan.copy(goal = com.lyape.domain.MonthlyGoal(plan.month, amount, type)))
                    if (!preview.calculationError) Text("Ingresos necesarios: ${formatCents(requireNotNull(preview.requiredIncomeCents))}", style = MaterialTheme.typography.bodySmall)
                }
                if (type == GoalType.PROFIT && plan.expenses.isEmpty()) Text("Sin gastos en el plan", style = MaterialTheme.typography.bodySmall)
                if (mode == GoalMode.FIXED_MONTHLY) Text("También cambia los meses anteriores donde se aplica.", style = MaterialTheme.typography.bodySmall)
                saveError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            Button(
                enabled = !busy,
                onClick = {
                    val amount = positiveAmountOrNull(amountText) { amountError = it }
                    if (amount != null) {
                        val preview = MonthlyProgressCalculator.calculate(0L, plan.copy(goal = com.lyape.domain.MonthlyGoal(plan.month, amount, type)))
                        if (preview.calculationError) amountError = "La meta y los gastos superan el límite de cálculo."
                        else onSave(amount, type, mode)
                    }
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
        modifier = modifier.heightIn(min = 48.dp),
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
