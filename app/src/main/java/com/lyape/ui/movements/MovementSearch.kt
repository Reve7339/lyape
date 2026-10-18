package com.lyape.ui.movements

import com.lyape.ui.model.MovementUi
import java.text.Normalizer
import java.util.Locale

private val COMBINING_MARKS = Regex("\\p{Mn}+")

/**
 * Normaliza texto para buscar sin distinguir mayúsculas ni tildes.
 *
 * Descompone los caracteres (NFD), elimina las marcas diacríticas y pasa a minúsculas. Es una
 * función pura: no toca Android ni el estado de la interfaz.
 */
fun normalizeForSearch(value: String): String =
    Normalizer.normalize(value, Normalizer.Form.NFD)
        .replace(COMBINING_MARKS, "")
        .lowercase(Locale.ROOT)
        .trim()

/**
 * Campos por los que se puede buscar un movimiento: remitente o nombre, descripción y motivo de
 * un ajuste.
 */
fun MovementUi.searchableText(): List<String> =
    listOfNotNull(title, description, supportingLabel)

/**
 * Indica si el movimiento coincide con la consulta [normalizedQuery], que debe venir ya
 * normalizada con [normalizeForSearch]. Una consulta vacía coincide con todo.
 */
fun MovementUi.matchesSearch(normalizedQuery: String): Boolean {
    if (normalizedQuery.isEmpty()) return true
    return searchableText().any { normalizeForSearch(it).contains(normalizedQuery) }
}
