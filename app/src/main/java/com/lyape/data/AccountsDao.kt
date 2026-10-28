package com.lyape.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** Agrega en SQLite: nunca traslada el historial completo a la memoria de la app. */
private const val BALANCES = """
SELECT a.id, a.name, a.kind, a.baselineAt, a.baselineBusinessCents, a.baselinePersonalCents,
 COALESCE((SELECT SUM(m.amountCents) FROM movements m WHERE m.accountId=a.id AND m.occurredAt>=a.baselineAt AND m.rowid>a.baselineMovementSequence AND m.classification='BUSINESS'),0) AS movementBusiness,
 COALESCE((SELECT SUM(m.amountCents) FROM movements m WHERE m.accountId=a.id AND m.occurredAt>=a.baselineAt AND m.rowid>a.baselineMovementSequence AND m.classification='PERSONAL'),0) AS movementPersonal,
 COALESCE((SELECT SUM(CASE WHEN o.fromAccountId=a.id THEN -o.amountCents ELSE o.amountCents END) FROM cash_operations o WHERE (o.fromAccountId=a.id OR o.toAccountId=a.id) AND o.sequence>a.baselineOperationSequence AND a.baselineAt IS NOT NULL AND o.voidedAt IS NULL AND o.classification='BUSINESS'),0) AS operationBusiness,
 COALESCE((SELECT SUM(CASE WHEN o.fromAccountId=a.id THEN -o.amountCents ELSE o.amountCents END) FROM cash_operations o WHERE (o.fromAccountId=a.id OR o.toAccountId=a.id) AND o.sequence>a.baselineOperationSequence AND a.baselineAt IS NOT NULL AND o.voidedAt IS NULL AND o.classification='PERSONAL'),0) AS operationPersonal
FROM cash_accounts a
"""

@Dao
interface AccountsDao {
    @Query(BALANCES + " ORDER BY CASE a.kind WHEN 'YAPE' THEN 0 WHEN 'CASH' THEN 1 ELSE 2 END, a.name, a.id")
    fun observeBalances(): Flow<List<CashBalanceRow>>

    @Query(BALANCES + " WHERE a.id=:id")
    suspend fun balance(id: String): CashBalanceRow?

    @Query(BALANCES)
    suspend fun balances(): List<CashBalanceRow>

    @Query("SELECT COALESCE(MAX(rowid),0) FROM movements")
    suspend fun latestMovementSequence(): Long

    @Query("SELECT * FROM cash_accounts WHERE id=:id")
    suspend fun account(id: String): CashAccountEntity?

    @Query("SELECT * FROM capture_accounts WHERE provider=:provider")
    suspend fun captureAccount(provider: String): CaptureAccountEntity?

    @Query("SELECT * FROM capture_accounts WHERE provider=:provider")
    fun observeCaptureAccount(provider: String): Flow<CaptureAccountEntity?>

    /** Cuentas crudas: no calcula saldos ni depende de movimientos u operaciones. */
    @Query("SELECT * FROM cash_accounts ORDER BY name, id")
    fun observeAccounts(): Flow<List<CashAccountEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCaptureAccount(account: CaptureAccountEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setCaptureAccount(account: CaptureAccountEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAccount(account: CashAccountEntity)

    @Update
    suspend fun updateAccount(account: CashAccountEntity)

    @Query("SELECT COALESCE(MAX(sequence),0) FROM cash_operations")
    suspend fun latestSequence(): Long

    @Insert
    suspend fun insertOperation(operation: CashOperationEntity)

    @Query("SELECT * FROM cash_operations WHERE id=:id")
    suspend fun operation(id: String): CashOperationEntity?

    @Query("UPDATE cash_operations SET voidedAt=:timestamp WHERE id=:id AND voidedAt IS NULL")
    suspend fun voidOperation(id: String, timestamp: Long)

    @Query("SELECT * FROM cash_operations WHERE (:accountId IS NULL OR fromAccountId=:accountId OR toAccountId=:accountId) ORDER BY sequence DESC LIMIT 100")
    fun observeOperations(accountId: String?): Flow<List<CashOperationEntity>>

    /** Historial del gasto, incluidos otros meses y años; carga incremental desde la UI. */
    @Query("SELECT * FROM cash_operations WHERE expenseId=:expenseId ORDER BY sequence DESC LIMIT :limit")
    fun observeExpenseOperations(expenseId: String, limit: Int): Flow<List<CashOperationEntity>>

    @Query("SELECT COALESCE(SUM(amountCents),0) FROM cash_operations WHERE expenseId=:id AND expensePeriod=:period AND voidedAt IS NULL")
    suspend fun paid(id: String, period: String): Long

    @Query("SELECT expenseId, expensePeriod, SUM(amountCents) AS paidCents FROM cash_operations WHERE expenseId IS NOT NULL AND expensePeriod IS NOT NULL AND (expensePeriod=:month OR expensePeriod=:year) AND voidedAt IS NULL GROUP BY expenseId, expensePeriod")
    suspend fun payments(month: String, year: String): List<ExpensePaidRow>

    /** Una sola fuente de invalidación para leer presupuesto y pagos en la misma transacción. */
    @Query("""
        SELECT EXISTS(SELECT 1 FROM monthly_expenses WHERE month=:month)
             + EXISTS(SELECT 1 FROM recurring_expenses)
             + EXISTS(SELECT 1 FROM cash_operations WHERE expensePeriod=:month OR expensePeriod=:year)
    """)
    fun observePaymentChanges(month: String, year: String): Flow<Int>
}
