package app.bp.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow

/**
 * Readings within the last 7 calendar days (today plus the previous six),
 * ascending by time — exactly the shape the chart wants.
 *
 * Pure function (no Android imports) so the windowing is unit-testable.
 */
fun List<Reading>.last7Days(
    zone: ZoneId = ZoneId.systemDefault(),
    nowMillis: Long = System.currentTimeMillis(),
): List<Reading> {
    val cutoff = LocalDate.ofInstant(Instant.ofEpochMilli(nowMillis), zone)
        .minusDays(6)
        .atStartOfDay(zone)
        .toInstant()
        .toEpochMilli()
    return filter { it.ts >= cutoff }.sortedBy { it.ts }
}

class ReadingRepository(private val dao: ReadingDao) {

    /** All readings, newest first. */
    val all: Flow<List<Reading>> = dao.all()

    suspend fun save(sys: Int, dia: Int, hr: Int): Reading {
        val reading = Reading(ts = System.currentTimeMillis(), sys = sys, dia = dia, hr = hr)
        dao.insert(reading)
        return reading
    }

    suspend fun delete(reading: Reading) = dao.deleteById(reading.id)

    /** Re-insert a deleted reading, keeping its id and timestamp (undo). */
    suspend fun restore(reading: Reading) = dao.insert(reading)
}
