package com.lyape.notification

/**
 * Metadatos confirmados por una inspección autorizada del teléfono.
 *
 * El paquete instalado permite filtrar antes de leer extras, pero no acredita el formato de las
 * notificaciones, su agrupación, los extras ni el comportamiento de las entregas. Interbank
 * está habilitado provisionalmente por autorización del usuario; falta validación física.
 */
object InterbankPackage {
    const val PACKAGE_NAME = "pe.com.interbank.mobilebanking"
    const val VERSION_NAME = "23.4"
    const val VERSION_CODE = 1870
}
