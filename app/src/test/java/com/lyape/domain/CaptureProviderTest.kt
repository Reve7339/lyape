package com.lyape.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class CaptureProviderTest {

    @Test
    fun `yape usa medio Yape y su cuenta integrada`() {
        assertEquals(PaymentMedium.YAPE, CaptureProvider.YAPE.medium)
        assertEquals(AccountKind.YAPE, CaptureProvider.YAPE.requiredAccountKind)
    }

    @Test
    fun `Plin Interbank usa medio Plin y exige una cuenta bancaria`() {
        assertEquals(PaymentMedium.PLIN, CaptureProvider.INTERBANK_PLIN.medium)
        assertEquals(AccountKind.BANK, CaptureProvider.INTERBANK_PLIN.requiredAccountKind)
        assertNotEquals(AccountKind.YAPE, CaptureProvider.INTERBANK_PLIN.requiredAccountKind)
    }

    @Test
    fun `ningun proveedor usa el medio desconocido`() {
        CaptureProvider.entries.forEach {
            assertNotEquals(PaymentMedium.UNKNOWN, it.medium)
        }
    }
}
