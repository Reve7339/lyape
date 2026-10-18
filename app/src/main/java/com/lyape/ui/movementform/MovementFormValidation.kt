package com.lyape.ui.movementform

import com.lyape.ui.format.AmountParse
import com.lyape.ui.format.instantOf
import com.lyape.ui.format.parseAmountCents
import com.lyape.ui.format.parseDateInput
import com.lyape.ui.format.parseTimeInput
import java.time.Instant
import java.time.ZoneId

/** Tipo de movimiento que registra el formulario. Un ajuste siempre exige motivo. */
enum class MovementFormKind(val needsReason: Boolean) {
    PAYMENT(false),
    ADJUSTMENT(true),
}

/** Signo de un ajuste. El monto se escribe en positivo y el signo se elige aparte. */
enum class AdjustmentSign { POSITIVE, NEGATIVE }

data class MovementFormValues(
    val kind: MovementFormKind,
    val amountText: String,
    val dateText: String,
    val timeText: String,
    val labelText: String,
    val adjustmentSign: AdjustmentSign = AdjustmentSign.POSITIVE,
)

/** True cuando el formulario difiere de los valores con los que inició. */
fun MovementFormValues.hasChangesFrom(initial: MovementFormValues): Boolean = this != initial

private const val FORM_VALUES_COUNT = 6

/** Serializa el formulario para sobrevivir a la recreación de la actividad. */
internal fun MovementFormValues.toSavedValues(): List<String> = listOf(
    kind.name,
    amountText,
    dateText,
    timeText,
    labelText,
    adjustmentSign.name,
)

/** Reconstruye el formulario guardado. Devuelve `null` si el contenido no es válido. */
internal fun movementFormValuesFromSaved(saved: List<String>): MovementFormValues? {
    if (saved.size != FORM_VALUES_COUNT) return null
    val kind = runCatching { MovementFormKind.valueOf(saved[0]) }.getOrNull() ?: return null
    val sign = runCatching { AdjustmentSign.valueOf(saved[5]) }.getOrNull() ?: return null
    return MovementFormValues(
        kind = kind,
        amountText = saved[1],
        dateText = saved[2],
        timeText = saved[3],
        labelText = saved[4],
        adjustmentSign = sign,
    )
}

/**
 * Resultado de validar un formulario.
 *
 * Los mensajes viven junto a cada campo; [amountCents] y [occurredAt] solo se completan cuando el
 * campo correspondiente es válido. El monto ya viene con signo: negativo para un ajuste negativo.
 */
data class MovementFormResult(
    val amountError: String? = null,
    val dateError: String? = null,
    val timeError: String? = null,
    val labelError: String? = null,
    val amountCents: Long? = null,
    val occurredAt: Instant? = null,
    val label: String? = null,
) {
    val hasErrors: Boolean
        get() = amountError != null || dateError != null || timeError != null || labelError != null
}

/** Validación de formularios, pura y sin Android, para poder probarla fuera de la interfaz. */
object MovementFormValidator {

    fun validate(
        values: MovementFormValues,
        zone: ZoneId = ZoneId.systemDefault(),
    ): MovementFormResult {
        val amountError: String?
        var amountCents: Long? = null
        when (val parsed = parseAmountCents(values.amountText)) {
            AmountParse.Empty -> {
                amountError = "Ingresa un monto."
            }

            AmountParse.Malformed -> {
                amountError = "Usa solo números, por ejemplo 35.50."
            }

            AmountParse.TooLarge -> {
                amountError = "El monto es demasiado grande."
            }

            is AmountParse.Valid -> if (parsed.cents == 0L) {
                amountError = "El monto debe ser mayor que cero."
            } else {
                amountError = null
                amountCents = if (
                    values.kind == MovementFormKind.ADJUSTMENT &&
                    values.adjustmentSign == AdjustmentSign.NEGATIVE
                ) {
                    -parsed.cents
                } else {
                    parsed.cents
                }
            }
        }

        val date = parseDateInput(values.dateText)
        val time = parseTimeInput(values.timeText)
        val label = values.labelText.trim().takeIf { it.isNotEmpty() }

        return MovementFormResult(
            amountError = amountError,
            dateError = if (date == null) "Usa el formato dd/mm/aaaa." else null,
            timeError = if (time == null) "Usa el formato hh:mm." else null,
            labelError = if (values.kind.needsReason && label == null) {
                "El ajuste necesita un motivo."
            } else {
                null
            },
            amountCents = amountCents,
            occurredAt = if (date != null && time != null) instantOf(date, time, zone) else null,
            label = label,
        )
    }
}
