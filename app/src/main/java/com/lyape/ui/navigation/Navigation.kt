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
}

/** Pestañas de la barra inferior. */
enum class BottomTab(val label: String, val destination: Destination) {
    SUMMARY("Resumen", Destination.Summary),
    MOVEMENTS("Movimientos", Destination.Movements),
    CLOSE("Cierre", Destination.DailyClose),
    SETTINGS("Ajustes", Destination.Settings),
}

/** Destinos raíz: los únicos donde se muestra la barra inferior. */
val Destination.isRootDestination: Boolean
    get() = BottomTab.entries.any { it.destination == this }

/** Formularios que pueden tener cambios sin guardar y descartarse. */
val Destination.isMovementForm: Boolean
    get() = this == Destination.AddMovement || this is Destination.MovementEdit

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

    fun back(): NavState = if (canGoBack) NavState(backStack.dropLast(1)) else this
}

private const val DETAIL_PREFIX = "detail:"
private const val EDIT_PREFIX = "edit:"
private const val PLAN_PREFIX = "plan:"

internal fun Destination.encode(): String = when (this) {
    Destination.Summary -> "summary"
    Destination.Movements -> "movements"
    Destination.DailyClose -> "close"
    Destination.Settings -> "settings"
    Destination.AddMovement -> "add"
    is Destination.MovementDetail -> "$DETAIL_PREFIX$movementId"
    is Destination.MovementEdit -> "$EDIT_PREFIX$movementId"
    is Destination.MonthlyPlan -> "$PLAN_PREFIX$month"
}

internal fun decodeDestination(value: String): Destination = when {
    value == "summary" -> Destination.Summary
    value == "movements" -> Destination.Movements
    value == "close" -> Destination.DailyClose
    value == "settings" -> Destination.Settings
    value == "add" -> Destination.AddMovement
    value.startsWith(DETAIL_PREFIX) -> Destination.MovementDetail(value.removePrefix(DETAIL_PREFIX))
    value.startsWith(EDIT_PREFIX) -> Destination.MovementEdit(value.removePrefix(EDIT_PREFIX))
    value.startsWith(PLAN_PREFIX) -> Destination.MonthlyPlan(value.removePrefix(PLAN_PREFIX))
    else -> Destination.Summary
}

/** Serializa el estado para sobrevivir a la recreación de la actividad. */
fun NavState.toSavedList(): List<String> = backStack.map { it.encode() }

/** Reconstruye el estado guardado. Si falta información, vuelve al resumen. */
fun navStateFromSavedList(saved: List<String>): NavState =
    if (saved.isEmpty()) NavState() else NavState(saved.map(::decodeDestination))

val NavStateSaver: Saver<NavState, List<String>> = Saver(
    save = { it.toSavedList() },
    restore = ::navStateFromSavedList,
)
