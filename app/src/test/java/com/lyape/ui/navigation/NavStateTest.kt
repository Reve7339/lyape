package com.lyape.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavStateTest {
    @Test
    fun `privacidad se restaura y vuelve a ajustes sin ser un formulario`() {
        val state = NavState().selectTab(BottomTab.SETTINGS).open(Destination.Privacy)
        assertEquals(state, navStateFromSavedList(state.toSavedList()))
        assertFalse(state.current.isRootDestination)
        assertEquals(ExitDecision.LEAVE, exitDecision(state.current, true))
        assertEquals(Destination.Settings, state.back().current)
    }


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
        assertTrue(Destination.Accounts.isRootDestination)
        assertFalse(Destination.DailyClose.isRootDestination)
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

    @Test
    fun `cuentas reportes y pagos conservan la pila tras recreacion`() {
        val state = NavState().selectTab(BottomTab.ACCOUNTS)
            .open(Destination.AccountDetail("yape"))
            .open(Destination.AccountForm("balance", "yape"))
            .open(Destination.AccountForm("add"))
            .open(Destination.ExpensePayment("pago-1", "2026-10"))
            .open(Destination.Reports)
        assertEquals(state, navStateFromSavedList(state.toSavedList()))
    }

    @Test
    fun `todos los nuevos formularios protegen los cambios y el guardado`() {
        listOf(
            Destination.AccountForm("balance", "yape"),
            Destination.AccountForm("transfer"),
            Destination.AccountForm("withdraw"),
            Destination.AccountForm("add"),
            Destination.ExpensePayment("pago-1", "2026-10"),
        ).forEach { destination ->
            assertFalse(destination.isRootDestination)
            assertEquals(ExitDecision.CONFIRM, exitDecision(destination, true))
            assertEquals(ExitDecision.STAY, exitDecision(destination, true, true))
            assertEquals(ExitDecision.LEAVE, exitDecision(destination, false))
        }
    }

    @Test
    fun `cierre antiguo restaura una ruta para volver a movimientos`() {
        val restored = navStateFromSavedList(listOf("close"))
        assertEquals(Destination.Movements, restored.current)
        assertFalse(restored.canGoBack)
        assertEquals(BottomTab.MOVEMENTS, restored.selectedTab)
    }

    @Test
    fun `formularios guardados mal formados no rompen navegacion`() {
        assertEquals(Destination.Accounts, decodeDestination("account-form:invalid"))
        assertEquals(Destination.Summary, decodeDestination("expense-payment:invalid"))
    }

    @Test
    fun `pagar y volver conserva el mes que se selecciono dentro del plan`() {
        val state = NavState().open(Destination.MonthlyPlan("2026-10"))
            .openExpensePayment("gasto-1", "2026-09")
        assertEquals(Destination.ExpensePayment("gasto-1", "2026-09"), state.current)
        assertEquals(Destination.MonthlyPlan("2026-09"), state.back().current)
        assertEquals(state, navStateFromSavedList(state.toSavedList()))
    }
}
