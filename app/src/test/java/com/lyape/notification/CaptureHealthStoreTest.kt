package com.lyape.notification

import com.lyape.domain.CaptureProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CyclicBarrier

class CaptureHealthStoreTest {

    @Test
    fun `un fallo se publica como incidencia del proveedor`() {
        val health = CaptureHealthStore()
        health.record(IngestResult.Failed(IngestFailure.PERSISTENCE), CaptureProvider.YAPE)

        assertEquals(
            CaptureIssue.SAVE_FAILED,
            health.state.value.issues[CaptureProvider.YAPE]?.issue,
        )
    }

    @Test
    fun `un fallo de lectura no se presenta como guardado incierto`() {
        val health = CaptureHealthStore()
        health.record(IngestResult.Failed(IngestFailure.ACCOUNT_READ), CaptureProvider.INTERBANK_PLIN)

        assertEquals(
            CaptureIssue.CAPTURE_FAILED,
            health.state.value.issues[CaptureProvider.INTERBANK_PLIN]?.issue,
        )
    }

    @Test
    fun `una cuenta no configurada tiene su propio estado`() {
        val health = CaptureHealthStore()
        health.record(
            IngestResult.Unconfigured(CaptureProvider.INTERBANK_PLIN),
            CaptureProvider.INTERBANK_PLIN,
        )

        assertEquals(
            CaptureIssue.ACCOUNT_UNAVAILABLE,
            health.state.value.issues[CaptureProvider.INTERBANK_PLIN]?.issue,
        )
    }

    @Test
    fun `un exito posterior no borra una incidencia pendiente`() {
        val health = CaptureHealthStore()
        health.record(IngestResult.Failed(IngestFailure.PERSISTENCE), CaptureProvider.YAPE)
        health.record(
            IngestResult.Stored(
                com.lyape.domain.CapturedPayment(
                    id = java.util.UUID.randomUUID(),
                    medium = com.lyape.domain.PaymentMedium.YAPE,
                    amountCents = 1_000L,
                    occurredAt = java.time.Instant.now(),
                ),
            ),
            CaptureProvider.YAPE,
        )

        assertTrue(health.state.value.hasIssues)
    }

    @Test
    fun `duplicado pausado y desconocido no son fallos tecnicos`() {
        val health = CaptureHealthStore()
        health.record(IngestResult.Duplicate, CaptureProvider.YAPE)
        health.record(IngestResult.Paused, CaptureProvider.YAPE)
        health.record(IngestResult.Unrecognized, CaptureProvider.YAPE)

        assertFalse(health.state.value.hasIssues)
    }

    @Test
    fun `la accion explicita retira solo el aviso elegido`() {
        val health = CaptureHealthStore()
        health.record(IngestResult.Failed(IngestFailure.PERSISTENCE), CaptureProvider.YAPE)
        health.record(
            IngestResult.Unconfigured(CaptureProvider.INTERBANK_PLIN),
            CaptureProvider.INTERBANK_PLIN,
        )
        val yapeVersion = health.state.value.issues.getValue(CaptureProvider.YAPE).version

        health.acknowledge(CaptureProvider.YAPE, yapeVersion)

        assertEquals(
            setOf(CaptureProvider.INTERBANK_PLIN),
            health.state.value.issues.keys,
        )
    }

    @Test
    fun `dos fallos concurrentes conservan ambos avisos`() = runBlocking {
        val health = CaptureHealthStore()
        val barrier = CyclicBarrier(2)

        listOf(CaptureProvider.YAPE, CaptureProvider.INTERBANK_PLIN).map { provider ->
            async(Dispatchers.Default) {
                barrier.await()
                health.record(IngestResult.Failed(IngestFailure.PERSISTENCE), provider)
            }
        }.awaitAll()

        assertEquals(
            setOf(CaptureProvider.YAPE, CaptureProvider.INTERBANK_PLIN),
            health.state.value.issues.keys,
        )
    }

    @Test
    fun `reconocer mientras llega un fallo de otro proveedor conserva el nuevo`() = runBlocking {
        val health = CaptureHealthStore()
        health.record(IngestResult.Failed(IngestFailure.PERSISTENCE), CaptureProvider.YAPE)
        val yapeVersion = health.state.value.issues.getValue(CaptureProvider.YAPE).version
        val barrier = CyclicBarrier(2)

        val acknowledge = async(Dispatchers.Default) {
            barrier.await()
            health.acknowledge(CaptureProvider.YAPE, yapeVersion)
        }
        val plinFailure = async(Dispatchers.Default) {
            barrier.await()
            health.record(IngestResult.Failed(IngestFailure.PERSISTENCE), CaptureProvider.INTERBANK_PLIN)
        }
        listOf(acknowledge, plinFailure).awaitAll()

        assertNull(health.state.value.issues[CaptureProvider.YAPE])
        assertEquals(
            CaptureIssue.SAVE_FAILED,
            health.state.value.issues[CaptureProvider.INTERBANK_PLIN]?.issue,
        )
    }

    @Test
    fun `reconocer una version antigua no elimina una nueva del mismo proveedor`() {
        val health = CaptureHealthStore()
        health.record(IngestResult.Failed(IngestFailure.PERSISTENCE), CaptureProvider.YAPE)
        val oldVersion = health.state.value.issues.getValue(CaptureProvider.YAPE).version
        health.record(IngestResult.Failed(IngestFailure.ACCOUNT_READ), CaptureProvider.YAPE)
        val newNotice = health.state.value.issues.getValue(CaptureProvider.YAPE)

        health.acknowledge(CaptureProvider.YAPE, oldVersion)

        assertEquals(newNotice, health.state.value.issues[CaptureProvider.YAPE])
    }

    @Test
    fun `las repeticiones no acumulan una lista ilimitada`() {
        val health = CaptureHealthStore()
        repeat(500) {
            health.record(IngestResult.Failed(IngestFailure.PERSISTENCE), CaptureProvider.YAPE)
        }

        assertEquals(1, health.state.value.issues.size)
        assertTrue(health.state.value.issues.getValue(CaptureProvider.YAPE).version >= 500L)
    }
}
