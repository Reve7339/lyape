package com.lyape.data

import com.lyape.domain.ExpenseSchedule
import com.lyape.domain.GoalMode
import com.lyape.domain.MonthlyExpense
import com.lyape.domain.MonthlyGoal
import com.lyape.domain.MonthlyPlan
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.YearMonth
import java.util.UUID

class MonthlyPlanRepository(
    private val dao: MonthlyPlanDao,
    private val newId: () -> UUID = UUID::randomUUID,
) {

    fun observePlan(month: YearMonth): Flow<MonthlyPlan> {
        val expenses = combine(
            dao.observeExpenses(month.toString()),
            dao.observeRecurringExpenses(),
        ) { extras, recurring ->
            (extras.map(MonthlyExpenseEntity::toDomain) +
                recurring.map { it.toDomain(month) })
                .sortedWith(compareBy<MonthlyExpense>({ it.schedule.ordinal }, { it.name.lowercase() }))
        }
        val goal = combine(
            dao.observeGoal(month.toString()),
            dao.observeGoalSettings(),
        ) { monthlyGoal, settings ->
            val mode = settings?.mode?.let(GoalMode::valueOf) ?: GoalMode.BY_MONTH
            val appliedGoal = when (mode) {
                GoalMode.FIXED_MONTHLY -> settings?.fixedAmountCents?.let { MonthlyGoal(month, it) }
                GoalMode.BY_MONTH -> monthlyGoal?.toDomain()
            }
            mode to appliedGoal
        }
        return combine(expenses, goal) { appliedExpenses, goalState ->
            MonthlyPlan(
                month = month,
                expenses = appliedExpenses,
                goal = goalState.second,
                goalMode = goalState.first,
            )
        }
    }

    /** Gasto extra que existe únicamente en [month]. */
    suspend fun addExpense(month: YearMonth, name: String, amountCents: Long): UUID =
        saveExpense(null, month, ExpenseSchedule.MONTHLY_EXTRA, name, amountCents)

    /** Compatibilidad con el flujo anterior: edita un gasto extra de un mes. */
    suspend fun updateExpense(id: UUID, month: YearMonth, name: String, amountCents: Long) {
        saveExpense(id, month, ExpenseSchedule.MONTHLY_EXTRA, name, amountCents)
    }

    suspend fun saveExpense(
        id: UUID?,
        selectedMonth: YearMonth,
        schedule: ExpenseSchedule,
        name: String,
        amountCents: Long,
    ): UUID {
        val expenseId = id ?: newId()
        val expense = validExpense(expenseId, selectedMonth, name, amountCents, schedule)
        when (schedule) {
            ExpenseSchedule.MONTHLY_EXTRA -> {
                dao.upsertExpense(expense.toEntity())
                dao.deleteRecurringExpense(expenseId)
            }

            ExpenseSchedule.MONTHLY_FIXED,
            ExpenseSchedule.ANNUAL_FIXED,
            -> {
                dao.upsertRecurringExpense(
                    RecurringExpenseEntity(
                        id = expenseId,
                        name = expense.name,
                        amountCents = amountCents,
                        schedule = schedule.name,
                    ),
                )
                dao.deleteExpense(expenseId)
            }
        }
        return expenseId
    }

    suspend fun removeExpense(expense: MonthlyExpense): Boolean = when (expense.schedule) {
        ExpenseSchedule.MONTHLY_EXTRA -> dao.deleteExpense(expense.id) > 0
        ExpenseSchedule.MONTHLY_FIXED,
        ExpenseSchedule.ANNUAL_FIXED,
        -> dao.deleteRecurringExpense(expense.id) > 0
    }

    /** Compatibilidad con pruebas y llamadas anteriores: elimina un gasto extra. */
    suspend fun removeExpense(id: UUID): Boolean = dao.deleteExpense(id) > 0

    /** Meta exclusiva de [month]. También selecciona el modo por mes. */
    suspend fun setGoal(month: YearMonth, amountCents: Long) = setMonthlyGoal(month, amountCents)

    suspend fun setMonthlyGoal(month: YearMonth, amountCents: Long) {
        require(amountCents > 0L) { "La meta debe ser mayor que cero." }
        dao.upsertGoal(MonthlyGoal(month, amountCents).toEntity())
        setGoalMode(GoalMode.BY_MONTH)
    }

    suspend fun setFixedGoal(amountCents: Long) {
        require(amountCents > 0L) { "La meta debe ser mayor que cero." }
        dao.upsertGoalSettings(
            GoalSettingsEntity(
                mode = GoalMode.FIXED_MONTHLY.name,
                fixedAmountCents = amountCents,
            ),
        )
    }

    suspend fun setGoalMode(mode: GoalMode) {
        val current = dao.getGoalSettings()
        dao.upsertGoalSettings(
            GoalSettingsEntity(
                mode = mode.name,
                fixedAmountCents = current?.fixedAmountCents,
            ),
        )
    }

    suspend fun clearGoal(month: YearMonth): Boolean = dao.deleteGoal(month.toString()) > 0

    suspend fun clearAppliedGoal(month: YearMonth, mode: GoalMode): Boolean = when (mode) {
        GoalMode.BY_MONTH -> clearGoal(month)
        GoalMode.FIXED_MONTHLY -> {
            dao.upsertGoalSettings(
                GoalSettingsEntity(
                    mode = GoalMode.FIXED_MONTHLY.name,
                    fixedAmountCents = null,
                ),
            )
            true
        }
    }

    private fun validExpense(
        id: UUID,
        month: YearMonth,
        name: String,
        amountCents: Long,
        schedule: ExpenseSchedule,
    ): MonthlyExpense {
        val cleanName = name.trim()
        require(cleanName.isNotEmpty()) { "El gasto necesita un nombre." }
        require(amountCents > 0L) { "El gasto debe ser mayor que cero." }
        return MonthlyExpense(
            id = id,
            month = month,
            name = cleanName,
            amountCents = amountCents,
            schedule = schedule,
            plannedAmountCents = amountCents,
        )
    }
}
