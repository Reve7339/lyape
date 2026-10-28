package com.lyape.ui.accounts

import com.lyape.domain.CaptureAccount
import com.lyape.domain.CaptureAccountLink
import com.lyape.domain.CaptureConfiguration
import com.lyape.domain.CaptureProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class AccountsCaptureStateTest {

    @Test
    fun `un fallo se expone como Error y una nueva suscripcion puede quedar Ready`() = runBlocking {
        var subscriptions = 0
        val source = flow {
            subscriptions++
            if (subscriptions == 1) throw IOException("consulta local")
            emit(listOf("cuenta"))
        }

        assertEquals(AccountLoadState.Error, source.accountLoadState().first())
        assertEquals(
            AccountLoadState.Ready(listOf("cuenta")),
            source.accountLoadState().first(),
        )
        assertEquals(2, subscriptions)
    }

    @Test
    fun `una configuracion sana no muestra aviso de reparacion`() {
        val account = CaptureAccount(id = "banco", name = "Banco", kind = com.lyape.domain.AccountKind.BANK)
        val configuration = CaptureConfiguration(
            provider = CaptureProvider.INTERBANK_PLIN,
            link = CaptureAccountLink(CaptureProvider.INTERBANK_PLIN, "banco", account),
            eligibleAccounts = listOf(account),
        )

        assertTrue(listOf(configuration).brokenCaptureLinks().isEmpty())
    }

    @Test
    fun `un vinculo roto se muestra pero una asociacion ausente no es una reparacion`() {
        val broken = CaptureConfiguration(
            provider = CaptureProvider.INTERBANK_PLIN,
            link = CaptureAccountLink(CaptureProvider.INTERBANK_PLIN, "borrada", null),
            eligibleAccounts = emptyList(),
        )
        val absent = CaptureConfiguration(
            provider = CaptureProvider.YAPE,
            link = null,
            eligibleAccounts = emptyList(),
        )

        assertEquals(listOf(broken), listOf(broken, absent).brokenCaptureLinks())
    }
}
