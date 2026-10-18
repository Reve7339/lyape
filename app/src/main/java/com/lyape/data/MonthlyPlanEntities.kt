package com.lyape.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.lyape.domain.MonthlyExpense
import com.lyape.domain.MonthlyGoal
import com.lyape.domain.ExpenseSchedule
import com.lyape.domain.annualExpenseShare
import java.time.YearMonth
import java.util.UUID

@Entity(
    tableName = "monthly_expenses",
    indices = [Index("month")],
)
data class MonthlyExpenseEntity(
    @PrimaryKey val id: UUID,
    val month: String,
    val name: String,
    val amountCents: Long,
)

@Entity(tableName = "monthly_goals")
data class MonthlyGoalEntity(
    @PrimaryKey val month: String,
    val amountCents: Long,
)

@Entity(
    tableName = "recurring_expenses",
    indices = [Index("schedule")],
)
data class RecurringExpenseEntity(
    @PrimaryKey val id: UUID,
    val name: String,
    val amountCents: Long,
    val schedule: String,
)

@Entity(tableName = "goal_settings")
data class GoalSettingsEntity(
    @PrimaryKey val id: Int = SINGLETON_ID,
    val mode: String,
    val fixedAmountCents: Long?,
) {
    companion object {
        const val SINGLETON_ID = 0
    }
}

internal fun MonthlyExpenseEntity.toDomain(): MonthlyExpense = MonthlyExpense(
    id = id,
    month = YearMonth.parse(month),
    name = name,
    amountCents = amountCents,
)

internal fun MonthlyExpense.toEntity(): MonthlyExpenseEntity = MonthlyExpenseEntity(
    id = id,
    month = month.toString(),
    name = name,
    amountCents = amountCents,
)

internal fun RecurringExpenseEntity.toDomain(month: YearMonth): MonthlyExpense = MonthlyExpense(
    id = id,
    month = month,
    name = name,
    amountCents = when (val parsedSchedule = ExpenseSchedule.valueOf(schedule)) {
        ExpenseSchedule.ANNUAL_FIXED -> annualExpenseShare(amountCents, month.monthValue)
        ExpenseSchedule.MONTHLY_FIXED -> amountCents
        ExpenseSchedule.MONTHLY_EXTRA -> error("Un gasto extra no pertenece a la tabla recurrente.")
    },
    schedule = ExpenseSchedule.valueOf(schedule),
    plannedAmountCents = amountCents,
)

internal fun MonthlyGoalEntity.toDomain(): MonthlyGoal = MonthlyGoal(
    month = YearMonth.parse(month),
    amountCents = amountCents,
)

internal fun MonthlyGoal.toEntity(): MonthlyGoalEntity = MonthlyGoalEntity(
    month = month.toString(),
    amountCents = amountCents,
)
