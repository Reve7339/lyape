package com.lyape.ui.model

import com.lyape.domain.Movement
import com.lyape.domain.MovementClassification
import com.lyape.domain.MovementSource
import com.lyape.domain.movementTitle
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Vista de solo lectura de un [Movement] para la lista y el detalle.
 *
 * El monto se conserva en céntimos (`Long`); el formato se aplica al pintar, no aquí.
 */
data class MovementUi(
    val id: String,
    val title: String,
    val amountCents: Long,
    val classification: MovementClassification,
    val originLabel: String,
    val supportingLabel: String?,
    val description: String?,
    val timeLabel: String,
    val dateTimeLabel: String,
    val avatarLabel: String,
    val reviewed: Boolean,
    val previousClassification: MovementClassification?,
) {
    val isExcluded: Boolean get() = classification == MovementClassification.EXCLUDED
}

private val TIME_FORMATTER = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)
private val DATE_LABEL_FORMATTER =
    DateTimeFormatter.ofPattern("d MMM yyyy", Locale.forLanguageTag("es-PE"))

fun Movement.toUi(zone: ZoneId = ZoneId.systemDefault()): MovementUi {
    val localDateTime = occurredAt.atZone(zone)
    val timeLabel = TIME_FORMATTER.format(localDateTime).lowercase(Locale.ROOT)
    return MovementUi(
        id = id.toString(),
        title = movementTitle(senderName, source),
        amountCents = amountCents,
        classification = classification,
        originLabel = sourceLabel(source),
        supportingLabel = if (source == MovementSource.ADJUSTMENT) {
            adjustmentReason?.trim()?.takeIf { it.isNotEmpty() }
        } else {
            null
        },
        description = description?.trim()?.takeIf { it.isNotEmpty() },
        timeLabel = timeLabel,
        dateTimeLabel = "${DATE_LABEL_FORMATTER.format(localDateTime)} · $timeLabel",
        avatarLabel = avatarLabelFor(senderName),
        reviewed = reviewed,
        previousClassification = previousClassification,
    )
}

fun sourceLabel(source: MovementSource): String = when (source) {
    MovementSource.NOTIFICATION -> "Detectado"
    MovementSource.MANUAL -> "Manual"
    MovementSource.ADJUSTMENT -> "Ajuste"
}

fun classificationLabel(classification: MovementClassification): String = when (classification) {
    MovementClassification.BUSINESS -> "Negocio"
    MovementClassification.PERSONAL -> "Personal"
    MovementClassification.EXCLUDED -> "Anulado"
}

private fun avatarLabelFor(senderName: String?): String {
    val name = senderName?.trim().orEmpty()
    if (name.isEmpty()) return "+"
    val parts = name.split(' ').filter { it.isNotBlank() }
    return if (parts.size >= 2) {
        "${parts[0].first().uppercaseChar()}${parts[1].first().uppercaseChar()}"
    } else {
        name.take(2).uppercase(Locale.ROOT)
    }
}
