package com.lyape.voice

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.lyape.LyapeApplication
import com.lyape.MainActivity
import com.lyape.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Servicio foreground de la sesión de avisos.
 *
 * Se inicia solo desde una acción visible del usuario. La confirmación se publica únicamente
 * después de que `startForeground` tuvo éxito y lleva el token de la sesión que lo pidió.
 * No arranca en BOOT_COMPLETED ni se reinicia automáticamente.
 */
class VoiceSessionService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var observer: Job? = null
    private var activeToken = 0L

    override fun onCreate() {
        super.onCreate()
        VoiceNotificationChannel.ensure(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val app = application as? LyapeApplication ?: run {
            stopSelf()
            return START_NOT_STICKY
        }
        val token = intent?.getLongExtra(VoiceServiceIntents.EXTRA_TOKEN, 0L) ?: 0L

        if (intent?.action == VoiceServiceIntents.ACTION_STOP) {
            // El coordinador acepta el token y persiste en su scope estable. El servicio no
            // espera ni ejecuta paradas tardías después de que su propio scope se cancele.
            app.voiceSession.requestDisableFromService(token)
            return START_NOT_STICKY
        }

        if (token == 0L) {
            VoiceServiceBridge.publish(VoiceServiceEvent.Failed(token))
            stopSelf()
            return START_NOT_STICKY
        }
        if (token != app.voiceSession.state.value.token) {
            // Un intent viejo no debe detener la instancia que ya atiende otra sesión.
            if (activeToken == 0L) {
                VoiceServiceBridge.publish(VoiceServiceEvent.Failed(token))
                stopSelf()
            }
            return START_NOT_STICKY
        }

        activeToken = token
        if (!AndroidVoiceNotificationGate(this).canShow()) {
            VoiceServiceBridge.publish(VoiceServiceEvent.NotificationBlocked(token))
            stopSelf()
            return START_NOT_STICKY
        }

        val notification = buildNotification(app.voiceSession.state.value)
        try {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                foregroundType(),
            )
        } catch (_: Exception) {
            VoiceServiceBridge.publish(
                VoiceServiceEvent.Failed(token, VoiceError.SERVICE_UNAVAILABLE),
            )
            stopForegroundNow()
            stopSelf()
            return START_NOT_STICKY
        }

        VoiceServiceBridge.publish(VoiceServiceEvent.Confirmed(token))
        if (observer == null) {
            observer = scope.launch {
                app.voiceSession.state.collectLatest { state ->
                    if (state.token != activeToken) return@collectLatest
                    getSystemService(android.app.NotificationManager::class.java)
                        ?.notify(NOTIFICATION_ID, buildNotification(state))
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // La sesión debe sobrevivir a retirar LYape de recientes.
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        observer?.cancel()
        observer = null
        if (activeToken != 0L) {
            VoiceServiceBridge.publish(VoiceServiceEvent.Stopped(activeToken))
        }
        stopForegroundNow()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(state: VoiceSessionState): Notification {
        val active = state.availability() == VoiceSessionAvailability.ACTIVE
        val openApp = PendingIntent.getActivity(
            this,
            REQUEST_OPEN,
            Intent(this, MainActivity::class.java)
                .putExtra(VoiceServiceIntents.EXTRA_OPEN_SETTINGS, true)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this,
            REQUEST_STOP,
            Intent(this, VoiceSessionService::class.java)
                .setAction(VoiceServiceIntents.ACTION_STOP)
                .putExtra(VoiceServiceIntents.EXTRA_TOKEN, activeToken),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, VoiceNotificationChannel.ID)
            .setSmallIcon(R.drawable.ic_stat_voice)
            .setContentTitle(
                getString(
                    if (active) R.string.voice_notification_active else R.string.voice_notification_preparing,
                ),
            )
            .setContentText(getString(R.string.voice_notification_text))
            .setContentIntent(openApp)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(
                R.drawable.ic_stat_voice,
                getString(R.string.voice_notification_disable),
                stop,
            )
            .build()
    }

    private fun foregroundType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        } else {
            0
        }

    private fun stopForegroundNow() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
    }

    companion object {
        private const val NOTIFICATION_ID = 41
        private const val REQUEST_OPEN = 41
        private const val REQUEST_STOP = 42
    }
}
