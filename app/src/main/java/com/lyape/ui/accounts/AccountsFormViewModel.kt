package com.lyape.ui.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal data class AccountSaveState(
    val saving: Boolean = false,
    val saved: Boolean = false,
    val error: String? = null,
)

/** El guardado sobrevive a la rotación y solo admite una operación a la vez. */
internal class AccountsFormViewModel : ViewModel() {
    private val mutableState = MutableStateFlow(AccountSaveState())
    val state = mutableState.asStateFlow()

    fun submit(operation: suspend () -> Unit) {
        if (mutableState.value.saving || mutableState.value.saved) return
        mutableState.value = AccountSaveState(saving = true)
        viewModelScope.launch {
            try {
                operation()
                mutableState.value = AccountSaveState(saved = true)
            } catch (cancelled: CancellationException) {
                mutableState.value = AccountSaveState()
                throw cancelled
            } catch (invalid: IllegalArgumentException) {
                mutableState.value = AccountSaveState(error = invalid.message ?: "Revisa los datos e inténtalo de nuevo.")
            } catch (_: Exception) {
                mutableState.value = AccountSaveState(error = "No se pudo guardar. Vuelve a intentarlo.")
            }
        }
    }

    fun consumeSaved() { mutableState.value = AccountSaveState() }

    fun clearError() {
        if (!mutableState.value.saving && !mutableState.value.saved) mutableState.value = AccountSaveState()
    }
}
