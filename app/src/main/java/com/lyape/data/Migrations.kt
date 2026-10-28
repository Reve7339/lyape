package com.lyape.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.lyape.domain.MovementSource
import com.lyape.domain.movementTitle
import com.lyape.domain.normalizeMovementSearch

/**
 * 8 → 9: revisión e identidad de edición para no sobrescribir cambios más recientes.
 *
 * Las filas existentes quedan en la revisión 0; no se recalcula ningún importe ni se toca el
 * historial. La identidad de la última edición solo se escribe en la siguiente edición.
 */
val MIGRATION_8_9: Migration = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE movements ADD COLUMN editVersion INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE movements ADD COLUMN lastEditAttemptId TEXT")
    }
}

/** Metas antiguas conservan su significado de ingresos; sin reinterpretar sus importes. */
val MIGRATION_7_8: Migration = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE monthly_goals ADD COLUMN type TEXT NOT NULL DEFAULT 'INCOME'")
        db.execSQL("ALTER TABLE goal_settings ADD COLUMN fixedType TEXT NOT NULL DEFAULT 'INCOME'")
    }
}

/** Conserva filas antiguas; previousClassification permite restaurar una anulación. */
val MIGRATION_1_2: Migration = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE movements ADD COLUMN previousClassification TEXT")
    }
}

/** Migración 2 → 3: agrega la planificación mensual sin modificar la tabla de movimientos. */
val MIGRATION_2_3: Migration = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS monthly_expenses (
                id TEXT NOT NULL PRIMARY KEY,
                month TEXT NOT NULL,
                name TEXT NOT NULL,
                amountCents INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_monthly_expenses_month " +
                "ON monthly_expenses (month)",
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS monthly_goals (
                month TEXT NOT NULL PRIMARY KEY,
                amountCents INTEGER NOT NULL
            )
            """.trimIndent(),
        )
    }
}

/**
 * Migración 3 → 4: agrega gastos recurrentes y la elección del modo de meta.
 * Los gastos y metas ya guardados siguen siendo exclusivos de su mes.
 */
val MIGRATION_3_4: Migration = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS recurring_expenses (
                id TEXT NOT NULL PRIMARY KEY,
                name TEXT NOT NULL,
                amountCents INTEGER NOT NULL,
                schedule TEXT NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_recurring_expenses_schedule " +
                "ON recurring_expenses (schedule)",
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS goal_settings (
                id INTEGER NOT NULL PRIMARY KEY,
                mode TEXT NOT NULL,
                fixedAmountCents INTEGER
            )
            """.trimIndent(),
        )
        db.execSQL(
            "INSERT OR IGNORE INTO goal_settings (id, mode, fixedAmountCents) " +
                "VALUES (0, 'BY_MONTH', NULL)",
        )
    }
}

/** 4 → 5: derivados para buscar sin tildes y orden estable del historial. No borra pagos. */
val MIGRATION_4_5: Migration = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        for (column in listOf("searchTitle", "searchDescription", "searchReason")) {
            db.execSQL("ALTER TABLE movements ADD COLUMN $column TEXT NOT NULL DEFAULT ''")
        }
        // Un cursor + una sentencia: no se construye una lista con todos los movimientos.
        db.compileStatement(
            "UPDATE movements SET searchTitle = ?, searchDescription = ?, searchReason = ? WHERE id = ?",
        ).use { update ->
            db.query("SELECT id, senderName, source, description, adjustmentReason FROM movements").use { rows ->
                while (rows.moveToNext()) {
                    val source = MovementSource.valueOf(rows.getString(2))
                    update.bindString(1, normalizeMovementSearch(movementTitle(rows.getString(1), source)))
                    update.bindString(2, normalizeMovementSearch(rows.getString(3).orEmpty()))
                    update.bindString(3, normalizeMovementSearch(
                        if (source == MovementSource.ADJUSTMENT) rows.getString(4).orEmpty() else "",
                    ))
                    update.bindString(4, rows.getString(0))
                    update.executeUpdateDelete()
                }
            }
        }
        db.execSQL("CREATE INDEX index_movements_occurredAt_createdAt_id ON movements (occurredAt, createdAt, id)")
    }
}

/** 5 → 6: cuentas sin saldo supuesto y operaciones separadas de los ingresos. */
val MIGRATION_5_6: Migration = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE movements ADD COLUMN accountId TEXT")
        db.execSQL("UPDATE movements SET accountId = 'yape' WHERE source = 'NOTIFICATION'")
        db.execSQL("CREATE INDEX index_movements_accountId_occurredAt_classification ON movements (accountId, occurredAt, classification)")
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS cash_accounts (
                id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, kind TEXT NOT NULL,
                baselineAt INTEGER, baselineBusinessCents INTEGER NOT NULL,
                baselinePersonalCents INTEGER NOT NULL, baselineOperationSequence INTEGER NOT NULL,
                baselineMovementSequence INTEGER NOT NULL
            )
        """.trimIndent())
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS cash_operations (
                sequence INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                id TEXT NOT NULL, kind TEXT NOT NULL, fromAccountId TEXT NOT NULL, toAccountId TEXT,
                classification TEXT NOT NULL, amountCents INTEGER NOT NULL, note TEXT,
                occurredAt INTEGER NOT NULL, voidedAt INTEGER, expenseId TEXT, expensePeriod TEXT,
                expenseName TEXT, expenseBudgetCents INTEGER, expenseSchedule TEXT
            )
        """.trimIndent())
        db.execSQL("CREATE UNIQUE INDEX index_cash_operations_id ON cash_operations (id)")
        db.execSQL("CREATE INDEX index_cash_operations_fromAccountId ON cash_operations (fromAccountId)")
        db.execSQL("CREATE INDEX index_cash_operations_toAccountId ON cash_operations (toAccountId)")
        db.execSQL("CREATE INDEX index_cash_operations_expenseId_expensePeriod ON cash_operations (expenseId, expensePeriod)")
        db.execSQL("CREATE INDEX index_cash_operations_occurredAt ON cash_operations (occurredAt)")
        seedCashAccounts(db)
    }
}

internal fun seedCashAccounts(db: SupportSQLiteDatabase) {
    db.execSQL("INSERT OR IGNORE INTO cash_accounts VALUES ('yape','Yape','YAPE',NULL,0,0,0,0)")
    db.execSQL("INSERT OR IGNORE INTO cash_accounts VALUES ('cash','Efectivo','CASH',NULL,0,0,0,0)")
}

/**
 * 6 → 7: separa el medio del origen y agrega la asociación proveedor → cuenta.
 *
 * El listener anterior solo capturaba Yape, así que las filas NOTIFICATION históricas se marcan
 * como YAPE. Los movimientos manuales y ajustes quedan UNKNOWN: no se les atribuye un medio que
 * nunca se observó. No se crea una cuenta Plin ni se mueve historial.
 */
val MIGRATION_6_7: Migration = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE movements ADD COLUMN medium TEXT NOT NULL DEFAULT 'UNKNOWN'")
        db.execSQL("UPDATE movements SET medium = 'YAPE' WHERE source = 'NOTIFICATION'")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS capture_accounts (
                provider TEXT NOT NULL PRIMARY KEY,
                accountId TEXT NOT NULL
            )
            """.trimIndent(),
        )
        seedCaptureAccounts(db)
    }
}

/** Asocia explícitamente Yape con su cuenta integrada; Plin queda sin asociación por defecto. */
internal fun seedCaptureAccounts(db: SupportSQLiteDatabase) {
    db.execSQL("INSERT OR IGNORE INTO capture_accounts (provider, accountId) VALUES ('YAPE','yape')")
}
