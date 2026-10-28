package com.lyape.notification

import com.lyape.domain.CaptureProvider

/** Parser puro de una notificación; el proveedor decide cuál corresponde. */
fun interface NotificationPayloadParser {
    fun parse(input: NotificationInput): ParseResult
}

/**
 * Proveedor cerrado de captura.
 *
 * El paquete se comprueba antes de leer extras y solo se acepta si el proveedor está habilitado.
 * [packageNames] vacío y [enabled] falso dejan al proveedor preparado pero sin captura real.
 */
data class CaptureProviderSpec(
    val provider: CaptureProvider,
    val packageNames: Set<String>,
    val enabled: Boolean,
    val parser: NotificationPayloadParser,
) {
    fun acceptsPackage(packageName: String): Boolean =
        enabled && packageName in packageNames

    fun parse(input: NotificationInput): ParseResult =
        if (acceptsPackage(input.packageName)) parser.parse(input) else ParseResult.Unknown
}

/** Registro cerrado: no se analiza ninguna aplicación que no esté listada y habilitada. */
class CaptureProviderRegistry(specs: List<CaptureProviderSpec>) {

    private val specs = specs.toList()

    fun forPackage(packageName: String): CaptureProviderSpec? =
        specs.firstOrNull { it.acceptsPackage(packageName) }
}

object CaptureProviders {

    /**
     * Yape conserva su comportamiento actual.
     *
     * Interbank-Plin tiene paquete confirmado (`pe.com.interbank.mobilebanking`, 23.4/1870),
     * habilitado provisionalmente por autorización del usuario. El parser conserva el formato
     * observado en la captura; la compatibilidad física y las variantes siguen por validar.
     */
    val production: CaptureProviderRegistry = CaptureProviderRegistry(
        listOf(
            CaptureProviderSpec(
                provider = CaptureProvider.YAPE,
                packageNames = setOf(YapePackage.PROVISIONAL_PACKAGE_ID),
                enabled = true,
                parser = NotificationPayloadParser(NotificationParser::parse),
            ),
            CaptureProviderSpec(
                provider = CaptureProvider.INTERBANK_PLIN,
                packageNames = setOf(InterbankPackage.PACKAGE_NAME),
                enabled = true,
                parser = NotificationPayloadParser(InterbankPlinParser::parse),
            ),
        ),
    )
}
