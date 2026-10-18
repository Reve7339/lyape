package com.lyape.ui.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lyape.domain.MonthlyPlan
import com.lyape.domain.MonthlyProgress
import com.lyape.domain.MonthlyProgressCalculator
import com.lyape.domain.MovementClassification
import com.lyape.domain.PeriodReportCalculator
import com.lyape.domain.ReportPeriod
import com.lyape.ui.components.LyapeCard
import com.lyape.ui.components.SectionLabel
import com.lyape.ui.format.formatCents
import com.lyape.ui.format.formatMonthLabel
import com.lyape.ui.format.formatReportRangeLabel
import com.lyape.ui.rememberMonthlyPlanRepository
import com.lyape.ui.rememberMovementRepository
import com.lyape.ui.theme.PillShape
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

@Composable
fun DashboardScreen(
    onReviewPending: () -> Unit,
    onConfigurePlan: (YearMonth) -> Unit,
    modifier: Modifier = Modifier,
) {
    val movementRepository = rememberMovementRepository()
    val planRepository = rememberMonthlyPlanRepository()
    val zone = remember { ZoneId.systemDefault() }

    var periodName by rememberSaveable { mutableStateOf(ReportPeriod.MONTH.name) }
    val period = remember(periodName) { ReportPeriod.valueOf(periodName) }
    var referenceEpochDay by rememberSaveable { mutableStateOf(LocalDate.now().toEpochDay()) }
    val referenceDate = LocalDate.ofEpochDay(referenceEpochDay)
    val today = LocalDate.now()

    val range = remember(period, referenceDate) {
        PeriodReportCalculator.rangeFor(period, referenceDate, zone)
    }
    val movements by remember(movementRepository, range) {
        movementRepository.observeInRange(range)
    }.collectAsStateWithLifecycle(initialValue = emptyList())
    val report = remember(movements, period, referenceDate) {
        PeriodReportCalculator.calculate(movements, period, referenceDate, zone)
    }
    val viewingToday = today >= report.startDate && today <= report.endDateInclusive

    val month = remember(referenceDate) { YearMonth.from(referenceDate) }
    val plan by remember(planRepository, month) {
        planRepository.observePlan(month)
    }.collectAsStateWithLifecycle(initialValue = MonthlyPlan.empty(month))
    val progress = remember(report.totals.businessCents, plan) {
        MonthlyProgressCalculator.calculate(report.totals.businessCents, plan)
    }

    val todayMovements by remember(movementRepository) {
        movementRepository.observeDay(LocalDate.now())
    }.collectAsStateWithLifecycle(initialValue = emptyList())
    val pendingToday = remember(todayMovements) {
        todayMovements.count {
            !it.reviewed && it.classification != MovementClassification.EXCLUDED
        }
    }

    fun shiftReference(offset: Int) {
        val date = LocalDate.ofEpochDay(referenceEpochDay)
        referenceEpochDay = when (period) {
            ReportPeriod.DAY -> date.plusDays(offset.toLong())
            ReportPeriod.WEEK -> date.plusWeeks(offset.toLong())
            ReportPeriod.MONTH -> date.plusMonths(offset.toLong())
        }.toEpochDay()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 17.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Resumen",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )

        Spacer(Modifier.height(12.dp))
        PeriodSelector(selected = period, onSelect = { periodName = it.name })

        // Los chips crecen hasta 48 dp de área táctil, así que sin esta separación tocarían las
        // flechas de periodo que están justo debajo.
        Spacer(Modifier.height(8.dp))
        RangeSelector(
            label = formatReportRangeLabel(period, report.startDate, report.endDateInclusive),
            showToday = !viewingToday,
            onToday = { referenceEpochDay = LocalDate.now().toEpochDay() },
            onPrevious = { shiftReference(-1) },
            onNext = { shiftReference(1) },
        )

        Spacer(Modifier.height(4.dp))
        Text(
            text = "Ingresos del negocio",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = formatCents(report.totals.businessCents),
            style = MaterialTheme.typography.displayLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = businessCountLabel(report.businessMovementCount),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )

        Spacer(Modifier.height(8.dp))
        Text(
            text = compactSummaryLabel(
                records = report.totals.movementCount,
                pending = report.pendingActiveCount,
                personalCents = report.totals.personalCents,
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )

        Spacer(Modifier.height(16.dp))
        SectionLabel(
            text = if (period == ReportPeriod.MONTH) "Progreso del mes" else "Distribución del periodo",
        )
        Spacer(Modifier.height(8.dp))
        if (report.hasBusinessMovements) {
            PeriodChart(report = report, progress = progress)
        } else {
            LyapeCard {
                Text(
                    text = "Aún no hay movimientos de negocio en este periodo.",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = if (period == ReportPeriod.MONTH) {
                        "Cuando clasifiques pagos como negocio, verás aquí el ingreso acumulado del mes."
                    } else {
                        "Cuando clasifiques pagos como negocio, verás aquí el gráfico por tramos."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (period == ReportPeriod.MONTH) {
            Spacer(Modifier.height(20.dp))
            SectionLabel(text = "Plan del mes")
            Spacer(Modifier.height(8.dp))
            PlanCard(
                progress = progress,
                month = month,
                onConfigurePlan = { onConfigurePlan(month) },
            )
        }

        Spacer(Modifier.height(12.dp))
        ReviewStrip(pendingCount = pendingToday, onClick = onReviewPending)
        Spacer(Modifier.height(28.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PeriodSelector(
    selected: ReportPeriod,
    onSelect: (ReportPeriod) -> Unit,
) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val labelStyle = MaterialTheme.typography.labelLarge
    val widths = ReportPeriod.entries.associateWith { period ->
        val textWidth = measurer.measure(
            text = AnnotatedString(periodLabel(period)),
            style = labelStyle,
            softWrap = false,
        ).size.width
        (with(density) { textWidth.toDp() } + 32.dp).coerceAtLeast(48.dp)
    }

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val equalWidth = (maxWidth - 16.dp) / ReportPeriod.entries.size
        if (widths.values.sumOf { it.value.toDouble() } + 16 <= maxWidth.value) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ReportPeriod.entries.forEach { option ->
                    val weight = if (widths.values.all { it <= equalWidth }) {
                        1f
                    } else {
                        widths.getValue(option).value
                    }
                    PeriodChip(option, selected, onSelect, Modifier.weight(weight))
                }
            }
        } else {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ReportPeriod.entries.forEach { option ->
                    PeriodChip(
                        option, selected, onSelect,
                        Modifier.widthIn(min = widths.getValue(option)),
                    )
                }
            }
        }
    }
}

@Composable
private fun PeriodChip(
    period: ReportPeriod,
    selected: ReportPeriod,
    onSelect: (ReportPeriod) -> Unit,
    modifier: Modifier,
) {
    FilterChip(
        selected = period == selected,
        onClick = { onSelect(period) },
        label = { Text(periodLabel(period), maxLines = 1, softWrap = false) },
        shape = PillShape,
        modifier = modifier,
        colors = FilterChipDefaults.filterChipColors(
            containerColor = MaterialTheme.colorScheme.surface,
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
        ),
    )
}

@Composable
private fun RangeSelector(
    label: String,
    showToday: Boolean,
    onToday: () -> Unit,
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
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Periodo anterior")
        }
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onNext) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Periodo siguiente")
        }
        if (showToday) {
            TextButton(
                onClick = onToday,
                contentPadding = PaddingValues(horizontal = 8.dp),
            ) {
                Text("Hoy", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun PlanCard(
    progress: MonthlyProgress,
    month: YearMonth,
    onConfigurePlan: () -> Unit,
) {
    LyapeCard {
        if (!progress.hasExpenses && !progress.hasGoal) {
            Text(
                text = "Todavía no definiste gastos ni meta para este mes.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onConfigurePlan,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
                shape = PillShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                Text("Crear plan", textAlign = TextAlign.Center)
            }
            return@LyapeCard
        }

        if (progress.hasExpenses) {
            ExpensesRow(progress)
        }
        if (progress.hasGoal) {
            if (progress.hasExpenses) {
                Spacer(Modifier.height(16.dp))
            }
            GoalRow(progress)
        }

        if (
            progress.hasExpenses && progress.hasGoal &&
            progress.goalCents.orEmptyAmount() < progress.totalExpensesCents
        ) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Tu meta es menor que los gastos previstos; alcanzarla todavía no " +
                    "cubriría el punto de equilibrio.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        Spacer(Modifier.height(16.dp))
        OutlinedButton(
            onClick = onConfigurePlan,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp),
            shape = PillShape,
        ) {
            Text("Editar plan de ${formatMonthLabel(month)}", textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun ExpensesRow(progress: MonthlyProgress) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Gastos",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = formatCents(progress.totalExpensesCents),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
    Spacer(Modifier.height(4.dp))
    Text(
        text = if (progress.expensesCovered) {
            "Ganancia estimada: ${formatCents(progress.estimatedProfitCents ?: 0L)}"
        } else {
            "Faltan ${formatCents(progress.expensesRemainingCents ?: 0L)} para cubrir los gastos"
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(8.dp))
    ProgressBar(
        current = progress.incomeCents,
        target = progress.totalExpensesCents,
        description = "Avance para cubrir gastos",
    )
}

@Composable
private fun GoalRow(progress: MonthlyProgress) {
    val goal = progress.goalCents ?: return
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Meta",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = formatCents(goal),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
    Spacer(Modifier.height(4.dp))
    Text(
        text = when {
            !progress.goalReached ->
                "Faltan ${formatCents(progress.goalRemainingCents ?: 0L)} para alcanzarla"
            (progress.goalExceededCents ?: 0L) == 0L -> "Alcanzada"
            else -> "Superada por ${formatCents(progress.goalExceededCents ?: 0L)}"
        },
        style = MaterialTheme.typography.bodySmall,
        color = if (progress.goalReached) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
    )
    Spacer(Modifier.height(8.dp))
    ProgressBar(
        current = progress.incomeCents,
        target = goal,
        description = "Avance hacia la meta de ingresos",
    )
}

@Composable
private fun ProgressBar(current: Long, target: Long, description: String) {
    val fraction = when {
        current <= 0L -> 0f
        current >= target -> 1f
        else -> (current.toDouble() / target.toDouble()).toFloat()
    }
    LinearProgressIndicator(
        progress = { fraction },
        modifier = Modifier
            .fillMaxWidth()
            .height(7.dp)
            .semantics { contentDescription = description },
        color = MaterialTheme.colorScheme.primary,
        trackColor = MaterialTheme.colorScheme.primaryContainer,
        drawStopIndicator = {},
    )
}

@Composable
private fun ReviewStrip(
    pendingCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hasPending = pendingCount > 0
    val surfaceModifier = modifier
        .fillMaxWidth()
        .heightIn(min = 52.dp)
    val shape = MaterialTheme.shapes.medium
    val color = MaterialTheme.colorScheme.primary
    val contentColor = MaterialTheme.colorScheme.onPrimary
    val border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)

    if (hasPending) {
        Surface(
            onClick = onClick,
            modifier = surfaceModifier,
            shape = shape,
            color = color,
            contentColor = contentColor,
            border = border,
        ) {
            ReviewStripRow(pendingCount = pendingCount, showAction = true)
        }
    } else {
        Surface(
            modifier = surfaceModifier,
            shape = shape,
            color = color,
            contentColor = contentColor,
            border = border,
        ) {
            ReviewStripRow(pendingCount = pendingCount, showAction = false)
        }
    }
}

@Composable
private fun ReviewStripRow(pendingCount: Int, showAction: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = pendingTodayLabel(pendingCount),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.weight(1f),
        )
        if (showAction) {
            Text(text = "Revisar", style = MaterialTheme.typography.labelMedium)
        }
    }
}

private fun periodLabel(period: ReportPeriod): String = when (period) {
    ReportPeriod.DAY -> "Día"
    ReportPeriod.WEEK -> "Semana"
    ReportPeriod.MONTH -> "Mes"
}

private fun Long?.orEmptyAmount(): Long = this ?: 0L
