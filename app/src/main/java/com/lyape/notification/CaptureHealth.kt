package com.lyape.notification

import com.lyape.domain.CaptureProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicLong

/** Incidencia técnica de captura; no contiene texto, remitente, monto ni códigos. */
enum class CaptureIssue {
    SAVE_FAILED,
    CAPTURE_FAILED,
    ACCOUNT_UNAVAILABLE,
}

/** Incidencia y versión que el usuario ve; la versión evita borrar una incidencia posterior. */
data class CaptureIssueNotice(
    val issue: CaptureIssue,
    val version: Long,
)

/**
 * Estado acotado de captura para Ajustes.
 *
 * Vive en el proceso y no se guarda en disco: así un fallo al escribir el propio estado no puede
 * iniciar otra cadena de fallos. Un reinicio borra el aviso; el límite se declara en la interfaz.
 * Un éxito posterior no borra una incidencia pendiente: solo la acción explícita del usuario.
 */
data class CaptureHealthState(
    val issues: Map<CaptureProvider, CaptureIssueNotice> = emptyMap(),
) {
    val hasIssues: Boolean get() = issues.isNotEmpty()
}

class CaptureHealthStore {

    private val mutableState = MutableStateFlow(CaptureHealthState())
    val state: StateFlow<CaptureHealthState> = mutableState.asStateFlow()

    /**
     * Versión global monótona. Si un aviso se reconoce y llega otro del mismo proveedor, el nuevo
     * recibe una versión mayor aunque el mapa se haya vaciado entre ambos.
     */
    private val nextVersion = AtomicLong(0L)

    fun record(result: IngestResult, provider: CaptureProvider) {
        val issue = when (result) {
            is IngestResult.Failed -> when (result.failure) {
                IngestFailure.PERSISTENCE -> CaptureIssue.SAVE_FAILED
                else -> CaptureIssue.CAPTURE_FAILED
            }

            is IngestResult.Unconfigured -> CaptureIssue.ACCOUNT_UNAVAILABLE
            is IngestResult.Stored,
            IngestResult.Duplicate,
            IngestResult.Paused,
            IngestResult.Unrecognized,
            -> return
        }
        val effectiveProvider = (result as? IngestResult.Unconfigured)?.provider ?: provider
        val notice = CaptureIssueNotice(issue = issue, version = nextVersion.incrementAndGet())
        mutableState.update { current ->
            // Un mapa por proveedor: las repeticiones no hacen crecer una lista.
            current.copy(issues = current.issues + (effectiveProvider to notice))
        }
    }

    /** Retira solo la versión concreta que el usuario estaba viendo. */
    fun acknowledge(provider: CaptureProvider, version: Long) {
        mutableState.update { current ->
            if (current.issues[provider]?.version == version) {
                current.copy(issues = current.issues - provider)
            } else {
                current
            }
        }
    }
}
