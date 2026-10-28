package com.lyape.notification

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.preferencesOf
import com.lyape.data.AccountUnavailableException
import com.lyape.data.CaptureAccountResolver
import com.lyape.data.CaptureMovementStore
import com.lyape.domain.CaptureProvider
import com.lyape.domain.Movement
import com.lyape.preferences.AppPreferences
import com.lyape.voice.TestDataStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException
import java.time.Duration
import java.time.Instant
import java.util.UUID

class NotificationIngestorTest {

    private val packageName = "test.yape"
    private val postedAt = Instant.parse("2026-10-26T12:00:00Z")
    private val fixedId = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val pausedKey = booleanPreferencesKey("capture_paused")

    private val source = CaptureProviderSpec(
        provider = CaptureProvider.YAPE,
        packageNames = setOf(packageName),
        enabled = true,
        parser = NotificationPayloadParser {
            ParseResult.Parsed(
                amountCents = 1_000L,
                senderName = "Persona Ejemplo",
                fingerprint = "huella",
            )
        },
    )

    private fun input() = NotificationInput(
        packageName = packageName,
        key = "clave",
        title = "Yape",
        text = "Persona Ejemplo te pagó S/ 10.00",
        postedAt = postedAt,
    )

    private fun ingestor(
        store: FakeStore,
        resolver: FakeResolver = FakeResolver(),
        prefs: TestDataStore = TestDataStore(preferencesOf(pausedKey to false)),
    ): NotificationIngestor = NotificationIngestor(
        repository = store,
        preferences = AppPreferences(prefs),
        accountResolver = resolver,
        now = { postedAt },
        newId = { fixedId },
    )

    @Test
    fun `un fallo al consultar duplicados no guarda ni produce evento`() = runBlocking {
        val store = FakeStore().apply { dedupFailure = IOException("consulta") }
        val result = ingestor(store).ingest(input(), source)

        assertEquals(
            IngestResult.Failed(IngestFailure.DEDUPLICATION_READ),
            result,
        )
        assertTrue(store.added.isEmpty())
    }

    @Test
    fun `un fallo al resolver la cuenta no se presenta como cuenta ausente`() = runBlocking {
        val store = FakeStore()
        val resolver = FakeResolver().apply { readFailure = IOException("lectura") }
        val result = ingestor(store, resolver).ingest(input(), source)

        assertEquals(IngestResult.Failed(IngestFailure.ACCOUNT_READ), result)
        assertTrue(result !is IngestResult.Unconfigured)
        assertTrue(store.added.isEmpty())
    }

    @Test
    fun `una cuenta realmente inexistente si es Unconfigured`() = runBlocking {
        val store = FakeStore()
        val result = ingestor(store, FakeResolver(accountId = null)).ingest(input(), source)

        assertEquals(IngestResult.Unconfigured(CaptureProvider.YAPE), result)
        assertTrue(store.added.isEmpty())
    }

    @Test
    fun `un fallo de insercion no produce evento Stored`() = runBlocking {
        val store = FakeStore().apply { addFailure = IOException("insercion") }
        val result = ingestor(store).ingest(input(), source)

        assertEquals(IngestResult.Failed(IngestFailure.PERSISTENCE), result)
        assertTrue(store.added.isEmpty())
    }

    @Test
    fun `una cuenta invalidada al insertar es Unconfigured y no un fallo generico`() = runBlocking {
        val store = FakeStore().apply {
            addFailure = AccountUnavailableException("La cuenta ya no existe.")
        }
        val result = ingestor(store).ingest(input(), source)

        assertEquals(IngestResult.Unconfigured(CaptureProvider.YAPE), result)
    }

    @Test
    fun `otro IllegalArgumentException no se disfraza de cuenta invalida`() = runBlocking {
        val store = FakeStore().apply {
            addFailure = IllegalArgumentException("dato invalido")
        }
        val result = ingestor(store).ingest(input(), source)

        assertEquals(IngestResult.Failed(IngestFailure.PERSISTENCE), result)
        assertTrue(result !is IngestResult.Unconfigured)
    }

    @Test
    fun `un fallo del parser es un fallo identificable`() = runBlocking {
        val store = FakeStore()
        val parser = NotificationPayloadParser { throw IOException("parser") }
        val parserSource = source.copy(parser = parser)
        val result = ingestor(store).ingest(input(), parserSource)

        assertEquals(IngestResult.Failed(IngestFailure.PARSE), result)
    }

    @Test
    fun `una preferencia ilegible mantiene silencio y no guarda`() = runBlocking {
        val store = FakeStore()
        val prefs = TestDataStore(preferencesOf(pausedKey to false)).apply {
            readFailure = IOException("preferencias")
        }
        val result = ingestor(store, prefs = prefs).ingest(input(), source)

        assertEquals(IngestResult.Paused, result)
        assertTrue(store.added.isEmpty())
    }

    @Test
    fun `un guardado confirmado expone solo el evento minimo`() = runBlocking {
        val store = FakeStore()
        val result = ingestor(store).ingest(input(), source)

        assertTrue(result is IngestResult.Stored)
        val payment = (result as IngestResult.Stored).payment
        assertEquals(fixedId, payment.id)
        assertEquals(CaptureProvider.YAPE.medium, payment.medium)
        assertEquals(1_000L, payment.amountCents)
        assertEquals(postedAt, payment.occurredAt)
        assertEquals(1, store.added.size)
    }

    @Test
    fun `dos entregas concurrentes producen una fila y un evento`() = runBlocking {
        val store = FakeStore()
        val subject = ingestor(store)
        val results = coroutineScope {
            List(2) { async { subject.ingest(input(), source) } }.awaitAll()
        }

        assertEquals(1, results.count { it is IngestResult.Stored })
        assertEquals(1, results.count { it is IngestResult.Duplicate })
        assertEquals(1, store.added.size)
    }

    @Test
    fun `un pago legitimo siguiente no se descarta`() = runBlocking {
        val store = FakeStore()
        val subject = ingestor(store)

        assertTrue(subject.ingest(input(), source) is IngestResult.Stored)
        val next = input().copy(key = "clave-2", postedAt = postedAt.plusSeconds(60))
        assertTrue(subject.ingest(next, source) is IngestResult.Stored)
        assertEquals(2, store.added.size)
    }

    @Test
    fun `un cupon o una negacion no guardan ni producen evento de voz`() = runBlocking {
        val parserSource = source.copy(parser = NotificationPayloadParser(NotificationParser::parse))
        listOf(
            "Recibiste un cupón de S/ 10.00",
            "No te llegó un pago por S/ 10.00",
            "Nunca recibiste un pago por S/ 10.00",
        ).forEach { text ->
            val store = FakeStore()
            val result = ingestor(store).ingest(input().copy(text = text), parserSource)

            assertEquals(IngestResult.Unrecognized, result)
            assertTrue(store.added.isEmpty())
        }
    }

    @Test
    fun `un cobro valido conserva una sola captura y su evento`() = runBlocking {
        val parserSource = source.copy(parser = NotificationPayloadParser(NotificationParser::parse))
        val store = FakeStore()

        val result = ingestor(store).ingest(
            input().copy(text = "Persona Ejemplo* te envió un pago por S/ 10.00. " +
                "El cód. de seguridad es: 123"),
            parserSource,
        )

        assertTrue(result is IngestResult.Stored)
        assertEquals(1, store.added.size)
    }

    @Test
    fun `una evidencia mixta con remitente no reconocido no guarda ni publica voz`() = runBlocking {
        val parserSource = source.copy(parser = NotificationPayloadParser(NotificationParser::parse))
        val store = FakeStore()
        val ambiguous = input().copy(
            title = "Confirmación de Pago",
            text = "Ana te pagó S/ 10.00",
            expandedText = "Recibiste un pago de Luis por S/ 10.00",
        )

        val result = ingestor(store).ingest(ambiguous, parserSource)

        assertEquals(IngestResult.Unrecognized, result)
        assertTrue(store.added.isEmpty())
    }

    @Test
    fun `un monto no positivo del parser no se guarda`() = runBlocking {
        val store = FakeStore()
        val zeroParser = source.copy(parser = NotificationPayloadParser {
            ParseResult.Parsed(amountCents = 0L, senderName = null, fingerprint = "huella")
        })
        val negativeParser = source.copy(parser = NotificationPayloadParser {
            ParseResult.Parsed(amountCents = -1L, senderName = null, fingerprint = "huella")
        })

        assertEquals(IngestResult.Unrecognized, ingestor(store).ingest(input(), zeroParser))
        assertEquals(IngestResult.Unrecognized, ingestor(store).ingest(input(), negativeParser))
        assertTrue(store.added.isEmpty())
    }

    @Test
    fun `la cancelacion se propaga y nunca se convierte en Stored`() = runBlocking {
        val cancellation = CancellationException("cancelado")
        val store = FakeStore().apply { dedupFailure = cancellation }
        try {
            ingestor(store).ingest(input(), source)
            fail("La cancelacion debe propagarse")
        } catch (actual: CancellationException) {
            assertSame(cancellation, actual)
        }
        assertTrue(store.added.isEmpty())
    }

    private class FakeStore : CaptureMovementStore {
        var dedupFailure: Exception? = null
        var addFailure: Exception? = null
        val added = mutableListOf<Movement>()

        override suspend fun findDedupCandidates(
            candidate: Movement,
            window: Duration,
        ): List<Movement> {
            dedupFailure?.let { throw it }
            return added.toList()
        }

        override suspend fun add(movement: Movement) {
            addFailure?.let { throw it }
            added += movement
        }
    }

    private class FakeResolver(
        var accountId: String? = "cuenta-1",
        var readFailure: Exception? = null,
    ) : CaptureAccountResolver {
        override suspend fun accountIdFor(provider: CaptureProvider): String? {
            readFailure?.let { throw it }
            return accountId
        }
    }
}
