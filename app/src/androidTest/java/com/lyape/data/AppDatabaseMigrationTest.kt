package com.lyape.data

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lyape.domain.MovementClassification
import com.lyape.domain.MovementSource
import com.lyape.domain.ExpenseSchedule
import com.lyape.domain.GoalMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.YearMonth
import java.util.UUID

/**
 * La instalación en un teléfono real ya tiene pagos guardados en la versión 1. Esta prueba migra
 * esa base hasta la versión actual y comprueba que no se pierde ninguna fila.
 */
@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {

    private companion object {
        const val TEST_DB = "migration-test.db"
        val V1_ID: UUID = UUID.fromString("11111111-2222-3333-4444-555555555555")

        /**
         * Fila de la versión 1: mismo esquema que generó Room, sin `previousClassification`.
         */
        const val INSERT_V1_ROW = """
            INSERT INTO movements (
                id, amountCents, currency, occurredAt, capturedAt, source, classification,
                reviewed, senderName, description, adjustmentReason, notificationKey,
                deduplicationHash, isEdited, createdAt, updatedAt
            ) VALUES (
                '11111111-2222-3333-4444-555555555555', 128450, 'PEN', 1000, 2000, 'NOTIFICATION',
                'BUSINESS', 0, 'Cliente real', NULL, NULL, 'clave-v1', 'huella-v1', 0, 3000, 4000
            )
        """
    }

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    private var database: AppDatabase? = null

    @After
    fun tearDown() {
        database?.close()
        InstrumentationRegistry.getInstrumentation().targetContext.deleteDatabase(TEST_DB)
    }

    @Test
    fun migrarDeLaVersion1ConservaLosPagosYAgregaLaClasificacionPrevia() {
        helper.createDatabase(TEST_DB, 1).use { v1 ->
            v1.execSQL(INSERT_V1_ROW)
        }

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.databaseBuilder(context, AppDatabase::class.java, TEST_DB)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
            .allowMainThreadQueries()
            .build()

        runBlocking {
            val dao = database!!.movementDao()
            val row = dao.getById(V1_ID)
                ?: error("La migración perdió el pago guardado en la versión 1.")

            assertEquals(128_450L, row.amountCents)
            assertEquals("PEN", row.currency)
            assertEquals(MovementSource.NOTIFICATION, row.source)
            assertEquals(MovementClassification.BUSINESS, row.classification)
            assertEquals("Cliente real", row.senderName)
            assertEquals("clave-v1", row.notificationKey)
            assertEquals("huella-v1", row.deduplicationHash)
            assertEquals(1_000L, row.occurredAt.toEpochMilli())
            assertEquals(4_000L, row.updatedAt.toEpochMilli())
            assertNull(row.previousClassification)
            assertEquals(0, database!!.monthlyPlanDao().observeExpenses("2026-10").first().size)
            assertNull(database!!.monthlyPlanDao().observeGoal("2026-10").first())
            assertEquals(GoalMode.BY_MONTH, MonthlyPlanRepository(database!!.monthlyPlanDao())
                .observePlan(YearMonth.of(2026, 10)).first().goalMode)
        }
    }

    @Test
    fun migrarDeLaVersion2AgregaElPlanMensualSinAlterarMovimientos() {
        helper.createDatabase(TEST_DB, 2).use { v2 ->
            v2.execSQL(
                """
                INSERT INTO movements (
                    id, amountCents, currency, occurredAt, capturedAt, source, classification,
                    reviewed, senderName, description, adjustmentReason, notificationKey,
                    deduplicationHash, isEdited, createdAt, updatedAt, previousClassification
                ) VALUES (
                    '11111111-2222-3333-4444-555555555555', 128450, 'PEN', 1000, 2000,
                    'NOTIFICATION', 'BUSINESS', 0, 'Cliente real', NULL, NULL, 'clave-v2',
                    'huella-v2', 0, 3000, 4000, NULL
                )
                """.trimIndent(),
            )
        }

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.databaseBuilder(context, AppDatabase::class.java, TEST_DB)
            .addMigrations(MIGRATION_2_3, MIGRATION_3_4)
            .allowMainThreadQueries()
            .build()

        runBlocking {
            assertEquals(128_450L, database!!.movementDao().getById(V1_ID)?.amountCents)

            val repository = MonthlyPlanRepository(database!!.monthlyPlanDao())
            repository.addExpense(YearMonth.of(2026, 10), "Alquiler", 50_000)
            repository.setGoal(YearMonth.of(2026, 10), 100_000)

            val plan = repository.observePlan(YearMonth.of(2026, 10)).first()
            assertEquals(50_000L, plan.expenses.single().amountCents)
            assertEquals(100_000L, plan.goal?.amountCents)
        }
    }

    @Test
    fun migrarDeLaVersion3ConservaPlanAnteriorYAgregaRecurrencias() {
        helper.createDatabase(TEST_DB, 3).use { v3 ->
            v3.execSQL(
                "INSERT INTO monthly_expenses (id, month, name, amountCents) " +
                    "VALUES ('11111111-2222-3333-4444-555555555555', '2026-10', 'Extra previo', 2500)",
            )
            v3.execSQL(
                "INSERT INTO monthly_goals (month, amountCents) VALUES ('2026-10', 100000)",
            )
        }

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.databaseBuilder(context, AppDatabase::class.java, TEST_DB)
            .addMigrations(MIGRATION_3_4)
            .allowMainThreadQueries()
            .build()

        runBlocking {
            val repository = MonthlyPlanRepository(database!!.monthlyPlanDao())
            var plan = repository.observePlan(YearMonth.of(2026, 10)).first()
            assertEquals(GoalMode.BY_MONTH, plan.goalMode)
            assertEquals(100_000L, plan.goal?.amountCents)
            assertEquals("Extra previo", plan.expenses.single().name)
            assertEquals(ExpenseSchedule.MONTHLY_EXTRA, plan.expenses.single().schedule)

            repository.saveExpense(
                id = null,
                selectedMonth = YearMonth.of(2026, 10),
                schedule = ExpenseSchedule.ANNUAL_FIXED,
                name = "Licencia anual",
                amountCents = 120_000,
            )
            repository.setFixedGoal(150_000)

            plan = repository.observePlan(YearMonth.of(2026, 11)).first()
            assertEquals(10_000L, plan.expenses.single().amountCents)
            assertEquals(150_000L, plan.goal?.amountCents)
            assertEquals(GoalMode.FIXED_MONTHLY, plan.goalMode)
        }
    }
}
