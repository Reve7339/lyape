package com.lyape.notification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationParserTest {

    private fun parsed(input: NotificationInput): ParseResult.Parsed {
        val result = NotificationParser.parse(input)
        assertTrue("Se esperaba un resultado interpretado, pero fue $result", result is ParseResult.Parsed)
        return result as ParseResult.Parsed
    }

    @Test
    fun `interpreta un formato provisional válido con remitente`() {
        val result = parsed(ProvisionalNotificationFixtures.withSender)

        assertEquals(3_500L, result.amountCents)
        assertEquals("María Rojas", result.senderName)
    }

    @Test
    fun `interpreta la confirmacion de pago real anonimizada`() {
        val result = parsed(AnonymizedRealNotificationFixtures.paymentConfirmation())

        assertEquals(10L, result.amountCents)
        assertEquals("Persona Ejemplo*", result.senderName)
    }

    @Test
    fun `el codigo de seguridad no cambia la huella persistida`() {
        val first = parsed(AnonymizedRealNotificationFixtures.paymentConfirmation("000"))
        val second = parsed(AnonymizedRealNotificationFixtures.paymentConfirmation("999"))

        assertEquals(first.fingerprint, second.fingerprint)
    }

    @Test
    fun `el remitente es opcional`() {
        val result = parsed(ProvisionalNotificationFixtures.withoutSender)

        assertEquals(2_000L, result.amountCents)
        assertNull(result.senderName)
    }

    @Test
    fun `tolera espacios irregulares y espacio duro`() {
        val result = parsed(ProvisionalNotificationFixtures.irregularSpacing)

        assertEquals(128_450L, result.amountCents)
    }

    @Test
    fun `acepta la coma como separador decimal`() {
        val result = parsed(ProvisionalNotificationFixtures.commaDecimal)

        assertEquals(1_650L, result.amountCents)
    }

    @Test
    fun `usa el texto expandido cuando el principal no trae monto`() {
        val result = parsed(ProvisionalNotificationFixtures.expandedTextOnly)

        assertEquals(12_450L, result.amountCents)
        assertEquals("Carlos Acuña", result.senderName)
    }

    @Test
    fun `acepta un monto sin separador de miles`() {
        val result = parsed(ProvisionalNotificationFixtures.input(text = "Recibiste S/ 1234.50"))

        assertEquals(123_450L, result.amountCents)
    }

    @Test
    fun `interpreta la coma como separador de miles`() {
        val result = parsed(ProvisionalNotificationFixtures.input(text = "Recibiste S/ 1,284"))

        assertEquals(128_400L, result.amountCents)
    }

    @Test
    fun `rechaza el punto como separador de miles`() {
        assertEquals(
            ParseResult.Unknown,
            NotificationParser.parse(ProvisionalNotificationFixtures.input(text = "Recibiste S/ 1.284")),
        )
    }

    @Test
    fun `un formato desconocido no inventa monto`() {
        val input = ProvisionalNotificationFixtures.input(text = "Tienes una nueva función disponible")

        assertEquals(ParseResult.Unknown, NotificationParser.parse(input))
    }

    @Test
    fun `rechaza un monto que desborda Long`() {
        val input = ProvisionalNotificationFixtures.input(
            text = "Recibiste un pago de S/ 9999999999999999999999",
        )

        assertEquals(ParseResult.Unknown, NotificationParser.parse(input))
    }

    @Test
    fun `rechaza decimales inválidos sin inventar monto`() {
        assertEquals(
            ParseResult.Unknown,
            NotificationParser.parse(ProvisionalNotificationFixtures.input(text = "Recibiste S/ 35.505")),
        )
    }

    @Test
    fun `rechaza dos montos distintos por ser ambiguos`() {
        val input = ProvisionalNotificationFixtures.input(
            text = "Recibiste S/ 35.00. Saldo mostrado: S/ 100.00",
        )

        assertEquals(ParseResult.Unknown, NotificationParser.parse(input))
    }

    @Test
    fun `permite el mismo monto repetido entre texto y texto expandido`() {
        val input = ProvisionalNotificationFixtures.input(
            text = "Recibiste S/ 35.00",
            expandedText = "María Rojas te pagó S/ 35.00",
        )

        assertEquals(3_500L, parsed(input).amountCents)
    }

    @Test
    fun `rechaza agrupaciones con cero inicial`() {
        listOf("0,005", "00,123").forEach { token ->
            assertEquals(
                "Debe rechazar S/ $token",
                ParseResult.Unknown,
                NotificationParser.parse(
                    ProvisionalNotificationFixtures.input(text = "Recibiste S/ $token"),
                ),
            )
        }
    }

    @Test
    fun `un token inválido no se ignora aunque exista otro monto válido`() {
        val input = ProvisionalNotificationFixtures.input(
            text = "Valores mostrados: S/ 0,005 y S/ 25.00",
        )

        assertEquals(ParseResult.Unknown, NotificationParser.parse(input))
    }

    @Test
    fun `ignora un texto sin prefijo monetario`() {
        assertEquals(
            ParseResult.Unknown,
            NotificationParser.parse(ProvisionalNotificationFixtures.input(text = "Recibiste 35 soles")),
        )
    }

    @Test
    fun `la huella es determinista para la misma notificación`() {
        val first = parsed(ProvisionalNotificationFixtures.withSender)
        val second = parsed(ProvisionalNotificationFixtures.withSender)

        assertEquals(first.fingerprint, second.fingerprint)
    }

    @Test
    fun `montos distintos producen huellas distintas`() {
        val first = parsed(ProvisionalNotificationFixtures.input(text = "Recibiste S/ 10.00"))
        val second = parsed(ProvisionalNotificationFixtures.input(text = "Recibiste S/ 20.00"))

        assertNotEquals(first.fingerprint, second.fingerprint)
    }
}
