package com.lyape.notification

/**
 * Conversión exacta de un token monetario PEN a céntimos.
 *
 * Se extrajo del parser de Yape para que todos los proveedores usen la misma regla, sin `Float`
 * ni `Double`. No decide si un texto es un ingreso: solo interpreta el número. El prefijo
 * monetario y el contexto los valida cada parser.
 *
 * Regla conservadora, orientada a la notación PEN: el punto es **siempre** separador decimal
 * (uno o dos dígitos) y la coma puede ser separador de miles (grupos exactos de tres) o
 * separador decimal, cuando no es ambigua. Cualquier otra forma, o un valor que desborde
 * `Long`, se rechaza; en particular se rechazan `1.284` y `35.505`.
 */
internal object PenAmount {

    fun centsOrNull(rawToken: String): Long? {
        val token = rawToken.trim().trimEnd('.', ',')
        if (token.isEmpty()) return null

        val parts = when {
            THOUSANDS_DECIMAL.matches(token) -> token.replace(",", "").split('.', limit = 2)
            COMMA_DECIMAL.matches(token) -> token.split(',', limit = 2)
            POINT_DECIMAL.matches(token) -> token.split('.', limit = 2)
            else -> return null
        }

        val integerPart = parts[0]
        if (integerPart.isEmpty()) return null
        val decimalPart = parts.getOrElse(1) { "" }
        val centsDigits = integerPart + decimalPart.padEnd(2, '0').take(2)
        // `toLongOrNull` rechaza tanto ceros sobrantes mal formados como el desbordamiento.
        return centsDigits.toLongOrNull()
    }

    /**
     * Miles con coma, decimales con punto: `1,284` o `1,284.50`.
     *
     * Exige al menos un grupo de miles y prohíbe ceros iniciales; así `0,005` no puede
     * reinterpretarse como cinco soles cuando en realidad es un decimal inválido.
     */
    private val THOUSANDS_DECIMAL = Regex("""^[1-9]\d{0,2}(,\d{3})+(\.\d{1,2})?$""")

    /** Decimales con coma: `16,50`. */
    private val COMMA_DECIMAL = Regex("""^\d+(,\d{1,2})?$""")

    /** Sin separador de miles, decimales opcionales con punto: `1234` o `1234.50`. */
    private val POINT_DECIMAL = Regex("""^\d+(\.\d{1,2})?$""")
}
