package com.lyape.notification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class NotificationFingerprintTest {

    @Test
    fun `normaliza espacios y mayúsculas`() {
        val normalized = NotificationFingerprint.normalizeText("  María\u00A0 Rojas   TE pagó  ")

        assertEquals("maría rojas te pagó", normalized)
    }

    @Test
    fun `normaliza un texto vacío a vacío`() {
        assertEquals("", NotificationFingerprint.normalizeText("   \u00A0  "))
    }

    @Test
    fun `oculta el codigo de seguridad antes de crear la huella`() {
        val normalized = NotificationFingerprint.normalizeForFingerprint(
            "Persona Ejemplo* te envió S/ 0.1. El cód. de seguridad es: 885",
        )

        assertEquals(
            "persona ejemplo* te envió s/ 0.1. el cód. de seguridad es: [oculto]",
            normalized,
        )
    }

    @Test
    fun `la huella es determinista`() {
        val first = NotificationFingerprint.compute("com.example", 3_500, "hola")
        val second = NotificationFingerprint.compute("com.example", 3_500, "hola")

        assertEquals(first, second)
        assertEquals(64, first.length)
    }

    @Test
    fun `la huella cambia con el paquete`() {
        assertNotEquals(
            NotificationFingerprint.compute("com.example.a", 100, "texto"),
            NotificationFingerprint.compute("com.example.b", 100, "texto"),
        )
    }

    @Test
    fun `la huella cambia con el monto`() {
        assertNotEquals(
            NotificationFingerprint.compute("com.example", 100, "texto"),
            NotificationFingerprint.compute("com.example", 200, "texto"),
        )
    }

    @Test
    fun `la huella cambia con el texto`() {
        assertNotEquals(
            NotificationFingerprint.compute("com.example", 100, "texto"),
            NotificationFingerprint.compute("com.example", 100, "otro"),
        )
    }
}
