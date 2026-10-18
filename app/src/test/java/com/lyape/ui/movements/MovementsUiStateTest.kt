package com.lyape.ui.movements

import com.lyape.domain.MovementCounts
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class MovementsUiStateTest {

    @Test
    fun `un historial vacio conserva los conteos en cero`() = runBlocking {
        val state = flowOf(MovementCounts()).toMovementCountsState().first()
        assertEquals(MovementCountsState.Content(MovementCounts()), state)
    }

    @Test
    fun `un error de lectura produce el estado Error`() = runBlocking {
        val failing: Flow<MovementCounts> = flow { throw IllegalStateException("sin datos") }

        assertEquals(MovementCountsState.Error, failing.toMovementCountsState().first())
    }

    @Test
    fun `reintentar vuelve a pedir los datos sin acumular la coleccion`() = runBlocking {
        var subscriptions = 0
        val source: Flow<MovementCounts> = flow {
            subscriptions += 1
            emit(MovementCounts(total = 2000, business = 1900, personal = 100))
        }
        val stream = source.toMovementCountsState()

        val first = stream.first() as MovementCountsState.Content
        val second = stream.first() as MovementCountsState.Content

        assertEquals(2, subscriptions)
        assertEquals(2000, first.counts.total)
        assertEquals(first, second)
    }
}
