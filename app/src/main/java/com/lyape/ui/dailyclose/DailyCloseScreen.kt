package com.lyape.ui.dailyclose

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lyape.domain.Movement
import com.lyape.domain.MovementClassification
import com.lyape.domain.Totals
import com.lyape.domain.TotalsCalculator
import com.lyape.ui.components.LyapeCard
import com.lyape.ui.components.MovementRow
import com.lyape.ui.components.SectionLabel
import com.lyape.ui.format.formatCents
import com.lyape.ui.format.formatDayLabel
import com.lyape.ui.model.toUi
import com.lyape.ui.rememberMovementRepository
import com.lyape.ui.theme.PillShape
import kotlinx.coroutines.launch
import java.time.LocalDate

@Composable
fun DailyCloseScreen(
    onAddMissing: () -> Unit,
    onOpenMovement: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val repository = rememberMovementRepository()
    val scope = rememberCoroutineScope()

    var epochDay by rememberSaveable { mutableStateOf(LocalDate.now().toEpochDay()) }
    val date = LocalDate.ofEpochDay(epochDay)

    val movements by remember(repository, date) { repository.observeDay(date) }
        .collectAsStateWithLifecycle(initialValue = emptyList())

    val totals = remember(movements) { TotalsCalculator.totalFor(movements) }
    val activeMovements = remember(movements) {
        movements.filter { it.classification != MovementClassification.EXCLUDED }
    }
    val pending = remember(activeMovements) { activeMovements.filter { !it.reviewed } }
    val reviewed = activeMovements.size - pending.size

    var busy by remember { mutableStateOf(false) }
    var actionError by remember { mutableStateOf<String?>(null) }

    fun run(action: suspend () -> Unit) {
        if (busy) return
        busy = true
        actionError = null
        scope.launch {
            if (runCatching { action() }.isFailure) {
                actionError = "No pudimos actualizar el día. Vuelve a intentarlo."
            }
            busy = false
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
            text = "Cierre diario",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )

        Spacer(Modifier.height(8.dp))
        DaySelector(
            label = formatDayLabel(date),
            showToday = date != LocalDate.now(),
            onToday = { epochDay = LocalDate.now().toEpochDay() },
            onPrevious = { epochDay -= 1 },
            onNext = { epochDay += 1 },
        )

        Spacer(Modifier.height(12.dp))
        ProgressPanel(reviewed = reviewed, total = activeMovements.size)

        Spacer(Modifier.height(16.dp))
        SectionLabel(text = "Pagos por revisar")
        Spacer(Modifier.height(8.dp))

        PendingList(
            pending = pending,
            hasMovements = activeMovements.isNotEmpty(),
            onOpenMovement = onOpenMovement,
        )

        Spacer(Modifier.height(12.dp))
        ClosingTotal(totals = totals)
        Spacer(Modifier.height(12.dp))

        if (pending.isNotEmpty()) {
            Button(
                onClick = { run { repository.closeDay(date) } },
                enabled = !busy,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
                shape = PillShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                Text(
                    text = "Marcar como revisado",
                    style = MaterialTheme.typography.labelLarge,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(8.dp))
        } else if (activeMovements.isNotEmpty()) {
            OutlinedButton(
                onClick = { run { repository.reopenDay(date) } },
                enabled = !busy,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
                shape = PillShape,
            ) {
                Text("Reabrir el día", style = MaterialTheme.typography.labelLarge)
            }
            Spacer(Modifier.height(8.dp))
        }

        OutlinedButton(
            onClick = onAddMissing,
            enabled = !busy,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp),
            shape = PillShape,
        ) {
            Text("Registrar pago faltante", style = MaterialTheme.typography.labelLarge)
        }

        val message = actionError
        if (message != null) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun DaySelector(
    label: String,
    showToday: Boolean,
    onToday: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPrevious) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = "Día anterior",
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onNext) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Día siguiente",
            )
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
private fun ProgressPanel(reviewed: Int, total: Int, modifier: Modifier = Modifier) {
    val fraction = if (total == 0) 0f else reviewed.toFloat() / total.toFloat()

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    text = "Confirma qué pagos pertenecen al negocio",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "$reviewed/$total",
                    style = MaterialTheme.typography.titleLarge,
                )
            }
            Spacer(Modifier.height(14.dp))
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp),
                color = MaterialTheme.colorScheme.onPrimary,
                trackColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.24f),
                drawStopIndicator = {},
            )
        }
    }
}

@Composable
private fun PendingList(
    pending: List<Movement>,
    hasMovements: Boolean,
    onOpenMovement: (String) -> Unit,
) {
    if (pending.isEmpty()) {
        LyapeCard {
            Text(
                text = if (hasMovements) {
                    "No hay pagos pendientes en esta fecha. " +
                        "Puedes reabrir el día para revisarlos otra vez."
                } else {
                    "No hay movimientos en esta fecha."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    LyapeCard(contentPadding = PaddingValues(0.dp)) {
        pending.forEachIndexed { index, movement ->
            MovementRow(
                movement = movement.toUi(),
                onClick = { onOpenMovement(movement.id.toString()) },
            )
            if (index != pending.lastIndex) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

@Composable
private fun ClosingTotal(totals: Totals, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                text = "Total del negocio",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = formatCents(totals.businessCents),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
    }
}
