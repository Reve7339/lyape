package com.lyape.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lyape.domain.MovementClassification
import com.lyape.ui.demo.DemoClassification
import com.lyape.ui.format.formatSignedCents
import com.lyape.ui.model.MovementUi
import com.lyape.ui.model.classificationLabel
import com.lyape.ui.theme.MoneyTextStyle

/** Tarjeta base con borde suave, como las del mockup. */
@Composable
fun LyapeCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(modifier = Modifier.padding(contentPadding), content = content)
    }
}

/** Etiqueta de clasificación: negocio, personal o anulado. */
@Composable
fun ClassificationTag(classification: DemoClassification, modifier: Modifier = Modifier) {
    ClassificationTag(
        label = classification.label,
        business = classification == DemoClassification.BUSINESS,
        modifier = modifier,
    )
}

/** Etiqueta de clasificación para un movimiento real. */
@Composable
fun ClassificationTag(classification: MovementClassification, modifier: Modifier = Modifier) {
    ClassificationTag(
        label = classificationLabel(classification),
        business = classification == MovementClassification.BUSINESS,
        modifier = modifier,
    )
}

@Composable
private fun ClassificationTag(label: String, business: Boolean, modifier: Modifier = Modifier) {
    val container = if (business) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val content = if (business) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = container,
        contentColor = content,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun Avatar(label: String, muted: Boolean, modifier: Modifier = Modifier) {
    val background = if (muted) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primary
    val content = if (muted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onPrimary

    Box(
        modifier = modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(background),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MoneyTextStyle,
            color = content,
            modifier = Modifier.clearAndSetSemantics { },
        )
    }
}

/**
 * Fila de movimiento. El texto puede crecer con el tamaño de fuente del sistema: la fila
 * usa altura mínima y deja que el contenido se expanda.
 */
@Composable
fun MovementRow(
    movement: MovementUi,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    actions: (@Composable () -> Unit)? = null,
) {
    val excluded = movement.isExcluded
    val clickable = if (onClick != null) {
        Modifier.clickable(
            onClickLabel = "Ver detalle de ${movement.title}",
            role = Role.Button,
            onClick = onClick,
        )
    } else {
        Modifier
    }

    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val amount = formatSignedCents(movement.amountCents)
    val amountWidth = with(density) {
        measurer.measure(AnnotatedString(amount), MoneyTextStyle, softWrap = false).size.width.toDp()
    }
    val tagWidth = with(density) {
        measurer.measure(
            AnnotatedString(classificationLabel(movement.classification)),
            MaterialTheme.typography.labelSmall,
            softWrap = false,
        ).size.width.toDp()
    } + 20.dp

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .then(clickable)
            .heightIn(min = 64.dp)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        // El menú comparte la columna del importe, dejando espacio para las iniciales.
        val amountColumnWidth = maxOf(amountWidth, tagWidth)
        val layout = movementRowLayout(maxWidth.value, amountColumnWidth.value)
        val stacked = layout == MovementRowLayout.STACKED
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = if (stacked) Alignment.Top else Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Avatar(label = movement.avatarLabel, muted = excluded)
            Column(modifier = Modifier.weight(1f)) {
                MovementDescription(movement)
                if (stacked) {
                    Spacer(Modifier.height(8.dp))
                    MovementAmount(movement, amount, actions = actions)
                }
            }
            if (!stacked) {
                MovementAmount(movement, amount, horizontalAlignment = Alignment.End, actions = actions)
            }
        }
    }
}

@Composable
private fun MovementDescription(movement: MovementUi) {
    Text(
        text = movement.title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
    movement.supportingLabel?.let { label ->
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
    Spacer(Modifier.height(2.dp))
    Text(
        text = movement.dateTimeLabel,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    val account = listOfNotNull(movement.accountLabel, movement.mediumLabel).distinct().joinToString(" · ")
    if (account.isNotBlank()) Text(account, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 2, overflow = TextOverflow.Ellipsis)
}

@Composable
private fun MovementAmount(
    movement: MovementUi,
    amount: String,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    actions: (@Composable () -> Unit)? = null,
) {
    Column(horizontalAlignment = horizontalAlignment) {
        Text(
            text = amount,
            style = MoneyTextStyle,
            color = if (movement.isExcluded) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
        Spacer(Modifier.height(4.dp))
        ClassificationTag(movement.classification)
        actions?.invoke()
    }
}

/** Métrica pequeña dentro de una tarjeta. */
@Composable
fun MetricColumn(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/** Etiqueta en mayúsculas y monoespaciada de sección. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

/** Fila informativa de solo lectura (ajustes y detalle). */
@Composable
fun InfoRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge,
            color = valueColor,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
        )
    }
}
