package com.lyape.ui.movementdetail

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lyape.domain.Movement
import com.lyape.domain.MovementClassification
import com.lyape.ui.components.ClassificationTag
import com.lyape.ui.components.InfoRow
import com.lyape.ui.components.LyapeCard
import com.lyape.ui.components.SectionLabel
import com.lyape.ui.format.formatSignedCents
import com.lyape.ui.model.classificationLabel
import com.lyape.ui.model.toUi
import com.lyape.ui.rememberMovementRepository
import com.lyape.ui.rememberAccountsRepository
import com.lyape.ui.theme.PillShape
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.UUID

private sealed interface MovementDetailState {
    data object Loading : MovementDetailState
    data object NotFound : MovementDetailState
    data object Error : MovementDetailState
    data class Found(val movement: Movement) : MovementDetailState
}

@Composable
fun MovementDetailScreen(
    movementId: String,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val repository = rememberMovementRepository()
    val scope = rememberCoroutineScope()
    val id = remember(movementId) { runCatching { UUID.fromString(movementId) }.getOrNull() }

    var retryKey by remember { mutableIntStateOf(0) }
    val state by remember(id, repository, retryKey) {
        val flow: Flow<MovementDetailState> = if (id == null) {
            flowOf(MovementDetailState.NotFound)
        } else {
            repository.observeById(id)
                .map { movement ->
                    if (movement == null) {
                        MovementDetailState.NotFound
                    } else {
                        MovementDetailState.Found(movement)
                    }
                }
                .catch { emit(MovementDetailState.Error) }
        }
        flow
    }.collectAsStateWithLifecycle(initialValue = MovementDetailState.Loading)

    var busy by remember { mutableStateOf(false) }
    var actionError by remember { mutableStateOf<String?>(null) }

    fun run(action: suspend () -> Unit) {
        if (busy) return
        busy = true
        actionError = null
        scope.launch {
            try {
                action()
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                actionError = "No pudimos guardar el cambio. Vuelve a intentarlo."
            } finally {
                busy = false
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 17.dp),
    ) {
        Spacer(Modifier.height(8.dp))

        when (val current = state) {
            MovementDetailState.Loading -> LoadingState()

            MovementDetailState.Error -> ErrorMessage(onRetry = { retryKey += 1 })

            MovementDetailState.NotFound -> Message("No encontramos este movimiento.")

            is MovementDetailState.Found -> DetailContent(
                movement = current.movement,
                busy = busy,
                actionError = actionError,
                onClassify = { classification ->
                    run { repository.classify(current.movement.id, classification) }
                },
                onExclude = { run { repository.exclude(current.movement.id) } },
                onRestore = { run { repository.restore(current.movement.id) } },
                onEdit = onEdit,
            )
        }

        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun DetailContent(
    movement: Movement,
    busy: Boolean,
    actionError: String?,
    onClassify: (MovementClassification) -> Unit,
    onExclude: () -> Unit,
    onRestore: () -> Unit,
    onEdit: () -> Unit,
) {
    val context = LocalContext.current
    val use24Hour = android.text.format.DateFormat.is24HourFormat(context)
    val ui = remember(movement, use24Hour) { movement.toUi(use24Hour = use24Hour) }
    val excluded = ui.isExcluded
    val accountsRepository = rememberAccountsRepository()
    val accountName by remember(accountsRepository, movement.accountId) {
        movement.accountId?.let { accountId ->
            // El nombre sale del catálogo de cuentas, no del cálculo de saldos: un fallo de
            // lectura de saldo no debe convertir una cuenta existente en «no disponible».
            accountsRepository.observeSelectableAccounts()
                .map { accounts ->
                    accounts.find { it.id == accountId }?.name ?: "Cuenta no disponible"
                }
                .catch { emit("No se pudo cargar la cuenta") }
        } ?: flowOf("Sin cuenta")
    }.collectAsStateWithLifecycle(initialValue = "Cargando…")

    Text(
        text = ui.title,
        style = MaterialTheme.typography.headlineMedium,
        color = MaterialTheme.colorScheme.onBackground,
    )
    Spacer(Modifier.height(4.dp))
    Text(
        text = formatSignedCents(ui.amountCents),
        style = MaterialTheme.typography.displayLarge,
        color = if (excluded) {
            MaterialTheme.colorScheme.onSurfaceVariant
        } else {
            MaterialTheme.colorScheme.onBackground
        },
    )
    Spacer(Modifier.height(10.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        ClassificationTag(classification = ui.classification)
    }

    Spacer(Modifier.height(16.dp))
    LyapeCard {
        InfoRow(label = "Origen", value = ui.originLabel)
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        InfoRow(label = "Cuenta", value = accountName)
        val supportingLabel = ui.supportingLabel
        if (supportingLabel != null) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            InfoRow(label = "Motivo", value = supportingLabel)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        InfoRow(label = "Recibido", value = ui.dateTimeLabel)
        if (excluded) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            InfoRow(
                label = "Estaba como",
                value = classificationLabel(
                    ui.previousClassification ?: MovementClassification.BUSINESS,
                ),
            )
        }
    }

    Spacer(Modifier.height(16.dp))
    SectionLabel(text = "Clasificación")
    Spacer(Modifier.height(8.dp))
    ClassifyRow(
        classification = ui.classification,
        enabled = !busy && !excluded,
        onSelect = onClassify,
    )
    ClassificationNote(classification = ui.classification, excluded = excluded)

    Spacer(Modifier.height(14.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        OutlinedButton(
            onClick = onEdit,
            enabled = !busy,
            shape = PillShape,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp),
        ) {
            Text("Editar")
        }

        if (excluded) {
            Button(
                onClick = onRestore,
                enabled = !busy,
                shape = PillShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp),
            ) {
                Text("Restaurar")
            }
        } else {
            OutlinedButton(
                onClick = onExclude,
                enabled = !busy,
                shape = PillShape,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp),
            ) {
                Text("Anular")
            }
        }
    }

    if (actionError != null) {
        Spacer(Modifier.height(10.dp))
        Text(
            text = actionError,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun ClassifyRow(
    classification: MovementClassification,
    enabled: Boolean,
    onSelect: (MovementClassification) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(4.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        listOf(MovementClassification.BUSINESS, MovementClassification.PERSONAL).forEach { option ->
            val selected = classification == option
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .selectable(
                        selected = selected,
                        enabled = enabled,
                        role = Role.RadioButton,
                        onClick = { onSelect(option) },
                    ),
                shape = CircleShape,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    Color.Transparent
                },
                contentColor = if (selected) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = classificationLabel(option),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun LoadingState() {
    Row(
        modifier = Modifier.padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(20.dp),
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = "Cargando movimiento…",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Message(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
private fun ClassificationNote(classification: MovementClassification, excluded: Boolean) {
    val note = classificationNote(classification, excluded)
    if (note != null) {
        Spacer(Modifier.height(6.dp))
        Text(
            text = note,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (excluded) {
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Restaura el movimiento para volver a clasificarlo.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Nota corta junto al selector de clasificación. Es `null` para negocio: es el estado por defecto
 * y no necesita explicación.
 */
internal fun classificationNote(
    classification: MovementClassification,
    excluded: Boolean,
): String? = when {
    excluded -> "No se suma y permanece en el historial."
    classification == MovementClassification.PERSONAL -> "No se suma a los totales."
    else -> null
}

@Composable
private fun ErrorMessage(onRetry: () -> Unit) {
    Column {
        Text(
            text = "No pudimos leer este movimiento guardado en el dispositivo.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(10.dp))
        OutlinedButton(onClick = onRetry, shape = PillShape) {
            Text("Reintentar")
        }
    }
}
