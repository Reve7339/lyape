package com.lyape.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PaymentAnnouncementFormatterTest {

    @Test
    fun `Yape un sol sin céntimos`() {
        assertEquals("Yape: un sol", PaymentAnnouncementFormatter.format(PaymentMethod.YAPE, 100))
    }

    @Test
    fun `Plin un sol sin céntimos`() {
        assertEquals("Plin: un sol", PaymentAnnouncementFormatter.format(PaymentMethod.PLIN, 100))
    }

    @Test
    fun `Plin diez soles con cincuenta céntimos`() {
        assertEquals(
            "Plin: diez soles con cincuenta céntimos",
            PaymentAnnouncementFormatter.format(PaymentMethod.PLIN, 1050),
        )
    }

    @Test
    fun `Yape diez céntimos`() {
        assertEquals("Yape: diez céntimos", PaymentAnnouncementFormatter.format(PaymentMethod.YAPE, 10))
    }

    @Test
    fun `Plin un céntimo singular`() {
        assertEquals("Plin: un céntimo", PaymentAnnouncementFormatter.format(PaymentMethod.PLIN, 1))
    }

    @Test
    fun `Plin dos céntimos plural`() {
        assertEquals("Plin: dos céntimos", PaymentAnnouncementFormatter.format(PaymentMethod.PLIN, 2))
    }

    @Test
    fun `Plin veintiún céntimos`() {
        assertEquals("Plin: veintiún céntimos", PaymentAnnouncementFormatter.format(PaymentMethod.PLIN, 21))
    }

    @Test
    fun `Plin veintiún soles`() {
        assertEquals("Plin: veintiún soles", PaymentAnnouncementFormatter.format(PaymentMethod.PLIN, 2100))
    }

    @Test
    fun `Plin mil soles`() {
        assertEquals("Plin: mil soles", PaymentAnnouncementFormatter.format(PaymentMethod.PLIN, 100000))
    }

    @Test
    fun `Plin diez mil soles`() {
        assertEquals("Plin: diez mil soles", PaymentAnnouncementFormatter.format(PaymentMethod.PLIN, 1000000))
    }

    @Test
    fun `Plin un millón de soles`() {
        assertEquals("Plin: un millón de soles", PaymentAnnouncementFormatter.format(PaymentMethod.PLIN, 100_000_000))
    }

    @Test
    fun `Plin un millón quinientos mil soles`() {
        assertEquals(
            "Plin: un millón quinientos mil soles",
            PaymentAnnouncementFormatter.format(PaymentMethod.PLIN, 150_000_000),
        )
    }

    @Test
    fun `Plin diez millones de soles`() {
        assertEquals("Plin: diez millones de soles", PaymentAnnouncementFormatter.format(PaymentMethod.PLIN, 1_000_000_000))
    }

    @Test
    fun `Plin mil doscientos ochenta y cuatro soles con cincuenta céntimos`() {
        assertEquals(
            "Plin: mil doscientos ochenta y cuatro soles con cincuenta céntimos",
            PaymentAnnouncementFormatter.format(PaymentMethod.PLIN, 128450),
        )
    }

    @Test
    fun `Plin un sol con un céntimo singular`() {
        assertEquals("Plin: un sol con un céntimo", PaymentAnnouncementFormatter.format(PaymentMethod.PLIN, 101))
    }

    @Test
    fun `Plin dos soles con un céntimo singular`() {
        assertEquals("Plin: dos soles con un céntimo", PaymentAnnouncementFormatter.format(PaymentMethod.PLIN, 201))
    }

    @Test
    fun `Plin diez soles con diez céntimos`() {
        assertEquals("Plin: diez soles con diez céntimos", PaymentAnnouncementFormatter.format(PaymentMethod.PLIN, 1010))
    }

    @Test
    fun `Plin cien mil soles sin céntimos`() {
        assertEquals("Plin: cien mil soles", PaymentAnnouncementFormatter.format(PaymentMethod.PLIN, 10_000_000))
    }

    @Test
    fun `Plin ciento un mil soles`() {
        assertEquals("Plin: ciento un mil soles", PaymentAnnouncementFormatter.format(PaymentMethod.PLIN, 10_100_000))
    }

    @Test
    fun `cero no se anuncia`() {
        assertNull(PaymentAnnouncementFormatter.format(PaymentMethod.PLIN, 0))
    }

    @Test
    fun `negativo no se anuncia`() {
        assertNull(PaymentAnnouncementFormatter.format(PaymentMethod.YAPE, -500))
    }

    @Test
    fun `Long MIN_VALUE no se anuncia`() {
        assertNull(PaymentAnnouncementFormatter.format(PaymentMethod.YAPE, Long.MIN_VALUE))
    }

    @Test
    fun `medio desconocido no se anuncia`() {
        assertNull(PaymentAnnouncementFormatter.format(PaymentMethod.UNKNOWN, 100))
    }

    @Test
    fun `monto muy grande queda en frase corta sin inventar el monto`() {
        assertEquals(
            "Plin: importe alto",
            PaymentAnnouncementFormatter.format(PaymentMethod.PLIN, Long.MAX_VALUE),
        )
    }

    @Test
    fun `monto justo encima del límite de soles no usa palabras`() {
        val amountCents = (999_999_999_999L + 1) * 100
        assertEquals("Plin: importe alto", PaymentAnnouncementFormatter.format(PaymentMethod.PLIN, amountCents))
    }
}
