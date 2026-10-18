package com.lyape.ui.format

/** Resultado de interpretar el monto escrito por el usuario. Siempre céntimos en `Long`. */
sealed interface AmountParse {
    data class Valid(val cents: Long) : AmountParse
    data object Empty : AmountParse
    data object Malformed : AmountParse
    data object TooLarge : AmountParse
}

/**
 * Interpreta un importe escrito a mano sin usar `Float` ni `Double`.
 *
 * Reglas, deliberadamente estrictas y sin ambigüedad:
 * - Solo dígitos y un único separador decimal, `.` o `,`, con 1 o 2 decimales.
 * - `"35"`, `"35.5"`, `"35.50"` y `"35,50"` son válidos; `"1,284.50"` y `"35.505"` no lo son.
 * - El resultado es siempre positivo: el signo de un ajuste se elige aparte.
 */
fun parseAmountCents(raw: String): AmountParse {
    val text = raw.trim().removePrefix("S/").removePrefix("s/").trim()
    if (text.isEmpty()) return AmountParse.Empty

    var separatorIndex = -1
    text.forEachIndexed { index, char ->
        when {
            char.isDigit() -> Unit
            char == '.' || char == ',' -> {
                if (separatorIndex >= 0) return AmountParse.Malformed
                separatorIndex = index
            }
            else -> return AmountParse.Malformed
        }
    }

    val unitsText = if (separatorIndex < 0) text else text.substring(0, separatorIndex)
    val centsText = if (separatorIndex < 0) "" else text.substring(separatorIndex + 1)

    if (centsText.length > 2) return AmountParse.Malformed
    if (unitsText.isEmpty() && centsText.isEmpty()) return AmountParse.Empty

    val units = if (unitsText.isEmpty()) 0L else unitsText.toLongOrNull() ?: return AmountParse.TooLarge
    val cents = when (centsText.length) {
        0 -> 0L
        1 -> centsText.toLong() * 10
        else -> centsText.toLong()
    }
    // Comprobar la suma completa evita que los centimos desborden cuando las unidades son
    // exactamente Long.MAX_VALUE / 100 (por ejemplo, ...58.08).
    if (units > (Long.MAX_VALUE - cents) / 100) return AmountParse.TooLarge

    return AmountParse.Valid(units * 100 + cents)
}

/** Convierte céntimos a un texto editable (`3500` → `"35.00"`) sin usar `Float` ni `Double`. */
fun formatAmountInput(cents: Long): String {
    val units = cents / 100
    val remainder = cents % 100
    val absoluteUnits = if (units < 0) -units else units
    val absoluteRemainder = if (remainder < 0) -remainder else remainder
    return "$absoluteUnits.${absoluteRemainder.toString().padStart(2, '0')}"
}
