package com.lyape.ui.format

/**
 * Formatea céntimos (Long) como soles sin usar Float ni Double.
 *
 * Ejemplos:
 * - `formatCents(128450)` -> `"S/ 1,284.50"`
 * - `formatCents(-800)` -> `"- S/ 8.00"`
 * - `formatSignedCents(3500)` -> `"+ S/ 35.00"`
 */
fun formatCents(cents: Long): String {
    val negative = cents < 0
    // Separa primero unidades y céntimos: ambos cocientes pueden convertirse a positivo
    // incluso cuando `cents` es Long.MIN_VALUE, cuyo valor absoluto no cabe en un Long.
    val units = cents / 100
    val remainder = cents % 100
    val absoluteUnits = if (units < 0) -units else units
    val absoluteRemainder = if (remainder < 0) -remainder else remainder
    val body = "S/ ${groupThousands(absoluteUnits)}.${twoDigits(absoluteRemainder)}"
    return if (negative) "- $body" else body
}

/** Añade signo explícito para importes de listas. El cero no lleva signo. */
fun formatSignedCents(cents: Long): String = when {
    cents > 0L -> "+ ${formatCents(cents)}"
    cents < 0L -> formatCents(cents)
    else -> formatCents(0L)
}

private fun twoDigits(value: Long): String = value.toString().padStart(2, '0')

private fun groupThousands(units: Long): String {
    val digits = units.toString()
    if (digits.length <= 3) return digits

    return buildString {
        val firstGroup = digits.length % 3
        if (firstGroup > 0) append(digits, 0, firstGroup)
        var index = firstGroup
        while (index < digits.length) {
            if (isNotEmpty()) append(',')
            append(digits, index, index + 3)
            index += 3
        }
    }
}
