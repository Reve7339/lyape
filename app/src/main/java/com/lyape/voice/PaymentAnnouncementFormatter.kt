package com.lyape.voice

/**
 * Construye el texto que se pronuncia para un pago, con el medio y el monto en palabras.
 *
 * Ejemplos:
 * - `format(YAPE, 100)` -> `"Yape: un sol"`
 * - `format(PLIN, 1050)` -> `"Plin: diez soles con cincuenta céntimos"`
 * - `format(YAPE, 10)` -> `"Yape: diez céntimos"`
 * - `format(PLIN, 1)` -> `"Plin: un céntimo"`
 *
 * Devuelve `null` si el importe no es estrictamente positivo. Si el monto en soles es
 * excepcionalmente grande, devuelve una frase corta que no truncaría ni inventaría el monto
 * (el detalle completo está visible en Movimientos).
 *
 * Solo se recibe y usa el medio y el monto en céntimos; ningún nombre, código, cuenta ni
 * notificación completa puede llegar aquí.
 */
object PaymentAnnouncementFormatter {

    /** Límite máximo de soles para pronunciar el monto en palabras. */
    private const val MAX_WORDED_SOLES = 999_999_999_999L

    fun format(method: PaymentMethod, amountCents: Long): String? {
        if (amountCents <= 0) return null
        val soles = amountCents / 100
        if (soles > MAX_WORDED_SOLES) {
            return when (method) {
                PaymentMethod.YAPE -> "Yape: importe alto"
                PaymentMethod.PLIN -> "Plin: importe alto"
                PaymentMethod.UNKNOWN -> null
            }
        }
        val label = when (method) {
            PaymentMethod.YAPE -> "Yape"
            PaymentMethod.PLIN -> "Plin"
            PaymentMethod.UNKNOWN -> return null
        }
        return "$label: ${formatAmountInSoles(amountCents)}"
    }

    private fun formatAmountInSoles(amountCents: Long): String {
        val soles = amountCents / 100
        val cents = (amountCents % 100).toInt()

        val solesText: String? = if (soles == 0L) {
            null
        } else if (soles == 1L) {
            "un sol"
        } else if (soles % 1_000_000L == 0L && soles >= 1_000_000L) {
            "${apocopateUno(numberToWords(soles))} de soles"
        } else {
            "${apocopateUno(numberToWords(soles))} soles"
        }

        val centsText: String? = if (cents == 0) null else amountUnitWords(cents.toLong(), "céntimo", "céntimos")

        return listOfNotNull(solesText, centsText).joinToString(" con ")
    }

    /**
     * Convierte 0..MAX_WORDED_SOLES a palabras en español.
     *
     * Escalas cubiertas hasta billones (long scale): millones, mil millones y billones.
     */
    private fun numberToWords(value: Long): String {
        require(value in 0..MAX_WORDED_SOLES)
        if (value == 0L) return "cero"

        val groups = mutableListOf<String>()
        val thousandsOfMillions = (value / 1_000_000_000L).toInt()
        val millions = ((value / 1_000_000L) % 1000L).toInt()
        val thousands = ((value / 1_000L) % 1000L).toInt()
        val units = (value % 1000L).toInt()

        if (thousandsOfMillions > 0) {
            groups += if (thousandsOfMillions == 1) {
                "mil millones"
            } else {
                "${apocopateUno(under1000(thousandsOfMillions))} mil millones"
            }
        }
        if (millions > 0) {
            groups += if (millions == 1) {
                "un millón"
            } else {
                "${apocopateUno(under1000(millions))} millones"
            }
        }
        if (thousands > 0) {
            groups += if (thousands == 1) {
                "mil"
            } else {
                "${apocopateUno(under1000(thousands))} mil"
            }
        }
        if (units > 0) {
            groups += under1000(units)
        }

        return groups.joinToString(" ")
    }

    private fun under1000(value: Int): String {
        require(value in 0..999)
        if (value == 0) return "cero"
        if (value == 100) return "cien"

        val hundreds = value / 100
        val remainder = value % 100

        val parts = mutableListOf<String>()
        if (hundreds > 0) {
            parts += HUNDREDS[hundreds]
        }
        if (remainder > 0) {
            parts += under100(remainder)
        }

        return parts.joinToString(" ")
    }

    private fun under100(value: Int): String {
        require(value in 1..99)
        return when (value) {
            1 -> "uno"
            2 -> "dos"
            3 -> "tres"
            4 -> "cuatro"
            5 -> "cinco"
            6 -> "seis"
            7 -> "siete"
            8 -> "ocho"
            9 -> "nueve"
            10 -> "diez"
            11 -> "once"
            12 -> "doce"
            13 -> "trece"
            14 -> "catorce"
            15 -> "quince"
            16 -> "dieciséis"
            17 -> "diecisiete"
            18 -> "dieciocho"
            19 -> "diecinueve"
            20 -> "veinte"
            in 21..29 -> "veintiuno".let {
                when (value) {
                    21 -> "veintiuno"
                    22 -> "veintidós"
                    23 -> "veintitrés"
                    24 -> "veinticuatro"
                    25 -> "veinticinco"
                    26 -> "veintiséis"
                    27 -> "veintisiete"
                    28 -> "veintiocho"
                    29 -> "veintinueve"
                    else -> it
                }
            }
            else -> {
                val ten = TENs[value / 10]
                val unit = value % 10
                if (unit == 0) ten else "$ten y ${UNITS[unit]}"
            }
        }
    }

    private fun amountUnitWords(value: Long, singular: String, plural: String): String {
        val label = if (value == 1L) "un" else apocopateUno(numberToWords(value))
        val noun = if (value == 1L) singular else plural
        return "$label $noun"
    }

    /**
     * Adapta "uno" a la forma corta "un" delante de sustantivo masculino (ej. "un sol",
     * "veintiún soles", "ciento un sol", "mil un sol").
     */
    private fun apocopateUno(words: String): String = when {
        words == "uno" -> "un"
        words.endsWith("veintiuno") -> words.dropLast(9) + "veintiún"
        words.endsWith(" y uno") -> words.dropLast(5) + " y un"
        words.endsWith(" uno") -> words.dropLast(4) + " un"
        else -> words
    }

    private val HUNDREDS = arrayOf(
        "", "ciento", "doscientos", "trescientos", "cuatrocientos", "quinientos",
        "seiscientos", "setecientos", "ochocientos", "novecientos",
    )

    private val TENs = arrayOf("", "", "", "treinta", "cuarenta", "cincuenta", "sesenta", "setenta", "ochenta", "noventa")

    private val UNITS = arrayOf("", "uno", "dos", "tres", "cuatro", "cinco", "seis", "siete", "ocho", "nueve")
}
