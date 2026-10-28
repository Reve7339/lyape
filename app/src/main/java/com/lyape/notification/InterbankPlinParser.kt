package com.lyape.notification

/**
 * Parser provisional de ingresos Plin notificados por Interbank.
 *
 * Evidencia: una única transcripción visual (2026-10-25) con el remitente ficticio
 * «Persona Ejemplo» y el texto «Persona Ejemplo te ha plineado\nS/ 1.00». Una inspección
 * autorizada confirmó el paquete `pe.com.interbank.mobilebanking` y la versión 23.4/1870.
 * Siguen sin confirmarse los extras de Android, la clave, el grupo, el postTime, el formato y la
 * agrupación de entregas. La captura se habilitó como provisional para prueba del usuario.
 *
 * Reglas deliberadamente conservadoras:
 * - analiza por campos (título/texto/expandido) para no confundir una notificación repetida con
 *   dos operaciones; admite como máximo una frase de ingreso por campo y solo une campos cuando
 *   contienen exactamente la misma notificación;
 * - exige la frase de ingreso «te ha plineado»; no basta con que aparezca «S/»;
 * - exige un único importe PEN positivo, interpretado con [PenAmount] en céntimos `Long`;
 * - valida el token monetario completo: no recorta un sufijo ni acepta un número mal formado;
 * - exige que el importe aparezca después de la frase;
 * - rechaza frases negadas, fallos, envíos y resúmenes;
 * - el remitente es opcional y nunca se inventa.
 *
 * La deduplicación entre notificaciones Android y el formato técnico de Interbank siguen sin
 * validar. Una promoción que copiara exactamente la frase de ingreso completada no se distingue
 * con una sola muestra; se revisará cuando haya más evidencia.
 */
internal object InterbankPlinParser {

    fun parse(input: NotificationInput): ParseResult {
        val fields = listOfNotNull(input.title, input.text, input.expandedText)
            .filter { it.isNotBlank() }
        if (fields.isEmpty()) return ParseResult.Unknown

        val operations = mutableListOf<Operation>()
        val amounts = mutableListOf<AmountToken>()

        for ((fieldIndex, fieldText) in fields.withIndex()) {
            if (REJECTED_MARKERS.any { it.containsMatchIn(fieldText) }) {
                return ParseResult.Unknown
            }
            if (NEGATIVE_AMOUNT_MARKER.containsMatchIn(fieldText)) {
                return ParseResult.Unknown
            }

            val phraseMatches = INCOME_PHRASE.findAll(fieldText).toList()
            if (phraseMatches.size > 1) {
                // Dos frases en el mismo campo son dos operaciones, aunque compartan remitente
                // o importe. No se elige la primera ni se suman.
                return ParseResult.Unknown
            }

            for (match in AMOUNT_RUN.findAll(fieldText)) {
                val cents = amountCentsOrNull(match.groupValues[1])
                    ?: return ParseResult.Unknown
                amounts += AmountToken(
                    fieldIndex = fieldIndex,
                    start = match.range.first,
                    cents = cents,
                )
            }

            if (phraseMatches.size == 1) {
                val phrase = phraseMatches.single()
                operations += Operation(
                    fieldIndex = fieldIndex,
                    phraseStart = phrase.range.first,
                    senderName = findSenderName(fieldText),
                    normalizedText = NotificationFingerprint.normalizeForFingerprint(fieldText),
                )
            }
        }

        if (operations.isEmpty()) return ParseResult.Unknown

        val operation = operations.first()
        if (operations.any {
                it.senderName != operation.senderName ||
                    it.normalizedText != operation.normalizedText
            }
        ) {
            // Solo se admite la misma notificación repetida en otro campo. Un remitente, texto o
            // contenido distinto es ambiguo y no se resuelve por coincidencia de importe.
            return ParseResult.Unknown
        }

        if (amounts.isEmpty()) return ParseResult.Unknown

        val distinctAmounts = amounts.map { it.cents }.distinct()
        if (distinctAmounts.size != 1) return ParseResult.Unknown

        val amountCents = distinctAmounts.single()
        if (amountCents <= 0L) return ParseResult.Unknown

        // El primer token monetario debe estar después de la frase de la operación. Un importe en
        // un campo anterior (por ejemplo, el título) o antes de la frase es ambiguo, aunque
        // coincida con el importe del pago.
        val firstAmount = amounts.first()
        val firstAmountIsAfterPhrase =
            firstAmount.fieldIndex > operation.fieldIndex ||
                (firstAmount.fieldIndex == operation.fieldIndex &&
                    firstAmount.start > operation.phraseStart)
        if (!firstAmountIsAfterPhrase) return ParseResult.Unknown

        return ParseResult.Parsed(
            amountCents = amountCents,
            senderName = operation.senderName,
            fingerprint = NotificationFingerprint.compute(
                packageName = input.packageName,
                amountCents = amountCents,
                normalizedText = operation.normalizedText,
            ),
        )
    }

    /**
     * Extrae el remitente solo cuando comparte línea con la frase de ingreso y su forma es
     * plausible. Si hay etiquetas, dígitos o puntuación no admitida, se deja en `null`.
     */
    private fun findSenderName(fieldText: String): String? {
        val match = SENDER_PATTERN.find(fieldText) ?: return null
        val name = match.groupValues[1]
            .trim()
            .replace(WHITESPACE, " ")
        return name.takeIf(::isPlausibleSenderName)
    }

    private fun isPlausibleSenderName(name: String): Boolean =
        name.length in MIN_NAME_LENGTH..MAX_NAME_LENGTH &&
            name.any { it.isLetter() } &&
            name.all { character ->
                character.isLetter() ||
                    character.isWhitespace() ||
                    character == '\u00A0' ||
                    character == '\u2007' ||
                    character == '\u202F' ||
                    character in ".*'-"
            }

    private data class AmountToken(
        val fieldIndex: Int,
        val start: Int,
        val cents: Long,
    )

    private data class Operation(
        val fieldIndex: Int,
        val phraseStart: Int,
        val senderName: String?,
        val normalizedText: String,
    )

    private const val MIN_NAME_LENGTH = 2
    private const val MAX_NAME_LENGTH = 60

    /**
     * Espacios horizontales, incluidos el duro y los finos. No incluye saltos de línea: la frase
     * de ingreso y el remitente deben compartir línea en la transcripción visual.
     */
    private const val H = "[ \\t\\u00A0\\u2007\\u202F]"

    /**
     * Captura el token completo que sigue a `S/` hasta el siguiente espacio. Después se retira
     * solo la puntuación final admitida y [PenAmount] valida el resto. Un sufijo como `e3` o
     * `abc`, un separador adicional o un signo pegado hacen que el token entero se rechace; no
     * se corta en un prefijo válido. La muestra usa `S/`; `S/.` sigue sin aceptarse mientras no
     * haya evidencia.
     */
    private val AMOUNT_RUN = Regex("""(?<![A-Za-z0-9])S/$H*([^\s\u00A0\u2007\u202F]+)""")

    /**
     * Puntuación que puede cerrar la frase justo después del importe. No incluye signos que
     * podrían formar parte de un número mal formado (`-`, `+`, `/`, `%`, `*`, etc.).
     */
    private val TRAILING_PUNCTUATION = ".,;:!?)]}'\"".toCharArray()

    /** Frase de ingreso confirmada en la transcripción visual. */
    private val INCOME_PHRASE = Regex("""\bte$H+ha$H+plineado\b""", RegexOption.IGNORE_CASE)

    /**
     * Marcadores inequívocos de una operación que no es un ingreso completado.
     *
     * No se incluyen «promoción» ni «saldo» como palabras sueltas: un pago legítimo podría
     * llevar un pie de promoción o de saldo. Un saldo con otro importe se rechaza por la regla
     * de importe único; un texto de promoción o saldo sin la frase de ingreso ya no coincide.
     * Esta lista se revisará con muestras reales.
     */
    private val REJECTED_MARKERS = listOf(
        Regex("""\bno$H+te$H+ha$H+plineado\b""", RegexOption.IGNORE_CASE),
        Regex("""\bhas$H+plineado\b""", RegexOption.IGNORE_CASE),
        Regex("""\benviaste\b""", RegexOption.IGNORE_CASE),
        Regex("""\bpagaste\b""", RegexOption.IGNORE_CASE),
        Regex("""\bno$H+se$H+pudo\b""", RegexOption.IGNORE_CASE),
        Regex("""\bfall[oó]$H+(?:el|la|tu|su)\b""", RegexOption.IGNORE_CASE),
        Regex(
            """\b(?:el|la|tu|su)$H+(?:pago|plin|operaci[oó]n)$H+fall[oó]\b""",
            RegexOption.IGNORE_CASE,
        ),
        Regex("""\brechazad[oa]\b""", RegexOption.IGNORE_CASE),
        Regex("""\bpendiente\b""", RegexOption.IGNORE_CASE),
        Regex("""\bresumen\b""", RegexOption.IGNORE_CASE),
    )

    /**
     * Retira solo la puntuación final. No se recorta un sufijo alfanumérico ni un separador
     * interno; en esos casos [PenAmount] rechaza el token completo.
     */
    private fun amountCentsOrNull(rawRun: String): Long? =
        PenAmount.centsOrNull(rawRun.trimEnd(*TRAILING_PUNCTUATION))

    private val NEGATIVE_AMOUNT_MARKER = Regex("""-$H*S/""")

    private val WHITESPACE = Regex("[\\s\\u00A0\\u2007\\u202F]+")

    /**
     * Remitente y frase en la misma línea. La captura no cruza el salto de línea; así no se
     * atribuye el título de la notificación como remitente.
     */
    private val SENDER_PATTERN = Regex(
        """(?im)^$H*([^\n]{2,60}?)$H+te$H+ha$H+plineado\b""",
    )
}
