package com.lyape.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import java.util.UUID

@Dao
interface MonthlyPlanDao {

    @Transaction
    suspend fun saveMonthlyGoal(goal: MonthlyGoalEntity) {
        upsertGoal(goal)
        val settings = getGoalSettings()
        upsertGoalSettings(settings?.copy(mode = "BY_MONTH") ?: GoalSettingsEntity(mode = "BY_MONTH", fixedAmountCents = null))
    }

    @Query("SELECT * FROM monthly_expenses WHERE id=:id")
    suspend fun expense(id: UUID): MonthlyExpenseEntity?

    @Query("SELECT * FROM recurring_expenses WHERE id=:id")
    suspend fun recurringExpense(id: UUID): RecurringExpenseEntity?

    @Query("SELECT * FROM monthly_expenses WHERE month=:month ORDER BY name COLLATE NOCASE, id")
    suspend fun expenses(month: String): List<MonthlyExpenseEntity>

    @Query("SELECT * FROM recurring_expenses ORDER BY schedule, name COLLATE NOCASE, id")
    suspend fun recurringExpenses(): List<RecurringExpenseEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM cash_operations WHERE expenseId=:id AND voidedAt IS NULL)")
    suspend fun hasRecordedPayment(id: UUID): Boolean

    @Transaction
    suspend fun savePlannedExpense(extra: MonthlyExpenseEntity?, recurring: RecurringExpenseEntity?) {
        val id = requireNotNull(extra?.id ?: recurring?.id)
        require(!hasRecordedPayment(id)) {
            "Este gasto tiene pagos registrados. Anula primero sus pagos para cambiar el presupuesto."
        }
        if (extra != null) {
            upsertExpense(extra)
            deleteRecurringExpense(id)
        } else {
            upsertRecurringExpense(requireNotNull(recurring))
            deleteExpense(id)
        }
    }

    @Transaction
    suspend fun removePlannedExpense(id: UUID, recurring: Boolean): Boolean {
        require(!hasRecordedPayment(id)) {
            "Este gasto tiene pagos registrados. Anula primero sus pagos para eliminarlo."
        }
        return (if (recurring) deleteRecurringExpense(id) else deleteExpense(id)) > 0
    }

    @Query("SELECT * FROM monthly_expenses WHERE month = :month ORDER BY name COLLATE NOCASE, id")
    fun observeExpenses(month: String): Flow<List<MonthlyExpenseEntity>>

    @Query("SELECT * FROM monthly_goals WHERE month = :month LIMIT 1")
    fun observeGoal(month: String): Flow<MonthlyGoalEntity?>

    @Query("SELECT * FROM recurring_expenses ORDER BY schedule, name COLLATE NOCASE, id")
    fun observeRecurringExpenses(): Flow<List<RecurringExpenseEntity>>

    @Query("SELECT * FROM goal_settings WHERE id = 0 LIMIT 1")
    fun observeGoalSettings(): Flow<GoalSettingsEntity?>

    @Query("SELECT * FROM goal_settings WHERE id = 0 LIMIT 1")
    suspend fun getGoalSettings(): GoalSettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertExpense(expense: MonthlyExpenseEntity)

    @Query("DELETE FROM monthly_expenses WHERE id = :id")
    suspend fun deleteExpense(id: UUID): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRecurringExpense(expense: RecurringExpenseEntity)

    @Query("DELETE FROM recurring_expenses WHERE id = :id")
    suspend fun deleteRecurringExpense(id: UUID): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertGoal(goal: MonthlyGoalEntity)

    @Query("DELETE FROM monthly_goals WHERE month = :month")
    suspend fun deleteGoal(month: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertGoalSettings(settings: GoalSettingsEntity)
}
