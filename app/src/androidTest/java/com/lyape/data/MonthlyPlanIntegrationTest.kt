package com.lyape.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lyape.domain.ExpenseSchedule
import com.lyape.domain.GoalMode
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
import java.time.YearMonth
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class MonthlyPlanIntegrationTest {

    private lateinit var database: AppDatabase
    private lateinit var repository: MonthlyPlanRepository
    private val october = YearMonth.of(2026, 10)
    private val november = YearMonth.of(2026, 11)
    private var nextId = 1L

    @Before
    fun createDatabase() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = MonthlyPlanRepository(database.monthlyPlanDao()) {
            UUID(0L, nextId++)
        }
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun agregarEditarYQuitarGastosNoCreaMovimientos() = runBlocking {
        val rentId = repository.addExpense(october, " Alquiler ", 50_000)
        repository.addExpense(october, "Internet", 8_000)

        var plan = repository.observePlan(october).first()
        assertEquals(listOf("Alquiler", "Internet"), plan.expenses.map { it.name })
        assertEquals(0, database.movementDao().getInRange(0, Long.MAX_VALUE).size)

        repository.updateExpense(rentId, october, "Local", 55_000)
        plan = repository.observePlan(october).first()
        assertEquals(55_000L, plan.expenses.first { it.id == rentId }.amountCents)
        assertEquals("Local", plan.expenses.first { it.id == rentId }.name)

        assertTrue(repository.removeExpense(rentId))
        assertFalse(repository.removeExpense(rentId))
        assertEquals(listOf("Internet"), repository.observePlan(october).first().expenses.map { it.name })
    }

    @Test
    fun metaYGastosSonIndependientesYNoSeFiltranAOtroMes() = runBlocking {
        repository.addExpense(october, "Alquiler", 50_000)
        repository.setGoal(october, 100_000)
        repository.addExpense(november, "Luz", 7_000)

        val octoberPlan = repository.observePlan(october).first()
        assertEquals(1, octoberPlan.expenses.size)
        assertEquals(100_000L, octoberPlan.goal?.amountCents)

        val novemberPlan = repository.observePlan(november).first()
        assertEquals(listOf("Luz"), novemberPlan.expenses.map { it.name })
        assertNull(novemberPlan.goal)

        assertTrue(repository.clearGoal(october))
        assertNull(repository.observePlan(october).first().goal)
        assertEquals(1, repository.observePlan(october).first().expenses.size)
    }

    @Test
    fun repositorioRechazaDatosInvalidos() = runBlocking {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repository.addExpense(october, " ", 1_000) }
        }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repository.addExpense(october, "Alquiler", 0) }
        }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repository.setGoal(october, 0) }
        }
        Unit
    }

    @Test
    fun gastosFijosMensualesYAnualesSeAplicanATodosLosMeses() = runBlocking {
        repository.saveExpense(
            id = null,
            selectedMonth = october,
            schedule = ExpenseSchedule.MONTHLY_FIXED,
            name = "Internet",
            amountCents = 8_000,
        )
        repository.saveExpense(
            id = null,
            selectedMonth = october,
            schedule = ExpenseSchedule.ANNUAL_FIXED,
            name = "Licencia",
            amountCents = 120_000,
        )

        val octoberPlan = repository.observePlan(october).first()
        val novemberPlan = repository.observePlan(november).first()

        assertEquals(listOf("Internet", "Licencia"), octoberPlan.expenses.map { it.name })
        assertEquals(listOf(8_000L, 10_000L), octoberPlan.expenses.map { it.amountCents })
        assertEquals(listOf(8_000L, 10_000L), novemberPlan.expenses.map { it.amountCents })
        assertEquals(120_000L, octoberPlan.expenses.last().plannedAmountCents)
        assertEquals(ExpenseSchedule.ANNUAL_FIXED, octoberPlan.expenses.last().schedule)
    }

    @Test
    fun gastoExtraSoloSeAplicaAlMesElegido() = runBlocking {
        repository.saveExpense(
            id = null,
            selectedMonth = october,
            schedule = ExpenseSchedule.MONTHLY_EXTRA,
            name = "Reparación",
            amountCents = 15_000,
        )

        assertEquals(listOf("Reparación"), repository.observePlan(october).first().expenses.map { it.name })
        assertTrue(repository.observePlan(november).first().expenses.isEmpty())
    }

    @Test
    fun usuarioPuedeAlternarEntreMetaFijaYMetasPorMesSinPerderValores() = runBlocking {
        repository.setMonthlyGoal(october, 100_000)
        repository.setMonthlyGoal(november, 110_000)
        repository.setFixedGoal(150_000)

        var octoberPlan = repository.observePlan(october).first()
        var novemberPlan = repository.observePlan(november).first()
        assertEquals(GoalMode.FIXED_MONTHLY, octoberPlan.goalMode)
        assertEquals(150_000L, octoberPlan.goal?.amountCents)
        assertEquals(150_000L, novemberPlan.goal?.amountCents)

        repository.setGoalMode(GoalMode.BY_MONTH)
        octoberPlan = repository.observePlan(october).first()
        novemberPlan = repository.observePlan(november).first()
        assertEquals(100_000L, octoberPlan.goal?.amountCents)
        assertEquals(110_000L, novemberPlan.goal?.amountCents)

        repository.setGoalMode(GoalMode.FIXED_MONTHLY)
        assertEquals(150_000L, repository.observePlan(october).first().goal?.amountCents)
    }

    @Test
    fun editarOCambiarFrecuenciaNoDejaCopiasYQuitarRecurrenteLoQuitaDeTodosLosMeses() = runBlocking {
        val id = repository.saveExpense(
            id = null,
            selectedMonth = october,
            schedule = ExpenseSchedule.MONTHLY_FIXED,
            name = "Licencia",
            amountCents = 10_000,
        )
        repository.saveExpense(
            id = id,
            selectedMonth = october,
            schedule = ExpenseSchedule.ANNUAL_FIXED,
            name = "Licencia anual",
            amountCents = 120_000,
        )

        val annual = repository.observePlan(november).first().expenses.single()
        assertEquals(ExpenseSchedule.ANNUAL_FIXED, annual.schedule)
        assertEquals(10_000L, annual.amountCents)

        assertTrue(repository.removeExpense(annual))
        assertTrue(repository.observePlan(october).first().expenses.isEmpty())
        assertTrue(repository.observePlan(november).first().expenses.isEmpty())
    }
}
