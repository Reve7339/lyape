package com.lyape.ui.movements

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import com.lyape.LyapeApplication
import com.lyape.domain.MovementClassification
import com.lyape.domain.MovementCounts
import com.lyape.ui.model.MovementUi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import java.util.UUID

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
    internal val actions = MovementActions(viewModelScope) { id, action ->
        val uuid = UUID.fromString(id)
        when (action) {
            MovementAction.PERSONAL -> repository.classify(uuid, MovementClassification.PERSONAL)
            MovementAction.BUSINESS -> repository.classify(uuid, MovementClassification.BUSINESS)
            MovementAction.EXCLUDE -> repository.exclude(uuid)
            MovementAction.RESTORE -> repository.restore(uuid)
        }
    }
    private val accounts = (application as LyapeApplication).accountsRepository.observeSelectableAccounts()
        .catch { emit(emptyList()) }

    // Un solo caché acotado. Cambiar consulta cancela la generación anterior.
    @OptIn(ExperimentalCoroutinesApi::class)
    val movements: Flow<PagingData<MovementUi>> = movementPagesWithAccounts(
        query.flatMapLatest { repository.history(it.classification, it.text) },
        accounts,
        viewModelScope,
        use24Hour = android.text.format.DateFormat.is24HourFormat(application),
    )

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
