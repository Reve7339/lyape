package com.lyape.notification

import android.app.Notification
import android.os.Bundle
import android.os.Process
import android.service.notification.StatusBarNotification
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lyape.data.AccountsRepository
import com.lyape.data.AppDatabase
import com.lyape.data.MovementRepository
import com.lyape.domain.CaptureProvider
import com.lyape.domain.MovementClassification
import com.lyape.domain.MovementSource
import com.lyape.domain.PaymentMedium
import com.lyape.preferences.AppPreferences
import com.lyape.testing.testMovements
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException
import java.time.Instant

/**
 * Recorrido real de la captura sobre un `StatusBarNotification` simulado y Room en memoria.
 *
 * Cubre el adaptador (extracción segura y filtro por paquete) y el ingestor (parser →
 * deduplicación → repositorio). No sustituye la prueba física en el teléfono objetivo.
 */
@RunWith(AndroidJUnit4::class)
class CapturePipelineIntegrationTest {

    private lateinit var database: AppDatabase
    private lateinit var repository: MovementRepository
    private lateinit var accounts: AccountsRepository
    private lateinit var preferences: AppPreferences
    private lateinit var ingestor: NotificationIngestor

    private val yapeSource: CaptureProviderSpec =
        requireNotNull(CaptureProviders.production.forPackage(YapePackage.PROVISIONAL_PACKAGE_ID))
    private val fakeInterbankPackage = "test.fake.interbank"
    private val fakeInterbankSource = CaptureProviderSpec(
        provider = CaptureProvider.INTERBANK_PLIN,
        packageNames = setOf(fakeInterbankPackage),
        enabled = true,
        parser = NotificationPayloadParser(NotificationParser::parse),
    )

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = MovementRepository(database.movementDao())
        accounts = AccountsRepository(database)
        preferences = AppPreferences(context)
        ingestor = NotificationIngestor(
            repository = repository,
            preferences = preferences,
            accountResolver = accounts,
        )
        // Las preferencias viven en el proceso y no se borran entre pruebas: se parte siempre de
        // "sin pausar".
        runBlocking { preferences.setCapturePaused(false) }
    }

    @After
    fun tearDown() {
        database.close()
    }

    // --- Adaptador ---

    @Test fun proveedorInterbankDeProduccionGuardaUnaFilaYUnEventoConCentimos() = runBlocking {
        val source = requireNotNull(CaptureProviders.production.forPackage(InterbankPackage.PACKAGE_NAME))
        val bank = accounts.addAccount("Interbank")
        accounts.setCaptureAccount(CaptureProvider.INTERBANK_PLIN, bank)
        val input = requireInput(statusBar(notification(title = "Interbank", text = "Persona Ejemplo te ha plineado\nS/ 1.25"), pkg = InterbankPackage.PACKAGE_NAME), source)
        val first = ingestor.ingest(input, source)
        assertTrue(first is IngestResult.Stored)
        assertEquals(PaymentMedium.PLIN, (first as IngestResult.Stored).payment.medium)
        assertDuplicate(ingestor.ingest(input, source))
        val rows = database.movementDao().getInRange(0, Long.MAX_VALUE)
        assertEquals(1, rows.size)
        assertEquals(bank, rows.single().accountId)
        assertEquals(125L, rows.single().amountCents)
        assertStored(ingestor.ingest(input.copy(key = "otro-cobro", postedAt = input.postedAt.plusSeconds(1)), source))
        assertEquals(2, database.movementDao().getInRange(0, Long.MAX_VALUE).size)
    }

    @Test fun resumenDeGrupoSeDescartaAntesDelParser() {
        val summary = notification().apply { flags = flags or Notification.FLAG_GROUP_SUMMARY }
        assertNull(NotificationAdapter.toInput(statusBar(summary), yapeSource))
    }

    @Test
    fun extraeLosCamposDeUnaNotificacionValida() {
        val sbn = statusBar(
            notification(
                text = "María Rojas te pagó S/ 35.00",
                bigText = "María Rojas te pagó S/ 35.00\nDetalle adicional",
            ),
        )

        val input = NotificationAdapter.toInput(sbn, yapeSource)

        assertNotNull(input)
        input!!
        assertEquals(YapePackage.PROVISIONAL_PACKAGE_ID, input.packageName)
        assertEquals("Yape", input.title)
        assertEquals("María Rojas te pagó S/ 35.00", input.text)
        assertEquals("María Rojas te pagó S/ 35.00\nDetalle adicional", input.expandedText)
        assertEquals(POST_TIME, input.postedAt.toEpochMilli())
    }

    @Test
    fun toleraCamposAusentesSinLanzarExcepciones() {
        val input = NotificationAdapter.toInput(
            statusBar(Notification().apply { extras = Bundle() }),
            yapeSource,
        )

        assertNotNull(input)
        assertEquals("", input!!.title)
        assertEquals("", input.text)
        assertNull(input.expandedText)
    }

    @Test
    fun unaNotificacionSinContenidoNoProduceEntrada() {
        assertNull(
            NotificationAdapter.toInput(
                statusBar(Notification().apply { extras = null }),
                yapeSource,
            ),
        )
    }

    @Test
    fun descartaOtraAplicacionAntesDeLeerElContenido() {
        val foreign = statusBar(
            notification(text = "María Rojas te pagó S/ 35.00"),
            pkg = "com.otra.app",
        )

        assertNull(NotificationAdapter.toInput(foreign, yapeSource))
        assertNull(CaptureProviders.production.forPackage("com.otra.app"))
        assertNotNull(CaptureProviders.production.forPackage(YapePackage.PROVISIONAL_PACKAGE_ID))
    }

    @Test
    fun unProveedorFalsoSeFiltraPorSuPaqueteAntesDeLeerExtras() {
        val foreign = statusBar(
            notification(text = "Persona Ejemplo te ha plineado\nS/ 1.00"),
            pkg = "com.otra.app",
        )
        val fake = statusBar(
            notification(text = "Persona Ejemplo te ha plineado\nS/ 1.00"),
            pkg = fakeInterbankPackage,
        )

        assertNull(NotificationAdapter.toInput(foreign, fakeInterbankSource))
        val input = NotificationAdapter.toInput(fake, fakeInterbankSource)
        assertNotNull(input)
        assertEquals(fakeInterbankPackage, input!!.packageName)
    }

    // --- Recorrido completo ---

    @Test
    fun unaNotificacionValidaGuardaExactamenteUnMovimiento() = runBlocking {
        val input = requireInput(statusBar(notification(text = "María Rojas te pagó S/ 35.00")))

        val result = ingestor.ingest(input, yapeSource)
        assertStored(result)
        val event = (result as IngestResult.Stored).payment
        assertEquals(3_500L, event.amountCents)
        assertEquals(PaymentMedium.YAPE, event.medium)

        val stored = repository.testMovements()
        assertEquals(1, stored.size)
        val movement = stored.single()
        assertEquals(3_500L, movement.amountCents)
        assertEquals(MovementSource.NOTIFICATION, movement.source)
        assertEquals(PaymentMedium.YAPE, movement.medium)
        assertEquals("yape", movement.accountId)
        assertEquals(MovementClassification.BUSINESS, movement.classification)
        assertFalse(movement.reviewed)
        assertEquals("María Rojas", movement.senderName)
        assertEquals(POST_TIME, movement.occurredAt.toEpochMilli())
    }

    @Test
    fun laEntregaDobleGuardaSoloUnMovimiento() = runBlocking {
        val input = requireInput(statusBar(notification(text = "María Rojas te pagó S/ 35.00")))

        assertStored(ingestor.ingest(input, yapeSource))
        assertDuplicate(ingestor.ingest(input, yapeSource))
        assertEquals(1, repository.testMovements().size)
    }

    @Test
    fun lasEntregasConcurrentesGuardanSoloUnMovimiento() = runBlocking {
        val input = requireInput(statusBar(notification(text = "María Rojas te pagó S/ 35.00")))

        val results = withContext(Dispatchers.Default) {
            listOf(
                async { ingestor.ingest(input, yapeSource) },
                async { ingestor.ingest(input, yapeSource) },
                async { ingestor.ingest(input, yapeSource) },
            ).awaitAll()
        }

        assertEquals(1, results.count { it is IngestResult.Stored })
        assertEquals(2, results.count { it is IngestResult.Duplicate })
        assertEquals(1, repository.testMovements().size)
    }

    @Test
    fun unFormatoDesconocidoNoCreaMovimientos() = runBlocking {
        val input = requireInput(statusBar(notification(text = "Tienes una función nueva disponible")))

        assertUnrecognized(ingestor.ingest(input, yapeSource))
        assertTrue(repository.testMovements().isEmpty())
    }

    @Test
    fun laCapturaPausadaNoCreaMovimientos() = runBlocking {
        preferences.setCapturePaused(true)
        val input = requireInput(statusBar(notification(text = "María Rojas te pagó S/ 35.00")))

        assertPaused(ingestor.ingest(input, yapeSource))
        assertTrue(repository.testMovements().isEmpty())
    }

    @Test
    fun unFalloDePreferenciasImpideGuardarUnPago() = runBlocking {
        val failingStore = object : DataStore<Preferences> {
            override val data: Flow<Preferences> = flow { throw IOException("fallo simulado") }

            override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
                error("La captura no debe escribir preferencias")
        }
        val blockedIngestor = NotificationIngestor(
            repository,
            AppPreferences(failingStore),
            accounts,
        )
        val input = requireInput(statusBar(notification(text = "María Rojas te pagó S/ 35.00")))

        assertPaused(blockedIngestor.ingest(input, yapeSource))
        assertTrue(repository.testMovements().isEmpty())
    }

    @Test
    fun laMismaClaveYElMismoInstanteEsUnaEntregaDuplicada() = runBlocking {
        val first = input(key = "clave-repetida", text = "María Rojas te pagó S/ 35.00")
        val second = input(key = "clave-repetida", text = "Carlos Acuña te pagó S/ 20.00")

        assertStored(ingestor.ingest(first, yapeSource))
        assertDuplicate(ingestor.ingest(second, yapeSource))
        assertEquals(1, repository.testMovements().size)
    }

    @Test
    fun laMismaClaveConHoraDistintaGuardaDosMovimientos() = runBlocking {
        val first = input(key = "clave-repetida", text = "María Rojas te pagó S/ 35.00")
        val second = input(
            key = "clave-repetida",
            text = "Carlos Acuña te pagó S/ 20.00",
            postedAt = POST_TIME + 3_600_000,
        )

        assertStored(ingestor.ingest(first, yapeSource))
        assertStored(ingestor.ingest(second, yapeSource))
        assertEquals(2, repository.testMovements().size)
    }

    @Test
    fun clavesDistintasConLaMismaHuellaGuardanDosMovimientos() = runBlocking {
        // Mismo monto y texto (misma huella) pero claves de Android distintas: son pagos distintos.
        val first = input(key = "clave-a", text = "María Rojas te pagó S/ 35.00")
        val second = input(key = "clave-b", text = "María Rojas te pagó S/ 35.00")

        assertStored(ingestor.ingest(first, yapeSource))
        assertStored(ingestor.ingest(second, yapeSource))
        assertEquals(2, repository.testMovements().size)
    }

    @Test
    fun laHuellaEsRespaldoCuandoElGuardadoNoTieneClave() = runBlocking {
        val first = input(key = null, text = "María Rojas te pagó S/ 35.00")
        val second = input(key = "clave-a", text = "María Rojas te pagó S/ 35.00")

        assertStored(ingestor.ingest(first, yapeSource))
        assertDuplicate(ingestor.ingest(second, yapeSource))
        assertEquals(1, repository.testMovements().size)
    }

    @Test
    fun laHuellaEsRespaldoCuandoElCandidatoNoTieneClave() = runBlocking {
        val first = input(key = "clave-a", text = "María Rojas te pagó S/ 35.00")
        val second = input(key = null, text = "María Rojas te pagó S/ 35.00")

        assertStored(ingestor.ingest(first, yapeSource))
        assertDuplicate(ingestor.ingest(second, yapeSource))
        assertEquals(1, repository.testMovements().size)
    }

    private fun assertStored(result: IngestResult) {
        assertTrue("Se esperaba guardado, pero fue $result", result is IngestResult.Stored)
    }

    private fun assertDuplicate(result: IngestResult) {
        assertTrue("Se esperaba duplicado, pero fue $result", result is IngestResult.Duplicate)
    }

    private fun assertPaused(result: IngestResult) {
        assertTrue("Se esperaba pausado, pero fue $result", result is IngestResult.Paused)
    }

    private fun assertUnrecognized(result: IngestResult) {
        assertTrue("Se esperaba desconocido, pero fue $result", result is IngestResult.Unrecognized)
    }

    private fun input(
        key: String?,
        text: String,
        postedAt: Long = POST_TIME,
    ): NotificationInput = NotificationInput(
        packageName = YapePackage.PROVISIONAL_PACKAGE_ID,
        key = key,
        title = "Yape",
        text = text,
        postedAt = Instant.ofEpochMilli(postedAt),
    )

    private fun requireInput(
        sbn: StatusBarNotification,
        source: CaptureProviderSpec = yapeSource,
    ): NotificationInput =
        NotificationAdapter.toInput(sbn, source)
            ?: error("La notificación de prueba no produjo entrada.")

    @Suppress("DEPRECATION")
    private fun statusBar(
        notification: Notification,
        pkg: String = YapePackage.PROVISIONAL_PACKAGE_ID,
    ): StatusBarNotification = StatusBarNotification(
        pkg,
        pkg,
        1,
        null,
        1000,
        1000,
        0,
        notification,
        Process.myUserHandle(),
        POST_TIME,
    )

    private fun notification(
        text: String = "María Rojas te pagó S/ 35.00",
        title: String = "Yape",
        bigText: String? = null,
    ): Notification = Notification().apply {
        extras = Bundle().apply {
            putCharSequence(Notification.EXTRA_TITLE, title)
            putCharSequence(Notification.EXTRA_TEXT, text)
            if (bigText != null) putCharSequence(Notification.EXTRA_BIG_TEXT, bigText)
        }
    }

    private companion object {
        /** Instante fijo y sintético (2025-10-15T22:00:00Z); no proviene de datos personales. */
        const val POST_TIME = 1_760_464_800_000L
    }
}
