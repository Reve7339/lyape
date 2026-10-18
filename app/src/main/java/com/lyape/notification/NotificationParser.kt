package com.lyape.notification

import java.time.Instant

/**
 * Datos mínimos de una notificación, sin dependencias de Android.
 *
 * El bloque 4 construirá esta entrada a partir de un `StatusBarNotification`; aquí solo se
 * modelan los campos que el parser necesita.
 */
data class NotificationInput(
    val packageName: String,
    val key: String?,
    val title: String,
    val text: String,
    val expandedText: String? = null,
    val postedAt: Instant,
)

/**
 * Resultado explícito del parser.
 *
 * Distingue una notificación interpretada de un formato desconocido. No existe un estado
 * intermedio con monto o remitente inventados: si algo no se puede leer con seguridad, el
 * resultado es [Unknown] y el bloque 4 lo ignora.
 */
sealed interface ParseResult {

    /** Notificación interpretada. */
    data class Parsed(
        val amountCents: Long,
        val senderName: String?,
        val fingerprint: String,
    ) : ParseResult

    /** Formato no reconocido o monto no interpretable. Nunca altera totales. */
    data object Unknown : ParseResult
}

/**
 * Parser de notificaciones, puro y aislado de Android (sin `Context`, Room ni APIs del sistema).
 *
 * ATENCIÓN: los patrones son **provisionales**. No están validados contra notificaciones reales de
 * Yape; deben refinarse con entre 3 y 5 muestras anonimizadas y el identificador de paquete
 * confirmado. Son deliberadamente conservadores:
 * exigen el prefijo monetario `S/` y no inventan monto ni remitente.
 */
object NotificationParser {

    fun parse(input: NotificationInput): ParseResult {
        val amountCents = findAmountCents(input) ?: return ParseResult.Unknown
        val normalizedText = NotificationFingerprint.normalizeForFingerprint(combinedText(input))
        return ParseResult.Parsed(
            amountCents = amountCents,
            senderName = findSenderName(input),
            fingerprint = NotificationFingerprint.compute(
                packageName = input.packageName,
                amountCents = amountCents,
                normalizedText = normalizedText,
            ),
        )
    }

    private fun combinedText(input: NotificationInput): String =
        listOfNotNull(input.title, input.text, input.expandedText)
            .filter { it.isNotBlank() }
            .joinToString("\n")

    /**
     * Devuelve un único monto no ambiguo.
     *
     * El mismo valor puede aparecer repetido entre título, texto y texto expandido. Si aparece
     * cualquier token monetario inválido o más de un monto distinto, se rechaza la notificación
     * completa para no elegir arbitrariamente un importe.
     */
    private fun findAmountCents(input: NotificationInput): Long? {
        val matches = AMOUNT_PATTERN.findAll(combinedText(input)).toList()
        if (matches.isEmpty()) return null

        val amounts = LinkedHashSet<Long>()
        for (match in matches) {
            val cents = amountCentsOrNull(match.groupValues[1]) ?: return null
            amounts += cents
            if (amounts.size > 1) return null
        }
        return amounts.singleOrNull()
    }

    /**
     * Extrae el remitente solo de frases explícitas, una por línea. Si el nombre y la frase no
     * comparten línea, se deja en `null` para no atribuir un remitente equivocado.
     */
    private fun findSenderName(input: NotificationInput): String? {
        for (rawLine in combinedText(input).split('\n')) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue
            for (pattern in SENDER_PATTERNS) {
                val match = pattern.find(line) ?: continue
                val name = match.groupValues[1].trim()
                if (isPlausibleName(name)) return name
            }
        }
        return null
    }

    private fun isPlausibleName(name: String): Boolean =
        name.length in MIN_NAME_LENGTH..MAX_NAME_LENGTH &&
            name.any { it.isLetter() } &&
            name.none { it.isDigit() }

    /**
     * Convierte el token numérico (ya sin el prefijo `S/`) a céntimos, sin `Float` ni `Double`.
     *
     * Regla conservadora, orientada a la notación PEN: el punto es **siempre** separador decimal
     * (uno o dos dígitos) y la coma puede ser separador de miles (grupos exactos de tres) o
     * separador decimal, cuando no es ambigua. Cualquier otra forma, o un valor que desborde
     * `Long`, se rechaza; en particular se rechazan `1.284` y `35.505`.
     */
    private fun amountCentsOrNull(rawToken: String): Long? {
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

    private const val MIN_NAME_LENGTH = 2
    private const val MAX_NAME_LENGTH = 60

    /** Prefijo monetario obligatorio: `S/` o `S/.`, con separación opcional. */
    private val AMOUNT_PATTERN = Regex("""(?<![A-Za-z0-9])S/\.?\s*([0-9][0-9.,]*)""")

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

    private val SENDER_PATTERNS = listOf(
        Regex("""^(.{2,60}?)\s+te\s+pag[oó](?=\s|$)""", RegexOption.IGNORE_CASE),
        Regex(
            """^(.{2,60}?)\s+te\s+envi[oó]\s+un\s+pago\s+por(?=\s|$)""",
            RegexOption.IGNORE_CASE,
        ),
        Regex("""^pago\s+de\s+(.{2,60}?)\s*[:\-]""", RegexOption.IGNORE_CASE),
    )
}
