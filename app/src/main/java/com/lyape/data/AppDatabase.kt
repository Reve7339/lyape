package com.lyape.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Base de datos local. No hay tablas de totales: los importes por periodo se derivan de los
 * movimientos mediante consultas filtradas por rango.
 */
@Database(
    entities = [
        MovementEntity::class,
        MonthlyExpenseEntity::class,
        MonthlyGoalEntity::class,
        RecurringExpenseEntity::class,
        GoalSettingsEntity::class,
        CashAccountEntity::class,
        CashOperationEntity::class,
        CaptureAccountEntity::class,
    ],
    version = 9,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun movementDao(): MovementDao

    abstract fun monthlyPlanDao(): MonthlyPlanDao

    abstract fun accountsDao(): AccountsDao

    companion object {
        private const val DATABASE_NAME = "lyape.db"

        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: build(context).also { instance = it }
            }

        private fun build(context: Context): AppDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                DATABASE_NAME,
            )
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
                .addCallback(object : RoomDatabase.Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        seedCashAccounts(db)
                        seedCaptureAccounts(db)
                    }
                })
                .build()
    }
}
