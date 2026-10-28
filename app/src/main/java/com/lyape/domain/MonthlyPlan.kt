package com.lyape.domain

import java.time.YearMonth
import java.util.UUID

enum class ExpenseSchedule {
    /** Se aplica automáticamente todos los meses. */
    MONTHLY_FIXED,

    /** El total anual se reparte entre los doce meses. */
    ANNUAL_FIXED,

    /** Solo se aplica al mes calendario concreto indicado en [MonthlyExpense.month]. */
    MONTHLY_EXTRA,
}

enum class GoalMode {
    /** Una misma meta se aplica a todos los meses. */
    FIXED_MONTHLY,

    /** Cada mes puede tener una meta distinta. */
    BY_MONTH,
}

enum class GoalType { INCOME, PROFIT }

/** Gasto aplicado al mes mostrado. Es planificación y nunca se convierte en movimiento. */
data class MonthlyExpense(
    val id: UUID,
    val month: YearMonth,
    val name: String,
    val amountCents: Long,
    val schedule: ExpenseSchedule = ExpenseSchedule.MONTHLY_EXTRA,
    /** Monto escrito por el usuario; en un anual es el total anual, no la parte mensual. */
    val plannedAmountCents: Long = amountCents,
)

/**
 * Parte mensual exacta de un gasto anual. Los céntimos que no dividen entre doce se asignan desde
 * enero, de modo que la suma de los doce meses coincide exactamente con [annualCents].
 */
fun annualExpenseShare(annualCents: Long, monthValue: Int): Long {
    require(annualCents > 0L) { "El gasto anual debe ser positivo." }
    require(monthValue in 1..12) { "El mes debe estar entre 1 y 12." }
    val base = annualCents / 12L
    val remainder = annualCents % 12L
    return base + if (monthValue <= remainder) 1L else 0L
}

/** Importe deseado según su tipo; nunca guarda gastos + ganancia como importe escrito. */
data class MonthlyGoal(
    val month: YearMonth,
    val amountCents: Long,
    val type: GoalType = GoalType.INCOME,
)

data class MonthlyPlan(
    val month: YearMonth,
    val expenses: List<MonthlyExpense>,
    val goal: MonthlyGoal?,
    val goalMode: GoalMode = GoalMode.BY_MONTH,
    val fixedGoal: MonthlyGoal? = null,
    val monthlyGoal: MonthlyGoal? = null,
) {
    companion object {
        fun empty(month: YearMonth): MonthlyPlan = MonthlyPlan(
            month = month,
            expenses = emptyList(),
            goal = null,
            goalMode = GoalMode.BY_MONTH,
        )
    }
}

/** Importes exactos del avance mensual. Los valores opcionales indican que no se configuró ese plan. */
data class MonthlyProgress(
    val incomeCents: Long,
    val totalExpensesCents: Long,
    val expensesRemainingCents: Long?,
    val estimatedProfitCents: Long?,
    val goalCents: Long?,
    val goalRemainingCents: Long?,
    val goalExceededCents: Long?,
    val goalType: GoalType = GoalType.INCOME,
    val requiredIncomeCents: Long? = goalCents,
    val signedProfitCents: Long = 0L,
    val calculationError: Boolean = false,
) {
    val hasExpenses: Boolean get() = expensesRemainingCents != null
    val expensesCovered: Boolean get() = hasExpenses && expensesRemainingCents == 0L
    val hasGoal: Boolean get() = goalCents != null
    val goalReached: Boolean get() = hasGoal && goalRemainingCents == 0L
}

/** Reglas únicas para el umbral de gastos y la meta mensual. */
object MonthlyProgressCalculator {

    fun calculate(incomeCents: Long, plan: MonthlyPlan): MonthlyProgress = try {
        calculateExact(incomeCents, plan)
    } catch (_: ArithmeticException) {
        // Nunca presentar un objetivo saturado como alcanzado.
        MonthlyProgress(incomeCents, 0L, null, null, null, null, null, calculationError = true)
    }

    private fun calculateExact(incomeCents: Long, plan: MonthlyPlan): MonthlyProgress {
        val totalExpenses = plan.expenses.fold(0L) { total, expense ->
            require(expense.amountCents >= 0L) { "Un gasto aplicado al mes no puede ser negativo." }
            Math.addExact(total, expense.amountCents)
        }
        val hasExpenses = plan.expenses.isNotEmpty()
        val goal = plan.goal?.also {
            require(it.amountCents > 0L) { "La meta debe ser positiva." }
        }?.amountCents
        val type = plan.goal?.type ?: GoalType.INCOME
        val target = goal?.let { if (type == GoalType.PROFIT) Math.addExact(totalExpenses, it) else it }
        val profit = Math.subtractExact(incomeCents, totalExpenses)

        return MonthlyProgress(
            incomeCents = incomeCents,
            totalExpensesCents = totalExpenses,
            expensesRemainingCents = if (hasExpenses) remaining(totalExpenses, incomeCents) else null,
            estimatedProfitCents = if (hasExpenses && incomeCents >= totalExpenses) {
                incomeCents - totalExpenses
            } else {
                null
            },
            goalCents = goal,
            goalRemainingCents = target?.let { remaining(it, incomeCents) },
            goalExceededCents = target?.takeIf { incomeCents >= it }?.let { Math.subtractExact(incomeCents, it) },
            goalType = type,
            requiredIncomeCents = target,
            signedProfitCents = profit,
        )
    }

    private fun remaining(target: Long, current: Long): Long {
        if (current >= target) return 0L
        return Math.subtractExact(target, current)
    }
}
