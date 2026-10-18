package com.lyape.domain

import java.text.Normalizer
import java.util.Locale

private val COMBINING_MARKS = Regex("\\p{Mn}+")

/** Misma normalización al guardar, migrar y consultar; SQL busca subcadenas literales. */
fun normalizeMovementSearch(value: String): String =
    Normalizer.normalize(value, Normalizer.Form.NFD)
        .replace(COMBINING_MARKS, "")
        .lowercase(Locale.ROOT)
        .trim()

/** Nombre que se presenta y por el que se busca, también cuando no hay remitente. */
fun movementTitle(senderName: String?, source: MovementSource): String =
    senderName?.trim()?.takeIf(String::isNotEmpty) ?: when (source) {
        MovementSource.NOTIFICATION -> "Pago sin nombre"
        MovementSource.MANUAL -> "Venta agregada"
        MovementSource.ADJUSTMENT -> "Ajuste"
    }
