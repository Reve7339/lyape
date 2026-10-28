package com.lyape.data

import com.lyape.domain.CaptureProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La resolución de captura se construye con filas crudas de `cash_accounts`, no con saldos.
 * La prueba Room complementaria fuerza un desbordamiento de saldo y comprueba que la cuenta
 * sigue resolviéndose.
 */
class CaptureConfigurationTest {

    private val yape = account("yape", "Yape", "YAPE")
    private val bank = account("bank-1", "Interbank", "BANK")

    private fun account(id: String, name: String, kind: String) = CashAccountEntity(
        id = id,
        name = name,
        kind = kind,
        baselineAt = null,
        baselineBusinessCents = 0,
        baselinePersonalCents = 0,
        baselineOperationSequence = 0,
    )

    @Test
    fun `una cuenta bancaria valida se resuelve solo con datos crudos`() {
        val configuration = captureConfigurationFor(
            provider = CaptureProvider.INTERBANK_PLIN,
            association = CaptureAccountEntity(CaptureProvider.INTERBANK_PLIN.name, bank.id),
            accounts = listOf(yape, bank),
        )

        assertEquals("bank-1", configuration.link?.accountId)
        assertEquals("Interbank", configuration.link?.account?.name)
        assertEquals(listOf("bank-1"), configuration.eligibleAccounts.map { it.id })
    }

    @Test
    fun `una asociacion a Yape no se resuelve para Plin`() {
        val configuration = captureConfigurationFor(
            provider = CaptureProvider.INTERBANK_PLIN,
            association = CaptureAccountEntity(CaptureProvider.INTERBANK_PLIN.name, yape.id),
            accounts = listOf(yape, bank),
        )

        assertEquals("yape", configuration.link?.accountId)
        assertNull(configuration.link?.account)
        assertEquals(listOf("bank-1"), configuration.eligibleAccounts.map { it.id })
    }

    @Test
    fun `sin asociacion el enlace es nulo pero la cuenta sigue disponible`() {
        val configuration = captureConfigurationFor(
            provider = CaptureProvider.INTERBANK_PLIN,
            association = null,
            accounts = listOf(yape, bank),
        )

        assertNull(configuration.link)
        assertTrue(configuration.eligibleAccounts.any { it.id == "bank-1" })
    }
}
