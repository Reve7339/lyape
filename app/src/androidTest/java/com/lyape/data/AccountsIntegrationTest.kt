package com.lyape.data

import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lyape.domain.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.YearMonth
import java.util.UUID

/** Solo base en memoria del emulador. Nunca usa la base del teléfono. */
@RunWith(AndroidJUnit4::class)
class AccountsIntegrationTest {
    private lateinit var db: AppDatabase
    private lateinit var accounts: AccountsRepository
    private lateinit var movements: MovementRepository
    private lateinit var plans: MonthlyPlanRepository
    private var clock = Instant.parse("2026-10-22T12:00:00Z")
    private val month = YearMonth.of(2026, 10)

    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext,
            AppDatabase::class.java).build()
        accounts = AccountsRepository(db, now = { clock })
        movements = MovementRepository(db.movementDao(), now = { clock })
        plans = MonthlyPlanRepository(db.monthlyPlanDao())
        runBlocking { accounts.observeSummary().first() }
    }

    @After fun teardown() { db.close() }

    @Test fun undoPaidExpenseReopensDebtAndAllowsDeletion() = runBlocking {
        accounts.setBalance("yape", 10000, 0)
        val id = plans.addExpense(month, "Servicio", 4000)
        val payment = accounts.payExpense(id, month, "yape", 4000)
        assertEquals(0L, accounts.observeExpensePayments(month).first().single().remainingCents)
        assertEquals(payment, accounts.observeExpenseOperations(id, 25).first().single().id)
        accounts.undoOperation(payment)
        assertEquals(10000L, accounts.observeAccount("yape").first()!!.businessCents)
        assertEquals(4000L, accounts.observeExpensePayments(month).first().single().remainingCents)
        assertTrue(accounts.observeExpenseOperations(id, 25).first().single().voided)
        plans.removeExpense(plans.observePlan(month).first().expenses.single())
        assertTrue(plans.observePlan(month).first().expenses.isEmpty())
    }

    @Test fun undoExpenseBeforeNewBalanceDoesNotCreditTwice() = runBlocking {
        accounts.setBalance("yape", 10000, 0)
        val id = plans.addExpense(month, "Servicio", 4000)
        val payment = accounts.payExpense(id, month, "yape", 4000)
        accounts.setBalance("yape", 9000, 1000)
        accounts.undoOperation(payment)
        assertEquals(9000L, accounts.observeAccount("yape").first()!!.businessCents)
        assertEquals(1000L, accounts.observeAccount("yape").first()!!.personalCents)
        assertEquals(4000L, accounts.observeExpensePayments(month).first().single().remainingCents)
        plans.removeExpense(plans.observePlan(month).first().expenses.single())
        assertTrue(plans.observePlan(month).first().expenses.isEmpty())
    }

    @Test fun expenseHistoryIncludesOtherPeriodsAndHasLimit() = runBlocking {
        accounts.setBalance("yape", 10000, 0)
        val id = plans.saveExpense(null, month, ExpenseSchedule.MONTHLY_FIXED, "Servicio", 2000)
        accounts.payExpense(id, month, "yape", 2000)
        clock = clock.plusSeconds(60)
        accounts.payExpense(id, month.plusMonths(1), "yape", 2000)
        assertEquals(1, accounts.observeExpenseOperations(id, 1).first().size)
        assertEquals(listOf("2026-11", "2026-10"), accounts.observeExpenseOperations(id, 25).first().map { it.expensePeriod })
    }

    @Test fun profitGoalDoesNotMoveWhenExpenseIsPaid() = runBlocking {
        accounts.setBalance("yape", 120000, 0)
        val expense = plans.addExpense(month, "Gastos", 95000)
        plans.setFixedGoal(50000, GoalType.PROFIT)
        val before = MonthlyProgressCalculator.calculate(120000, plans.observePlan(month).first())
        accounts.payExpense(expense, month, "yape", 40000)
        val after = MonthlyProgressCalculator.calculate(120000, plans.observePlan(month).first())
        assertEquals(before, after)
        assertEquals(145000L, after.requiredIncomeCents)
        assertEquals(25000L, after.signedProfitCents)
        assertEquals(80000L, accounts.observeAccount("yape").first()!!.businessCents)
        assertEquals(55000L, accounts.observeExpensePayments(month).first().single().remainingCents)
    }

    @Test fun manualPersonalGoesToChosenAccountNotBusinessIncome() = runBlocking {
        accounts.setBalance("cash", 0, 0)
        val id = movements.addManual(5000, clock, "Personal", "cash", MovementClassification.PERSONAL)
        assertEquals(5000L, accounts.observeAccount("cash").first()!!.personalCents)
        assertEquals(0L, accounts.observeAccount("cash").first()!!.businessCents)
        assertEquals(MovementClassification.PERSONAL, movements.findById(id)!!.classification)
        movements.exclude(id)
        movements.restore(id)
        assertEquals(MovementClassification.PERSONAL, movements.findById(id)!!.classification)
    }

    @Test fun defaultsDoNotInferHistoricalBalance() = runBlocking {
        movements.add(payment(50000, clock.minusSeconds(60)))
        val initial = accounts.observeSummary().first()
        assertEquals(listOf("yape", "cash", "plin"), initial.accounts.map { it.id })
        assertEquals(0L, initial.totalCents)
        assertTrue(initial.hasUnconfiguredAccounts)
        accounts.setBalance("yape", 10000, 2000)
        assertEquals(12000L, accounts.observeAccount("yape").first()!!.totalCents)
        assertEquals(1, movements.observeCounts().first().total)
    }

    @Test fun sameMillisecondExistingMovementIsNotCountedTwiceButNewDeliveryIs() = runBlocking {
        movements.add(payment(5000))
        accounts.setBalance("yape", 10000, 0)
        assertEquals(10000L, accounts.observeAccount("yape").first()!!.businessCents)
        movements.add(payment(1000))
        assertEquals(11000L, accounts.observeAccount("yape").first()!!.businessCents)
    }

    @Test fun classificationExclusionAndEditsUpdateOnlyCorrectBucket() = runBlocking {
        accounts.setBalance("yape", 10000, 2000)
        clock = clock.plusSeconds(1)
        val incoming = payment(3000)
        movements.add(incoming)
        assertEquals("yape", movements.findById(incoming.id)!!.accountId)
        assertEquals(13000L, accounts.observeAccount("yape").first()!!.businessCents)
        movements.classify(incoming.id, MovementClassification.PERSONAL)
        assertEquals(5000L, accounts.observeAccount("yape").first()!!.personalCents)
        assertEquals(10000L, accounts.observeAccount("yape").first()!!.businessCents)
        movements.exclude(incoming.id)
        assertEquals(12000L, accounts.observeAccount("yape").first()!!.totalCents)
        movements.restore(incoming.id)
        movements.edit(
            incoming.id, 4000, clock, "Prueba", null, null,
            expectedVersion = 0L, editAttemptId = UUID.randomUUID(),
        )
        assertEquals(6000L, accounts.observeAccount("yape").first()!!.personalCents)
    }

    @Test fun transferConservesMoneyAndIncomeAndUndoKeepsAudit() = runBlocking {
        accounts.setBalance("yape", 10000, 2000)
        accounts.setBalance("cash", 1000, 500)
        val before = accounts.observeSummary().first()
        val id = accounts.transfer("yape", "cash", 3000, MovementClassification.BUSINESS)
        val after = accounts.observeSummary().first()
        assertEquals(before.totalCents, after.totalCents)
        assertEquals(before.businessCents, after.businessCents)
        assertEquals(0, movements.observeCounts().first().total)
        assertEquals(4000L, accounts.observeAccount("cash").first()!!.businessCents)
        accounts.undoOperation(id)
        assertEquals(1000L, accounts.observeAccount("cash").first()!!.businessCents)
        assertTrue(accounts.observeOperations().first().single().voided)
    }

    @Test fun paymentChangesCashAndUnpaidButNotPlannedCostOrIncome() = runBlocking {
        accounts.setBalance("yape", 30000, 9000)
        val expenseId = plans.addExpense(month, "Luz", 15000)
        val id = accounts.payExpense(expenseId, month, "yape", 5000)
        assertEquals(25000L, accounts.observeAccount("yape").first()!!.businessCents)
        val progress = accounts.observeExpensePayments(month).first().single()
        assertEquals(15000L, progress.budgetCents)
        assertEquals(10000L, progress.remainingCents)
        assertEquals(15000L, plans.observePlan(month).first().expenses.single().amountCents)
        assertEquals(0, movements.observeCounts().first().total)
        expectInvalid { plans.updateExpense(expenseId, month, "Luz", 20000) }
        expectInvalid { plans.removeExpense(expenseId) }
        accounts.undoOperation(id)
        assertEquals(15000L, accounts.observeExpensePayments(month).first().single().remainingCents)
        assertEquals(30000L, accounts.observeAccount("yape").first()!!.businessCents)
        plans.updateExpense(expenseId, month, "Luz corregida", 16000)
        assertEquals(16000L, plans.observePlan(month).first().expenses.single().amountCents)
    }

    @Test fun personalFundsDoNotPayBusinessExpenseAndConcurrentPaymentsCannotOverspend() = runBlocking {
        accounts.setBalance("yape", 10000, 90000)
        val expenseId = plans.addExpense(month, "Luz", 10000)
        expectInvalid { accounts.payExpense(expenseId, month, "yape", 10001) }
        val successes = listOf(1, 2).map { async {
            runCatching { accounts.payExpense(expenseId, month, "yape", 7000) }.isSuccess
        } }.awaitAll()
        assertEquals(1, successes.count { it })
        assertEquals(3000L, accounts.observeAccount("yape").first()!!.businessCents)
        assertEquals(90000L, accounts.observeAccount("yape").first()!!.personalCents)
        expectInvalid { accounts.withdraw("yape", 3001, MovementClassification.BUSINESS) }
    }

    @Test fun annualPaymentSharedAcrossMonthsButMonthlyCostRetained() = runBlocking {
        accounts.setBalance("yape", 200000, 0)
        val id = plans.saveExpense(null, month, ExpenseSchedule.ANNUAL_FIXED, "Seguro", 120001)
        accounts.payExpense(id, month, "yape", 120001)
        val november = accounts.observeExpensePayments(month.plusMonths(1)).first().single()
        assertEquals(0L, november.monthlyUnpaidCents)
        assertEquals(0L, november.remainingCents)
        assertEquals(10000L, plans.observePlan(month.plusMonths(1)).first().expenses.single().amountCents)
        expectInvalid { accounts.payExpense(id, month.plusMonths(1), "yape", 1) }
        assertEquals(120001L, accounts.observeExpensePayments(YearMonth.of(2027, 1)).first().single().remainingCents)
    }

    @Test fun reconciliationDoesNotReplayLedgerAndDisallowsUnsafeUndo() = runBlocking {
        accounts.setBalance("yape", 10000, 0)
        val withdrawal = accounts.withdraw("yape", 1000, MovementClassification.BUSINESS)
        accounts.setBalance("yape", 7000, 0)
        assertEquals(7000L, accounts.observeAccount("yape").first()!!.businessCents)
        expectInvalid { accounts.undoOperation(withdrawal) }
        accounts.withdraw("yape", 500, MovementClassification.BUSINESS)
        assertEquals(6500L, accounts.observeAccount("yape").first()!!.businessCents)
    }

    @Test fun aggregateOverflowRollsBackCorrection() = runBlocking {
        accounts.setBalance("yape", Long.MAX_VALUE, 0)
        expectInvalid { accounts.setBalance("cash", 1, 0) }
        assertFalse(accounts.observeAccount("cash").first()!!.initialized)
        assertEquals(Long.MAX_VALUE, accounts.observeSummary().first().totalCents)
    }

    @Test fun assigningAndClearingAccountPreservesIncomeAndTraceability() = runBlocking {
        accounts.setBalance("cash", 1000, 0)
        clock = clock.plusSeconds(1)
        val id = movements.addManual(2000, clock, "Venta")
        val original = movements.findById(id)!!
        movements.edit(
            id, 2000, clock, "Venta", null, null,
            expectedVersion = original.editVersion,
            editAttemptId = UUID.randomUUID(),
            accountId = "cash",
            updateAccount = true,
        )
        val linked = movements.findById(id)!!
        assertTrue(linked.isEdited)
        assertEquals(original.createdAt, linked.createdAt)
        assertEquals(original.source, linked.source)
        assertEquals(3000L, accounts.observeAccount("cash").first()!!.businessCents)
        movements.edit(
            id, 2000, clock, "Venta", null, null,
            expectedVersion = linked.editVersion,
            editAttemptId = UUID.randomUUID(),
            accountId = null,
            updateAccount = true,
        )
        assertEquals(1000L, accounts.observeAccount("cash").first()!!.businessCents)
        assertEquals(2000L, movements.findById(id)!!.amountCents)
    }

    @Test fun undoTransferRejectsSpentDestinationFundsWithoutPartialChange() = runBlocking {
        accounts.setBalance("yape", 10000, 0)
        accounts.setBalance("cash", 0, 0)
        val id = accounts.transfer("yape", "cash", 3000, MovementClassification.BUSINESS)
        accounts.withdraw("cash", 2000, MovementClassification.BUSINESS)
        expectInvalid { accounts.undoOperation(id) }
        assertEquals(7000L, accounts.observeAccount("yape").first()!!.businessCents)
        assertEquals(1000L, accounts.observeAccount("cash").first()!!.businessCents)
        assertFalse(accounts.observeOperations().first().first { it.id == id }.voided)
    }

    @Test fun undoOverflowAcrossAccountsRollsBackVoiding() = runBlocking {
        accounts.setBalance("yape", Long.MAX_VALUE, 0)
        val withdrawal = accounts.withdraw("yape", 1, MovementClassification.BUSINESS)
        accounts.setBalance("cash", 1, 0)
        expectInvalid { accounts.undoOperation(withdrawal) }
        assertEquals(Long.MAX_VALUE, accounts.observeSummary().first().totalCents)
        assertEquals(Long.MAX_VALUE - 1, accounts.observeAccount("yape").first()!!.businessCents)
        assertFalse(accounts.observeOperations().first().single().voided)
    }

    @Test fun maxBaselineWithdrawalAndReceiptRemainReadable() = runBlocking {
        accounts.setBalance("yape", Long.MAX_VALUE, 0)
        accounts.withdraw("yape", 1, MovementClassification.BUSINESS)
        movements.add(payment(1))
        assertEquals(Long.MAX_VALUE, accounts.observeAccount("yape").first()!!.businessCents)
        assertEquals(Long.MAX_VALUE, accounts.observeSummary().first().totalCents)
    }

    @Test fun paymentObserverReadsBudgetAndPaymentsFromSameSnapshot() = runBlocking {
        withTimeout(10000) {
            accounts.setBalance("yape", 10000, 0)
            val expense = plans.addExpense(month, "Luz", 100)
            val payment = accounts.payExpense(expense, month, "yape", 90)
            val started = CompletableDeferred<Unit>()
            val observed = mutableListOf<ExpensePaymentProgress>()
            val observation = async {
                accounts.observeExpensePayments(month).onEach { progress ->
                    observed.add(progress.single())
                    started.complete(Unit)
                }.first { it.single().budgetCents == 20L }
            }
            started.await()
            db.withTransaction {
                accounts.undoOperation(payment)
                plans.updateExpense(expense, month, "Luz corregida", 20)
            }
            assertEquals(20L, observation.await().single().remainingCents)
            assertTrue(observed.all { it.paidCents <= it.budgetCents })
            assertEquals(0L, observed.last().paidCents)
        }
    }

    private suspend fun expectInvalid(block: suspend () -> Unit) {
        try { block(); fail("Se esperaba rechazo de la operación") } catch (_: IllegalArgumentException) { }
    }

    private fun payment(amount: Long, time: Instant = clock) = Movement(
        UUID.randomUUID(), amount, time, time, MovementSource.NOTIFICATION,
        MovementClassification.BUSINESS, false, time, time,
        accountId = "yape",
        medium = PaymentMedium.YAPE,
    )
}
