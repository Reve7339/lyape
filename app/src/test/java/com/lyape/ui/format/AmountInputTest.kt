package com.lyape.ui.format

import org.junit.Assert.assertEquals
import org.junit.Test

class AmountInputTest {

    @Test
    fun `interpreta soles con y sin decimales`() {
        assertEquals(AmountParse.Valid(3_500), parseAmountCents("35"))
        assertEquals(AmountParse.Valid(3_550), parseAmountCents("35.5"))
        assertEquals(AmountParse.Valid(3_550), parseAmountCents("35.50"))
        assertEquals(AmountParse.Valid(3_550), parseAmountCents("35,50"))
        assertEquals(AmountParse.Valid(1), parseAmountCents("0.01"))
        assertEquals(AmountParse.Valid(50), parseAmountCents(".5"))
        assertEquals(AmountParse.Valid(3_500), parseAmountCents("35."))
        assertEquals(AmountParse.Valid(1_200), parseAmountCents("S/ 12.00"))
    }

    @Test
    fun `vacio es distinto de invalido`() {
        assertEquals(AmountParse.Empty, parseAmountCents(""))
        assertEquals(AmountParse.Empty, parseAmountCents("   "))
    }

    @Test
    fun `rechaza formatos ambiguos`() {
        assertEquals(AmountParse.Malformed, parseAmountCents("1,284.50"))
        assertEquals(AmountParse.Malformed, parseAmountCents("35.505"))
        assertEquals(AmountParse.Malformed, parseAmountCents("35.5.5"))
        assertEquals(AmountParse.Malformed, parseAmountCents("35 soles"))
        assertEquals(AmountParse.Malformed, parseAmountCents("abc"))
        assertEquals(AmountParse.Malformed, parseAmountCents("-35"))
    }

    @Test
    fun `rechaza montos que no caben en Long`() {
        assertEquals(AmountParse.TooLarge, parseAmountCents("99999999999999999999"))
        assertEquals(
            AmountParse.Valid(Long.MAX_VALUE),
            parseAmountCents("92233720368547758.07"),
        )
        assertEquals(AmountParse.TooLarge, parseAmountCents("92233720368547758.08"))
    }

    @Test
    fun `formatea centimos para editar sin decimales flotantes`() {
        assertEquals("35.00", formatAmountInput(3_500))
        assertEquals("0.05", formatAmountInput(5))
        assertEquals("8.00", formatAmountInput(-800))
    }

    @Test
    fun `el viaje de ida y vuelta conserva el monto`() {
        listOf(1L, 5L, 99L, 100L, 3_500L, 128_450L).forEach { cents ->
            assertEquals(AmountParse.Valid(cents), parseAmountCents(formatAmountInput(cents)))
        }
    }
}
