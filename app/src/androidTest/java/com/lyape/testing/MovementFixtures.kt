package com.lyape.testing

import com.lyape.data.MovementRepository
import com.lyape.domain.DateRange
import java.time.Instant

/** Snapshot solo de fixtures pequeños; la app no tiene API para materializar todo el historial. */
suspend fun MovementRepository.testMovements() = movementsInRange(
    DateRange(Instant.ofEpochMilli(Long.MIN_VALUE), Instant.ofEpochMilli(Long.MAX_VALUE)),
)
