package com.lyape.notification

import java.security.MessageDigest
import java.util.Locale

/**
 * Huella determinista de una notificación ya interpretada.
 *
 * Combina paquete, monto en céntimos y texto normalizado. El resultado es un resumen SHA-256 en
 * hexadecimal: no guarda el texto en claro, no usa `Float` ni `Double` y no depende de Android.
 * Es la base del respaldo de deduplicación cuando no hay clave de Android.
 */
object NotificationFingerprint {

    /**
     * Normaliza el texto para comparar: colapsa cualquier secuencia de espacios (incluidos el
     * espacio duro y los finos), recorta los extremos y unifica mayúsculas/minúsculas.
     */
    fun normalizeText(raw: String): String =
        raw.replace(WHITESPACE, " ").trim().lowercase(Locale.ROOT)

    /**
     * Prepara el texto para la huella sin incorporar códigos de seguridad de Yape.
     *
     * Aunque la huella sea SHA-256, un código de pocos dígitos tiene tan poca entropía que no debe
     * formar parte del dato persistido. Se sustituye en memoria antes de normalizar y resumir.
     */
    fun normalizeForFingerprint(raw: String): String =
        normalizeText(raw.replace(SECURITY_CODE, "$1[oculto]"))

    fun compute(packageName: String, amountCents: Long, normalizedText: String): String {
        val canonical = "$packageName\n$amountCents\n$normalizedText"
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(canonical.toByteArray(Charsets.UTF_8))
        return digest.joinToString(separator = "") { byte ->
            (byte.toInt() and 0xFF).toString(16).padStart(2, '0')
        }
    }

    private val WHITESPACE = Regex("[\\s\\u00A0\\u2007\\u202F]+")

    private val SECURITY_CODE = Regex(
        """(c[oó]d(?:igo)?\.?\s+de\s+seguridad(?:\s+es)?\s*:\s*)\d+""",
        RegexOption.IGNORE_CASE,
    )
}
