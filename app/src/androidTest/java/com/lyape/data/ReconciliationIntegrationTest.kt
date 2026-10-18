package com.lyape.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lyape.domain.DateRange
import com.lyape.domain.Movement
import com.lyape.domain.MovementClassification
import com.lyape.domain.MovementSource
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

/**
 * Conciliación real contra Room: clasificar, anular y restaurar, alta manual, ajustes, edición y
 * cierre diario. Cada operación debe recalcular los totales sin perder trazabilidad y sin borrar
 * filas.
 */
@RunWith(AndroidJUnit4::class)
class ReconciliationIntegrationTest {

    private lateinit var database: AppDatabase
    private lateinit var repository: MovementRepository

    private var clock: Instant = Instant.parse("2026-10-15T20:00:00Z")

    private val zone: ZoneId = ZoneId.systemDefault()
    private val date: LocalDate = LocalDate.of(2026, 10, 15)
    private val day: DateRange get() = DateRange.day(date, zone)

    @Before
    fun createDatabase() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = MovementRepository(
            dao = database.movementDao(),
            now = { clock },
            newId = { UUID.randomUUID() },
        )
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    private fun at(hour: Int, minute: Int = 0, day: LocalDate = date): Instant =
        day.atTime(hour, minute).atZone(zone).toInstant()

    private fun movement(
        amountCents: Long = 3_500,
        classification: MovementClassification = MovementClassification.BUSINESS,
        source: MovementSource = MovementSource.NOTIFICATION,
        reviewed: Boolean = false,
        occurredAt: Instant = at(12),
    ): Movement = Movement(
        id = UUID.randomUUID(),
        amountCents = amountCents,
        occurredAt = occurredAt,
        capturedAt = occurredAt,
        source = source,
        classification = classification,
        reviewed = reviewed,
        createdAt = occurredAt,
        updatedAt = occurredAt,
        senderName = "Cliente",
        notificationKey = "clave-${UUID.randomUUID()}",
        deduplicationHash = "huella-${UUID.randomUUID()}",
    )

    // --- Clasificar ---

    @Test
    fun reclasificarRecalculaTotalesYConservaTrazabilidad() = runBlocking {
        val original = movement(amountCents = 5_000)
        repository.add(original)

        assertTrue(repository.classify(original.id, MovementClassification.PERSONAL))
        val personal = repository.findById(original.id)!!
        assertEquals(MovementClassification.PERSONAL, personal.classification)
        assertTrue(personal.isEdited)
        assertEquals(MovementSource.NOTIFICATION, personal.source)
        assertEquals(original.capturedAt, personal.capturedAt)
        assertEquals(original.notificationKey, personal.notificationKey)
        assertEquals(original.deduplicationHash, personal.deduplicationHash)

        assertEquals(0L, repository.totalsInRange(day).businessCents)
        assertEquals(5_000L, repository.totalsInRange(day).personalCents)

        repository.classify(original.id, MovementClassification.BUSINESS)
        assertEquals(5_000L, repository.totalsInRange(day).businessCents)
    }

    @Test
    fun clasificarUnMovimientoInexistenteNoCambiaNada() = runBlocking {
        assertFalse(repository.classify(UUID.randomUUID(), MovementClassification.PERSONAL))
    }

    // --- Anular y restaurar ---

    @Test
    fun anularNoBorraYRestaurarDevuelveElTotalDeNegocio() = runBlocking {
        val original = movement(amountCents = 4_000)
        repository.add(original)

        repository.exclude(original.id)
        val excluded = repository.findById(original.id)!!
        assertEquals(MovementClassification.EXCLUDED, excluded.classification)
        assertEquals(MovementClassification.BUSINESS, excluded.previousClassification)
        assertEquals(0L, repository.totalsInRange(day).businessCents)
        // La fila sigue existiendo: no hay borrado físico.
        assertEquals(1, repository.movementsInRange(day).size)

        repository.restore(original.id)
        val restored = repository.findById(original.id)!!
        assertEquals(MovementClassification.BUSINESS, restored.classification)
        assertNull(restored.previousClassification)
        assertEquals(4_000L, repository.totalsInRange(day).businessCents)
    }

    @Test
    fun restaurarUnMovimientoPersonalVuelveAPersonal() = runBlocking {
        val original = movement(amountCents = 2_500, classification = MovementClassification.PERSONAL)
        repository.add(original)

        repository.exclude(original.id)
        assertEquals(
            MovementClassification.PERSONAL,
            repository.findById(original.id)!!.previousClassification,
        )

        repository.restore(original.id)
        val restored = repository.findById(original.id)!!
        assertEquals(MovementClassification.PERSONAL, restored.classification)
        assertEquals(0L, repository.totalsInRange(day).businessCents)
        assertEquals(2_500L, repository.totalsInRange(day).personalCents)
    }

    // --- Alta manual y ajustes ---

    @Test
    fun unPagoManualEntraComoNegocioPendiente() = runBlocking {
        val id = repository.addManual(amountCents = 2_000, occurredAt = at(10), label = "Bodega")

        val manual = repository.findById(id)!!
        assertEquals(MovementSource.MANUAL, manual.source)
        assertEquals(MovementClassification.BUSINESS, manual.classification)
        assertFalse(manual.reviewed)
        assertEquals("Bodega", manual.senderName)
        assertEquals(2_000L, repository.totalsInRange(day).businessCents)
    }

    @Test
    fun losAjustesPositivosYNegativosAjustanElTotal() = runBlocking {
        repository.addManual(amountCents = 2_000, occurredAt = at(10), label = null)
        repository.addAdjustment(amountCents = 500, occurredAt = at(11), reason = "Redondeo")
        val negativeId = repository.addAdjustment(
            amountCents = -1_000,
            occurredAt = at(12),
            reason = "Devolución",
        )

        val adjustment = repository.findById(negativeId)!!
        assertEquals(MovementSource.ADJUSTMENT, adjustment.source)
        assertEquals("Devolución", adjustment.adjustmentReason)

        assertEquals(1_500L, repository.totalsInRange(day).businessCents)
    }

    @Test
    fun noSeAceptaUnAjusteEnCero() = runBlocking {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repository.addAdjustment(0L, at(11), "Nada") }
        }
        assertEquals(0, repository.movementsInRange(day).size)
    }

    // --- Edición ---

    @Test
    fun editarCambiaElMontoYConservaOrigenCapturaYHuella() = runBlocking {
        val original = movement(amountCents = 1_000, occurredAt = at(9))
        repository.add(original)
        clock = clock.plusSeconds(120)

        repository.edit(
            id = original.id,
            amountCents = 2_500,
            occurredAt = at(11),
            senderName = "  Ana  ",
            description = null,
            adjustmentReason = null,
        )

        val edited = repository.findById(original.id)!!
        assertEquals(2_500, edited.amountCents)
        assertEquals("Ana", edited.senderName)
        assertEquals(at(11), edited.occurredAt)
        assertTrue(edited.isEdited)
        assertEquals(clock, edited.updatedAt)
        assertEquals(MovementSource.NOTIFICATION, edited.source)
        assertEquals(original.capturedAt, edited.capturedAt)
        assertEquals(original.createdAt, edited.createdAt)
        assertEquals(original.deduplicationHash, edited.deduplicationHash)
        assertEquals(2_500L, repository.totalsInRange(day).businessCents)
    }

    // --- Cierre diario ---

    @Test
    fun cerrarElDiaMarcaSoloEsaFechaYReabrirNoPierdeClasificaciones() = runBlocking {
        val personal = movement(
            amountCents = 1_000,
            classification = MovementClassification.PERSONAL,
            occurredAt = at(9),
        )
        val business = movement(amountCents = 2_000, occurredAt = at(10))
        val nextDay = movement(amountCents = 3_000, occurredAt = at(9, day = date.plusDays(1)))
        repository.addAll(listOf(personal, business, nextDay))

        val closed = repository.closeDay(date)
        assertEquals(2, closed)

        assertTrue(repository.findById(personal.id)!!.reviewed)
        assertTrue(repository.findById(business.id)!!.reviewed)
        assertFalse(repository.findById(nextDay.id)!!.reviewed)
        assertEquals(MovementClassification.PERSONAL, repository.findById(personal.id)!!.classification)
        assertEquals(0, repository.observePendingOn(date).first().size)

        val reopened = repository.reopenDay(date)
        assertEquals(2, reopened)
        assertFalse(repository.findById(personal.id)!!.reviewed)
        assertFalse(repository.findById(business.id)!!.reviewed)
        assertEquals(MovementClassification.PERSONAL, repository.findById(personal.id)!!.classification)
        assertEquals(2, repository.observePendingOn(date).first().size)
    }

    @Test
    fun losAnuladosNoBloqueanElCierreYAlRestaurarlosVuelvenAPendiente() = runBlocking {
        val active = movement(amountCents = 2_000, occurredAt = at(10))
        val excluded = movement(amountCents = 1_000, occurredAt = at(11))
        repository.addAll(listOf(active, excluded))
        repository.exclude(excluded.id)

        val pendingBeforeClose = repository.observePendingOn(date).first()
        assertEquals(listOf(active.id), pendingBeforeClose.map { it.id })

        assertEquals(1, repository.closeDay(date))
        assertTrue(repository.findById(active.id)!!.reviewed)
        assertFalse(repository.findById(excluded.id)!!.reviewed)
        assertEquals(0, repository.observePendingOn(date).first().size)

        assertEquals(1, repository.reopenDay(date))
        assertFalse(repository.findById(active.id)!!.reviewed)
        assertFalse(repository.findById(excluded.id)!!.reviewed)

        repository.restore(excluded.id)
        val pendingAfterRestore = repository.observePendingOn(date).first()
        assertEquals(setOf(active.id, excluded.id), pendingAfterRestore.map { it.id }.toSet())
    }

    @Test
    fun reabrirElDiaConservaLasEdiciones() = runBlocking {
        val edited = movement(amountCents = 1_000, occurredAt = at(9))
        repository.add(edited)
        repository.edit(
            id = edited.id,
            amountCents = 1_500,
            occurredAt = at(9),
            senderName = "Corregido",
            description = null,
            adjustmentReason = null,
        )

        repository.closeDay(date)
        repository.reopenDay(date)

        val reopened = repository.findById(edited.id)!!
        assertTrue(reopened.isEdited)
        assertEquals(1_500, reopened.amountCents)
        assertEquals("Corregido", reopened.senderName)
    }

    @Test
    fun observarUnMovimientoReflejaElCambio() = runBlocking {
        val original = movement()
        repository.add(original)

        repository.classify(original.id, MovementClassification.PERSONAL)

        val observed = repository.observeById(original.id).first()!!
        assertEquals(MovementClassification.PERSONAL, observed.classification)
    }
}
