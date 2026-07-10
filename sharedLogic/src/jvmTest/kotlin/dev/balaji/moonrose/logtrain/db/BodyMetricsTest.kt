package dev.balaji.moonrose.logtrain.db

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class BodyMetricsTest {
    private val dbFile = File.createTempFile("logtrain-body-test", ".db")
    private val db = Room.databaseBuilder<AppDatabase>(name = dbFile.absolutePath)
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .addCallback(ExerciseSeedCallback)
        .addMigrations(MIGRATION_9_10)
        .build()

    @AfterTest
    fun tearDown() {
        db.close()
        dbFile.delete()
    }

    private suspend fun seedProfile() {
        val now = nowMillis()
        db.profileDao().upsert(
            ProfileEntity(name = "Test", age = null, gender = null, weight = 80.0, height = null, createdAt = now, updatedAt = now),
        )
    }

    @Test
    fun `entries round-trip newest first and range queries filter`() = runBlocking {
        seedProfile()
        val now = nowMillis()
        db.bodyMetricDao().insert(BodyMetricEntity(profileId = PROFILE_ID, weight = 80.0, loggedAt = 1000, createdAt = now, updatedAt = now))
        db.bodyMetricDao().insert(BodyMetricEntity(profileId = PROFILE_ID, weight = 79.0, loggedAt = 3000, createdAt = now, updatedAt = now))

        val all = db.bodyMetricDao().observeAll(PROFILE_ID).first()
        assertEquals(listOf(79.0, 80.0), all.map { it.weight })
        assertEquals(79.0, assertNotNull(db.bodyMetricDao().latest(PROFILE_ID)).weight)
        assertEquals(listOf(79.0), db.bodyMetricDao().getInRange(PROFILE_ID, startMillis = 2000).map { it.weight })

        db.bodyMetricDao().delete(all.last().id)
        assertEquals(1, db.bodyMetricDao().observeAll(PROFILE_ID).first().size)
    }

    @Test
    fun `updateWeight syncs the profile scalar and is a no-op without a profile`() = runBlocking {
        db.profileDao().updateWeight(75.0, nowMillis())
        assertNull(db.profileDao().observe().first())

        seedProfile()
        db.profileDao().updateWeight(75.0, nowMillis())
        assertEquals(75.0, assertNotNull(db.profileDao().observe().first()).weight)
    }

    @Test
    fun `trend reports latest change and slope`() {
        val today = 1000L
        fun entry(day: Long, weight: Double) =
            BodyWeightEntry(id = day, loggedAt = day * 86_400_000, epochDay = day, weight = weight)

        assertNull(trendFrom(emptyList(), today))

        val single = trendFrom(listOf(entry(today, 80.0)), today)!!
        assertEquals(80.0, single.latest)
        assertEquals(0.0, single.change30Days)

        val trend = trendFrom(
            listOf(entry(today - 30, 80.0), entry(today - 15, 79.2), entry(today, 78.5)),
            today,
        )!!
        assertEquals(78.5, trend.latest)
        assertEquals(-1.5, trend.change30Days)
        assertTrue(trend.slopePerWeek < 0)
        assertEquals(3, trend.points.size)
    }
}
