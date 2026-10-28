package com.lyape.ui.movementform

import com.lyape.R
import com.lyape.data.MovementAttemptConflictException
import com.lyape.data.MovementEditResult
import com.lyape.data.MovementFormStore
import com.lyape.domain.MovementClassification
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.time.Instant
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class MovementFormViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val occurredAt: Instant = Instant.parse("2026-10-15T20:00:00Z")

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `un doble toque solo inicia un guardado`() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        val store = FakeStore().apply {
            onManual = { _, _, _, _, _, _ ->
                gate.await()
                UUID.randomUUID()
            }
        }
        val viewModel = MovementFormViewModel(store)

        viewModel.saveAdd(UUID.randomUUID(), 1_000, occurredAt, "Bodega", null, MovementClassification.BUSINESS, false)
        viewModel.saveAdd(UUID.randomUUID(), 1_000, occurredAt, "Bodega", null, MovementClassification.BUSINESS, false)
        runCurrent()

        assertTrue(viewModel.submission.value.saving)
        assertEquals(1, store.manualCalls)

        gate.complete(Unit)
        advanceUntilIdle()

        assertTrue(viewModel.submission.value.saved)
        assertEquals(1, store.manualCalls)
    }

    @Test
    fun `un fallo recuperable libera el guardado y permite reintentar`() = runTest(dispatcher) {
        var fail = true
        val store = FakeStore().apply {
            onManual = { _, _, _, _, _, _ ->
                if (fail) {
                    fail = false
                    throw IOException("sin red local")
                }
                UUID.randomUUID()
            }
        }
        val viewModel = MovementFormViewModel(store)
        val attempt = UUID.randomUUID()

        viewModel.saveAdd(attempt, 1_000, occurredAt, "Bodega", null, MovementClassification.BUSINESS, false)
        advanceUntilIdle()

        assertFalse(viewModel.submission.value.saving)
        assertFalse(viewModel.submission.value.saved)
        assertEquals(R.string.movement_save_error, viewModel.submission.value.errorRes)

        viewModel.saveAdd(attempt, 1_000, occurredAt, "Bodega", null, MovementClassification.BUSINESS, false)
        advanceUntilIdle()

        assertTrue(viewModel.submission.value.saved)
        assertEquals(2, store.manualCalls)
    }

    @Test
    fun `un commit confirmado sin entrega se recupera al reintentar el mismo intento`() = runTest(dispatcher) {
        var committed = false
        val storedId = UUID.randomUUID()
        val store = FakeStore().apply {
            onManual = { _, _, _, _, _, _ ->
                if (!committed) {
                    committed = true
                    throw IOException("resultado no entregado")
                }
                storedId
            }
        }
        val viewModel = MovementFormViewModel(store)
        val attempt = UUID.randomUUID()

        viewModel.saveAdd(attempt, 1_000, occurredAt, "Bodega", null, MovementClassification.BUSINESS, false)
        advanceUntilIdle()
        assertFalse(viewModel.submission.value.saved)

        viewModel.saveAdd(attempt, 1_000, occurredAt, "Bodega", null, MovementClassification.BUSINESS, false)
        advanceUntilIdle()

        assertTrue(committed)
        assertTrue(viewModel.submission.value.saved)
        assertEquals(2, store.manualCalls)
    }

    @Test
    fun `un propietario nuevo recupera un intento ya confirmado en almacenamiento`() = runTest(dispatcher) {
        var committedAttempt: UUID? = null
        val storedId = UUID.randomUUID()
        val durableStore = FakeStore().apply {
            onManual = { _, _, _, _, _, id ->
                if (committedAttempt == id) storedId
                else {
                    committedAttempt = id
                    throw IOException("proceso anterior sin resultado")
                }
            }
        }
        val attempt = UUID.randomUUID()
        val previousOwner = MovementFormViewModel(durableStore)
        previousOwner.saveAdd(attempt, 1_000, occurredAt, "Bodega", null, MovementClassification.BUSINESS, false)
        advanceUntilIdle()

        val newOwner = MovementFormViewModel(durableStore)
        newOwner.saveAdd(attempt, 1_000, occurredAt, "Bodega", null, MovementClassification.BUSINESS, false)
        advanceUntilIdle()

        assertTrue(newOwner.submission.value.saved)
        assertEquals(2, durableStore.manualCalls)
    }

    @Test
    fun `un conflicto de intento no se presenta como exito`() = runTest(dispatcher) {
        val store = FakeStore().apply {
            onManual = { _, _, _, _, _, _ ->
                throw MovementAttemptConflictException("conflicto local")
            }
        }
        val viewModel = MovementFormViewModel(store)

        viewModel.saveAdd(UUID.randomUUID(), 1_000, occurredAt, "Bodega", null, MovementClassification.BUSINESS, false)
        advanceUntilIdle()

        assertFalse(viewModel.submission.value.saving)
        assertFalse(viewModel.submission.value.saved)
        assertEquals(R.string.movement_attempt_conflict, viewModel.submission.value.errorRes)
        assertNull(viewModel.submission.value.error)
    }

    @Test
    fun `una edicion ya aplicada se reconoce sin volver a escribir`() = runTest(dispatcher) {
        val store = FakeStore().apply { onEdit = { _, _, _, _, _, _, _, _, _, _ -> MovementEditResult.AlreadyApplied } }
        val viewModel = MovementFormViewModel(store)

        viewModel.saveEdit(
            movementId = UUID.randomUUID(),
            amountCents = 2_000,
            occurredAt = occurredAt,
            senderName = "Bodega",
            description = null,
            adjustmentReason = null,
            accountId = null,
            expectedVersion = 2,
            editAttemptId = UUID.randomUUID(),
        )
        advanceUntilIdle()

        assertTrue(viewModel.submission.value.saved)
        assertEquals(1, store.editCalls)
    }

    @Test
    fun `una edicion con revision posterior muestra conflicto`() = runTest(dispatcher) {
        val store = FakeStore().apply { onEdit = { _, _, _, _, _, _, _, _, _, _ -> MovementEditResult.Conflict } }
        val viewModel = MovementFormViewModel(store)

        viewModel.saveEdit(
            movementId = UUID.randomUUID(),
            amountCents = 2_000,
            occurredAt = occurredAt,
            senderName = "Bodega",
            description = null,
            adjustmentReason = null,
            accountId = null,
            expectedVersion = 2,
            editAttemptId = UUID.randomUUID(),
        )
        advanceUntilIdle()

        assertFalse(viewModel.submission.value.saving)
        assertFalse(viewModel.submission.value.saved)
        assertEquals(R.string.movement_edit_conflict, viewModel.submission.value.errorRes)
    }

    @Test
    fun `una cancelacion no deja el formulario bloqueado`() = runTest(dispatcher) {
        val store = FakeStore().apply {
            onManual = { _, _, _, _, _, _ -> throw CancellationException("cancelado") }
        }
        val viewModel = MovementFormViewModel(store)

        viewModel.saveAdd(UUID.randomUUID(), 1_000, occurredAt, "Bodega", null, MovementClassification.BUSINESS, false)
        advanceUntilIdle()

        assertFalse(viewModel.submission.value.saving)
        assertFalse(viewModel.submission.value.saved)
    }

    @Test
    fun `un resultado de una instancia anterior no modifica una instancia nueva`() = runTest(dispatcher) {
        val store = FakeStore()
        val old = MovementFormViewModel(store)
        val current = MovementFormViewModel(store)

        old.saveAdd(UUID.randomUUID(), 1_000, occurredAt, "Bodega", null, MovementClassification.BUSINESS, false)
        advanceUntilIdle()

        assertTrue(old.submission.value.saved)
        assertFalse(current.submission.value.saved)
        assertNull(current.submission.value.errorRes)
    }

    private class FakeStore : MovementFormStore {
        var manualCalls = 0
        var adjustmentCalls = 0
        var editCalls = 0
        var onManual: suspend (Long, Instant, String?, String?, MovementClassification, UUID) -> UUID =
            { _, _, _, _, _, _ -> UUID.randomUUID() }
        var onAdjustment: suspend (Long, Instant, String, String?, MovementClassification, UUID) -> UUID =
            { _, _, _, _, _, _ -> UUID.randomUUID() }
        var onEdit: suspend (
            UUID,
            Long,
            Instant,
            String?,
            String?,
            String?,
            String?,
            Boolean,
            Long,
            UUID,
        ) -> MovementEditResult = { _, _, _, _, _, _, _, _, _, _ -> MovementEditResult.Applied }

        override suspend fun saveManual(
            amountCents: Long,
            occurredAt: Instant,
            label: String?,
            accountId: String?,
            classification: MovementClassification,
            id: UUID,
        ): UUID {
            manualCalls++
            return onManual(amountCents, occurredAt, label, accountId, classification, id)
        }

        override suspend fun saveAdjustment(
            amountCents: Long,
            occurredAt: Instant,
            reason: String,
            accountId: String?,
            classification: MovementClassification,
            id: UUID,
        ): UUID {
            adjustmentCalls++
            return onAdjustment(amountCents, occurredAt, reason, accountId, classification, id)
        }

        override suspend fun saveEdit(
            id: UUID,
            amountCents: Long,
            occurredAt: Instant,
            senderName: String?,
            description: String?,
            adjustmentReason: String?,
            accountId: String?,
            updateAccount: Boolean,
            expectedVersion: Long,
            editAttemptId: UUID,
        ): MovementEditResult {
            editCalls++
            return onEdit(
                id,
                amountCents,
                occurredAt,
                senderName,
                description,
                adjustmentReason,
                accountId,
                updateAccount,
                expectedVersion,
                editAttemptId,
            )
        }
    }
}
