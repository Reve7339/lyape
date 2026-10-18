package com.lyape.ui.movementform

import org.junit.Assert.assertEquals
import org.junit.Test

class SegmentedDateTimeFieldsTest {

    @Test
    fun `separa una fecha conservando sus tres grupos`() {
        assertEquals(
            DateInputGroups(day = "15", month = "10", year = "2026"),
            dateInputGroups("15/10/2026"),
        )
        assertEquals("/10/2026", DateInputGroups("", "10", "2026").asText())
    }

    @Test
    fun `separa una hora conservando ambos grupos`() {
        assertEquals(TimeInputGroups(hour = "17", minute = "42"), timeInputGroups("17:42"))
        assertEquals("17:", TimeInputGroups("17", "").asText())
    }

    @Test
    fun `recupera grupos desde texto antiguo sin separadores`() {
        assertEquals(DateInputGroups("15", "10", "2026"), dateInputGroups("15102026"))
        assertEquals(TimeInputGroups("17", "42"), timeInputGroups("1742"))
    }
}
