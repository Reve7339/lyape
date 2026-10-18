package com.lyape.data

import androidx.paging.PagingConfig
import org.junit.Assert.*
import org.junit.Test

class HistoryPagingConfigTest {
    @Test
    fun `las cargas y la retencion del historial estan acotadas`() {
        assertEquals(50, HISTORY_PAGING_CONFIG.pageSize)
        assertEquals(50, HISTORY_PAGING_CONFIG.initialLoadSize)
        assertEquals(150, HISTORY_PAGING_CONFIG.maxSize)
        assertFalse(HISTORY_PAGING_CONFIG.enablePlaceholders)
        assertNotEquals(PagingConfig.MAX_SIZE_UNBOUNDED, HISTORY_PAGING_CONFIG.maxSize)
        assertTrue(HISTORY_PAGING_CONFIG.pageSize + 2 * HISTORY_PAGING_CONFIG.prefetchDistance <= HISTORY_PAGING_CONFIG.maxSize)
    }
}
