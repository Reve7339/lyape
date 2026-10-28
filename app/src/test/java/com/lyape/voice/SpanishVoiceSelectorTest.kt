package com.lyape.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class SpanishVoiceSelectorTest {

    private fun descriptor(
        localeTag: String,
        quality: Int = 400,
        network: Boolean = false,
        id: String = localeTag,
    ): VoiceDescriptor {
        val locale = Locale.forLanguageTag(localeTag)
        return VoiceDescriptor(
            id = id,
            locale = locale,
            isNetworkConnectionRequired = network,
            quality = quality,
        )
    }

    @Test
    fun `prefiere es-PE sobre latinoamericano y europeo`() {
        val options = SpanishVoiceSelector.select(
            descriptors = listOf(
                descriptor("es-ES", 500),
                descriptor("es-MX", 400),
                descriptor("es-PE", 400),
                descriptor("es-US", 500),
                descriptor("es-AR", 100),
            ),
        )
        assertEquals(listOf("es-PE", "es-US", "es-MX", "es-AR", "es-ES"), options.map { it.id })
    }

    @Test
    fun `excluye voces que requieren red`() {
        val options = SpanishVoiceSelector.select(
            descriptors = listOf(
                descriptor("es-ES", 500, network = true),
                descriptor("es-PE", 400, network = true),
                descriptor("es-MX", 400),
            ),
        )
        assertEquals(listOf("es-MX"), options.map { it.id })
        assertFalse(options.any { it.id.endsWith("-ES") || it.id.endsWith("-PE") })
    }

    @Test
    fun `excluye idiomas que no son español`() {
        val options = SpanishVoiceSelector.select(
            descriptors = listOf(
                descriptor("en-US", 500),
                descriptor("es-ES", 500),
                descriptor("ca-ES", 500),
            ),
        )
        assertEquals(listOf("es-ES"), options.map { it.id })
    }

    @Test
    fun `sin voces españolas devuelve lista vacía`() {
        val options = SpanishVoiceSelector.select(
            descriptors = listOf(
                descriptor("en-US", 500),
                descriptor("fr-FR", 500),
            ),
        )
        assertTrue(options.isEmpty())
    }

    @Test
    fun `ordena por calidad dentro del mismo rango de región`() {
        val options = SpanishVoiceSelector.select(
            descriptors = listOf(
                descriptor("es-MX", 500),
                descriptor("es-MX", 300),
                descriptor("es-MX", 500),
            ),
        )
        assertEquals(
            listOf("es-MX", "es-MX", "es-MX"),
            options.map { it.id },
        )
    }

    @Test
    fun `etiquetas legibles y únicas en caso de duplicados de idioma`() {
        val options = SpanishVoiceSelector.select(
            descriptors = listOf(
                descriptor("es-MX", 500, id = "voice-mx-1"),
                descriptor("es-MX", 500, id = "voice-mx-2"),
            ),
        )
        assertEquals(2, options.size)
        assertTrue(options.any { it.id == "voice-mx-1" })
        assertTrue(options.any { it.id == "voice-mx-2" })
        val labels = options.map { it.label }
        assertEquals("las etiquetas deben ser únicas", 2, labels.distinct().size)
        labels.forEach { label ->
            assertTrue("la etiqueta no debe contener ids técnicos: $label", label.contains("mx-1").not() || label.contains("voice-").not())
        }
    }

    @Test
    fun `etiquetas no están vacías`() {
        val options = SpanishVoiceSelector.select(
            descriptors = listOf(
                descriptor("es-PE", 400),
                descriptor("es-MX", 400),
                descriptor("es-ES", 400),
                descriptor("es-419", 400),
            ),
        )
        assertTrue(options.all { it.label.isNotBlank() })
    }

    @Test
    fun `latinoamericano se prefiere sobre cualquier otro español`() {
        val options = SpanishVoiceSelector.select(
            descriptors = listOf(
                descriptor("es-PE", 100),
                descriptor("es-419", 500),
                descriptor("es-US", 400),
                descriptor("es-ES", 500),
            ),
        )
        assertEquals(listOf("es-PE", "es-419", "es-US", "es-ES"), options.map { it.id })
    }

    @Test
    fun `Perú se prefiere incluso con peor calidad`() {
        val options = SpanishVoiceSelector.select(
            descriptors = listOf(
                descriptor("es-PE", 100),
                descriptor("es-MX", 500),
                descriptor("es-ES", 500),
            ),
        )
        assertEquals(listOf("es-PE", "es-MX", "es-ES"), options.map { it.id })
    }

    @Test
    fun `la opción conserva idioma, país y tag del descriptor`() {
        val options = SpanishVoiceSelector.select(
            descriptors = listOf(descriptor("es-PE", 400, id = "pe-voice-1")),
        )
        val option = options.first()
        assertEquals("es-PE", option.languageTag)
        assertEquals("PE", option.country)
        assertEquals("pe-voice-1", option.id)
    }
}
