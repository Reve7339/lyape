package com.lyape.ui.format

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class DateTimeInputTest {

    private val lima = ZoneId.of("America/Lima")

    @Test
    fun `interpreta fecha y hora en formato local`() {
        assertEquals(LocalDate.of(2026, 10, 15), parseDateInput("15/10/2026"))
        assertEquals(LocalTime.of(17, 42), parseTimeInput("17:42"))
    }

    @Test
    fun `una fecha o hora mal escrita devuelve nulo`() {
        assertNull(parseDateInput("2026-10-15"))
        assertNull(parseDateInput("31/02/2026"))
        assertNull(parseTimeInput("25:00"))
        assertNull(parseTimeInput("17:60"))
        assertNull(parseTimeInput("5pm"))
    }

    @Test
    fun `combinar fecha y hora usa la zona del telefono`() {
        val instant = instantOf(LocalDate.of(2026, 10, 15), LocalTime.of(17, 42), lima)
        val expected = LocalDate.of(2026, 10, 15).atTime(17, 42).atZone(lima).toInstant()
        assertEquals(expected, instant)
    }

    @Test
    fun `el formato de entrada es el inverso del analisis`() {
        val date = LocalDate.of(2026, 10, 15)
        assertEquals(date, parseDateInput(formatDateInput(date)))

        val time = LocalTime.of(9, 5)
        assertEquals(time, parseTimeInput(formatTimeInput(time)))
    }

    @Test
    fun `la etiqueta del dia incluye numero y mes`() {
        val label = formatDayLabel(LocalDate.of(2026, 10, 15)).lowercase()
        assertTrue(label.contains("15"))
        assertTrue(label.contains("oct"))
    }
}
