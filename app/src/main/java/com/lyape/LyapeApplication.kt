package com.lyape

import android.app.Application
import com.lyape.data.AppDatabase
import com.lyape.data.AccountsRepository
import com.lyape.data.MovementRepository
import com.lyape.data.MonthlyPlanRepository
import com.lyape.notification.CaptureProviderRegistry
import com.lyape.notification.CaptureProviders
import com.lyape.notification.CaptureHealthStore
import com.lyape.notification.NotificationIngestor
import com.lyape.preferences.AppPreferences
import com.lyape.voice.VoiceSessionCoordinator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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

    val accountsRepository: AccountsRepository by lazy { AccountsRepository(database) }

    open val preferences: AppPreferences by lazy { AppPreferences(this) }

    /** Yape y Plin Interbank; compatibilidad Interbank provisional hasta la prueba física. */
    open val captureProviders: CaptureProviderRegistry = CaptureProviders.production

    /** Sesión de voz del proceso; el servicio foreground la mantiene viva. */
    val voiceSession: VoiceSessionCoordinator by lazy {
        VoiceSessionCoordinator(this, preferences)
    }

    private val _captureListenerConnected = MutableStateFlow(false)
    val captureListenerConnected: StateFlow<Boolean> = _captureListenerConnected.asStateFlow()

    fun setCaptureListenerConnected(connected: Boolean) {
        _captureListenerConnected.value = connected
    }

    /** Incidencias de captura del proceso; no persiste texto ni datos financieros. */
    val captureHealth = CaptureHealthStore()

    val notificationIngestor: NotificationIngestor by lazy {
        NotificationIngestor(
            repository = movementRepository,
            preferences = preferences,
            accountResolver = accountsRepository,
        )
    }
}
