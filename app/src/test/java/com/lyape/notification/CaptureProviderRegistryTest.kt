package com.lyape.notification

import com.lyape.domain.CaptureProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class CaptureProviderRegistryTest {

    private val fakeInterbankPackage = "test.fake.interbank"
    private val at = Instant.parse("2026-10-25T12:00:00Z")

    @Test
    fun `el paquete confirmado de Interbank esta habilitado provisionalmente`() {
        val yape = CaptureProviders.production.forPackage(
            YapePackage.PROVISIONAL_PACKAGE_ID,
        )

        assertEquals(CaptureProvider.YAPE, yape?.provider)
        assertTrue(yape!!.enabled)
        assertEquals(CaptureProvider.INTERBANK_PLIN, CaptureProviders.production.forPackage(InterbankPackage.PACKAGE_NAME)?.provider)
        assertNull(CaptureProviders.production.forPackage(fakeInterbankPackage))
        assertNull(CaptureProviders.production.forPackage("com.otra.app"))
    }

    @Test
    fun `un proveedor falso habilitado resuelve solo su paquete`() {
        val spec = CaptureProviderSpec(
            provider = CaptureProvider.INTERBANK_PLIN,
            packageNames = setOf(fakeInterbankPackage),
            enabled = true,
            parser = NotificationPayloadParser { ParseResult.Unknown },
        )
        val registry = CaptureProviderRegistry(listOf(spec))

        assertEquals(spec, registry.forPackage(fakeInterbankPackage))
        assertNull(registry.forPackage("com.otra.app"))
        assertFalse(spec.acceptsPackage("com.otra.app"))
    }

    @Test
    fun `un proveedor deshabilitado no captura aunque declare un paquete`() {
        val disabled = CaptureProviderSpec(
            provider = CaptureProvider.INTERBANK_PLIN,
            packageNames = setOf(fakeInterbankPackage),
            enabled = false,
            parser = NotificationPayloadParser { ParseResult.Unknown },
        )

        assertNull(CaptureProviderRegistry(listOf(disabled)).forPackage(fakeInterbankPackage))
        assertFalse(disabled.acceptsPackage(fakeInterbankPackage))
    }

    @Test
    fun `el parser Interbank real solo procesa el paquete falso registrado`() {
        val spec = CaptureProviderSpec(
            provider = CaptureProvider.INTERBANK_PLIN,
            packageNames = setOf(fakeInterbankPackage),
            enabled = true,
            parser = NotificationPayloadParser(InterbankPlinParser::parse),
        )
        val matching = NotificationInput(
            packageName = fakeInterbankPackage,
            key = null,
            title = "Interbank",
            text = "Persona Ejemplo te ha plineado\nS/ 1.00",
            postedAt = at,
        )

        val parsed = spec.parse(matching) as ParseResult.Parsed
        assertEquals(100L, parsed.amountCents)
        assertEquals("Persona Ejemplo", parsed.senderName)
        assertEquals(
            ParseResult.Unknown,
            spec.parse(matching.copy(packageName = "com.otra.app")),
        )
    }
}
