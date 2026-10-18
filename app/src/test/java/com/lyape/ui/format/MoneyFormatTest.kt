package com.lyape.ui.format

import org.junit.Assert.assertEquals
import org.junit.Test

class MoneyFormatTest {

    @Test
    fun `formatea cero`() {
        assertEquals("S/ 0.00", formatCents(0))
    }

    @Test
    fun `formatea centimos sueltos`() {
        assertEquals("S/ 0.05", formatCents(5))
        assertEquals("S/ 0.99", formatCents(99))
    }

    @Test
    fun `formatea soles completos`() {
        assertEquals("S/ 1.00", formatCents(100))
    }

    @Test
    fun `agrupa los miles`() {
        assertEquals("S/ 1,284.50", formatCents(128_450))
        assertEquals("S/ 1,000,000.00", formatCents(100_000_000))
    }

    @Test
    fun `los montos negativos llevan signo menos`() {
        assertEquals("- S/ 8.00", formatCents(-800))
    }

    @Test
    fun `formatea el menor Long sin desbordarse`() {
        assertEquals(
            "- S/ 92,233,720,368,547,758.08",
            formatCents(Long.MIN_VALUE),
        )
    }

    @Test
    fun `la firma explicita distingue suma y resta`() {
        assertEquals("+ S/ 35.00", formatSignedCents(3_500))
        assertEquals("- S/ 8.00", formatSignedCents(-800))
        assertEquals("- S/ 92,233,720,368,547,758.08", formatSignedCents(Long.MIN_VALUE))
        assertEquals("S/ 0.00", formatSignedCents(0))
    }
}
