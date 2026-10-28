package com.lyape.voice

import java.util.Locale

/**
 * Descriptor simple de una voz disponible, sin dependencias de Android.
 *
 * Se usa para filtrar, clasificar y seleccionar voces en español que el motor declare
 * locales. El identificador técnico no se muestra al usuario.
 */
data class VoiceDescriptor(
    val id: String,
    val locale: Locale,
    val isNetworkConnectionRequired: Boolean,
    val quality: Int,
)

/**
 * Opción de voz presentada al usuario: una etiqueta legible asociada a un identificador
 * persistente. El identificador nunca se muestra en la interfaz.
 */
data class SpanishVoiceOption(
    val id: String,
    val label: String,
    val languageTag: String,
    val country: String,
)

/**
 * Selecciona voces en español locales del conjunto de voces disponibles.
 *
 * Preferencias:
 * 1. Español de Perú (`es-PE`).
 * 2. Cualquier variante española latinoamericana disponible.
 * 3. Cualquier otra variante en español.
 *
 * Se excluyen las voces que requieren conexión de red. Los duplicados de etiqueta se
 * distinguen con un sufijo numérico, sin mostrar identificadores técnicos.
 */
object SpanishVoiceSelector {

    /** Países y territorios considerados latinoamericanos (excluye Perú, rango superior). */
    private val LATIN_AMERICA = setOf(
        "AR", "BO", "CL", "CO", "CR", "CU", "DO", "EC", "SV", "GT", "HN", "MX",
        "NI", "PA", "PY", "PE", "PR", "UY", "VE", "US", "419",
    )

    fun select(descriptors: List<VoiceDescriptor>): List<SpanishVoiceOption> {
        val eligible = descriptors.filter { descriptor ->
            !descriptor.isNetworkConnectionRequired &&
                descriptor.locale.language.equals("es", ignoreCase = true)
        }.sortedWith(
            compareBy<VoiceDescriptor> { rank(it.locale) }
                .thenByDescending { it.quality }
                .thenBy { labelFor(it.locale) }
                .thenBy { it.id },
        )

        val labels = eligible.map { descriptor -> labelFor(descriptor.locale) }
        val counts = mutableMapOf<String, Int>()

        return eligible.mapIndexed { index, descriptor ->
            val base = labels[index]
            val occurrence = (counts[base] ?: 0) + 1
            counts[base] = occurrence
            val displayLabel = if (occurrence == 1) base else "$base · $occurrence"
            SpanishVoiceOption(
                id = descriptor.id,
                label = displayLabel,
                languageTag = descriptor.locale.toLanguageTag(),
                country = descriptor.locale.country,
            )
        }
    }

    private fun rank(locale: Locale): Int = when {
        locale.country.equals("PE", ignoreCase = true) -> 0
        locale.country in LATIN_AMERICA -> 1
        else -> 2
    }

    private fun labelFor(locale: Locale): String {
        return locale.getDisplayName(Locale("es")).ifBlank { "Español" }
    }
}
