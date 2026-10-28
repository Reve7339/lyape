package com.lyape.data

import com.lyape.domain.CaptureProvider

/**
 * Resuelve la cuenta receptora de un proveedor para una captura nueva.
 *
 * Devuelve `null` si falta la asociación, si la cuenta ya no existe o si dejó de ser del tipo
 * requerido. La captura debe quedar bloqueada; nunca se sustituye por Yape.
 */
fun interface CaptureAccountResolver {
    suspend fun accountIdFor(provider: CaptureProvider): String?
}
