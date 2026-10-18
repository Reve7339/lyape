package com.lyape.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.lyape.LyapeApplication
import com.lyape.data.MovementRepository
import com.lyape.data.MonthlyPlanRepository
import com.lyape.preferences.AppPreferences

/**
 * Acceso a las dependencias de [LyapeApplication] desde Compose, sin librería de inyección.
 */
@Composable
private fun rememberLyapeApplication(): LyapeApplication =
    LocalContext.current.applicationContext as LyapeApplication

@Composable
fun rememberMovementRepository(): MovementRepository {
    val application = rememberLyapeApplication()
    return remember(application) { application.movementRepository }
}

@Composable
fun rememberMonthlyPlanRepository(): MonthlyPlanRepository {
    val application = rememberLyapeApplication()
    return remember(application) { application.monthlyPlanRepository }
}

@Composable
fun rememberAppPreferences(): AppPreferences {
    val application = rememberLyapeApplication()
    return remember(application) { application.preferences }
}
