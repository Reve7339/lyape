package com.lyape.ui.settings

import com.lyape.domain.CaptureConfiguration
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

/**
 * Estado de la configuración de captura en Ajustes.
 *
 * [Error] es distinto de [Ready] con `link == null`: un fallo de lectura nunca debe mostrarse
 * como «sin cuenta asociada».
 */
internal sealed interface CaptureConfigUiState {
    data object Loading : CaptureConfigUiState
    data object Error : CaptureConfigUiState
    data class Ready(val configuration: CaptureConfiguration) : CaptureConfigUiState
}

internal fun Flow<CaptureConfiguration>.toCaptureConfigUiState(): Flow<CaptureConfigUiState> =
    map<CaptureConfiguration, CaptureConfigUiState> { CaptureConfigUiState.Ready(it) }
        .onStart { emit(CaptureConfigUiState.Loading) }
        .catch { emit(CaptureConfigUiState.Error) }
