package com.lyape.ui.movements

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MovementActionsTest {
    @Test fun `doble toque guarda una vez y libera la fila`() = runTest {
        val completion = CompletableDeferred<Unit>()
        var saves = 0
        val actions = MovementActions(this) { _, _ -> saves++; completion.await(); true }
        actions.perform("1", MovementAction.PERSONAL)
        actions.perform("1", MovementAction.EXCLUDE)
        runCurrent()
        assertEquals(1, saves)
        assertEquals(setOf("1"), actions.busy.value)
        completion.complete(Unit)
        runCurrent()
        assertTrue(actions.busy.value.isEmpty())
        assertEquals("Marcado como personal", actions.feedback.first())
    }
    @Test fun `error permite reintentar sin anunciar exito`() = runTest {
        var fail = true
        val actions = MovementActions(this) { _, _ -> if (fail) error("DB") else true }
        actions.perform("1", MovementAction.RESTORE)
        runCurrent()
        assertTrue(actions.feedback.first().startsWith("No se guardó"))
        assertTrue(actions.busy.value.isEmpty())
        fail = false
        actions.perform("1", MovementAction.RESTORE)
        runCurrent()
        assertEquals("Movimiento restaurado", actions.feedback.first())
    }
    @Test fun `registro desaparecido no anuncia exito`() = runTest {
        val actions = MovementActions(this) { _, _ -> false }
        actions.perform("1", MovementAction.BUSINESS)
        runCurrent()
        assertEquals("El movimiento ya no está disponible.", actions.feedback.first())
    }
}
