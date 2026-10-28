package com.lyape.ui.movements

import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.map
import com.lyape.domain.CaptureAccount
import com.lyape.domain.Movement
import com.lyape.ui.model.MovementUi
import com.lyape.ui.model.toUi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** combine reutiliza la generación al cambiar una cuenta: cachear ANTES de combinar. */
internal fun movementPagesWithAccounts(
    pages: Flow<PagingData<Movement>>,
    accounts: Flow<List<CaptureAccount>>,
    scope: CoroutineScope,
    use24Hour: Boolean = false,
): Flow<PagingData<MovementUi>> = pages.cachedIn(scope)
    .combine(accounts) { page, catalog ->
        val names = catalog.associate { it.id to it.name }
        page.map { movement ->
            movement.toUi(use24Hour = use24Hour)
                .copy(accountLabel = movement.accountId?.let { names[it] ?: "Cuenta no disponible" } ?: "Sin cuenta")
        }
    }.cachedIn(scope)
