package com.lyape.ui.settings

import com.lyape.domain.AccountKind
import com.lyape.domain.CaptureAccount
import com.lyape.domain.CaptureAccountLink
import com.lyape.domain.CaptureConfiguration
import com.lyape.domain.CaptureProvider
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class CaptureConfigUiStateTest {

    private val provider = CaptureProvider.INTERBANK_PLIN

    @Test
    fun `un fallo de lectura se presenta como error y no como ausencia de asociacion`() = runBlocking {
        val states = flow<CaptureConfiguration> {
            throw IOException("fallo de lectura simulado")
        }.toCaptureConfigUiState().toList()

        assertEquals(listOf(CaptureConfigUiState.Loading, CaptureConfigUiState.Error), states)
        assertFalse(states.any { it is CaptureConfigUiState.Ready })
    }

    @Test
    fun `sin asociacion es un estado listo con enlace nulo`() = runBlocking {
        val states = flowOf(
            CaptureConfiguration(provider = provider, link = null, eligibleAccounts = emptyList()),
        ).toCaptureConfigUiState().toList()

        assertEquals(2, states.size)
        assertEquals(CaptureConfigUiState.Loading, states.first())
        val ready = states.last() as CaptureConfigUiState.Ready
        assertNull(ready.configuration.link)
        assertTrue(ready.configuration.eligibleAccounts.isEmpty())
    }

    @Test
    fun `la asociacion valida se conserva en el estado listo`() = runBlocking {
        val account = CaptureAccount("bank-1", "Interbank", AccountKind.BANK)
        val link = CaptureAccountLink(provider, account.id, account)
        val configuration = CaptureConfiguration(
            provider = provider,
            link = link,
            eligibleAccounts = listOf(account),
        )
        val ready = flowOf(configuration).toCaptureConfigUiState().toList().last()
            as CaptureConfigUiState.Ready

        assertEquals(configuration, ready.configuration)
        assertEquals(account, ready.configuration.link?.account)
    }
}
