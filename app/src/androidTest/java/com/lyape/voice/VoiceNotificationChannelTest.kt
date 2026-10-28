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
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Prueba instrumentada preparada para CI. No se ejecutó en esta revisión.
 *
 * Comprueba que un canal bloqueado por el usuario no se recrea para saltarse el bloqueo.
 */
@RunWith(AndroidJUnit4::class)
class VoiceNotificationChannelTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val manager = context.getSystemService(NotificationManager::class.java)

    @Before
    fun cleanChannel() {
        manager.deleteNotificationChannel(VoiceNotificationChannel.ID)
    }

    @After
    fun deleteChannel() {
        manager.deleteNotificationChannel(VoiceNotificationChannel.ID)
    }

    @Test
    fun crearElCanalNoEvadeUnBloqueoExistente() {
        VoiceNotificationChannel.ensure(context)
        val created = manager.getNotificationChannel(VoiceNotificationChannel.ID)
        assertNotNull(created)
        assertFalse(VoiceNotificationChannel.isBlocked(context))

        manager.deleteNotificationChannel(VoiceNotificationChannel.ID)
        manager.createNotificationChannel(
            NotificationChannel(
                VoiceNotificationChannel.ID,
                "Avisos de voz",
                NotificationManager.IMPORTANCE_NONE,
            ),
        )
        assertTrue(VoiceNotificationChannel.isBlocked(context))

        VoiceNotificationChannel.ensure(context)
        val blocked = manager.getNotificationChannel(VoiceNotificationChannel.ID)
        assertNotNull(blocked)
        assertEquals(NotificationManager.IMPORTANCE_NONE, blocked.importance)
        assertTrue(VoiceNotificationChannel.isBlocked(context))
    }
}
