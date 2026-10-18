package com.lyape.ui.movements

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.lyape.LyapeApplication
import com.lyape.domain.Movement
import com.lyape.ui.model.MovementUi
import com.lyape.ui.model.toUi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

/** Estado de la lista de movimientos. */
sealed interface MovementsUiState {
    data object Loading : MovementsUiState
    data object Empty : MovementsUiState
    data object Error : MovementsUiState
    data class Content(val movements: List<MovementUi>) : MovementsUiState
}

/**
 * Expone los movimientos de Room como un `Flow` de estados. No hay lógica de negocio aquí: el
 * orden y el filtrado por periodo los define el repositorio.
 */
class MovementsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = (application as LyapeApplication).movementRepository

    /**
     * Flujo frío de estados. Cada acceso crea uno nuevo para que `Reintentar` pueda volver a leer
     * los datos sin reiniciar la aplicación.
     */
    val state: Flow<MovementsUiState>
        get() = repository.observeAll().toMovementsUiState()
}

/**
 * Traduce los movimientos de Room a estados de la lista.
 *
 * Es una función pura sobre un `Flow` frío: volver a recolectarlo pide los datos de nuevo sin
 * acumular la colección anterior.
 */
internal fun Flow<List<Movement>>.toMovementsUiState(): Flow<MovementsUiState> =
    map { movements ->
        if (movements.isEmpty()) {
            MovementsUiState.Empty
        } else {
            MovementsUiState.Content(movements.map { it.toUi() })
        }
    }.catch { emit(MovementsUiState.Error) }
