package com.lyape.notification

import org.junit.Assert.*
import org.junit.Test

class CaptureAdmissionTest {
    private val source = requireNotNull(CaptureProviders.production.forPackage(InterbankPackage.PACKAGE_NAME))
    @Test fun `solo acepta paquete confirmado y usuario principal`() {
        assertTrue(acceptsCaptureEnvelope(source, InterbankPackage.PACKAGE_NAME, 0, true, false))
        assertFalse(acceptsCaptureEnvelope(source, "otra.app", 0, true, false))
        assertFalse(acceptsCaptureEnvelope(source, InterbankPackage.PACKAGE_NAME, 150, false, false))
        assertFalse(acceptsCaptureEnvelope(source, InterbankPackage.PACKAGE_NAME, 0, false, false))
    }
    @Test fun `un resumen de grupo nunca se analiza como un cobro`() {
        assertFalse(acceptsCaptureEnvelope(source, InterbankPackage.PACKAGE_NAME, 0, true, true))
    }
}
