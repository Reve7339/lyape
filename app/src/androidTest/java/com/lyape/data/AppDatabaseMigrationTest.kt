package com.lyape.data

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lyape.domain.MovementClassification
import com.lyape.domain.MovementSource
import com.lyape.domain.PaymentMedium
import com.lyape.domain.ExpenseSchedule
import com.lyape.domain.GoalMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    @Test fun migrar7a8ConservaAmbasMetasComoIngresos() {
        helper.createDatabase(TEST_DB, 7).use { db ->
            db.execSQL("INSERT INTO monthly_goals(month, amountCents) VALUES('2026-10', 50000)")
            db.execSQL("INSERT INTO goal_settings(id, mode, fixedAmountCents) VALUES(0, 'FIXED_MONTHLY', 90000)")
        }
        helper.runMigrationsAndValidate(TEST_DB, 8, true, MIGRATION_7_8).use { db ->
            db.query("SELECT amountCents, type FROM monthly_goals").use {
                assertTrue(it.moveToFirst()); assertEquals(50000L, it.getLong(0)); assertEquals("INCOME", it.getString(1))
            }
            db.query("SELECT mode, fixedAmountCents, fixedType FROM goal_settings").use {
                assertTrue(it.moveToFirst()); assertEquals("FIXED_MONTHLY", it.getString(0))
                assertEquals(90000L, it.getLong(1)); assertEquals("INCOME", it.getString(2))
            }
        }
    }

    @Test
    fun migrar8a9AgregaRevisionSinAlterarMovimientosExistentes() {
        helper.createDatabase(TEST_DB, 8).use { v8 ->
            v8.execSQL(
                """
                INSERT INTO movements (
                    id, amountCents, currency, occurredAt, capturedAt, source, classification,
                    reviewed, senderName, description, adjustmentReason, notificationKey,
                    deduplicationHash, isEdited, createdAt, updatedAt, previousClassification,
                    searchTitle, searchDescription, searchReason, accountId, medium
                ) VALUES (
                    '11111111-2222-3333-4444-555555555555', 128450, 'PEN', 1000, 2000,
                    'NOTIFICATION', 'BUSINESS', 0, 'Cliente real', NULL, NULL, 'clave-v8',
                    'huella-v8', 0, 3000, 4000, NULL, 'cliente real', '', '', 'yape', 'YAPE'
                )
                """.trimIndent(),
            )
        }

        helper.runMigrationsAndValidate(TEST_DB, 9, true, MIGRATION_8_9).use { migrated ->
            migrated.query("SELECT * FROM movements").use { rows ->
                assertEquals(1, rows.count)
                rows.moveToFirst()
                assertEquals(128_450L, rows.getLong(rows.getColumnIndexOrThrow("amountCents")))
                assertEquals(0L, rows.getLong(rows.getColumnIndexOrThrow("editVersion")))
                assertTrue(rows.isNull(rows.getColumnIndexOrThrow("lastEditAttemptId")))
                assertEquals("clave-v8", rows.getString(rows.getColumnIndexOrThrow("notificationKey")))
            }
        }
    }

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
            .addMigrations(
                MIGRATION_1_2,
                MIGRATION_2_3,
                MIGRATION_3_4,
                MIGRATION_4_5,
                MIGRATION_5_6,
                MIGRATION_6_7,
                MIGRATION_7_8,
                MIGRATION_8_9,
            )
            .allowMainThreadQueries()
            .build()

        runBlocking {
            val dao = database!!.movementDao()
            val row = dao.getById(V1_ID)
                ?: error("La migración perdió el pago guardado en la versión 1.")

            assertEquals(128_450L, row.amountCents)
            assertEquals("PEN", row.currency)
            assertEquals(MovementSource.NOTIFICATION, row.source)
            assertEquals(PaymentMedium.YAPE, row.medium)
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
            .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9)
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
            .addMigrations(MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9)
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
    @Test
    fun historyMigration04To05PreservesRowsAndBackfillsSearch() {
        helper.createDatabase(TEST_DB, 4).use { v4 ->
            v4.execSQL(INSERT_V1_ROW.replace("Cliente real", "María Ñúñez"))
            v4.execSQL("UPDATE movements SET description = 'Pago 10%_literal', previousClassification = 'PERSONAL'")
        }
        helper.runMigrationsAndValidate(TEST_DB, 5, true, MIGRATION_4_5).use { migrated ->
            migrated.query("SELECT * FROM movements").use { rows ->
                assertEquals(1, rows.count)
                rows.moveToFirst()
                assertEquals(128450L, rows.getLong(rows.getColumnIndexOrThrow("amountCents")))
                assertEquals("María Ñúñez", rows.getString(rows.getColumnIndexOrThrow("senderName")))
                assertEquals("maria nunez", rows.getString(rows.getColumnIndexOrThrow("searchTitle")))
                assertEquals("pago 10%_literal", rows.getString(rows.getColumnIndexOrThrow("searchDescription")))
                assertEquals("", rows.getString(rows.getColumnIndexOrThrow("searchReason")))
                assertEquals("PERSONAL", rows.getString(rows.getColumnIndexOrThrow("previousClassification")))
                assertEquals("clave-v1", rows.getString(rows.getColumnIndexOrThrow("notificationKey")))
                assertEquals("huella-v1", rows.getString(rows.getColumnIndexOrThrow("deduplicationHash")))
            }
        }
    }

    @Test
    fun captureMigration06To07SeparatesMediumAndAssociatesYape() {
        helper.createDatabase(TEST_DB, 6).use { v6 ->
            v6.execSQL(
                """
                INSERT INTO movements (
                    id, amountCents, currency, occurredAt, capturedAt, source, classification,
                    reviewed, isEdited, createdAt, updatedAt, accountId
                ) VALUES
                    ('11111111-2222-3333-4444-555555555551', 100, 'PEN', 1000, 1000,
                     'NOTIFICATION', 'BUSINESS', 0, 0, 1000, 1000, 'yape'),
                    ('11111111-2222-3333-4444-555555555552', 200, 'PEN', 2000, 2000,
                     'MANUAL', 'BUSINESS', 0, 0, 2000, 2000, NULL),
                    ('11111111-2222-3333-4444-555555555553', -50, 'PEN', 3000, 3000,
                     'ADJUSTMENT', 'BUSINESS', 0, 0, 3000, 3000, NULL)
                """.trimIndent(),
            )
            v6.execSQL(
                "INSERT INTO monthly_expenses (id, month, name, amountCents) " +
                    "VALUES ('11111111-2222-3333-4444-555555555554', '2026-10', 'Luz', 15000)",
            )
        }

        helper.runMigrationsAndValidate(TEST_DB, 7, true, MIGRATION_6_7).use { migrated ->
            migrated.query(
                "SELECT id, medium, accountId FROM movements ORDER BY occurredAt",
            ).use { rows ->
                assertEquals(3, rows.count)
                rows.moveToFirst()
                assertEquals("YAPE", rows.getString(rows.getColumnIndexOrThrow("medium")))
                assertEquals("yape", rows.getString(rows.getColumnIndexOrThrow("accountId")))
                rows.moveToNext()
                assertEquals("UNKNOWN", rows.getString(rows.getColumnIndexOrThrow("medium")))
                assertTrue(rows.isNull(rows.getColumnIndexOrThrow("accountId")))
                rows.moveToNext()
                assertEquals("UNKNOWN", rows.getString(rows.getColumnIndexOrThrow("medium")))
                assertTrue(rows.isNull(rows.getColumnIndexOrThrow("accountId")))
            }
            migrated.query("SELECT provider, accountId FROM capture_accounts").use { rows ->
                assertEquals(1, rows.count)
                rows.moveToFirst()
                assertEquals("YAPE", rows.getString(0))
                assertEquals("yape", rows.getString(1))
            }
            migrated.query("SELECT amountCents FROM monthly_expenses").use { rows ->
                assertEquals(1, rows.count)
                rows.moveToFirst()
                assertEquals(15000L, rows.getLong(0))
            }
        }
    }

    @Test
    fun accountsMigration05To06PreservesHistoryWithoutInventingBalances() {
        helper.createDatabase(TEST_DB, 5).use { v5 ->
            v5.execSQL(INSERT_V1_ROW)
            v5.execSQL("INSERT INTO monthly_expenses (id,month,name,amountCents) VALUES ('11111111-2222-3333-4444-555555555555','2026-10','Luz',15000)")
        }
        helper.runMigrationsAndValidate(TEST_DB, 6, true, MIGRATION_5_6).use { migrated ->
            migrated.query("SELECT amountCents, accountId, notificationKey FROM movements").use { rows ->
                assertEquals(1, rows.count)
                rows.moveToFirst()
                assertEquals(128450L, rows.getLong(0))
                assertEquals("yape", rows.getString(1))
                assertEquals("clave-v1", rows.getString(2))
            }
            migrated.query("SELECT baselineAt, baselineBusinessCents, baselinePersonalCents FROM cash_accounts").use { rows ->
                assertEquals(2, rows.count)
                while (rows.moveToNext()) {
                    org.junit.Assert.assertTrue(rows.isNull(0))
                    assertEquals(0L, rows.getLong(1))
                    assertEquals(0L, rows.getLong(2))
                }
            }
            migrated.query("SELECT amountCents FROM monthly_expenses").use { rows ->
                rows.moveToFirst()
                assertEquals(15000L, rows.getLong(0))
            }
        }
    }
}
