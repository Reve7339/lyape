package com.lyape.notification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InterbankPlinParserTest {

    private fun parsed(input: NotificationInput): ParseResult.Parsed {
        val result = InterbankPlinParser.parse(input)
        assertTrue("Se esperaba un resultado interpretado, pero fue $result", result is ParseResult.Parsed)
        return result as ParseResult.Parsed
    }

    private fun unknown(input: NotificationInput) {
        assertEquals(ParseResult.Unknown, InterbankPlinParser.parse(input))
    }

    @Test
    fun `interpreta la transcripcion visual real anonimizada`() {
        val result = parsed(InterbankPlinNotificationFixtures.visualTranscription())

        assertEquals(100L, result.amountCents)
        assertEquals("Persona Ejemplo", result.senderName)
    }

    @Test
    fun `tolera espacios irregulares y espacio duro`() {
        val result = parsed(
            InterbankPlinNotificationFixtures.syntheticText(
                "Persona   Ejemplo\tte  ha   plineado\u00A0S/ \t 1.00",
            ),
        )

        assertEquals(100L, result.amountCents)
        assertEquals("Persona Ejemplo", result.senderName)
    }

    @Test
    fun `interpreta el importe en la misma linea`() {
        val result = parsed(
            InterbankPlinNotificationFixtures.visualTranscription(separator = " "),
        )

        assertEquals(100L, result.amountCents)
        assertEquals("Persona Ejemplo", result.senderName)
    }

    @Test
    fun `interpreta centimos y separador de miles`() {
        assertEquals(
            1_050L,
            parsed(InterbankPlinNotificationFixtures.visualTranscription(amount = "10.50")).amountCents,
        )
        assertEquals(
            10L,
            parsed(InterbankPlinNotificationFixtures.visualTranscription(amount = "0.10")).amountCents,
        )
        assertEquals(
            128_450L,
            parsed(InterbankPlinNotificationFixtures.visualTranscription(amount = "1,284.50")).amountCents,
        )
    }

    @Test
    fun `el remitente es opcional`() {
        val result = parsed(
            InterbankPlinNotificationFixtures.syntheticText("Te ha plineado\nS/ 1.00"),
        )

        assertEquals(100L, result.amountCents)
        assertNull(result.senderName)
    }

    @Test
    fun `no atribuye el titulo Interbank como remitente`() {
        val result = parsed(
            InterbankPlinNotificationFixtures.syntheticText(
                text = "Te ha plineado\nS/ 1.00",
                title = "Interbank",
            ),
        )

        assertNull(result.senderName)
    }

    @Test
    fun `usa el texto expandido si el principal no trae el importe`() {
        val result = parsed(
            InterbankPlinNotificationFixtures.syntheticText(
                text = "Persona Ejemplo te ha plineado",
                expandedText = "S/ 1.00",
            ),
        )

        assertEquals(100L, result.amountCents)
        assertEquals("Persona Ejemplo", result.senderName)
    }

    @Test
    fun `rechaza un texto sin la frase de ingreso`() {
        unknown(InterbankPlinNotificationFixtures.syntheticText("Recibiste S/ 1.00"))
    }

    @Test
    fun `rechaza un envio`() {
        unknown(
            InterbankPlinNotificationFixtures.syntheticText(
                "Has plineado a Persona Ejemplo\nS/ 1.00",
            ),
        )
    }

    @Test
    fun `rechaza un envio aunque mencione la frase`() {
        unknown(
            InterbankPlinNotificationFixtures.syntheticText(
                "Has plineado a Persona Ejemplo. Persona Ejemplo te ha plineado\nS/ 1.00",
            ),
        )
    }

    @Test
    fun `rechaza una promocion`() {
        unknown(
            InterbankPlinNotificationFixtures.syntheticText(
                "Promoción: paga con Plin y gana\nS/ 1.00",
            ),
        )
    }

    @Test
    fun `rechaza un saldo`() {
        unknown(
            InterbankPlinNotificationFixtures.syntheticText(
                "Saldo disponible\nS/ 1.00",
            ),
        )
    }

    @Test
    fun `rechaza un fallo`() {
        unknown(
            InterbankPlinNotificationFixtures.syntheticText(
                "No se pudo completar el Plin\nS/ 1.00",
            ),
        )
    }

    @Test
    fun `rechaza un fallo aunque mencione la frase`() {
        unknown(
            InterbankPlinNotificationFixtures.syntheticText(
                "No se pudo completar. Persona Ejemplo te ha plineado\nS/ 1.00",
            ),
        )
    }

    @Test
    fun `rechaza una frase negada`() {
        unknown(
            InterbankPlinNotificationFixtures.syntheticText(
                "No te ha plineado Persona Ejemplo\nS/ 1.00",
            ),
        )
    }

    @Test
    fun `rechaza un resumen agrupado aunque mencione la frase`() {
        unknown(
            InterbankPlinNotificationFixtures.syntheticText(
                "Resumen: Persona Ejemplo te ha plineado\nS/ 1.00",
            ),
        )
    }

    @Test
    fun `rechaza un saldo con un segundo importe`() {
        unknown(
            InterbankPlinNotificationFixtures.syntheticText(
                "Persona Ejemplo te ha plineado\nS/ 1.00\nSaldo disponible: S/ 100.00",
            ),
        )
    }

    @Test
    fun `rechaza varios importes distintos`() {
        unknown(
            InterbankPlinNotificationFixtures.syntheticText(
                "Persona Ejemplo te ha plineado\nS/ 1.00\nOtro: S/ 2.00",
            ),
        )
    }

    @Test
    fun `acepta el mismo importe repetido`() {
        val result = parsed(
            InterbankPlinNotificationFixtures.syntheticText(
                "Persona Ejemplo te ha plineado\nS/ 1.00\nTotal: S/ 1.00",
            ),
        )

        assertEquals(100L, result.amountCents)
    }

    @Test
    fun `rechaza dos remitentes con el mismo importe en un campo`() {
        unknown(
            InterbankPlinNotificationFixtures.syntheticText(
                "Ana te ha plineado S/ 1.00\nLuis te ha plineado S/ 1.00",
            ),
        )
    }

    @Test
    fun `rechaza dos frases del mismo remitente y importe en un campo`() {
        unknown(
            InterbankPlinNotificationFixtures.syntheticText(
                "Ana te ha plineado S/ 1.00\nAna te ha plineado S/ 1.00",
            ),
        )
    }

    @Test
    fun `rechaza dos remitentes con el mismo importe en campos distintos`() {
        unknown(
            InterbankPlinNotificationFixtures.syntheticText(
                text = "Ana te ha plineado S/ 1.00",
                expandedText = "Luis te ha plineado S/ 1.00",
            ),
        )
    }

    @Test
    fun `acepta el mismo mensaje individual repetido en texto y expandido`() {
        val message = "Ana te ha plineado S/ 1.00"
        val result = parsed(
            InterbankPlinNotificationFixtures.syntheticText(
                text = message,
                expandedText = message,
            ),
        )

        assertEquals(100L, result.amountCents)
        assertEquals("Ana", result.senderName)
    }

    @Test
    fun `rechaza dos operaciones repetidas en ambos campos`() {
        val message = "Ana te ha plineado S/ 1.00\nLuis te ha plineado S/ 1.00"

        unknown(
            InterbankPlinNotificationFixtures.syntheticText(
                text = message,
                expandedText = message,
            ),
        )
    }

    @Test
    fun `acepta el importe repetido como total en otro campo sin segunda operacion`() {
        val result = parsed(
            InterbankPlinNotificationFixtures.syntheticText(
                text = "Persona Ejemplo te ha plineado S/ 1.00",
                expandedText = "Total: S/ 1.00",
            ),
        )

        assertEquals(100L, result.amountCents)
        assertEquals("Persona Ejemplo", result.senderName)
    }

    @Test
    fun `rechaza cero`() {
        unknown(InterbankPlinNotificationFixtures.visualTranscription(amount = "0.00"))
    }

    @Test
    fun `rechaza un importe negativo`() {
        unknown(
            InterbankPlinNotificationFixtures.syntheticText(
                "Persona Ejemplo te ha plineado\n-S/ 1.00",
            ),
        )
    }

    @Test
    fun `rechaza un importe que desborda Long`() {
        unknown(
            InterbankPlinNotificationFixtures.visualTranscription(
                amount = "9999999999999999999999",
            ),
        )
    }

    @Test
    fun `rechaza decimales invalidos`() {
        unknown(InterbankPlinNotificationFixtures.visualTranscription(amount = "35.505"))
    }

    @Test
    fun `rechaza un importe con sufijo exponencial`() {
        unknown(
            InterbankPlinNotificationFixtures.syntheticText(
                "Persona Ejemplo te ha plineado S/ 1.00e3",
            ),
        )
    }

    @Test
    fun `rechaza un importe con sufijo alfabetico`() {
        unknown(
            InterbankPlinNotificationFixtures.syntheticText(
                "Persona Ejemplo te ha plineado S/ 1.00abc",
            ),
        )
    }

    @Test
    fun `rechaza un segundo punto decimal`() {
        unknown(
            InterbankPlinNotificationFixtures.syntheticText(
                "Persona Ejemplo te ha plineado S/ 1.00.50",
            ),
        )
    }

    @Test
    fun `rechaza un token con puntuacion interna y digitos`() {
        listOf(
            "Persona Ejemplo te ha plineado S/ 1:00",
            "Persona Ejemplo te ha plineado S/ 1;00",
            "Persona Ejemplo te ha plineado S/ 1)00",
            "Persona Ejemplo te ha plineado S/ 1.00)abc",
        ).forEach { text ->
            unknown(InterbankPlinNotificationFixtures.syntheticText(text))
        }
    }

    @Test
    fun `rechaza un token invalido junto a otro valido del mismo importe`() {
        unknown(
            InterbankPlinNotificationFixtures.syntheticText(
                "Persona Ejemplo te ha plineado S/ 1.00e3 y S/ 1.00",
            ),
        )
    }

    @Test
    fun `rechaza un token invalido en el texto expandido`() {
        unknown(
            InterbankPlinNotificationFixtures.syntheticText(
                text = "Persona Ejemplo te ha plineado S/ 1.00",
                expandedText = "S/ 1.00e3",
            ),
        )
    }

    @Test
    fun `conserva la puntuacion final admitida`() {
        listOf(
            "Persona Ejemplo te ha plineado S/ 1.00.",
            "Persona Ejemplo te ha plineado S/ 1.00, y listo",
            "Persona Ejemplo te ha plineado (S/ 1.00)",
            "Persona Ejemplo te ha plineado \"S/ 1.00\"",
            "Persona Ejemplo te ha plineado 'S/ 1.00'",
            "Persona Ejemplo te ha plineado S/ 1.00;",
            "Persona Ejemplo te ha plineado S/ 1.00:",
        ).forEach { text ->
            assertEquals(
                text,
                100L,
                parsed(InterbankPlinNotificationFixtures.syntheticText(text)).amountCents,
            )
        }
    }

    @Test
    fun `rechaza un importe anterior a la frase`() {
        unknown(
            InterbankPlinNotificationFixtures.syntheticText(
                "S/ 1.00 Persona Ejemplo te ha plineado",
            ),
        )
    }

    @Test
    fun `rechaza un importe en el titulo`() {
        unknown(
            InterbankPlinNotificationFixtures.syntheticText(
                text = "Persona Ejemplo te ha plineado",
                title = "Interbank S/ 1.00",
            ),
        )
    }

    @Test
    fun `rechaza un importe sin prefijo monetario`() {
        unknown(
            InterbankPlinNotificationFixtures.syntheticText(
                "Persona Ejemplo te ha plineado\n1.00 soles",
            ),
        )
    }

    @Test
    fun `no acepta un prefijo no confirmado`() {
        unknown(
            InterbankPlinNotificationFixtures.syntheticText(
                "Persona Ejemplo te ha plineado\nS/. 1.00",
            ),
        )
    }

    @Test
    fun `no inventa un remitente a partir de un texto no plausible`() {
        val result = parsed(
            InterbankPlinNotificationFixtures.syntheticText(
                "Pago 123 te ha plineado\nS/ 1.00",
            ),
        )

        assertEquals(100L, result.amountCents)
        assertNull(result.senderName)
    }

    @Test
    fun `no atribuye una etiqueta con dos puntos como remitente`() {
        val result = parsed(
            InterbankPlinNotificationFixtures.syntheticText(
                "Nuevo pago: Persona Ejemplo te ha plineado\nS/ 1.00",
            ),
        )

        assertEquals(100L, result.amountCents)
        assertNull(result.senderName)
    }

    @Test
    fun `la huella es determinista para la misma notificacion`() {
        val first = parsed(InterbankPlinNotificationFixtures.visualTranscription())
        val second = parsed(InterbankPlinNotificationFixtures.visualTranscription())

        assertEquals(first.fingerprint, second.fingerprint)
    }

    @Test
    fun `importes distintos producen huellas distintas`() {
        val first = parsed(InterbankPlinNotificationFixtures.visualTranscription(amount = "1.00"))
        val second = parsed(InterbankPlinNotificationFixtures.visualTranscription(amount = "2.00"))

        assertNotEquals(first.fingerprint, second.fingerprint)
    }
}
