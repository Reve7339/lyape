package com.lyape.data

import androidx.paging.PagingSource
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lyape.domain.DateRange
import com.lyape.domain.Movement
import com.lyape.domain.MovementClassification
import com.lyape.domain.MovementCounts
import com.lyape.domain.MovementSource
import com.lyape.domain.normalizeMovementSearch
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.util.UUID

/** Room real y páginas pequeñas sobre una tabla mayor; nunca usa lyape.db del teléfono. */
@RunWith(AndroidJUnit4::class)
class HistoryPagingIntegrationTest {
    private lateinit var db: AppDatabase
    private val dao get() = db.movementDao()
    private val now = Instant.parse("2026-10-17T12:00:00Z")

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java,
        ).build()
    }

    @After
    fun tearDown() = db.close()

    private fun movement(i: Int, name: String = "Pago $i") = Movement(
        id = UUID(0L, i.toLong()), amountCents = 100,
        occurredAt = now, capturedAt = now, createdAt = now, updatedAt = now,
        source = MovementSource.MANUAL, classification = MovementClassification.BUSINESS,
        reviewed = false, senderName = name,
    )

    private suspend fun page(
        source: PagingSource<Int, MovementEntity>, offset: Int? = null,
    ): PagingSource.LoadResult.Page<Int, MovementEntity> {
        val params = if (offset == null) PagingSource.LoadParams.Refresh<Int>(null, 50, false)
        else PagingSource.LoadParams.Append(offset, 50, false)
        val result = source.load(params)
        assertTrue("Room debe entregar una página, no un error: $result", result is PagingSource.LoadResult.Page)
        return result as PagingSource.LoadResult.Page<Int, MovementEntity>
    }

    @Test
    fun history01PagesAreBoundedStableAndComplete() = runBlocking {
        val records = (1..1203).map { movement(it) }
        dao.insertAll(records.map(Movement::toEntity))
        val source = dao.pageHistory(null, "")
        val ids = mutableListOf<UUID>()
        var key: Int? = null
        do {
            val result = page(source, key)
            assertTrue(result.data.size <= 50)
            ids += result.data.map { it.id }
            key = result.nextKey
        } while (key != null)
        assertEquals(records.map { it.id }.sortedByDescending(UUID::toString), ids)
        assertEquals(1203, ids.distinct().size)
    }

    @Test
    fun history02SearchAndFilterReachRecordsBeyondFirstPage() = runBlocking {
        dao.insertAll((1..200).map { movement(it).toEntity() })
        val target = movement(0, "María Ñúñez").copy(classification = MovementClassification.PERSONAL)
        dao.insert(target.toEntity())
        assertFalse(page(dao.pageHistory(null, "")).data.any { it.id == target.id })
        val result = page(dao.pageHistory(MovementClassification.PERSONAL, normalizeMovementSearch("MARÍA")))
        assertEquals(listOf(target.id), result.data.map { it.id })
        assertTrue(page(dao.pageHistory(MovementClassification.BUSINESS, "maria")).data.isEmpty())
    }

    @Test
    fun history03SearchIsLiteralAndIncludesDescriptionsAndReasons() = runBlocking {
        dao.insertAll(listOf(
            movement(1).copy(description = "Oferta 10%_literal").toEntity(),
            movement(2).copy(source = MovementSource.ADJUSTMENT, senderName = null,
                adjustmentReason = "Devolución O'Connor").toEntity(),
            movement(3).toEntity(),
        ))
        for (query in listOf("%", "_", "10%_", "LÍTÉRAL")) {
            assertEquals(listOf(UUID(0, 1)), page(dao.pageHistory(null, normalizeMovementSearch(query))).data.map { it.id })
        }
        for (query in listOf("devolucion", "O'CONNOR", "ajuste")) {
            assertEquals(listOf(UUID(0, 2)), page(dao.pageHistory(null, normalizeMovementSearch(query))).data.map { it.id })
        }
    }

    @Test
    fun history04CountsAndTotalsCoverEntirePeriodNotJustPage() = runBlocking {
        val repository = MovementRepository(dao)
        repository.addAll((1..201).map { movement(it) })
        repository.add(movement(202).copy(classification = MovementClassification.PERSONAL))
        repository.add(movement(203).copy(classification = MovementClassification.EXCLUDED))
        assertEquals(50, page(dao.pageHistory(null, "")).data.size)
        assertEquals(MovementCounts(total = 203, business = 201, personal = 1, excluded = 1), dao.observeCounts().first())
        assertEquals(20100L, repository.totalsInRange(DateRange(now.minusSeconds(1), now.plusSeconds(1))).businessCents)
    }

    @Test
    fun history05EditsInvalidatePagesAndRebuildSearch() = runBlocking {
        val original = movement(1, "Nombre anterior")
        val repository = MovementRepository(dao)
        repository.add(original)
        val source = dao.pageHistory(null, "")
        page(source)
        val invalidated = CompletableDeferred<Unit>()
        source.registerInvalidatedCallback { invalidated.complete(Unit) }
        repository.update(original.copy(senderName = "Óscar", description = "Otra descripción"))
        withTimeout(5000) { invalidated.await() }
        assertTrue(page(dao.pageHistory(null, "anterior")).data.isEmpty())
        assertEquals(listOf(original.id), page(dao.pageHistory(null, "oscar")).data.map { it.id })
        assertEquals(listOf(original.id), page(dao.pageHistory(null, "descripcion")).data.map { it.id })
    }

    @Test
    fun history06ClassificationChangesInvalidateFilteredPages() = runBlocking {
        val original = movement(1)
        val repository = MovementRepository(dao)
        repository.add(original)
        val source = dao.pageHistory(MovementClassification.BUSINESS, "")
        page(source)
        val invalidated = CompletableDeferred<Unit>()
        source.registerInvalidatedCallback { invalidated.complete(Unit) }
        repository.exclude(original.id)
        withTimeout(5000) { invalidated.await() }
        assertTrue(page(dao.pageHistory(MovementClassification.BUSINESS, "")).data.isEmpty())
        assertEquals(1, dao.observeCounts().first().excluded)
    }
}
