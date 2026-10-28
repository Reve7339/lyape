package com.lyape.ui.navigation

import androidx.compose.runtime.saveable.Saver

/**
 * Destinos de la navegación mínima del bloque 1.
 *
 * "Detalle" se abre sobre la pestaña de movimientos; el resto son pestañas de primer nivel.
 */
sealed interface Destination {
    data object Summary : Destination
    data object Movements : Destination
    data class MovementDetail(val movementId: String) : Destination
    data object AddMovement : Destination
    data class MovementEdit(val movementId: String) : Destination
    data class MonthlyPlan(val month: String) : Destination
    data object DailyClose : Destination
    data object Settings : Destination
    data object Privacy : Destination
    data object Accounts : Destination
    data object Reports : Destination
    data class AccountDetail(val accountId: String) : Destination
    data class AccountForm(val mode: String, val accountId: String? = null) : Destination
    data class ExpensePayment(val expenseId: String, val month: String) : Destination
}

/** Pestañas de la barra inferior. */
enum class BottomTab(val label: String, val destination: Destination) {
    SUMMARY("Inicio", Destination.Summary),
    ACCOUNTS("Cuentas", Destination.Accounts),
    MOVEMENTS("Movimientos", Destination.Movements),
    SETTINGS("Ajustes", Destination.Settings),
}

/** Destinos raíz: los únicos donde se muestra la barra inferior. */
val Destination.isRootDestination: Boolean
    get() = BottomTab.entries.any { it.destination == this }

/** Formularios que pueden tener cambios sin guardar y descartarse. */
val Destination.isMovementForm: Boolean
    get() = this == Destination.AddMovement || this is Destination.MovementEdit ||
        this is Destination.AccountForm || this is Destination.ExpensePayment

/** Qué hacer al retroceder desde una pantalla. */
enum class ExitDecision { LEAVE, CONFIRM, STAY }

/**
 * Decide si al retroceder hay que salir o pedir confirmación.
 *
 * Solo un formulario con cambios sin guardar pide confirmación.
 */
fun exitDecision(
    destination: Destination,
    hasUnsavedChanges: Boolean,
    isSaving: Boolean = false,
): ExitDecision = when {
    destination.isMovementForm && isSaving -> ExitDecision.STAY
    destination.isMovementForm && hasUnsavedChanges -> ExitDecision.CONFIRM
    else -> ExitDecision.LEAVE
}

/**
 * Estado de navegación puro y comprobable sin Android.
 *
 * - [selectTab] resetea la pila a una pestaña de primer nivel.
 * - [open] apila un destino secundario (por ejemplo, el detalle).
 * - [back] desapila; en la raíz no hace nada.
 */
data class NavState(val backStack: List<Destination> = listOf(Destination.Summary)) {
    init {
        require(backStack.isNotEmpty()) { "La pila de navegación no puede estar vacía." }
    }

    val current: Destination get() = backStack.last()

    val canGoBack: Boolean get() = backStack.size > 1

    /** Pestaña resaltada: la raíz de la pila, ya que las pestañas resetean la pila. */
    val selectedTab: BottomTab?
        get() = BottomTab.entries.firstOrNull { it.destination == backStack.first() }

    fun selectTab(tab: BottomTab): NavState = NavState(listOf(tab.destination))

    fun open(destination: Destination): NavState = NavState(backStack + destination)

    /** Conserva el mes realmente consultado al volver del pago, no solo el mes de entrada. */
    fun openExpensePayment(expenseId: String, month: String): NavState {
        val base = if (current is Destination.MonthlyPlan) {
            copy(backStack = backStack.dropLast(1) + Destination.MonthlyPlan(month))
        } else this
        return base.open(Destination.ExpensePayment(expenseId, month))
    }

    fun back(): NavState = if (canGoBack) NavState(backStack.dropLast(1)) else this
}

private const val DETAIL_PREFIX = "detail:"
private const val EDIT_PREFIX = "edit:"
private const val PLAN_PREFIX = "plan:"
private const val ACCOUNT_PREFIX = "account:"
private const val ACCOUNT_FORM_PREFIX = "account-form:"
private const val EXPENSE_PAYMENT_PREFIX = "expense-payment:"

internal fun Destination.encode(): String = when (this) {
    Destination.Summary -> "summary"
    Destination.Movements -> "movements"
    Destination.DailyClose -> "close"
    Destination.Settings -> "settings"
    Destination.Privacy -> "privacy"
    Destination.AddMovement -> "add"
    Destination.Accounts -> "accounts"
    Destination.Reports -> "reports"
    is Destination.AccountDetail -> "$ACCOUNT_PREFIX$accountId"
    is Destination.AccountForm -> "$ACCOUNT_FORM_PREFIX$mode:${accountId.orEmpty()}"
    is Destination.ExpensePayment -> "$EXPENSE_PAYMENT_PREFIX$month:$expenseId"
    is Destination.MovementDetail -> "$DETAIL_PREFIX$movementId"
    is Destination.MovementEdit -> "$EDIT_PREFIX$movementId"
    is Destination.MonthlyPlan -> "$PLAN_PREFIX$month"
}

internal fun decodeDestination(value: String): Destination = when {
    value == "summary" -> Destination.Summary
    value == "movements" -> Destination.Movements
    value == "close" -> Destination.Movements
    value == "settings" -> Destination.Settings
    value == "privacy" -> Destination.Privacy
    value == "add" -> Destination.AddMovement
    value == "accounts" -> Destination.Accounts
    value == "reports" -> Destination.Reports
    value.startsWith(ACCOUNT_PREFIX) -> Destination.AccountDetail(value.removePrefix(ACCOUNT_PREFIX))
    value.startsWith(ACCOUNT_FORM_PREFIX) -> {
        val parts = value.removePrefix(ACCOUNT_FORM_PREFIX).split(':', limit = 2)
        if (parts.size == 2 && parts[0] in setOf("add", "balance", "transfer", "withdraw")) {
            Destination.AccountForm(parts[0], parts[1].takeIf { it.isNotBlank() })
        } else Destination.Accounts
    }
    value.startsWith(EXPENSE_PAYMENT_PREFIX) -> {
        val parts = value.removePrefix(EXPENSE_PAYMENT_PREFIX).split(':', limit = 2)
        if (parts.size == 2) Destination.ExpensePayment(parts[1], parts[0]) else Destination.Summary
    }
    value.startsWith(DETAIL_PREFIX) -> Destination.MovementDetail(value.removePrefix(DETAIL_PREFIX))
    value.startsWith(EDIT_PREFIX) -> Destination.MovementEdit(value.removePrefix(EDIT_PREFIX))
    value.startsWith(PLAN_PREFIX) -> Destination.MonthlyPlan(value.removePrefix(PLAN_PREFIX))
    else -> Destination.Summary
}

/** Serializa el estado para sobrevivir a la recreación de la actividad. */
fun NavState.toSavedList(): List<String> = backStack.map { it.encode() }

/** Reconstruye el estado guardado. Si falta información, vuelve al resumen. */
fun navStateFromSavedList(saved: List<String>): NavState {
    if (saved.isEmpty()) return NavState()
    val destinations = saved.map(::decodeDestination)
    return NavState(destinations)
}

val NavStateSaver: Saver<NavState, List<String>> = Saver(
    save = { it.toSavedList() },
    restore = ::navStateFromSavedList,
)
