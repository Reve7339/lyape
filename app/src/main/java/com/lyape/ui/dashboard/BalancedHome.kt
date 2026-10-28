package com.lyape.ui.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lyape.domain.AccountBalance
import com.lyape.domain.CashSummary
import com.lyape.domain.ExpensePaymentProgress
import com.lyape.domain.MonthlyProgress
import com.lyape.domain.PeriodReport
import com.lyape.ui.components.InfoRow
import com.lyape.ui.components.LyapeCard
import com.lyape.ui.format.formatCents
import com.lyape.ui.rememberAccountsRepository
import java.time.YearMonth

/** La composición C conserva el mismo peso para lo generado y el dinero disponible. */
@Composable
internal fun BalancedHome(
    report: PeriodReport,
    progress: MonthlyProgress,
    month: YearMonth,
    onOpenAccounts: () -> Unit,
    onOpenAccount: (String) -> Unit,
    onConfigurePlan: () -> Unit,
    onOpenReports: () -> Unit,
) {
    val repository = rememberAccountsRepository()
    var cashRetry by remember { mutableStateOf(0) }
    var paymentRetry by remember { mutableStateOf(0) }
    val cashReading by remember(repository, cashRetry) {
        repository.observeSummary().asScreenRead()
    }.collectAsStateWithLifecycle(initialValue = ScreenRead.Loading)
    val paymentReading by remember(repository, month, paymentRetry) {
        repository.observeExpensePayments(month).asScreenRead()
    }.collectAsStateWithLifecycle(initialValue = ScreenRead.Loading)
    val cash = (cashReading as? ScreenRead.Ready)?.value
    val payments = (paymentReading as? ScreenRead.Ready)?.value

    Spacer(Modifier.height(12.dp))
    HomeMetricPair(
        firstLabel = "Ingresos del mes",
        firstValue = formatCents(report.totals.businessCents),
        secondLabel = "Caja del negocio",
        secondValue = cash?.let { formatCents(it.businessCents) } ?: "—",
        secondNote = when {
            cash == null -> if (cashReading is ScreenRead.Failed) "No disponible" else "Cargando…"
            cash.hasUnconfiguredAccounts -> "Saldo parcial"
            month != YearMonth.now() -> "Hoy"
            else -> "Ahora"
        },
        onSecondClick = onOpenAccounts,
    )

    if (cash != null) {
        Spacer(Modifier.height(12.dp))
        TotalsStrip(cash)
    }
    HomeSection("Cuentas", action = "Ver todas", onAction = onOpenAccounts)
    if (cash == null) {
        ReadFeedback(
            failed = cashReading is ScreenRead.Failed,
            message = "No se pudieron leer los saldos.",
            onRetry = { cashRetry++ },
        )
    } else {
        LyapeCard(contentPadding = PaddingValues(0.dp)) {
            cash.accounts.forEachIndexed { index, account ->
                CompactAccount(account, onClick = { onOpenAccount(account.id) })
                if (index < cash.accounts.lastIndex) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
            if (cash.accounts.isEmpty()) {
                Text("Agrega una cuenta para registrar tus saldos.", Modifier.padding(16.dp))
            }
        }
        if (cash.hasUnconfiguredAccounts) {
            Text(
                "Configura los saldos iniciales para completar la caja.",
                modifier = Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    HomeSection("Plan del mes", action = "Ver plan", onAction = onConfigurePlan)
    if (!progress.hasExpenses && !progress.hasGoal) {
        Text("Sin gastos ni meta para este mes.", style = MaterialTheme.typography.bodyMedium)
        TextButton(onClick = onConfigurePlan) { Text("Crear plan") }
    } else {
        HomeMetricPair(
            firstLabel = "Ganancia estimada",
            firstValue = formatCents(progress.signedProfitCents),
            secondLabel = "Por pagar",
            secondValue = payments?.let { formatCents(monthlyUnpaidTotal(it)) } ?: "—",
            secondNote = if (payments == null) {
                if (paymentReading is ScreenRead.Failed) "No disponible" else "Cargando…"
            } else null,
            onSecondClick = onConfigurePlan,
        )
        if (paymentReading is ScreenRead.Failed) {
            ReadFeedback(true, "No se pudieron leer los pagos del plan.", { paymentRetry++ })
        }
        Text(
            "Gastos del mes: ${formatCents(progress.totalExpensesCents)}",
            modifier = Modifier.padding(top = 8.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (payments?.any { it.annual && it.remainingCents > 0L } == true) {
            Text(
                "Por pagar incluye la parte mensual de los gastos anuales pendientes.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }

    HomeSection("Progreso del mes")
    if (report.hasBusinessMovements || progress.hasExpenses || progress.hasGoal) {
        // El gráfico mantiene el coste presupuestado. Un pago no rebaja el umbral de ganancia.
        PeriodChart(
            report = report,
            progress = progress,
            unpaidExpensesCents = if (progress.hasExpenses) payments?.let(::monthlyUnpaidTotal) else null,
        )
    } else {
        Text(
            "Aún no hay ingresos de negocio este mes.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (progress.hasGoal) {
        Spacer(Modifier.height(16.dp))
        GoalRow(progress)
        if (progress.goalType == com.lyape.domain.GoalType.INCOME && progress.goalCents!! < progress.totalExpensesCents) {
            Text(
                "La meta no cubre los gastos del mes.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
    TextButton(onClick = onOpenReports, modifier = Modifier.heightIn(min = 48.dp)) {
        Text("Ver reportes")
    }
}

@Composable
private fun TotalsStrip(summary: CashSummary) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
            InfoRow(
                if (summary.hasUnconfiguredAccounts) "Total configurado" else "Total en cuentas",
                formatCents(summary.totalCents),
            )
            Text(
                "Personal ${formatCents(summary.personalCents)}",
                modifier = Modifier.padding(bottom = 8.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CompactAccount(account: AccountBalance, onClick: () -> Unit) {
    Surface(onClick = onClick, color = MaterialTheme.colorScheme.surface) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(account.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            Text(
                if (account.initialized) formatCents(account.totalCents) else "Configurar saldo",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.End,
            )
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
        }
    }
}

@Composable
private fun HomeSection(title: String, action: String? = null, onAction: () -> Unit = {}) {
    Spacer(Modifier.height(16.dp))
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        if (action != null) TextButton(onClick = onAction) { Text(action) }
    }
    Spacer(Modifier.height(4.dp))
}

@Composable
internal fun HomeMetricPair(
    firstLabel: String,
    firstValue: String,
    secondLabel: String,
    secondValue: String,
    secondNote: String? = null,
    onSecondClick: () -> Unit,
) {
    val measure = rememberTextMeasurer()
    val density = LocalDensity.current
    val style = MaterialTheme.typography.titleLarge
    val minimum = with(density) {
        maxOf(
            measure.measure(AnnotatedString(firstValue), style, softWrap = false).size.width,
            measure.measure(AnnotatedString(secondValue), style, softWrap = false).size.width,
        ).toDp()
    } + 24.dp
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if ((maxWidth - 12.dp) / 2 < minimum) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                HomeMetric(firstLabel, firstValue, modifier = Modifier.fillMaxWidth())
                HomeMetric(secondLabel, secondValue, secondNote, onSecondClick, Modifier.fillMaxWidth())
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                HomeMetric(firstLabel, firstValue, modifier = Modifier.weight(1f))
                HomeMetric(secondLabel, secondValue, secondNote, onSecondClick, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun HomeMetric(
    label: String,
    value: String,
    note: String? = null,
    onClick: (() -> Unit)? = null,
    modifier: Modifier,
) {
    val body: @Composable () -> Unit = {
        Column(Modifier.padding(12.dp).semantics(mergeDescendants = true) {}) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
            Text(value, style = MaterialTheme.typography.titleLarge)
            if (note != null) {
                Spacer(Modifier.height(4.dp))
                Text(note, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = modifier.heightIn(min = 104.dp),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            content = body,
        )
    } else {
        Surface(
            modifier = modifier.heightIn(min = 104.dp),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            content = body,
        )
    }
}

internal fun monthlyUnpaidTotal(payments: List<ExpensePaymentProgress>): Long =
    payments.fold(0L) { total, payment ->
        try {
            Math.addExact(total, payment.monthlyUnpaidCents)
        } catch (_: ArithmeticException) {
            Long.MAX_VALUE
        }
    }

internal fun estimatedMonthlyProfit(income: Long, plannedExpenses: Long): Long = try {
    Math.subtractExact(income, plannedExpenses)
} catch (_: ArithmeticException) {
    Long.MIN_VALUE
}
