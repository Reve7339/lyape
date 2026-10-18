package com.lyape.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lyape.domain.MovementClassification
import com.lyape.domain.MovementSource
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.util.UUID

/** Verifica Room en ejecución: escritura, conversores y consulta temporal semiabierta. */
@RunWith(AndroidJUnit4::class)
class MovementDaoIntegrationTest {

    private lateinit var database: AppDatabase
    private lateinit var dao: MovementDao

    @Before
    fun createDatabase() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.movementDao()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun insertQueryByRangeAndConvertersRoundTrip() = runBlocking {
        val start = Instant.parse("2026-10-14T05:00:00Z")
        val endExclusive = Instant.parse("2026-10-15T05:00:00Z")

        val before = entity(
            id = "00000000-0000-0000-0000-000000000001",
            occurredAt = start.minusMillis(1),
        )
        val atStart = entity(
            id = "00000000-0000-0000-0000-000000000002",
            occurredAt = start,
            source = MovementSource.NOTIFICATION,
            classification = MovementClassification.BUSINESS,
        )
        val inside = entity(
            id = "00000000-0000-0000-0000-000000000003",
            occurredAt = Instant.parse("2026-10-14T18:30:00.123Z"),
            amountCents = -805,
            source = MovementSource.ADJUSTMENT,
            classification = MovementClassification.PERSONAL,
            senderName = null,
            adjustmentReason = "Fixture de devolución",
        )
        val atEnd = entity(
            id = "00000000-0000-0000-0000-000000000004",
            occurredAt = endExclusive,
            classification = MovementClassification.EXCLUDED,
        )

        dao.insertAll(listOf(before, atStart, inside, atEnd))

        val result = dao.getInRange(start.toEpochMilli(), endExclusive.toEpochMilli())

        // La consulta es [inicio, fin) y conserva el orden descendente definido por el DAO.
        assertEquals(listOf(inside, atStart), result)

        // La búsqueda por UUID obliga a recorrer también su conversor de Room.
        assertEquals(inside, dao.getById(inside.id))
    }

    private fun entity(
        id: String,
        occurredAt: Instant,
        amountCents: Long = 1_250,
        source: MovementSource = MovementSource.MANUAL,
        classification: MovementClassification = MovementClassification.BUSINESS,
        senderName: String? = "Persona de prueba",
        adjustmentReason: String? = null,
    ): MovementEntity = MovementEntity(
        id = UUID.fromString(id),
        amountCents = amountCents,
        currency = "PEN",
        occurredAt = occurredAt,
        capturedAt = occurredAt.plusMillis(250),
        source = source,
        classification = classification,
        reviewed = false,
        senderName = senderName,
        description = "Fixture instrumentado",
        adjustmentReason = adjustmentReason,
        notificationKey = "test:$id",
        deduplicationHash = "hash:$id",
        isEdited = source == MovementSource.ADJUSTMENT,
        createdAt = occurredAt.plusMillis(500),
        updatedAt = occurredAt.plusMillis(750),
        previousClassification = null,
    )
}
