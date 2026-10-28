package com.lyape.ui.dashboard

import com.lyape.ui.format.formatCents

/**
 * Textos compactos del Resumen, puros para poder probar singular, plural y el contenido del
 * resumen sin depender de Android.
 */

/** `1 de negocio` / `6 de negocio`. */
internal fun businessCountLabel(count: Int): String =
    if (count == 1) "1 de negocio" else "$count de negocio"

/** Conteo y dinero personal, sin estados de la antigua revisión diaria. */
internal fun compactSummaryLabel(records: Int, personalCents: Long): String {
    val registros = if (records == 1) "1 registro" else "$records registros"
    return "$registros · ${formatCents(personalCents)} personal"
}

/** `Sin pendientes hoy` / `1 pendiente hoy` / `N pendientes hoy`. */
internal fun pendingTodayLabel(pending: Int): String = when (pending) {
    0 -> "Sin pendientes hoy"
    1 -> "1 pendiente hoy"
    else -> "$pending pendientes hoy"
}
