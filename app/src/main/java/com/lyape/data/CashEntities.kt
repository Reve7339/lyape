package com.lyape.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "cash_accounts")
data class CashAccountEntity(
    @PrimaryKey val id: String,
    val name: String,
    val kind: String,
    val baselineAt: Long?,
    val baselineBusinessCents: Long,
    val baselinePersonalCents: Long,
    /** Evita volver a aplicar operaciones anteriores si se concilia en el mismo milisegundo. */
    val baselineOperationSequence: Long,
    val baselineMovementSequence: Long = 0,
)

/** Una transferencia o pago es una sola fila atómica, con su instantánea de gasto inmutable. */
@Entity(
    tableName = "cash_operations",
    indices = [Index(value = ["id"], unique = true), Index("fromAccountId"), Index("toAccountId"),
        Index(value = ["expenseId", "expensePeriod"]), Index("occurredAt")],
)
data class CashOperationEntity(
    @PrimaryKey(autoGenerate = true) val sequence: Long = 0,
    val id: String,
    val kind: String,
    val fromAccountId: String,
    val toAccountId: String?,
    val classification: String,
    val amountCents: Long,
    val note: String?,
    val occurredAt: Long,
    val voidedAt: Long?,
    val expenseId: String?,
    val expensePeriod: String?,
    val expenseName: String?,
    val expenseBudgetCents: Long?,
    val expenseSchedule: String?,
)

data class CashBalanceRow(
    val id: String,
    val name: String,
    val kind: String,
    val baselineAt: Long?,
    val baselineBusinessCents: Long,
    val baselinePersonalCents: Long,
    val movementBusiness: Long,
    val movementPersonal: Long,
    val operationBusiness: Long,
    val operationPersonal: Long,
)

data class ExpensePaidRow(val expenseId: String, val expensePeriod: String, val paidCents: Long)

/** Asociación explícita de un proveedor de captura con su cuenta receptora. */
@Entity(tableName = "capture_accounts")
data class CaptureAccountEntity(
    @PrimaryKey val provider: String,
    val accountId: String,
)
