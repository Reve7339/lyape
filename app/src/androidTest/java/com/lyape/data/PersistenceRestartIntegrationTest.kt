package com.lyape.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lyape.domain.DateRange
import com.lyape.domain.ExpenseSchedule
import com.lyape.domain.GoalMode
import com.lyape.domain.MovementClassification
import com.lyape.preferences.AppPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.UUID

/** Cierra y reabre almacenamiento real de prueba; no simula la muerte completa del proceso. */
@RunWith(AndroidJUnit4::class)
class PersistenceRestartIntegrationTest {
    @Test
    fun file01ReopenKeepsMovementsAndMonthlyPlan() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "quality-restart-${UUID.randomUUID()}.db"
        val date = LocalDate.of(2026, 10, 15)
        val month = YearMonth.from(date)
        val at = date.atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant()
        fun open() = Room.databaseBuilder(context, AppDatabase::class.java, name).build()
        var database = open()
        try {
            val movements = MovementRepository(database.movementDao())
            val plans = MonthlyPlanRepository(database.monthlyPlanDao())
            val id = movements.addManual(10_000, at, "Venta persistente")
            movements.addAdjustment(-500, at, "Devolución persistente")
            movements.classify(id, MovementClassification.PERSONAL)
            movements.exclude(id)
            plans.saveExpense(null, month, ExpenseSchedule.MONTHLY_FIXED, "Alquiler", 5_000)
            plans.addExpense(month, "Extra octubre", 100)
            plans.setMonthlyGoal(month, 20_000)
            plans.setFixedGoal(25_000)

            database.close()
            database = open()
            val reopenedMovements = MovementRepository(database.movementDao())
            val reopenedPlans = MonthlyPlanRepository(database.monthlyPlanDao())

            val excluded = reopenedMovements.findById(id)!!
            assertEquals(MovementClassification.EXCLUDED, excluded.classification)
            assertEquals(MovementClassification.PERSONAL, excluded.previousClassification)
            assertEquals("Venta persistente", excluded.senderName)
            assertEquals(10_000L, excluded.amountCents)
            assertEquals(-500L, reopenedMovements.totalsInRange(DateRange.day(date)).businessCents)
            val plan = reopenedPlans.observePlan(month).first()
            assertEquals(5_100L, plan.expenses.sumOf { it.amountCents })
            assertEquals(25_000L, plan.goal!!.amountCents)
            reopenedPlans.setGoalMode(GoalMode.BY_MONTH)
            assertEquals(20_000L, reopenedPlans.observePlan(month).first().goal!!.amountCents)
            reopenedMovements.restore(id)
            assertEquals(MovementClassification.PERSONAL, reopenedMovements.findById(id)!!.classification)
        } finally {
            database.close()
            // Nombre exclusivo generado por esta prueba; nunca lyape.db.
            context.deleteDatabase(name)
        }
    }

    @Test
    fun file02ReopenPreferencesKeepsCapturePause() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, "quality-pause-${UUID.randomUUID()}.preferences_pb")
        var scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        fun open() = AppPreferences(PreferenceDataStoreFactory.create(scope = scope, produceFile = { file }))
        try {
            open().setCapturePaused(true)
            scope.coroutineContext[Job]!!.cancelAndJoin()
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            assertTrue(open().capturePaused.first())
        } finally {
            scope.coroutineContext[Job]!!.cancelAndJoin()
            file.delete()
        }
    }
}
