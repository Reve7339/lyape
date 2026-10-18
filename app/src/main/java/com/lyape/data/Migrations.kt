package com.lyape.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migración 1 → 2: agrega `previousClassification`, la clasificación activa que un movimiento
 * recuerda al anularse para poder restaurarlo.
 *
 * Es aditiva y no destructiva: usa `ALTER TABLE ... ADD COLUMN`, de modo que las instalaciones
 * existentes con pagos reales conservan todas sus filas. Las filas antiguas quedan con
 * `previousClassification = NULL`.
 */
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
