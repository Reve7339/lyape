package com.lyape.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lyape.domain.DateRange
import com.lyape.domain.MonthlyPlan
import com.lyape.domain.MonthlyProgress
import com.lyape.domain.MonthlyProgressCalculator
import com.lyape.domain.Movement
import com.lyape.domain.MovementClassification
import com.lyape.domain.MovementSource
import com.lyape.domain.TotalsCalculator
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.util.UUID

/** Comprueba el recorrido Room → totales → progreso que usa el resumen. */
@RunWith(AndroidJUnit4::class)
class MonthlyProgressIntegrationTest {

    private lateinit var database: AppDatabase
    private lateinit var movements: MovementRepository
    private lateinit var plans: MonthlyPlanRepository
    private val month = YearMonth.of(2026, 10)
    private val zone = ZoneId.of("America/Lima")
    private val range = DateRange.month(month.atDay(1), zone)
    private val occurredAt = month.atDay(15).atTime(12, 0).atZone(zone).toInstant()

    @Before
    fun createDatabase() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        movements = MovementRepository(database.movementDao())
        plans = MonthlyPlanRepository(database.monthlyPlanDao())
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun reclasificarUnIngresoActualizaAmbosFaltantes() = runBlocking {
        plans.addExpense(month, "Alquiler", 5_000)
        plans.setGoal(month, 8_000)
        val movement = movement(amountCents = 6_000)
        movements.add(movement)

        val asBusiness = progress()
        assertEquals(0L, asBusiness.expensesRemainingCents)
        assertEquals(1_000L, asBusiness.estimatedProfitCents)
        assertEquals(2_000L, asBusiness.goalRemainingCents)

        movements.classify(movement.id, MovementClassification.PERSONAL)

        val asPersonal = progress()
        assertEquals(5_000L, asPersonal.expensesRemainingCents)
        assertEquals(8_000L, asPersonal.goalRemainingCents)
    }

    @Test
    fun ajusteNegativoReduceElIngresoUsadoPorElPlan() = runBlocking {
        plans.addExpense(month, "Gastos", 5_000)
        movements.addAll(
            listOf(
                movement(amountCents = 7_000),
                movement(amountCents = -1_500, source = MovementSource.ADJUSTMENT),
            ),
        )

        val progress = progress()
        assertEquals(5_500L, progress.incomeCents)
        assertEquals(500L, progress.estimatedProfitCents)
    }

    private suspend fun progress(): MonthlyProgress {
        val monthMovements = movements.movementsInRange(range)
        val plan: MonthlyPlan = plans.observePlan(month).first()
        return MonthlyProgressCalculator.calculate(
            incomeCents = TotalsCalculator.businessTotalCents(monthMovements),
            plan = plan,
        )
    }

    private fun movement(
        amountCents: Long,
        source: MovementSource = MovementSource.NOTIFICATION,
    ): Movement = Movement(
        id = UUID.randomUUID(),
        amountCents = amountCents,
        occurredAt = occurredAt,
        capturedAt = Instant.now(),
        source = source,
        classification = MovementClassification.BUSINESS,
        reviewed = false,
        createdAt = occurredAt,
        updatedAt = occurredAt,
        adjustmentReason = if (source == MovementSource.ADJUSTMENT) "Devolución" else null,
    )
}
