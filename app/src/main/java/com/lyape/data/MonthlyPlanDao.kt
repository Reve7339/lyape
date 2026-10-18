package com.lyape.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import java.util.UUID

@Dao
interface MonthlyPlanDao {

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
