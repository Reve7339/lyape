package com.lyape.voice

import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/**
 * Prueba instrumentada preparada para CI. No se ejecutó en esta revisión.
 *
 * Comprueba que un canal bloqueado por el usuario no se recrea para saltarse el bloqueo.
 */
@RunWith(AndroidJUnit4::class)
class VoiceNotificationChannelTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val manager = context.getSystemService(NotificationManager::class.java)

    private val channelId = "voice-test-${UUID.randomUUID()}"

    @After
    fun deleteChannel() {
        manager.deleteNotificationChannel(channelId)
    }

    @Test
    fun crearElCanalNoEvadeUnBloqueoExistente() {
        // No borrar/recrear para simular un bloqueo: Android restaura los ajustes del ID.
        // Se prepara un canal NUEVO bloqueado y se ejecuta la misma lógica de producción.
        manager.createNotificationChannel(
            NotificationChannel(channelId, "Avisos de voz", NotificationManager.IMPORTANCE_NONE),
        )
        assertTrue(VoiceNotificationChannel.isBlocked(context, channelId))
        repeat(2) { VoiceNotificationChannel.ensure(context, channelId) }
        val blocked = manager.getNotificationChannel(channelId)
        assertNotNull(blocked)
        assertEquals(NotificationManager.IMPORTANCE_NONE, blocked.importance)
        assertTrue(VoiceNotificationChannel.isBlocked(context, channelId))
    }

    @Test
    fun canalNuevoEsSilenciosoYNoEstaBloqueado() {
        assertTrue(VoiceNotificationChannel.isBlocked(context, channelId))
        VoiceNotificationChannel.ensure(context, channelId)
        val created = manager.getNotificationChannel(channelId)
        assertNotNull(created)
        assertEquals(NotificationManager.IMPORTANCE_LOW, created.importance)
        org.junit.Assert.assertNull(created.sound)
        assertFalse(created.shouldVibrate())
        assertFalse(VoiceNotificationChannel.isBlocked(context, channelId))
    }
}
