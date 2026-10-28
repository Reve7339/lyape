package com.lyape.ui.movements

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

internal enum class MovementAction { PERSONAL, BUSINESS, EXCLUDE, RESTORE }

/** Dueño del guardado por fila; vive con el ViewModel, no con una fila reciclable. */
internal class MovementActions(
    private val scope: CoroutineScope,
    private val save: suspend (String, MovementAction) -> Boolean,
) {
    private val pending = MutableStateFlow<Set<String>>(emptySet())
    val busy = pending.asStateFlow()
    private val messages = Channel<String>(Channel.BUFFERED)
    val feedback = messages.receiveAsFlow()

    // Llamado desde UI/main. La reserva ocurre antes de lanzar la corrutina.
    fun perform(id: String, action: MovementAction) {
        if (id in pending.value) return
        pending.value += id
        scope.launch {
            try {
                val changed = save(id, action)
                messages.trySend(if (!changed) "El movimiento ya no está disponible." else when (action) {
                    MovementAction.PERSONAL -> "Marcado como personal"
                    MovementAction.BUSINESS -> "Marcado como negocio"
                    MovementAction.EXCLUDE -> "Anulado. Puedes restaurarlo desde su menú."
                    MovementAction.RESTORE -> "Movimiento restaurado"
                })
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) {
                messages.trySend("No se guardó el cambio. Vuelve a intentarlo desde el menú.")
            } finally { pending.value -= id }
        }
    }
}
