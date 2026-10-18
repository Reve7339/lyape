package com.lyape.ui.movements

import com.lyape.domain.Movement
import com.lyape.domain.MovementFactory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.util.UUID

class MovementsUiStateTest {

    private val now = Instant.parse("2026-10-16T15:00:00Z")

    private fun movement(amountCents: Long, id: String): Movement =
        MovementFactory.manual(
            id = UUID.fromString(id),
            amountCents = amountCents,
            occurredAt = now,
            label = null,
            now = now,
        )

    @Test
    fun `una lista vacia produce el estado Empty`() = runBlocking {
        val state = flowOf(emptyList<Movement>()).toMovementsUiState().first()

        assertEquals(MovementsUiState.Empty, state)
    }

    @Test
    fun `un error de lectura produce el estado Error`() = runBlocking {
        val failing: Flow<List<Movement>> = flow { throw IllegalStateException("sin datos") }

        assertEquals(MovementsUiState.Error, failing.toMovementsUiState().first())
    }

    @Test
    fun `reintentar vuelve a pedir los datos sin acumular la coleccion`() = runBlocking {
        var subscriptions = 0
        val source: Flow<List<Movement>> = flow {
            subscriptions += 1
            emit(
                listOf(
                    movement(500L, "11111111-1111-1111-1111-111111111111"),
                    movement(300L, "22222222-2222-2222-2222-222222222222"),
                ),
            )
        }
        val stream = source.toMovementsUiState()

        val first = stream.first() as MovementsUiState.Content
        val second = stream.first() as MovementsUiState.Content

        assertEquals(2, subscriptions)
        assertEquals(2, first.movements.size)
        assertEquals(2, second.movements.size)
    }
}
