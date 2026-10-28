package com.lyape.ui.movementform

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lyape.R
import com.lyape.data.MovementAttemptConflictException
import com.lyape.data.MovementEditResult
import com.lyape.data.MovementFormStore
import com.lyape.domain.MovementClassification
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.UUID

internal data class MovementFormSubmission(
    val saving: Boolean = false,
    val saved: Boolean = false,
    val error: String? = null,
    val errorRes: Int? = null,
)

/**
 * Operación de alta o edición fuera del scope de composición.
 *
 * `attemptId` llega desde `rememberSaveable`: una recreación reutiliza el mismo intento. El store
 * recupera un intento ya guardado con contenido idéntico y devuelve conflicto si los datos
 * cambiaron. La identidad de sesión de la pantalla evita que un resultado antiguo cierre un
 * formulario nuevo.
 *
 * Límites separados: una recreación de actividad conserva este ViewModel y los valores
 * `rememberSaveable`; una muerte de proceso lo recrea desde cero y solo puede recuperar el intento
 * con la identidad y el contenido ya confirmados en almacenamiento. La prueba JVM de propietario
 * nuevo simula esa recuperación, no una muerte real del proceso Android.
 */
internal class MovementFormViewModel(private val store: MovementFormStore) : ViewModel() {

    private val mutableSubmission = MutableStateFlow(MovementFormSubmission())
    val submission = mutableSubmission.asStateFlow()

    fun saveAdd(
        attemptId: UUID,
        amountCents: Long,
        occurredAt: Instant,
        label: String?,
        accountId: String?,
        classification: MovementClassification,
        isAdjustment: Boolean,
    ) {
        if (mutableSubmission.value.saving || mutableSubmission.value.saved) return
        mutableSubmission.value = MovementFormSubmission(saving = true)
        viewModelScope.launch {
            try {
                if (isAdjustment) {
                    store.saveAdjustment(
                        amountCents = amountCents,
                        occurredAt = occurredAt,
                        reason = label.orEmpty(),
                        accountId = accountId,
                        classification = classification,
                        id = attemptId,
                    )
                } else {
                    store.saveManual(
                        amountCents = amountCents,
                        occurredAt = occurredAt,
                        label = label,
                        accountId = accountId,
                        classification = classification,
                        id = attemptId,
                    )
                }
                mutableSubmission.value = MovementFormSubmission(saved = true)
            } catch (cancelled: CancellationException) {
                // La pantalla puede seguir viva si solo se canceló esta operación: nunca deja el
                // formulario bloqueado en "guardando".
                mutableSubmission.value = MovementFormSubmission()
                throw cancelled
            } catch (_: MovementAttemptConflictException) {
                mutableSubmission.value = MovementFormSubmission(errorRes = R.string.movement_attempt_conflict)
            } catch (error: IllegalArgumentException) {
                mutableSubmission.value = MovementFormSubmission(
                    error = error.message ?: null,
                    errorRes = if (error.message == null) R.string.movement_invalid_data else null,
                )
            } catch (_: Exception) {
                mutableSubmission.value = MovementFormSubmission(errorRes = R.string.movement_save_error)
            }
        }
    }

    fun saveEdit(
        movementId: UUID,
        amountCents: Long,
        occurredAt: Instant,
        senderName: String?,
        description: String?,
        adjustmentReason: String?,
        accountId: String?,
        expectedVersion: Long,
        editAttemptId: UUID,
    ) {
        if (mutableSubmission.value.saving || mutableSubmission.value.saved) return
        mutableSubmission.value = MovementFormSubmission(saving = true)
        viewModelScope.launch {
            try {
                when (
                    store.saveEdit(
                        id = movementId,
                        amountCents = amountCents,
                        occurredAt = occurredAt,
                        senderName = senderName,
                        description = description,
                        adjustmentReason = adjustmentReason,
                        accountId = accountId,
                        updateAccount = true,
                        expectedVersion = expectedVersion,
                        editAttemptId = editAttemptId,
                    )
                ) {
                    MovementEditResult.Applied,
                    MovementEditResult.AlreadyApplied,
                    -> mutableSubmission.value = MovementFormSubmission(saved = true)

                    MovementEditResult.NotFound ->
                        mutableSubmission.value = MovementFormSubmission(errorRes = R.string.movement_edit_missing)

                    MovementEditResult.Conflict ->
                        mutableSubmission.value = MovementFormSubmission(errorRes = R.string.movement_edit_conflict)
                }
            } catch (cancelled: CancellationException) {
                mutableSubmission.value = MovementFormSubmission()
                throw cancelled
            } catch (error: IllegalArgumentException) {
                mutableSubmission.value = MovementFormSubmission(
                    error = error.message ?: null,
                    errorRes = if (error.message == null) R.string.movement_invalid_data else null,
                )
            } catch (_: Exception) {
                mutableSubmission.value = MovementFormSubmission(errorRes = R.string.movement_edit_save_error)
            }
        }
    }

    fun clearError() {
        if (mutableSubmission.value.error != null || mutableSubmission.value.errorRes != null) {
            mutableSubmission.value = MovementFormSubmission()
        }
    }

    fun acknowledgeSuccess() {
        if (mutableSubmission.value.saved) mutableSubmission.value = MovementFormSubmission()
    }
}
