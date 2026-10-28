package com.lyape.ui.accounts

import com.lyape.domain.AccountBalance
import com.lyape.domain.AccountKind
import com.lyape.domain.MovementClassification
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class AccountFormValidationTest {
    private val accounts = listOf(
        AccountBalance("yape", "Yape", AccountKind.YAPE, true, 10_000L, 2_000L, Instant.EPOCH),
        AccountBalance("cash", "Efectivo", AccountKind.CASH, true, 4_000L, 0L, Instant.EPOCH),
        AccountBalance("bank", "Banco", AccountKind.BANK, false, 0L, 0L, null),
    )

    @Test fun `no se configura cero silenciosamente con campos vacios`() {
        val result = validateAccountForm("balance", AccountFormValues(), accounts)
        assertFalse(result.valid)
        assertTrue("business" in result.errors)
        assertTrue("personal" in result.errors)
    }

    @Test fun `el usuario puede declarar cero explicitamente en ambos saldos`() {
        val result = validateAccountForm("balance", AccountFormValues(business = "0", personal = "0.00"), accounts)
        assertTrue(result.valid)
        assertEquals(0L, result.businessCents)
        assertEquals(0L, result.personalCents)
    }

    @Test fun `saldo total no puede desbordar aunque cada importe sea valido`() {
        val result = validateAccountForm("balance", AccountFormValues(business = "92233720368547758.07", personal = "0.01"), accounts)
        assertFalse(result.valid)
        assertTrue("personal" in result.errors)
    }

    @Test fun `transferencia valida conserva propietario y centimos exactos`() {
        val values = AccountFormValues(fromId = "yape", toId = "cash", amount = "12,34")
        val result = validateAccountForm("transfer", values, accounts)
        assertTrue(result.valid)
        assertEquals(1_234L, result.amountCents)
    }

    @Test fun `transferencia necesita cuentas distintas configuradas`() {
        val values = AccountFormValues(fromId = "yape", toId = "yape", amount = "10")
        assertTrue("to" in validateAccountForm("transfer", values, accounts).errors)
        assertTrue("to" in validateAccountForm("transfer", values.copy(toId = "bank"), accounts).errors)
        assertTrue("from" in validateAccountForm("transfer", values.copy(fromId = "bank", toId = "cash"), accounts).errors)
    }

    @Test fun `no se gasta dinero personal usando el saldo del negocio`() {
        val values = AccountFormValues(fromId = "yape", amount = "25", classification = MovementClassification.PERSONAL)
        assertTrue("amount" in validateAccountForm("withdraw", values, accounts).errors)
        assertTrue(validateAccountForm("withdraw", values.copy(classification = MovementClassification.BUSINESS), accounts).valid)
    }

    @Test fun `salida necesita un importe positivo`() {
        listOf("0", "-1", "35.505", "1,284.50", "").forEach { amount ->
            assertFalse(validateAccountForm("withdraw", AccountFormValues(fromId = "yape", amount = amount), accounts).valid)
        }
    }

    @Test fun `anulado no es propietario de dinero`() {
        val values = AccountFormValues(fromId = "yape", amount = "1", classification = MovementClassification.EXCLUDED)
        assertTrue("classification" in validateAccountForm("withdraw", values, accounts).errors)
    }

    @Test fun `nombre y nota tienen limites con error visible`() {
        assertFalse(validateAccountForm("add", AccountFormValues(name = "  "), accounts).valid)
        assertFalse(validateAccountForm("add", AccountFormValues(name = "x".repeat(61)), accounts).valid)
        assertTrue(validateAccountForm("add", AccountFormValues(name = "Mi banco"), accounts).valid)
        assertTrue("note" in validateAccountForm("withdraw", AccountFormValues(fromId = "yape", amount = "1", note = "x".repeat(241)), accounts).errors)
    }

    @Test fun `serializacion conserva todos los campos y propietario`() {
        val values = AccountFormValues("Banco", "10.50", "2", "yape", "cash", "1.25", MovementClassification.PERSONAL, "Retiro")
        assertEquals(values, restoreAccountForm(values.saved()))
    }

    @Test fun `serializacion rechaza contenido incompleto y propietario invalido`() {
        assertNull(restoreAccountForm(listOf("Banco")))
        val invalid = AccountFormValues().saved().toMutableList().apply { this[6] = "EXCLUDED" }
        assertNull(restoreAccountForm(invalid))
        invalid[6] = "UNKNOWN"
        assertNull(restoreAccountForm(invalid))
    }

    @Test fun `saldo parcial se declara con singular y plural`() {
        assertEquals("Falta configurar 1 cuenta.", unconfiguredAccountsLabel(1))
        assertEquals("Falta configurar 2 cuentas.", unconfiguredAccountsLabel(2))
    }
}
