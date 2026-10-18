package com.lyape.domain

/** Agregados de SQL: no requieren materializar el historial. Incluyen anulados en total. */
data class MovementCounts(
    val total: Int = 0,
    val business: Int = 0,
    val personal: Int = 0,
    val excluded: Int = 0,
)
