package com.lyape.acceptance

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lyape.data.AppDatabase
import com.lyape.data.MonthlyPlanRepository
import com.lyape.data.MovementRepository
import com.lyape.domain.DateRange
import com.lyape.domain.ExpenseSchedule
import com.lyape.domain.GoalMode
import com.lyape.domain.MovementClassification
import com.lyape.notification.IngestResult
import com.lyape.notification.NotificationIngestor
import com.lyape.notification.NotificationInput
import com.lyape.notification.YapePackage
import com.lyape.preferences.AppPreferences
import com.lyape.testing.testMovements
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** Recorridos de negocio reales contra Room, no pruebas de compatibilidad física con Yape. */
@RunWith(AndroidJUnit4::class)
class BusinessJourneyAcceptanceTest {
    private lateinit var database: AppDatabase
    private lateinit var movements: MovementRepository
    private lateinit var plans: MonthlyPlanRepository
    private lateinit var preferences: AppPreferences
    private lateinit var ingestor: NotificationIngestor
    private var previousPause = false
    private val date = LocalDate.of(2026, 10, 15)
    private val now = date.atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant()
    private val month = YearMonth.from(date)

    @Before
    fun givenAnIsolatedDatabase() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        movements = MovementRepository(database.movementDao(), now = { now })
        plans = MonthlyPlanRepository(database.monthlyPlanDao())
        preferences = AppPreferences(context)
        previousPause = preferences.capturePaused.first()
        preferences.setCapturePaused(false)
        ingestor = NotificationIngestor(movements, preferences, now = { now })
    }

    @After
    fun restorePreferencesAndCloseDatabase() = runBlocking {
        try {
            preferences.setCapturePaused(previousPause)
        } finally {
            database.close()
        }
    }

    @Test
    fun bdd08DuplicateDeliveryAndPauseResume() = runBlocking {
        // Dada una notificación de S/ 35 entregada dos veces.
        val input = input("pago-1", now)
        assertEquals(IngestResult.STORED, ingestor.ingest(input))
        assertEquals(IngestResult.DUPLICATE, ingestor.ingest(input))
        // Cuando pauso la captura, otro aviso no se guarda; al reanudar se acepta.
        preferences.setCapturePaused(true)
        val next = input("pago-2", now.plusSeconds(60))
        assertEquals(IngestResult.PAUSED, ingestor.ingest(next))
        assertEquals(1, movements.testMovements().size)
        preferences.setCapturePaused(false)
        assertEquals(IngestResult.STORED, ingestor.ingest(next))
        // Entonces hay exactamente dos pagos, incluso si su remitente y monto coinciden.
        val stored = movements.testMovements()
        assertEquals(2, stored.size)
        assertEquals(7_000L, movements.totalsInRange(DateRange.day(date)).businessCents)
        assertTrue(stored.all { it.senderName == "Cliente Prueba" && !it.reviewed })
    }

    @Test
    fun bdd09ReconciliationCloseAndReopenKeepIncome() = runBlocking {
        // Dado un pago capturado S/ 35, uno manual S/ 50 y una devolución S/ 5.
        assertEquals(IngestResult.STORED, ingestor.ingest(input("pago-3", now)))
        val captured = movements.testMovements().single()
        val manual = movements.addManual(5_000, now, "Venta")
        movements.addAdjustment(-500, now, "Devolución")
        // Cuando separo el pago personal, anulo/restauro la venta y cierro/reabro el día.
        movements.classify(captured.id, MovementClassification.PERSONAL)
        movements.exclude(manual)
        assertEquals(-500L, movements.totalsInRange(DateRange.day(date)).businessCents)
        movements.restore(manual)
        assertEquals(3, movements.closeDay(date))
        assertTrue(movements.observePendingOn(date).first().isEmpty())
        assertEquals(4_500L, movements.totalsInRange(DateRange.day(date)).businessCents)
        assertEquals(3, movements.reopenDay(date))
        // Entonces cambia la revisión, no el dinero ni la trazabilidad de captura.
        assertEquals(3, movements.observePendingOn(date).first().size)
        val restoredCapture = movements.findById(captured.id)!!
        assertEquals(captured.notificationKey, restoredCapture.notificationKey)
        assertEquals(captured.deduplicationHash, restoredCapture.deduplicationHash)
        assertEquals(MovementClassification.PERSONAL, restoredCapture.classification)
        assertFalse(restoredCapture.reviewed)
        assertEquals(4_500L, movements.totalsInRange(DateRange.day(date)).businessCents)
    }

    @Test
    fun bdd10RecurringPlanKeepsExtrasInTheirMonth() = runBlocking {
        // Dado alquiler fijo S/ 100, anual S/ 100.01 y un extra S/ 10 de octubre.
        plans.saveExpense(null, month, ExpenseSchedule.MONTHLY_FIXED, "Alquiler", 10_000)
        plans.saveExpense(null, month, ExpenseSchedule.ANNUAL_FIXED, "Licencia", 10_001)
        plans.addExpense(month, "Reparación", 1_000)
        // Cuando consulto todos los meses y avanzo a noviembre.
        val resolved = (1..12).map { plans.observePlan(YearMonth.of(2026, it)).first() }
        val october = resolved[9]
        val november = resolved[10]
        // Entonces solo octubre lleva el extra y el reparto anual conserva cada céntimo.
        assertEquals(11_833L, october.expenses.sumOf { it.amountCents })
        assertEquals(10_833L, november.expenses.sumOf { it.amountCents })
        assertEquals(1, resolved.count { plan ->
            plan.expenses.any { it.schedule == ExpenseSchedule.MONTHLY_EXTRA }
        })
        assertEquals(10_001L, resolved.sumOf { plan ->
            plan.expenses.single { it.schedule == ExpenseSchedule.ANNUAL_FIXED }.amountCents
        })
        // Un gasto planificado nunca se convierte en movimiento de dinero.
        assertTrue(movements.testMovements().isEmpty())
    }

    @Test
    fun bdd11GoalModesPreserveEachConfiguration() = runBlocking {
        // Dadas metas por mes de S/ 200 y S/ 300, y una fija de S/ 250.
        plans.setMonthlyGoal(month, 20_000)
        plans.setMonthlyGoal(month.plusMonths(1), 30_000)
        plans.setFixedGoal(25_000)
        assertEquals(25_000L, plans.observePlan(month).first().goal!!.amountCents)
        assertEquals(25_000L, plans.observePlan(month.plusMonths(1)).first().goal!!.amountCents)
        // Cuando alterno entre modos.
        plans.setGoalMode(GoalMode.BY_MONTH)
        // Entonces recupero las metas originales, sin borrar tampoco la fija.
        assertEquals(20_000L, plans.observePlan(month).first().goal!!.amountCents)
        assertEquals(30_000L, plans.observePlan(month.plusMonths(1)).first().goal!!.amountCents)
        plans.setGoalMode(GoalMode.FIXED_MONTHLY)
        assertEquals(25_000L, plans.observePlan(month).first().goal!!.amountCents)
    }

    private fun input(key: String, postedAt: Instant) = NotificationInput(
        packageName = YapePackage.PROVISIONAL_PACKAGE_ID,
        key = key,
        title = "Confirmación de Pago",
        text = "Cliente Prueba te envió un pago por S/ 35.00",
        postedAt = postedAt,
    )
}
