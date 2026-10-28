package com.lyape.voice

/**
 * Anuncio preparado para pronunciar: un identificador y el texto formateado.
 *
 * Solo el texto ya construido viaja a la cola y al motor de voz; aquí no se guarda ni
 * procesa ninguna notificación completa, ni nombres, ni códigos.
 *
 * El identificador suele ser el id del movimiento, para evitar reencolados del mismo pago.
 */
data class VoiceAnnouncement(
    val id: String,
    val text: String,
    val createdAtMillis: Long,
    /** Identidad de la prueba sintética; null para pagos reales. */
    val previewGeneration: Long? = null,
) {
    /** Expira [ttlMillis] después de su creación. */
    fun isExpired(nowMillis: Long, ttlMillis: Long = VOICE_TTL_MILLIS): Boolean {
        return createdAtMillis + ttlMillis <= nowMillis
    }

    companion object {
        /** Máximo de anuncios de voz pendientes en memoria. */
        const val VOICE_MAX_PENDING = 10

        /** Anuncios con más de este tiempo desde la captura ya no se anuncian. */
        const val VOICE_TTL_MILLIS: Long = 30_000
    }
}
