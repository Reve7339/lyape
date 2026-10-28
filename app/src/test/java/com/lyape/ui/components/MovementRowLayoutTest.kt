package com.lyape.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class MovementRowLayoutTest {
    @Test fun `conserva avatar e importe lateral en pantallas compactas`() {
        // Menú bajo el importe: 320/360 dp menos márgenes 34 y padding 28.
        assertEquals(MovementRowLayout.WITH_AVATAR, movementRowLayout(258f, 100f))
        assertEquals(MovementRowLayout.WITH_AVATAR, movementRowLayout(298f, 120f))
        assertEquals(MovementRowLayout.WITH_AVATAR, movementRowLayout(298f, 150f))
    }
    @Test fun `mantiene avatar solo cuando ambas columnas tienen espacio`() {
        assertEquals(MovementRowLayout.WITH_AVATAR, movementRowLayout(320f, 120f))
    }
    @Test fun `fuente grande o importe extremo puede apilar sin reducir la letra`() {
        assertEquals(MovementRowLayout.STACKED, movementRowLayout(242f, 200f))
        assertEquals(MovementRowLayout.STACKED, movementRowLayout(180f, 120f))
    }
}
