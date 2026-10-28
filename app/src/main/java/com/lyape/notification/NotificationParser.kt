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
 * Yape solo se interpreta cuando el texto contiene una frase explícita de **recepción**. Tener un
 * monto, un título genérico o el nombre del remitente no basta: una notificación de envío, saldo,
 * promoción, fallo o negación se descarta completa. Los patrones de remitente son provisionales;
 * los formatos confirmados y los sintéticos se distinguen en las pruebas.
 *
 * La regla es deliberadamente conservadora: ante dos operaciones, dos remitentes contradictorios,
 * un token monetario inválido o un monto no positivo, no se elige un importe al azar.
 */
object NotificationParser {

    fun parse(input: NotificationInput): ParseResult {
        val combined = combinedText(input)
        if (REJECTED_CONTEXT.containsMatchIn(combined)) return ParseResult.Unknown

        val receipt = receiptEvidence(input) ?: return ParseResult.Unknown
        val amountCents = findAmountCents(combined, receipt.start) ?: return ParseResult.Unknown
        if (amountCents <= 0L) return ParseResult.Unknown

        val normalizedText = NotificationFingerprint.normalizeForFingerprint(combined)
        return ParseResult.Parsed(
            amountCents = amountCents,
            senderName = receipt.senderName,
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
     * Evidencia de recepción por campo.
     *
     * La frase positiva debe describir un cobro, no solo contener una palabra. Cada campo aporta
     * una operación como máximo. Los campos text/bigText pueden repetir la misma operación o
     * completarla; dos operaciones sin remitente que difieren en su frase también son ambiguas y
     * se rechazan aunque compartan el importe. Un título genérico sin monto ni remitente no cuenta
     * como una segunda operación.
     */
    private fun receiptEvidence(input: NotificationInput): ReceiptEvidence? {
        val fields = listOfNotNull(input.title, input.text, input.expandedText)
            .filter { it.isNotBlank() }
        val evidence = mutableListOf<FieldEvidence>()
        var firstStart = Int.MAX_VALUE
        var fieldOffset = 0

        for (field in fields) {
            val matches = RECEIPT_PATTERNS.flatMap { it.findAll(field).toList() }
                .sortedBy { it.range.first }
            if (matches.size > 1) return null
            val match = matches.singleOrNull()
            if (match != null) {
                if (isNegated(field, match.range.first)) return null
                val senderName = findSenderName(field)
                val amountAfterReceipt = AMOUNT_PATTERN.find(field, match.range.last + 1)
                firstStart = minOf(firstStart, fieldOffset + match.range.first)
                evidence += FieldEvidence(
                    senderName = senderName,
                    receiptLine = NotificationFingerprint.normalizeForFingerprint(
                        lineAt(field, match.range.first),
                    ),
                    operational = senderName != null || amountAfterReceipt != null,
                    minimalReceipt = amountAfterReceipt?.let { amount ->
                        isMinimalReceipt(field, match.range, amount.range)
                    } ?: false,
                )
            }
            fieldOffset += field.length + 1
        }

        if (evidence.isEmpty()) return null

        val operational = evidence.filter { it.operational }
        val named = operational.filter { it.senderName != null }
        if (named.mapNotNull { it.senderName }.distinct().size > 1) return null

        // Repetición idéntica text/bigText: válida. Dos frases distintas con el mismo remitente
        // siguen siendo ambiguas; la línea de la recepción debe coincidir.
        if (named.size > 1 && named.map { it.receiptLine }.distinct().size > 1) return null

        // Sin remitente no hay una señal adicional para unir las dos evidencias. Solo se acepta
        // la repetición de la misma frase de recepción (puede cambiar el detalle de otra línea).
        if (named.isEmpty() && operational.map { it.receiptLine }.distinct().size > 1) return null

        // Evidencia mixta: un campo con remitente reconocido y otro sin él. El segundo no puede
        // aportar ninguna otra palabra operativa (por ejemplo, otro nombre no extraíble): elegir el
        // único nombre reconocible o compartir importe no demuestra que sea la misma operación.
        // Sí se admiten los complementos que solo repiten la frase de cobro y el importe.
        val unnamed = operational.filter { it.senderName == null }
        if (named.isNotEmpty() && unnamed.any { !it.minimalReceipt }) return null

        return ReceiptEvidence(
            start = firstStart,
            senderName = named.firstOrNull()?.senderName,
        )
    }

    /** La negación se evalúa junto a la frase positiva, no como una lista global de casos. */
    private fun isNegated(text: String, matchStart: Int): Boolean {
        val from = (matchStart - NEGATION_LOOKBEHIND).coerceAtLeast(0)
        return NEGATION_BEFORE.containsMatchIn(text.substring(from, matchStart))
    }

    private fun lineAt(text: String, index: Int): String {
        val lineStart = text.lastIndexOf('\n', index).let { if (it < 0) 0 else it + 1 }
        val lineEnd = text.indexOf('\n', index).let { if (it < 0) text.length else it }
        return text.substring(lineStart, lineEnd).trim()
    }

    /**
     * Un complemento sin remitente solo puede repetir la frase de recepción, el importe y
     * conectores genéricos. Cualquier otro término o dígito queda sin atribuir: no se une la
     * operación por compartir importe ni se elige el único nombre reconocible.
     */
    private fun isMinimalReceipt(text: String, receipt: IntRange, amount: IntRange): Boolean {
        val residual = text.removeRange(amount).removeRange(receipt)
        if (residual.any { it.isDigit() }) return false
        return LEXICAL_WORD.findAll(residual).all {
            it.value.lowercase() in COMPLEMENTARY_RECEIPT_WORDS
        }
    }

    /**
     * Devuelve un único monto no ambiguo y posterior a la frase de recepción.
     *
     * El mismo valor puede aparecer repetido entre título, texto y texto expandido. Cualquier
     * token monetario inválido, o más de un monto distinto, invalida la notificación completa.
     */
    private fun findAmountCents(combined: String, receiptStart: Int): Long? {
        val matches = AMOUNT_PATTERN.findAll(combined).toList()
        if (matches.isEmpty()) return null

        val amounts = LinkedHashSet<Long>()
        for (match in matches) {
            if (match.range.first < receiptStart) return null
            val token = normalizeToken(match.groupValues[1]) ?: return null
            val cents = PenAmount.centsOrNull(token) ?: return null
            amounts += cents
            if (amounts.size > 1) return null
        }
        return amounts.singleOrNull()
    }

    /**
     * Captura el token completo (no solo el prefijo numérico) y retira puntuación de cierre
     * legítima. Así `10.00abc` y `1e3` no se reinterpretan como `10` o `1`.
     */
    private fun normalizeToken(raw: String): String? {
        val trimmed = raw.trim().trimEnd(*TOKEN_TRAILING_PUNCTUATION)
        return trimmed.takeIf { it.isNotEmpty() }
    }

    /**
     * Extrae el remitente solo de frases explícitas, una por línea. Si el nombre y la frase no
     * comparten línea, se deja en `null` para no atribuir un remitente equivocado.
     */
    private fun findSenderName(text: String): String? {
        for (rawLine in text.split('\n')) {
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

    private const val MIN_NAME_LENGTH = 2
    private const val MAX_NAME_LENGTH = 60
    private const val NEGATION_LOOKBEHIND = 48

    /**
     * Palabras que no añaden una segunda operación ni identifican a otra persona. La lista es
     * deliberadamente corta: cualquier otro término en un campo sin remitente invalida la mezcla.
     */
    private val COMPLEMENTARY_RECEIPT_WORDS = setOf(
        "de", "del", "el", "en", "la", "las", "los", "por", "un", "una",
    )
    private val LEXICAL_WORD = Regex("""\p{L}+""")

    /** Espacios normales, duros y finos usados por las notificaciones. */
    private const val WS = """[\s\u00A0\u2007\u202F]"""

    private data class FieldEvidence(
        val senderName: String?,
        val receiptLine: String,
        val operational: Boolean,
        val minimalReceipt: Boolean,
    )

    private data class ReceiptEvidence(
        val start: Int,
        val senderName: String?,
    )

    /** Prefijo monetario obligatorio: `S/` o `S/.`, con separación opcional. */
    private val AMOUNT_PATTERN = Regex("""(?<![A-Za-z0-9])S/\.?\s*([^\s]+)""")

    /**
     * Frases que describen un cobro. `recibiste` exige un objeto monetario o el importe pegado:
     * no basta con la palabra suelta, que también aparece en cupones y descuentos.
     */
    private val RECEIPT_PATTERNS = listOf(
        Regex(
            """\brecibiste$WS+(?:(?:un|una|el|la|tu|su)$WS+)?(?:pago|abono|dep[oó]sito|transferencia)\b""",
            RegexOption.IGNORE_CASE,
        ),
        Regex("""\brecibiste$WS+(?=S/)""", RegexOption.IGNORE_CASE),
        Regex("""\bte$WS+pag[oó](?=$WS|$|[.,;:!?])""", RegexOption.IGNORE_CASE),
        Regex("""\bte$WS+envi[oó]$WS+un$WS+pago$WS+por\b""", RegexOption.IGNORE_CASE),
        Regex("""\bte$WS+lleg[oó]$WS+un$WS+pago\b""", RegexOption.IGNORE_CASE),
        Regex("""\bpago$WS+recibido\b""", RegexOption.IGNORE_CASE),
    )

    /**
     * Negación inmediatamente anterior a la frase positiva. Se evalúa por campo y junto al
     * patrón admitido, de modo que cada nueva frase de cobro hereda la misma protección.
     */
    private val NEGATION_BEFORE = Regex(
        """(?:\bno|\bnunca|\bjam[áa]s|\ba[úu]n$WS+no|\btodav[íi]a$WS+no|""" +
            """\bno$WS+(?:hay|existe))(?:$WS+(?:se|te|me|nos))?$WS*$""",
        RegexOption.IGNORE_CASE,
    )

    /**
     * Contextos que no son un cobro. Incluye envíos, saldo, promociones, fallos, negaciones y
     * resúmenes. No se usa como única condición: la frase positiva sigue siendo obligatoria.
     */
    private val REJECTED_CONTEXT = Regex(
        """\b(?:pagaste|enviaste|has\s+pagado|has\s+enviado|saldos?|promoci[oó]n(?:es)?|""" +
            """promos?|fall[oó]|fallid[oa]|rechazad[oa]|pendiente|resúmenes?|resumen|""" +
            """negad[oa]|anulad[oa]|cancelad[oa]|revertid[oa]|devoluci[oó]n|""" +
            """no\s+te\s+pag[oó]|no\s+te\s+envi[oó]|no\s+se\s+pudo|no\s+recibiste)""" +
            """(?=\s|$|[.,;:!?])""",
        RegexOption.IGNORE_CASE,
    )

    private val SENDER_PATTERNS = listOf(
        Regex("""^(.{2,60}?)\s+te\s+pag[oó](?=\s|$)""", RegexOption.IGNORE_CASE),
        Regex(
            """^(.{2,60}?)\s+te\s+envi[oó]\s+un\s+pago\s+por(?=\s|$)""",
            RegexOption.IGNORE_CASE,
        ),
        Regex("""^pago\s+de\s+(.{2,60}?)\s*[:\-]""", RegexOption.IGNORE_CASE),
    )

    private val TOKEN_TRAILING_PUNCTUATION = charArrayOf(
        '.', ',', ';', ':', '!', '?', ')', ']', '}', '»', '”', '"',
    )
}
