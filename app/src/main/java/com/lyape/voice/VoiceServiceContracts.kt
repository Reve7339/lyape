package com.lyape.voice

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.lyape.R
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** Eventos de identidad del servicio, asociados al token de la sesión que los solicitó. */
sealed interface VoiceServiceEvent {
    val token: Long

    data class Confirmed(override val token: Long) : VoiceServiceEvent

    data class Failed(
        override val token: Long,
        val error: VoiceError = VoiceError.SERVICE_UNAVAILABLE,
    ) : VoiceServiceEvent

    data class NotificationBlocked(override val token: Long) : VoiceServiceEvent

    data class Stopped(override val token: Long) : VoiceServiceEvent
}

/** Frontera pequeña para probar el arranque/parada del servicio sin Android real. */
interface VoiceServiceHost {
    val events: Flow<VoiceServiceEvent>

    fun start(token: Long): Boolean

    /** Detiene solo si el host sigue asociado a [token]; un cierre viejo no toca B. */
    fun stop(token: Long)
}

/** Comprueba permiso, notificaciones de la app y bloqueo del canal del aviso persistente. */
fun interface VoiceNotificationGate {
    fun canShow(): Boolean
}

/**
 * Todos los efectos se ejecutan en el mismo ejecutor serial (main en Android).
 * No separa la comprobación del token del efecto ni mantiene monitores durante llamadas Android.
 */
internal class ServiceStopGate {
    private var activeToken = 0L
    fun start(token: Long, effect: () -> Unit) {
        effect()
        activeToken = token
    }
    fun stop(token: Long, effect: () -> Unit) {
        if (activeToken != token) return
        effect()
        activeToken = 0L
    }
}

internal class SerialServiceCommands(
    private val post: (() -> Unit) -> Boolean,
    private val startEffect: (Long) -> Unit,
    private val stopEffect: () -> Unit,
    private val onFailure: (Long) -> Unit,
) {
    private val gate = ServiceStopGate()
    fun start(token: Long): Boolean = post {
        try { gate.start(token) { startEffect(token) } }
        catch (_: Exception) { onFailure(token) }
    }
    fun stop(token: Long) {
        if (!post {
            try { gate.stop(token, stopEffect) }
            catch (_: Exception) { onFailure(token) }
        }) onFailure(token)
    }
}

class AndroidVoiceServiceHost(context: Context) : VoiceServiceHost {
    private val appContext = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private val commands = SerialServiceCommands(
        // Siempre encolar, incluso desde main: no adelantar comandos ni permitir reentrada.
        post = { command -> handler.post { command() } },
        startEffect = { token ->
            ContextCompat.startForegroundService(
                appContext,
                Intent(appContext, VoiceSessionService::class.java)
                    .setAction(VoiceServiceIntents.ACTION_START)
                    .putExtra(VoiceServiceIntents.EXTRA_TOKEN, token),
            )
        },
        stopEffect = { appContext.stopService(Intent(appContext, VoiceSessionService::class.java)); Unit },
        onFailure = { VoiceServiceBridge.publish(VoiceServiceEvent.Failed(it)) },
    )

    override val events: Flow<VoiceServiceEvent> = VoiceServiceBridge.events

    override fun start(token: Long): Boolean = commands.start(token)

    override fun stop(token: Long) {
        commands.stop(token)
    }
}

class AndroidVoiceNotificationGate(context: Context) : VoiceNotificationGate {
    private val appContext = context.applicationContext

    override fun canShow(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        if (!NotificationManagerCompat.from(appContext).areNotificationsEnabled()) {
            return false
        }
        VoiceNotificationChannel.ensure(appContext)
        return !VoiceNotificationChannel.isBlocked(appContext)
    }
}

object VoiceServiceBridge {
    private val mutableEvents = MutableSharedFlow<VoiceServiceEvent>(
        replay = 1,
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events: Flow<VoiceServiceEvent> = mutableEvents.asSharedFlow()

    fun publish(event: VoiceServiceEvent) {
        mutableEvents.tryEmit(event)
    }
}

object VoiceServiceIntents {
    const val ACTION_START = "com.lyape.voice.action.START"
    const val ACTION_STOP = "com.lyape.voice.action.STOP"
    const val EXTRA_TOKEN = "com.lyape.voice.extra.TOKEN"
    const val EXTRA_OPEN_SETTINGS = "com.lyape.voice.extra.OPEN_SETTINGS"
}

object VoiceNotificationChannel {
    const val ID = "voice_session"

    fun ensure(context: Context) = ensure(context, ID)

    /** ID inyectable para probar canales aislados sin alterar el canal de la sesión. */
    internal fun ensure(context: Context, channelId: String) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        // Se vuelve a crear también si ya existe para actualizar nombre y descripción al migrar
        // textos; Android conserva la importancia y las preferencias del usuario.
        val channel = NotificationChannel(
            channelId,
            context.getString(R.string.voice_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = context.getString(R.string.voice_channel_description)
            setSound(null, null)
            enableVibration(false)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
        }
        manager.createNotificationChannel(channel)
    }

    fun isBlocked(context: Context): Boolean = isBlocked(context, ID)

    internal fun isBlocked(context: Context, channelId: String): Boolean {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return true
        val channel = manager.getNotificationChannel(channelId) ?: return true
        return channel.importance == NotificationManager.IMPORTANCE_NONE
    }
}
