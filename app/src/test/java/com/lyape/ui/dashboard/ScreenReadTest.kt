package com.lyape.ui.dashboard

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ScreenReadTest {
    @Test fun realZeroIsOnlyPublishedAfterSuccessfulRead() = runBlocking {
        assertEquals(listOf(ScreenRead.Loading, ScreenRead.Ready(0L)), flowOf(0L).asScreenRead().toList())
    }

    @Test fun failedReadDoesNotPretendTheBalanceIsZero() = runBlocking {
        val states = flow<Long> { error("database unavailable") }.asScreenRead().toList()
        assertEquals(listOf(ScreenRead.Loading, ScreenRead.Failed), states)
        assertFalse(states.any { it is ScreenRead.Ready })
    }

    @Test(expected = CancellationException::class)
    fun cancellationIsNotConvertedToReadFailure() = runBlocking {
        flow<Long> { throw CancellationException("closed") }.asScreenRead().toList()
        Unit
    }
}
