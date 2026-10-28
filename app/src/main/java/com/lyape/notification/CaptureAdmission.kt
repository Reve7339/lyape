package com.lyape.notification

/** Se evalúa antes de leer extras, también para proveedores simulados. */
internal fun acceptsCaptureEnvelope(
    source: CaptureProviderSpec,
    packageName: String,
    userId: Int,
    sameUserAsProcess: Boolean,
    groupSummary: Boolean,
): Boolean = source.acceptsPackage(packageName) && userId == 0 && sameUserAsProcess && !groupSummary
