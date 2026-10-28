package com.lyape.data

import androidx.room.withTransaction
import com.lyape.domain.AccountBalance
import com.lyape.domain.AccountKind
import com.lyape.domain.CaptureAccount
import com.lyape.domain.CaptureAccountLink
import com.lyape.domain.CaptureConfiguration
import com.lyape.domain.CaptureProvider
import com.lyape.domain.CashOperation
import com.lyape.domain.CashOperationKind
import com.lyape.domain.CashSummary
import com.lyape.domain.ExpensePaymentProgress
import com.lyape.domain.ExpenseSchedule
import com.lyape.domain.MovementClassification
import com.lyape.domain.expensePaymentPeriod
import com.lyape.domain.expensePaymentProgress
import com.lyape.domain.requireCashAmount
import com.lyape.domain.sumCashExact
import com.lyape.domain.MonthlyExpense
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import java.time.Instant
import java.time.YearMonth
import java.util.UUID

/** Resultado de reparar una asociación; no implica que se haya movido un vínculo válido. */
enum class CaptureLinkRepair {
    ALREADY_VALID,
    REPAIRED,
}

/** Saldos registrados, no saldos bancarios: cada cuenta comienza en una conciliación explícita. */
class AccountsRepository(
    private val database: AppDatabase,
    private val now: () -> Instant = Instant::now,
    private val newId: () -> UUID = UUID::randomUUID,
) : CaptureAccountResolver {
    private val dao = database.accountsDao()
    private val planDao = database.monthlyPlanDao()

    fun observeSummary(): Flow<CashSummary> = dao.observeBalances()
        .onStart { ensureAccounts() }
        .map { rows -> CashSummary(rows.map(CashBalanceRow::toDomain)).also {
            // Detecta extremos antes de entregar el estado a Compose.
            it.businessCents
            it.personalCents
            it.totalCents
        } }

    fun observeAccount(id: String): Flow<AccountBalance?> =
        observeSummary().map { summary -> summary.accounts.find { it.id == id } }

    /** Catálogo para formularios: no depende del cálculo de saldos. */
    fun observeSelectableAccounts(): Flow<List<CaptureAccount>> = dao.observeAccounts()
        .onStart { ensureAccounts() }
        .map { rows -> rows.map { CaptureAccount(it.id, it.name, AccountKind.valueOf(it.kind)) } }

    suspend fun addAccount(name: String, kind: AccountKind = AccountKind.BANK): String = safeTransaction {
        require(name.trim().isNotEmpty()) { "Escribe el nombre de la cuenta." }
        require(name.trim().length <= 60) { "Usa un nombre de hasta 60 caracteres." }
        require(kind == AccountKind.BANK) { "Yape y Efectivo ya están disponibles." }
        val id = newId().toString()
        dao.insertAccount(CashAccountEntity(id, name.trim(), kind.name, null, 0, 0, 0))
        id
    }

    /** No reescribe historia: la cifra conciliada reemplaza el punto de partida desde ahora. */
    suspend fun setBalance(accountId: String, businessCents: Long, personalCents: Long) = safeTransaction {
        require(businessCents >= 0 && personalCents >= 0) { "Los saldos no pueden ser negativos." }
        Math.addExact(businessCents, personalCents)
        ensureAccounts()
        val account = requireNotNull(dao.account(accountId)) { "La cuenta ya no existe." }
        dao.updateAccount(account.copy(
            baselineAt = now().toEpochMilli(),
            baselineBusinessCents = businessCents,
            baselinePersonalCents = personalCents,
            baselineOperationSequence = dao.latestSequence(),
            baselineMovementSequence = dao.latestMovementSequence(),
        ))
        validateSummary()
    }

    suspend fun transfer(
        fromAccountId: String,
        toAccountId: String,
        amountCents: Long,
        classification: MovementClassification,
        note: String? = null,
    ): UUID = safeTransaction {
        require(fromAccountId != toAccountId) { "Elige dos cuentas distintas." }
        requireCashAmount(amountCents, bucket(initializedBalance(fromAccountId), classification))
        val destination = initializedBalance(toAccountId)
        Math.addExact(bucket(destination, classification), amountCents)
        Math.addExact(destination.totalCents, amountCents)
        insertOperation(CashOperationKind.TRANSFER, fromAccountId, toAccountId, amountCents, classification, note)
    }

    suspend fun withdraw(
        accountId: String,
        amountCents: Long,
        classification: MovementClassification,
        note: String? = null,
    ): UUID = safeTransaction {
        requireCashAmount(amountCents, bucket(initializedBalance(accountId), classification))
        insertOperation(CashOperationKind.WITHDRAWAL, accountId, null, amountCents, classification, note)
    }

    fun observeOperations(accountId: String? = null): Flow<List<CashOperation>> =
        dao.observeOperations(accountId).map { rows -> rows.map { it.toDomain() } }

    fun observeExpenseOperations(expenseId: UUID, limit: Int): Flow<List<CashOperation>> {
        require(limit > 0)
        return dao.observeExpenseOperations(expenseId.toString(), limit).map { rows -> rows.map { it.toDomain() } }
    }

    /**
     * Configuración de captura observada a partir de cuentas crudas.
     *
     * No usa `observeSummary()` ni calcula saldos: una cuenta válida debe poder resolverse y
     * seleccionarse aunque sus totales monetarios fallen por desbordamiento u otro error.
     */
    fun observeCaptureConfiguration(provider: CaptureProvider): Flow<CaptureConfiguration> =
        combine(
            dao.observeCaptureAccount(provider.name),
            dao.observeAccounts(),
        ) { association, accounts ->
            captureConfigurationFor(provider, association, accounts)
        }.onStart { ensureAccounts() }

    /**
     * Cambia solo la asociación de capturas futuras. No reescribe movimientos ni saldos y conserva
     * la cuenta requerida por el proveedor. No mueve dinero entre cuentas.
     */
    suspend fun setCaptureAccount(provider: CaptureProvider, accountId: String) = safeTransaction {
        val account = requireNotNull(dao.account(accountId)) { "La cuenta ya no existe. Elige otra." }
        require(AccountKind.valueOf(account.kind) == provider.requiredAccountKind) {
            "Elige una cuenta compatible con este proveedor."
        }
        dao.setCaptureAccount(CaptureAccountEntity(provider.name, accountId))
    }

    /**
     * Restablece únicamente una asociación ausente o rota.
     *
     * Relee la asociación y la cuenta cruda dentro de la misma transacción: una solicitud preparada
     * cuando el vínculo estaba roto no redirige una asociación que ya se reparó con una cuenta
     * personalizada. No duplica cuentas ni mueve movimientos, saldos o bases históricas.
     */
    suspend fun repairCaptureLink(provider: CaptureProvider): CaptureLinkRepair = safeTransaction {
        val association = dao.captureAccount(provider.name)
        val currentAccount = association?.let { dao.account(it.accountId) }
        if (currentAccount != null &&
            AccountKind.valueOf(currentAccount.kind) == provider.requiredAccountKind
        ) {
            return@safeTransaction CaptureLinkRepair.ALREADY_VALID
        }

        val defaultId = when (provider) {
            CaptureProvider.YAPE -> "yape"
            CaptureProvider.INTERBANK_PLIN -> "plin"
        }
        val defaultName = when (provider) {
            CaptureProvider.YAPE -> "Yape"
            CaptureProvider.INTERBANK_PLIN -> "Plin"
        }
        if (dao.account(defaultId) == null) {
            dao.insertAccount(
                CashAccountEntity(
                    id = defaultId,
                    name = defaultName,
                    kind = provider.requiredAccountKind.name,
                    baselineAt = null,
                    baselineBusinessCents = 0,
                    baselinePersonalCents = 0,
                    baselineOperationSequence = 0,
                    baselineMovementSequence = 0,
                ),
            )
        }
        val default = requireNotNull(dao.account(defaultId)) { "No se pudo preparar el registro local." }
        require(AccountKind.valueOf(default.kind) == provider.requiredAccountKind) {
            "El registro local no es compatible con este proveedor."
        }
        dao.setCaptureAccount(CaptureAccountEntity(provider.name, defaultId))
        CaptureLinkRepair.REPAIRED
    }

    /**
     * Cuenta receptora válida o `null`; nunca cae a Yape ni a una cuenta por nombre similar.
     * Lee la cuenta cruda: no depende del cálculo de saldos.
     */
    suspend fun captureAccountLink(provider: CaptureProvider): CaptureAccountLink? {
        ensureAccounts()
        val association = dao.captureAccount(provider.name) ?: return null
        val account = dao.account(association.accountId)
            ?.toCaptureAccount()
            ?.takeIf { it.kind == provider.requiredAccountKind }
        return CaptureAccountLink(provider, association.accountId, account)
    }

    override suspend fun accountIdFor(provider: CaptureProvider): String? =
        captureAccountLink(provider)?.account?.id

    fun observeExpensePayments(month: YearMonth): Flow<List<ExpensePaymentProgress>> =
        dao.observePaymentChanges(month.toString(), month.year.toString()).map {
            database.withTransaction {
                val expenses = (planDao.expenses(month.toString()).map(MonthlyExpenseEntity::toDomain) +
                    planDao.recurringExpenses().map { it.toDomain(month) })
                    .sortedWith(compareBy<MonthlyExpense>({ it.schedule.ordinal }, { it.name.lowercase() }))
                val paid = dao.payments(month.toString(), month.year.toString())
                    .associate { (it.expenseId to it.expensePeriod) to it.paidCents }
                expenses.map { expense ->
                    expensePaymentProgress(expense,
                        paid[expense.id.toString() to expensePaymentPeriod(month, expense.schedule)] ?: 0L)
                }
            }
        }

    suspend fun payExpense(
        expenseId: UUID,
        month: YearMonth,
        accountId: String,
        amountCents: Long,
    ): UUID = safeTransaction {
        // Recupera presupuesto y saldo dentro de la misma transacción; no confía en la pantalla.
        val recurring = planDao.recurringExpense(expenseId)
        val expense = recurring?.toDomain(month)
            ?: planDao.expense(expenseId)?.takeIf { it.month == month.toString() }?.toDomain()
        requireNotNull(expense) { "Este gasto ya no está disponible. Vuelve al plan." }
        val period = expensePaymentPeriod(month, expense.schedule)
        val progress = expensePaymentProgress(expense, dao.paid(expenseId.toString(), period))
        require(amountCents > 0L) { "El importe debe ser mayor que cero." }
        require(amountCents <= progress.remainingCents) { "El importe supera lo pendiente. Actualiza el plan." }
        requireCashAmount(amountCents, initializedBalance(accountId).businessCents)
        val id = newId()
        dao.insertOperation(CashOperationEntity(
            id = id.toString(), kind = CashOperationKind.EXPENSE_PAYMENT.name,
            fromAccountId = accountId, toAccountId = null,
            classification = MovementClassification.BUSINESS.name,
            amountCents = amountCents, note = null, occurredAt = now().toEpochMilli(), voidedAt = null,
            expenseId = expenseId.toString(), expensePeriod = period, expenseName = expense.name,
            expenseBudgetCents = progress.budgetCents, expenseSchedule = expense.schedule.name,
        ))
        id
    }

    /** Anula sin borrar. Tras una conciliación posterior ya no es seguro alterar ese saldo. */
    suspend fun undoOperation(id: UUID) = safeTransaction {
        val operation = requireNotNull(dao.operation(id.toString())) { "La operación ya no existe." }
        require(operation.voidedAt == null) { "La operación ya está anulada." }
        for (accountId in listOfNotNull(operation.fromAccountId, operation.toAccountId)) {
            val account = requireNotNull(dao.account(accountId)) { "La cuenta ya no existe." }
            require(operation.kind == CashOperationKind.EXPENSE_PAYMENT.name || operation.sequence > account.baselineOperationSequence) {
                "El saldo se corrigió después de esta operación. Corrige el saldo actual para ajustarlo."
            }
        }
        val classification = MovementClassification.valueOf(operation.classification)
        operation.toAccountId?.let { destination ->
            requireCashAmount(operation.amountCents, bucket(initializedBalance(destination), classification))
        }
        val sourceAccount = requireNotNull(dao.account(operation.fromAccountId))
        // Un pago anterior al saldo actualizado ya no participa en ese saldo. Anularlo
        // reabre el gasto sin devolver dinero por segunda vez ni alterar la nueva base.
        if (operation.sequence > sourceAccount.baselineOperationSequence) {
            val source = initializedBalance(operation.fromAccountId)
            Math.addExact(bucket(source, classification), operation.amountCents)
            Math.addExact(source.totalCents, operation.amountCents)
        }
        dao.voidOperation(id.toString(), now().toEpochMilli())
        validateSummary()
    }

    suspend fun voidOperation(id: UUID) = undoOperation(id)

    private suspend fun initializedBalance(id: String): AccountBalance {
        val balance = requireNotNull(dao.balance(id)) { "La cuenta ya no existe." }.toDomain()
        require(balance.initialized) { "Configura primero el saldo de ${balance.name}." }
        return balance
    }

    private fun bucket(balance: AccountBalance, classification: MovementClassification): Long = when (classification) {
        MovementClassification.BUSINESS -> balance.businessCents
        MovementClassification.PERSONAL -> balance.personalCents
        MovementClassification.EXCLUDED -> throw IllegalArgumentException("Elige Negocio o Personal.")
    }

    private suspend fun insertOperation(
        kind: CashOperationKind, from: String, to: String?, amount: Long,
        classification: MovementClassification, note: String?,
    ): UUID {
        require((note?.length ?: 0) <= 500) { "Usa una nota de hasta 500 caracteres." }
        val id = newId()
        dao.insertOperation(CashOperationEntity(
            id = id.toString(), kind = kind.name, fromAccountId = from, toAccountId = to,
            classification = classification.name, amountCents = amount, note = note?.trim()?.takeIf(String::isNotEmpty),
            occurredAt = now().toEpochMilli(), voidedAt = null,
            expenseId = null, expensePeriod = null, expenseName = null, expenseBudgetCents = null, expenseSchedule = null,
        ))
        return id
    }

    private suspend fun ensureAccounts() = database.withTransaction {
        dao.insertAccount(CashAccountEntity("yape", "Yape", AccountKind.YAPE.name, null, 0, 0, 0))
        dao.insertAccount(CashAccountEntity("cash", "Efectivo", AccountKind.CASH.name, null, 0, 0, 0))
        dao.insertCaptureAccount(CaptureAccountEntity(CaptureProvider.YAPE.name, "yape"))
        // Registro local listo para usar, no una cuenta bancaria nueva. Una asociación previa
        // conserva su cuenta, saldo e historial; nunca se redirige silenciosamente.
        if (dao.captureAccount(CaptureProvider.INTERBANK_PLIN.name) == null) {
            dao.insertAccount(CashAccountEntity("plin", "Plin", AccountKind.BANK.name, null, 0, 0, 0))
            dao.insertCaptureAccount(CaptureAccountEntity(CaptureProvider.INTERBANK_PLIN.name, "plin"))
        }
    }

    private suspend fun validateSummary() {
        val summary = CashSummary(dao.balances().map(CashBalanceRow::toDomain))
        summary.businessCents
        summary.personalCents
        summary.totalCents
    }

    private suspend fun <T> safeTransaction(block: suspend () -> T): T = try {
        database.withTransaction { block() }
    } catch (_: ArithmeticException) {
        throw IllegalArgumentException("El importe supera el límite admitido. Revisa los saldos.")
    }
}

/**
 * Resolución pura sobre cuentas crudas. No recibe ni calcula saldos; una cuenta válida debe
 * poder asociarse aunque su total monetario falle en otra consulta.
 */
internal fun captureConfigurationFor(
    provider: CaptureProvider,
    association: CaptureAccountEntity?,
    accounts: List<CashAccountEntity>,
): CaptureConfiguration {
    val link = association?.let { row ->
        val account = accounts.firstOrNull { it.id == row.accountId }
            ?.toCaptureAccount()
            ?.takeIf { it.kind == provider.requiredAccountKind }
        CaptureAccountLink(provider, row.accountId, account)
    }
    return CaptureConfiguration(
        provider = provider,
        link = link,
        eligibleAccounts = accounts
            .filter { it.kind == provider.requiredAccountKind.name }
            .map(CashAccountEntity::toCaptureAccount),
    )
}

private fun CashAccountEntity.toCaptureAccount(): CaptureAccount =
    CaptureAccount(id, name, AccountKind.valueOf(kind))

private fun CashBalanceRow.toDomain(): AccountBalance = AccountBalance(
    id, name, AccountKind.valueOf(kind), baselineAt != null,
    sumCashExact(baselineBusinessCents, movementBusiness, operationBusiness),
    sumCashExact(baselinePersonalCents, movementPersonal, operationPersonal),
    baselineAt?.let(Instant::ofEpochMilli),
).also { it.totalCents }

private fun CashOperationEntity.toDomain() = CashOperation(
    UUID.fromString(id), CashOperationKind.valueOf(kind), fromAccountId, toAccountId,
    MovementClassification.valueOf(classification), amountCents, note, Instant.ofEpochMilli(occurredAt),
    voidedAt != null, expenseName, expensePeriod,
)
