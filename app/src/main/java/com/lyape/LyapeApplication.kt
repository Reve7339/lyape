package com.lyape

import android.app.Application
import com.lyape.data.AppDatabase
import com.lyape.data.MovementRepository
import com.lyape.data.MonthlyPlanRepository
import com.lyape.notification.NotificationIngestor
import com.lyape.preferences.AppPreferences

/**
 * Contenedor mínimo de dependencias.
 *
 * No hay librería de inyección: el listener, los ViewModels y las pantallas obtienen aquí las
 * mismas instancias. Se crean de forma perezosa para no abrir Room ni las preferencias antes de
 * que haga falta.
 */
open class LyapeApplication : Application() {

    open val database: AppDatabase by lazy { AppDatabase.getInstance(this) }

    val movementRepository: MovementRepository by lazy {
        MovementRepository(database.movementDao())
    }

    val monthlyPlanRepository: MonthlyPlanRepository by lazy {
        MonthlyPlanRepository(database.monthlyPlanDao())
    }

    open val preferences: AppPreferences by lazy { AppPreferences(this) }

    val notificationIngestor: NotificationIngestor by lazy {
        NotificationIngestor(
            repository = movementRepository,
            preferences = preferences,
        )
    }
}
