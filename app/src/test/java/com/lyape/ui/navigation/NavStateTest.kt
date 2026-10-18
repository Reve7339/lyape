package com.lyape.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavStateTest {

    @Test
    fun `inicia en el resumen sin historial`() {
        val state = NavState()

        assertEquals(Destination.Summary, state.current)
        assertEquals(BottomTab.SUMMARY, state.selectedTab)
        assertFalse(state.canGoBack)
    }

    @Test
    fun `seleccionar una pestaña resetea la pila`() {
        val state = NavState()
            .open(Destination.MovementDetail("demo-001"))
            .selectTab(BottomTab.SETTINGS)

        assertEquals(Destination.Settings, state.current)
        assertEquals(BottomTab.SETTINGS, state.selectedTab)
        assertFalse(state.canGoBack)
    }

    @Test
    fun `abrir el detalle lo apila y mantiene la pestaña de movimientos`() {
        val state = NavState()
            .selectTab(BottomTab.MOVEMENTS)
            .open(Destination.MovementDetail("demo-002"))

        assertEquals(Destination.MovementDetail("demo-002"), state.current)
        assertEquals(BottomTab.MOVEMENTS, state.selectedTab)
        assertTrue(state.canGoBack)
    }

    @Test
    fun `volver desapila hasta la pestaña raíz`() {
        val state = NavState()
            .selectTab(BottomTab.MOVEMENTS)
            .open(Destination.MovementDetail("demo-003"))
            .back()

        assertEquals(Destination.Movements, state.current)
        assertEquals(BottomTab.MOVEMENTS, state.selectedTab)
        assertFalse(state.canGoBack)
    }

    @Test
    fun `volver en la raíz no cambia el estado`() {
        val state = NavState()

        assertEquals(state, state.back())
    }

    @Test
    fun `el estado sobrevive a la serializacion`() {
        val state = NavState()
            .selectTab(BottomTab.MOVEMENTS)
            .open(Destination.MovementDetail("demo-004"))

        val restored = navStateFromSavedList(state.toSavedList())

        assertEquals(state, restored)
    }

    @Test
    fun `los destinos de conciliacion sobreviven a la serializacion`() {
        val state = NavState()
            .selectTab(BottomTab.MOVEMENTS)
            .open(Destination.AddMovement)
            .open(Destination.MovementEdit("11111111-1111-1111-1111-111111111111"))

        val restored = navStateFromSavedList(state.toSavedList())

        assertEquals(state, restored)
        assertEquals(
            Destination.MovementEdit("11111111-1111-1111-1111-111111111111"),
            restored.current,
        )
    }

    @Test
    fun `el plan mensual conserva el mes al restaurar la navegacion`() {
        val state = NavState().open(Destination.MonthlyPlan("2026-10"))

        assertEquals(state, navStateFromSavedList(state.toSavedList()))
        assertEquals(Destination.MonthlyPlan("2026-10"), state.current)
    }

    @Test
    fun `una lista guardada vacia vuelve al resumen`() {
        assertEquals(NavState(), navStateFromSavedList(emptyList()))
    }

    @Test
    fun `un valor guardado desconocido vuelve al resumen`() {
        assertEquals(Destination.Summary, decodeDestination("desconocido"))
    }

    @Test
    fun `la barra inferior solo pertenece a los destinos raiz`() {
        assertTrue(Destination.Summary.isRootDestination)
        assertTrue(Destination.Movements.isRootDestination)
        assertTrue(Destination.DailyClose.isRootDestination)
        assertTrue(Destination.Settings.isRootDestination)

        assertFalse(Destination.AddMovement.isRootDestination)
        assertFalse(Destination.MovementDetail("demo-001").isRootDestination)
        assertFalse(Destination.MovementEdit("demo-001").isRootDestination)
        assertFalse(Destination.MonthlyPlan("2026-10").isRootDestination)
    }

    @Test
    fun `un formulario con cambios pide confirmacion al salir`() {
        assertEquals(
            ExitDecision.CONFIRM,
            exitDecision(Destination.AddMovement, hasUnsavedChanges = true),
        )
        assertEquals(
            ExitDecision.CONFIRM,
            exitDecision(Destination.MovementEdit("demo-001"), hasUnsavedChanges = true),
        )
    }

    @Test
    fun `un formulario intacto sale sin confirmacion`() {
        assertEquals(
            ExitDecision.LEAVE,
            exitDecision(Destination.AddMovement, hasUnsavedChanges = false),
        )
        assertEquals(
            ExitDecision.LEAVE,
            exitDecision(Destination.MovementEdit("demo-001"), hasUnsavedChanges = false),
        )
    }

    @Test
    fun `un formulario que se esta guardando no sale ni abre confirmacion`() {
        assertEquals(
            ExitDecision.STAY,
            exitDecision(
                Destination.AddMovement,
                hasUnsavedChanges = true,
                isSaving = true,
            ),
        )
        assertEquals(
            ExitDecision.STAY,
            exitDecision(
                Destination.MovementEdit("demo-001"),
                hasUnsavedChanges = true,
                isSaving = true,
            ),
        )
    }

    @Test
    fun `las pantallas que no son formularios salen sin confirmacion`() {
        assertEquals(
            ExitDecision.LEAVE,
            exitDecision(Destination.Movements, hasUnsavedChanges = true),
        )
        assertEquals(
            ExitDecision.LEAVE,
            exitDecision(Destination.MovementDetail("demo-001"), hasUnsavedChanges = true),
        )
        assertEquals(
            ExitDecision.LEAVE,
            exitDecision(Destination.MonthlyPlan("2026-10"), hasUnsavedChanges = true),
        )
    }
}
