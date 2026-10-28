package com.lyape.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class VoiceAnnouncementQueueTest {

    private val now = 1_000_000_000_000L
    private val id1 = "mov-1"
    private val id2 = "mov-2"
    private val id3 = "mov-3"
    private val text1 = "Prueba. Plin: un sol"
    private val text2 = "Prueba. Yape: diez soles"

    @Before
    fun setUp() {
        VoiceAnnouncementQueue.resetTimeSource()
    }

    @Test
    fun `cola vacía no devuelve anuncios`() {
        val queue = VoiceAnnouncementQueue()
        assertNull(queue.takeNext(now))
        assertEquals(0, queue.countAt(now))
    }

    @Test
    fun `anuncio en cola vacío se devuelve al sacar`() {
        val queue = VoiceAnnouncementQueue()
        val item = VoiceAnnouncement(id1, text1, now)
        assertEquals(VoiceAnnouncementQueue.Outcome.ENQUEUED, queue.enqueue(item, now))
        val taken = queue.takeNext(now)
        assertEquals(id1, taken?.id)
        assertEquals(text1, taken?.text)
        assertEquals(0, queue.countAt(now))
    }

    @Test
    fun `cola serial respeta orden FIFO`() {
        val queue = VoiceAnnouncementQueue()
        queue.enqueue(VoiceAnnouncement(id1, text1, now), now)
        queue.enqueue(VoiceAnnouncement(id2, text2, now), now)
        queue.enqueue(VoiceAnnouncement(id3, text1, now), now)
        assertEquals(id1, queue.takeNext(now)?.id)
        assertEquals(id2, queue.takeNext(now)?.id)
        assertEquals(id3, queue.takeNext(now)?.id)
    }

    @Test
    fun `máximo de anuncios pendientes se respeta`() {
        val max = 3
        val queue = VoiceAnnouncementQueue(maxPending = max)
        (1..max).forEach { i ->
            val item = VoiceAnnouncement("m$i", "text", now)
            assertEquals(VoiceAnnouncementQueue.Outcome.ENQUEUED, queue.enqueue(item, now))
        }
        val extra = VoiceAnnouncement("m-extra", "text", now)
        assertEquals(VoiceAnnouncementQueue.Outcome.FULL, queue.enqueue(extra, now))
        assertEquals(max, queue.countAt(now))
    }

    @Test
    fun `el exceso de cola no se reproduce ni guarda el anuncio`() {
        val queue = VoiceAnnouncementQueue(maxPending = 2)
        queue.enqueue(VoiceAnnouncement("a", "Yape: un sol", now), now)
        queue.enqueue(VoiceAnnouncement("b", "Yape: dos soles", now), now)
        assertEquals(VoiceAnnouncementQueue.Outcome.FULL, queue.enqueue(VoiceAnnouncement("c", "Yape: tres soles", now), now))
        assertEquals(2, queue.countAt(now))
        assertEquals("Yape: un sol", queue.takeNext(now)?.text)
        assertEquals("Yape: dos soles", queue.takeNext(now)?.text)
        assertNull(queue.takeNext(now))
    }

    @Test
    fun `anuncio caducado no entra en la cola`() {
        val queue = VoiceAnnouncementQueue(ttlMillis = 30_000)
        val oldItem = VoiceAnnouncement("old", "Yape: un sol", now - 30_001)
        assertEquals(VoiceAnnouncementQueue.Outcome.EXPIRED, queue.enqueue(oldItem, now))
        assertEquals(0, queue.countAt(now))
    }

    @Test
    fun `anuncio que caduca justo en el límite se descarta`() {
        val queue = VoiceAnnouncementQueue(ttlMillis = 30_000)
        val item = VoiceAnnouncement("edge", "Yape: un sol", now - 30_000)
        assertEquals(VoiceAnnouncementQueue.Outcome.EXPIRED, queue.enqueue(item, now))
    }

    @Test
    fun `anuncio recién creado no caduca en el mismo instante`() {
        val queue = VoiceAnnouncementQueue(ttlMillis = 30_000)
        val item = VoiceAnnouncement("fresh", "Yape: un sol", now)
        assertEquals(VoiceAnnouncementQueue.Outcome.ENQUEUED, queue.enqueue(item, now))
        assertEquals(1, queue.countAt(now))
    }

    @Test
    fun `al avanzar el tiempo los caducados se limpian y no se devuelven`() {
        val queue = VoiceAnnouncementQueue(ttlMillis = 30_000)
        queue.enqueue(VoiceAnnouncement("old", "Yape: un sol", now - 31_000), now)
        queue.enqueue(VoiceAnnouncement("fresh", "Plin: un sol", now), now)
        assertEquals(1, queue.countAt(now))
        assertEquals("Plin: un sol", queue.takeNext(now)?.text)
        assertNull(queue.takeNext(now))
    }

    @Test
    fun `el duplicado de identificador se descarta mientras está pendiente`() {
        val queue = VoiceAnnouncementQueue()
        val item = VoiceAnnouncement(id1, text1, now)
        assertEquals(VoiceAnnouncementQueue.Outcome.ENQUEUED, queue.enqueue(item, now))
        assertEquals(VoiceAnnouncementQueue.Outcome.DUPLICATE, queue.enqueue(item, now))
        assertEquals(1, queue.countAt(now))
        assertEquals(id1, queue.takeNext(now)?.id)
    }

    @Test
    fun `mismo identificador se descarta aunque se haya sacado una vez`() {
        val queue = VoiceAnnouncementQueue()
        val item = VoiceAnnouncement(id1, text1, now)
        queue.enqueue(item, now)
        queue.takeNext(now)
        assertEquals(VoiceAnnouncementQueue.Outcome.DUPLICATE, queue.enqueue(item, now))
    }

    @Test
    fun `remove retira solo la prueba y conserva el cobro`() {
        val queue = VoiceAnnouncementQueue()
        queue.enqueue(VoiceAnnouncement("real-1", "Yape: un sol", now), now)
        queue.enqueue(VoiceAnnouncement("preview-1", "Prueba. Plin: un sol", now), now)

        assertEquals(1, queue.remove { it.id.startsWith("preview-") })
        assertEquals("real-1", queue.takeNext(now)?.id)
        assertNull(queue.takeNext(now))
    }

    @Test
    fun `clear vacía pendientes e identificadores`() {
        val queue = VoiceAnnouncementQueue()
        queue.enqueue(VoiceAnnouncement(id1, text1, now), now)
        queue.enqueue(VoiceAnnouncement(id2, text2, now), now)
        assertEquals(2, queue.countAt(now))
        queue.clear()
        assertEquals(0, queue.countAt(now))
        assertEquals(VoiceAnnouncementQueue.Outcome.ENQUEUED, queue.enqueue(VoiceAnnouncement(id1, text1, now), now))
    }

    @Test
    fun `el identificador no contiene el texto ni el contenido de la notificación`() {
        val queue = VoiceAnnouncementQueue()
        val item = VoiceAnnouncement(id1, text1, now)
        queue.enqueue(item, now)
        assertTrue(item.id.isNotBlank())
        assertEquals(id1, item.id)
        // El texto está en la propiedad text, no en el id.
        assertNotEquals(id1, item.text)
    }
}
