package com.lyape.ui.movements

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.map
import com.lyape.LyapeApplication
import com.lyape.domain.MovementClassification
import com.lyape.domain.MovementCounts
import com.lyape.ui.model.MovementUi
import com.lyape.ui.model.toUi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn

sealed interface MovementCountsState {
    data object Loading : MovementCountsState
    data object Error : MovementCountsState
    data class Content(val counts: MovementCounts) : MovementCountsState
}

internal data class HistoryQuery(
    val classification: MovementClassification? = null,
    val text: String = "",
)

class MovementsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = (application as LyapeApplication).movementRepository
    private val query = MutableStateFlow(HistoryQuery())

    // Un solo caché acotado. Cambiar consulta cancela la generación anterior.
    @OptIn(ExperimentalCoroutinesApi::class)
    val movements: Flow<PagingData<MovementUi>> = query
        .flatMapLatest { repository.history(it.classification, it.text) }
        .map { page -> page.map { it.toUi() } }
        .flowOn(Dispatchers.Default)
        .cachedIn(viewModelScope)

    fun updateQuery(classification: MovementClassification?, text: String) {
        query.value = HistoryQuery(classification, normalizeForSearch(text))
    }

    // Frío para que Reintentar renueve también una consulta de conteos fallida.
    val counts: Flow<MovementCountsState>
        get() = repository.observeCounts().toMovementCountsState()
}

internal fun Flow<MovementCounts>.toMovementCountsState(): Flow<MovementCountsState> =
    map<MovementCounts, MovementCountsState> { MovementCountsState.Content(it) }
        .catch { emit(MovementCountsState.Error) }
