package com.lyape.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lyape.domain.AccountKind
import com.lyape.domain.CaptureProvider
import com.lyape.domain.Movement
import com.lyape.domain.MovementClassification
import com.lyape.domain.MovementSource
import com.lyape.domain.PaymentMedium
import com.lyape.notification.CaptureProviderSpec
import com.lyape.notification.IngestResult
import com.lyape.notification.NotificationIngestor
import com.lyape.notification.NotificationInput
import com.lyape.notification.NotificationParser
import com.lyape.notification.NotificationPayloadParser
import com.lyape.notification.YapePackage
import com.lyape.preferences.AppPreferences
import com.lyape.testing.testMovements
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.util.UUID

/**
 * C1 y preparación C2 contra Room en memoria: asociación proveedor → cuenta, medio persistido,
 * ausencia de fallback a Yape y despacho con un proveedor **falso**.
 *
 * Interbank usa un paquete ficticio solo en pruebas y un parser genérico; no se ejecuta captura
 * Interbank real ni se afirma compatibilidad física.
 */
@RunWith(AndroidJUnit4::class)
class CaptureAccountsIntegrationTest {

    private lateinit var database: AppDatabase
    private lateinit var accounts: AccountsRepository
    private lateinit var movements: MovementRepository
    private lateinit var ingestor: NotificationIngestor
    private var clock = Instant.parse("2026-10-25T12:00:00Z")

    /** Paquete ficticio: no es el de Interbank y no existe fuera de estas pruebas. */
    private val fakeInterbankPackage = "test.fake.interbank"
    private val yapeSource = CaptureProviderSpec(
        provider = CaptureProvider.YAPE,
        packageNames = setOf(YapePackage.PROVISIONAL_PACKAGE_ID),
        enabled = true,
        parser = NotificationPayloadParser(NotificationParser::parse),
    )
    private val plinSource = CaptureProviderSpec(
        provider = CaptureProvider.INTERBANK_PLIN,
        packageNames = setOf(fakeInterbankPackage),
        enabled = true,
        parser = NotificationPayloadParser(NotificationParser::parse),
    )

    @Before
    fun setUp(): Unit = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        accounts = AccountsRepository(database, now = { clock })
        movements = MovementRepository(database.movementDao(), now = { clock })
        ingestor = NotificationIngestor(
            repository = movements,
            preferences = AppPreferences(MemoryPreferences()),
            accountResolver = accounts,
        )
        accounts.observeSummary().first()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun yapeYPlinTienenCuentaPorDefectoSinConfiguracionManual() = runBlocking {
        assertEquals("yape", accounts.accountIdFor(CaptureProvider.YAPE))
        assertEquals("plin", accounts.accountIdFor(CaptureProvider.INTERBANK_PLIN))
        assertEquals(
            "yape",
            database.accountsDao().captureAccount(CaptureProvider.YAPE.name)?.accountId,
        )
        assertEquals("plin", database.accountsDao().captureAccount(CaptureProvider.INTERBANK_PLIN.name)?.accountId)
        assertEquals("BANK", database.accountsDao().account("plin")?.kind)
        assertEquals("Plin", database.accountsDao().account("plin")?.name)
    }

    @Test
    fun inicializacionConcurrenteNoDuplicaCuentasNiReiniciaSaldos() = runBlocking {
        database.openHelper.writableDatabase.execSQL(
            "DELETE FROM capture_accounts WHERE provider = 'INTERBANK_PLIN'",
        )
        database.openHelper.writableDatabase.execSQL("DELETE FROM cash_accounts WHERE id = 'plin'")
        withContext(Dispatchers.Default) {
            (1..8).map { async { accounts.captureAccountLink(CaptureProvider.INTERBANK_PLIN) } }.awaitAll()
        }
        accounts.setBalance("plin", 12_000, 3_000)
        withContext(Dispatchers.Default) {
            (1..8).map { async { accounts.captureAccountLink(CaptureProvider.INTERBANK_PLIN) } }.awaitAll()
        }
        assertEquals(setOf("yape", "cash", "plin"), database.accountsDao().observeAccounts().first().map { it.id }.toSet())
        val balance = accounts.observeAccount("plin").first()!!
        assertEquals(12_000L, balance.businessCents)
        assertEquals(3_000L, balance.personalCents)
    }

    @Test
    fun actualizacionConservaCuentaPlinPreviaSinCrearOtra() = runBlocking {
        // Simula una instalación anterior que ya tenía su propia cuenta asociada.
        val bank = accounts.addAccount("Mi Plin")
        accounts.setCaptureAccount(CaptureProvider.INTERBANK_PLIN, bank)
        database.openHelper.writableDatabase.execSQL("DELETE FROM cash_accounts WHERE id = 'plin'")
        accounts.setBalance(bank, 9_000, 1_000)
        clock = clock.plusSeconds(1)
        assertStored(ingestor.ingest(input("plin-previo", fakeInterbankPackage), plinSource))
        val before = accounts.observeAccount(bank).first()
        val recordsBefore = movements.testMovements()

        val reopened = AccountsRepository(database, now = { clock })
        assertEquals(bank, reopened.accountIdFor(CaptureProvider.INTERBANK_PLIN))
        assertEquals(before, reopened.observeAccount(bank).first())
        assertEquals(recordsBefore, movements.testMovements())
        assertNull(database.accountsDao().account("plin"))
        assertEquals(3, database.accountsDao().observeAccounts().first().size)
    }

    @Test
    fun unaAsociacionRotaSeRestableceAlRegistroLocalSinMoverHistorial() = runBlocking {
        val bank = accounts.addAccount("Interbank temporal")
        accounts.setBalance(bank, 7_000, 1_000)
        accounts.setCaptureAccount(CaptureProvider.INTERBANK_PLIN, bank)
        clock = clock.plusSeconds(1)
        movements.add(
            Movement(
                id = UUID.randomUUID(),
                amountCents = 500,
                occurredAt = clock,
                capturedAt = clock,
                source = MovementSource.NOTIFICATION,
                classification = MovementClassification.PERSONAL,
                reviewed = false,
                createdAt = clock,
                updatedAt = clock,
                accountId = bank,
                medium = PaymentMedium.PLIN,
            ),
        )
        val recordsBefore = movements.testMovements()
        database.openHelper.writableDatabase.execSQL("DELETE FROM cash_accounts WHERE id = ?", arrayOf(bank))

        assertNull(accounts.captureAccountLink(CaptureProvider.INTERBANK_PLIN)?.account)

        assertEquals(
            CaptureLinkRepair.REPAIRED,
            accounts.repairCaptureLink(CaptureProvider.INTERBANK_PLIN),
        )

        assertEquals("plin", accounts.accountIdFor(CaptureProvider.INTERBANK_PLIN))
        assertEquals("plin", database.accountsDao().captureAccount(CaptureProvider.INTERBANK_PLIN.name)?.accountId)
        assertEquals("BANK", database.accountsDao().account("plin")?.kind)
        assertEquals(recordsBefore, movements.testMovements())
    }

    @Test
    fun repararUnVinculoValidoNoRedirigeNiCreaOtroRegistroPlin() = runBlocking {
        val bank = accounts.addAccount("Mi cuenta personalizada")
        accounts.setCaptureAccount(CaptureProvider.INTERBANK_PLIN, bank)
        database.openHelper.writableDatabase.execSQL("DELETE FROM cash_accounts WHERE id = 'plin'")
        val recordsBefore = movements.testMovements()

        val result = accounts.repairCaptureLink(CaptureProvider.INTERBANK_PLIN)

        assertEquals(CaptureLinkRepair.ALREADY_VALID, result)
        assertEquals(
            bank,
            database.accountsDao().captureAccount(CaptureProvider.INTERBANK_PLIN.name)?.accountId,
        )
        assertNull(database.accountsDao().account("plin"))
        assertEquals(recordsBefore, movements.testMovements())
    }

    @Test
    fun unaSolicitudDesactualizadaNoRedirigeUnVinculoQueYaEsValido() = runBlocking {
        val broken = accounts.addAccount("Interbank temporal")
        val repairedElsewhere = accounts.addAccount("Interbank vigente")
        accounts.setCaptureAccount(CaptureProvider.INTERBANK_PLIN, broken)
        database.openHelper.writableDatabase.execSQL("DELETE FROM cash_accounts WHERE id = ?", arrayOf(broken))
        assertNull(accounts.captureAccountLink(CaptureProvider.INTERBANK_PLIN)?.account)

        accounts.setCaptureAccount(CaptureProvider.INTERBANK_PLIN, repairedElsewhere)
        val result = accounts.repairCaptureLink(CaptureProvider.INTERBANK_PLIN)

        assertEquals(CaptureLinkRepair.ALREADY_VALID, result)
        assertEquals(repairedElsewhere, accounts.accountIdFor(CaptureProvider.INTERBANK_PLIN))
        assertNull(database.accountsDao().account("plin"))
    }

    @Test
    fun repararEsIdempotenteYLasLlamadasConcurrentesNoDuplican() = runBlocking {
        val bank = accounts.addAccount("Interbank temporal")
        accounts.setCaptureAccount(CaptureProvider.INTERBANK_PLIN, bank)
        database.openHelper.writableDatabase.execSQL("DELETE FROM cash_accounts WHERE id = ?", arrayOf(bank))

        val results = withContext(Dispatchers.Default) {
            (1..8).map { async { accounts.repairCaptureLink(CaptureProvider.INTERBANK_PLIN) } }.awaitAll()
        }
        assertEquals(1, results.count { it == CaptureLinkRepair.REPAIRED })
        assertEquals(7, results.count { it == CaptureLinkRepair.ALREADY_VALID })
        assertEquals(
            CaptureLinkRepair.ALREADY_VALID,
            accounts.repairCaptureLink(CaptureProvider.INTERBANK_PLIN),
        )
        assertEquals(1, database.accountsDao().observeAccounts().first().count { it.id == "plin" })
        assertEquals(
            "plin",
            database.accountsDao().captureAccount(CaptureProvider.INTERBANK_PLIN.name)?.accountId,
        )
    }

    @Test
    fun unRegistroPredeterminadoIncompatibleNoModificaNada() = runBlocking {
        val bank = accounts.addAccount("Interbank temporal")
        accounts.setCaptureAccount(CaptureProvider.INTERBANK_PLIN, bank)
        database.openHelper.writableDatabase.execSQL("DELETE FROM cash_accounts WHERE id = 'plin'")
        database.accountsDao().insertAccount(
            CashAccountEntity("plin", "Plin", AccountKind.YAPE.name, null, 0, 0, 0, 0),
        )
        database.openHelper.writableDatabase.execSQL("DELETE FROM cash_accounts WHERE id = ?", arrayOf(bank))
        val recordsBefore = movements.testMovements()

        expectInvalid { accounts.repairCaptureLink(CaptureProvider.INTERBANK_PLIN) }

        assertEquals(
            bank,
            database.accountsDao().captureAccount(CaptureProvider.INTERBANK_PLIN.name)?.accountId,
        )
        assertEquals("YAPE", database.accountsDao().account("plin")?.kind)
        assertEquals(recordsBefore, movements.testMovements())
    }

    @Test
    fun plinSoloSeAsociaAUnaCuentaBancariaExistente() = runBlocking {
        val bank = accounts.addAccount("Cuenta Interbank")
        accounts.setCaptureAccount(CaptureProvider.INTERBANK_PLIN, bank)

        val link = accounts.captureAccountLink(CaptureProvider.INTERBANK_PLIN)
        assertNotNull(link)
        assertEquals(bank, link!!.accountId)
        assertEquals(bank, link.account?.id)
        assertEquals(bank, accounts.accountIdFor(CaptureProvider.INTERBANK_PLIN))

        expectInvalid { accounts.setCaptureAccount(CaptureProvider.INTERBANK_PLIN, "yape") }
        expectInvalid { accounts.setCaptureAccount(CaptureProvider.INTERBANK_PLIN, "cash") }
        expectInvalid { accounts.setCaptureAccount(CaptureProvider.INTERBANK_PLIN, "no-existe") }
    }

    @Test
    fun cambiarLaAsociacionNoReescribeElHistorialNiLosSaldos() = runBlocking {
        val first = accounts.addAccount("Interbank uno")
        val second = accounts.addAccount("Interbank dos")
        accounts.setBalance(first, 10_000, 0)
        accounts.setCaptureAccount(CaptureProvider.INTERBANK_PLIN, first)
        clock = clock.plusSeconds(1)
        val movement = Movement(
            id = UUID.randomUUID(),
            amountCents = 500,
            occurredAt = clock,
            capturedAt = clock,
            source = MovementSource.NOTIFICATION,
            classification = MovementClassification.BUSINESS,
            reviewed = false,
            createdAt = clock,
            updatedAt = clock,
            accountId = first,
            medium = PaymentMedium.PLIN,
        )
        movements.add(movement)
        val balanceBefore = accounts.observeAccount(first).first()!!

        accounts.setCaptureAccount(CaptureProvider.INTERBANK_PLIN, second)

        assertEquals(first, movements.findById(movement.id)!!.accountId)
        assertEquals(PaymentMedium.PLIN, movements.findById(movement.id)!!.medium)
        assertEquals(balanceBefore, accounts.observeAccount(first).first())
        assertEquals(second, accounts.accountIdFor(CaptureProvider.INTERBANK_PLIN))
    }

    @Test
    fun saldoBaseClasificacionYAnulacionConservanLosBuckets() = runBlocking {
        val bank = accounts.addAccount("Cuenta Interbank")
        accounts.setBalance(bank, 10_000, 0)
        clock = clock.plusSeconds(1)
        val movement = Movement(
            id = UUID.randomUUID(),
            amountCents = 500,
            occurredAt = clock,
            capturedAt = clock,
            source = MovementSource.NOTIFICATION,
            classification = MovementClassification.PERSONAL,
            reviewed = false,
            createdAt = clock,
            updatedAt = clock,
            accountId = bank,
            medium = PaymentMedium.PLIN,
        )
        movements.add(movement)

        var balance = accounts.observeAccount(bank).first()!!
        assertEquals(10_000L, balance.businessCents)
        assertEquals(500L, balance.personalCents)

        movements.exclude(movement.id)
        balance = accounts.observeAccount(bank).first()!!
        assertEquals(10_000L, balance.businessCents)
        assertEquals(0L, balance.personalCents)
        assertNotNull(movements.findById(movement.id))

        movements.restore(movement.id)
        balance = accounts.observeAccount(bank).first()!!
        assertEquals(10_000L, balance.businessCents)
        assertEquals(500L, balance.personalCents)
    }

    @Test
    fun cuentaValidaSeResuelveAunqueFalleSuCalculoDeSaldo() = runBlocking {
        val bank = accounts.addAccount("Interbank")
        accounts.setBalance(bank, Long.MAX_VALUE, 0)
        clock = clock.plusSeconds(1)
        movements.add(
            Movement(
                id = UUID.randomUUID(),
                amountCents = 1,
                occurredAt = clock,
                capturedAt = clock,
                source = MovementSource.NOTIFICATION,
                classification = MovementClassification.BUSINESS,
                reviewed = false,
                createdAt = clock,
                updatedAt = clock,
                accountId = bank,
                medium = PaymentMedium.PLIN,
            ),
        )

        try {
            accounts.observeAccount(bank).first()
            fail("Se esperaba que el cálculo de saldo desbordara")
        } catch (_: ArithmeticException) {
        }

        accounts.setCaptureAccount(CaptureProvider.INTERBANK_PLIN, bank)
        val link = accounts.captureAccountLink(CaptureProvider.INTERBANK_PLIN)
        assertNotNull(link)
        assertEquals(bank, link!!.accountId)
        assertEquals("Interbank", link.account?.name)

        val configuration = accounts.observeCaptureConfiguration(
            CaptureProvider.INTERBANK_PLIN,
        ).first()
        assertEquals("Interbank", configuration.link?.account?.name)
        assertTrue(configuration.eligibleAccounts.any { it.id == bank })
        assertEquals(bank, accounts.accountIdFor(CaptureProvider.INTERBANK_PLIN))

        clock = clock.plusSeconds(1)
        assertStored(
            ingestor.ingest(input("despues-del-desborde", fakeInterbankPackage), plinSource),
        )
        val captured = movements.testMovements().first {
            it.notificationKey == "despues-del-desborde"
        }
        assertEquals(PaymentMedium.PLIN, captured.medium)
        assertEquals(bank, captured.accountId)
    }

    @Test
    fun unPagoYapeConservaSuMedioYCuentaExplicita() = runBlocking {
        assertStored(ingestor.ingest(input("yape-1"), yapeSource))

        val stored = movements.testMovements().single()
        assertEquals(PaymentMedium.YAPE, stored.medium)
        assertEquals("yape", stored.accountId)
    }

    @Test
    fun primerPagoPlinSeRegistraSinElegirCuentaYNuncaEnYape() = runBlocking {
        assertStored(
            ingestor.ingest(input("plin-sin-cuenta", fakeInterbankPackage), plinSource),
        )
        val stored = movements.testMovements().single()
        assertEquals("plin", stored.accountId)
        assertEquals(PaymentMedium.PLIN, stored.medium)
        assertEquals(0, accounts.observeAccount("yape").first()!!.businessCents)
    }

    @Test
    fun conAsociacionInterbankGuardaMedioYCuentaBancaria() = runBlocking {
        val bank = accounts.addAccount("Cuenta Interbank")
        accounts.setCaptureAccount(CaptureProvider.INTERBANK_PLIN, bank)

        assertStored(
            ingestor.ingest(input("plin-con-cuenta", fakeInterbankPackage), plinSource),
        )

        val stored = movements.testMovements().single()
        assertEquals(PaymentMedium.PLIN, stored.medium)
        assertEquals(bank, stored.accountId)
        assertFalse(stored.accountId == "yape")
    }

    @Test
    fun laDobleEntregaConcurrenteDelProveedorFalsoGuardaUnaSolaFila() = runBlocking {
        val bank = accounts.addAccount("Cuenta Interbank")
        accounts.setCaptureAccount(CaptureProvider.INTERBANK_PLIN, bank)
        val input = input("plin-concurrente", fakeInterbankPackage)

        val results = withContext(Dispatchers.Default) {
            listOf(
                async { ingestor.ingest(input, plinSource) },
                async { ingestor.ingest(input, plinSource) },
                async { ingestor.ingest(input, plinSource) },
            ).awaitAll()
        }

        assertEquals(1, results.count { it is IngestResult.Stored })
        assertEquals(2, results.count { it is IngestResult.Duplicate })
        assertEquals(1, movements.testMovements().size)
        assertEquals(PaymentMedium.PLIN, movements.testMovements().single().medium)
    }

    @Test
    fun unaCuentaAsociadaEliminadaBloqueaNuevasCapturasSinBorrarLaAsociacion() = runBlocking {
        val bank = accounts.addAccount("Cuenta Interbank")
        accounts.setCaptureAccount(CaptureProvider.INTERBANK_PLIN, bank)

        database.openHelper.writableDatabase.execSQL(
            "DELETE FROM cash_accounts WHERE id = ?",
            arrayOf(bank),
        )

        val link = accounts.captureAccountLink(CaptureProvider.INTERBANK_PLIN)
        assertNotNull(link)
        assertNull(link!!.account)
        assertNull(accounts.accountIdFor(CaptureProvider.INTERBANK_PLIN))
        assertUnconfigured(
            ingestor.ingest(input("plin-cuenta-eliminada", fakeInterbankPackage), plinSource),
        )
        assertTrue(movements.testMovements().isEmpty())
        assertEquals(
            bank,
            database.accountsDao().captureAccount(CaptureProvider.INTERBANK_PLIN.name)?.accountId,
        )
    }

    private suspend fun expectInvalid(block: suspend () -> Unit) {
        try {
            block()
            fail("Se esperaba rechazo de la asociación")
        } catch (_: IllegalArgumentException) {
        }
    }

    private fun assertStored(result: IngestResult) {
        assertTrue("Se esperaba guardado, pero fue $result", result is IngestResult.Stored)
    }

    private fun assertUnconfigured(result: IngestResult) {
        assertTrue("Se esperaba sin cuenta, pero fue $result", result is IngestResult.Unconfigured)
    }

    private fun input(
        key: String,
        packageName: String = YapePackage.PROVISIONAL_PACKAGE_ID,
    ) = NotificationInput(
        packageName = packageName,
        key = key,
        title = "Yape",
        text = "María Rojas te pagó S/ 35.00",
        postedAt = clock,
    )

    private class MemoryPreferences : DataStore<Preferences> {
        private val state = MutableStateFlow(emptyPreferences())

        override val data: Flow<Preferences> = state

        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
            val updated = transform(state.value)
            state.value = updated
            return updated
        }
    }
}
