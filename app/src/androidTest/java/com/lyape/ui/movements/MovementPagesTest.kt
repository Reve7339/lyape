package com.lyape.ui.movements

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.paging.PagingDataPresenter
import androidx.paging.PagingDataEvent
import com.lyape.domain.*
import com.lyape.ui.model.MovementUi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class MovementPagesTest {
    @Test fun accountChangesAndReturningReusePageSafely() = runTest {
        val now = Instant.parse("2026-10-26T12:00:00Z")
        val movement = Movement(UUID.randomUUID(), 10000, now, now, MovementSource.MANUAL,
            MovementClassification.BUSINESS, false, now, now, accountId = "bank")
        var loads = 0
        val pager = Pager(PagingConfig(pageSize = 20, enablePlaceholders = false)) {
            object : PagingSource<Int, Movement>() {
                override fun getRefreshKey(state: PagingState<Int, Movement>): Int? = null
                override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Movement> {
                    loads++
                    return LoadResult.Page(listOf(movement), null, null)
                }
            }
        }
        val accounts = MutableStateFlow(listOf(CaptureAccount("bank", "Interbank", AccountKind.BANK)))
        val pages = movementPagesWithAccounts(pager.flow, accounts, backgroundScope)
        fun presenter() = object : PagingDataPresenter<MovementUi>(StandardTestDispatcher(testScheduler)) {
            override suspend fun presentPagingDataEvent(event: PagingDataEvent<MovementUi>) = Unit
        }
        val first = presenter()
        val collecting = backgroundScope.launch { pages.collectLatest { first.collectFrom(it) } }
        runCurrent()
        assertEquals("Interbank", first.snapshot().items.single().accountLabel)
        accounts.value = listOf(CaptureAccount("bank", "Interbank negocio", AccountKind.BANK))
        runCurrent()
        assertEquals("Interbank negocio", first.snapshot().items.single().accountLabel)
        collecting.cancel()
        val returned = presenter()
        backgroundScope.launch { pages.collectLatest { returned.collectFrom(it) } }
        runCurrent()
        assertEquals("Interbank negocio", returned.snapshot().items.single().accountLabel)
        assertEquals(1, loads)
    }
}
