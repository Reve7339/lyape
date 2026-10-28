package com.lyape.data

import com.lyape.domain.ExpenseSchedule
import com.lyape.domain.GoalMode
import com.lyape.domain.GoalType
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
            val fixed = settings?.fixedAmountCents?.let { MonthlyGoal(month, it, GoalType.valueOf(settings.fixedType)) }
            val appliedGoal = when (mode) {
                GoalMode.FIXED_MONTHLY -> fixed
                GoalMode.BY_MONTH -> monthlyGoal?.toDomain()
            }
            MonthlyPlan(month, emptyList(), appliedGoal, mode, fixed, monthlyGoal?.toDomain())
        }
        return combine(expenses, goal) { appliedExpenses, goalState ->
            goalState.copy(expenses = appliedExpenses)
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
                dao.savePlannedExpense(expense.toEntity(), null)
            }

            ExpenseSchedule.MONTHLY_FIXED,
            ExpenseSchedule.ANNUAL_FIXED,
            -> {
                dao.savePlannedExpense(null,
                    RecurringExpenseEntity(
                        id = expenseId,
                        name = expense.name,
                        amountCents = amountCents,
                        schedule = schedule.name,
                    ),
                )
            }
        }
        return expenseId
    }

    suspend fun removeExpense(expense: MonthlyExpense): Boolean = when (expense.schedule) {
        ExpenseSchedule.MONTHLY_EXTRA -> dao.removePlannedExpense(expense.id, false)
        ExpenseSchedule.MONTHLY_FIXED,
        ExpenseSchedule.ANNUAL_FIXED,
        -> dao.removePlannedExpense(expense.id, true)
    }

    /** Compatibilidad con pruebas y llamadas anteriores: elimina un gasto extra. */
    suspend fun removeExpense(id: UUID): Boolean = dao.removePlannedExpense(id, false)

    /** Meta exclusiva de [month]. También selecciona el modo por mes. */
    suspend fun setGoal(month: YearMonth, amountCents: Long) = setMonthlyGoal(month, amountCents)

    suspend fun setMonthlyGoal(month: YearMonth, amountCents: Long, type: GoalType = GoalType.INCOME) {
        require(amountCents > 0L) { "La meta debe ser mayor que cero." }
        dao.saveMonthlyGoal(MonthlyGoal(month, amountCents, type).toEntity())
    }

    suspend fun setFixedGoal(amountCents: Long, type: GoalType = GoalType.INCOME) {
        require(amountCents > 0L) { "La meta debe ser mayor que cero." }
        dao.upsertGoalSettings(
            GoalSettingsEntity(
                mode = GoalMode.FIXED_MONTHLY.name,
                fixedAmountCents = amountCents,
                fixedType = type.name,
            ),
        )
    }

    suspend fun setGoalMode(mode: GoalMode) {
        val current = dao.getGoalSettings()
        dao.upsertGoalSettings(
            GoalSettingsEntity(
                mode = mode.name,
                fixedAmountCents = current?.fixedAmountCents,
                fixedType = current?.fixedType ?: GoalType.INCOME.name,
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
