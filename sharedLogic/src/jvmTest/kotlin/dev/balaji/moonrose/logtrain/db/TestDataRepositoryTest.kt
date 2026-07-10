package dev.balaji.moonrose.logtrain.db

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import dev.balaji.moonrose.logtrain.model.WeightUnit
import java.io.File
import kotlin.math.abs
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate

class TestDataRepositoryTest {
    private val dbFile = File.createTempFile("logtrain-testdata-test", ".db")
    private val db = Room.databaseBuilder<AppDatabase>(name = dbFile.absolutePath)
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .addCallback(ExerciseSeedCallback)
        .build()

    @AfterTest
    fun tearDown() {
        db.close()
        dbFile.delete()
    }

    private val monday = LocalDate(2026, 1, 5).toEpochDays()

    private fun plan(days: Int, seed: Int, unit: WeightUnit = WeightUnit.METRIC, bodyWeight: Double? = null) =
        planTestData(days, monday, unit, bodyWeight, Random(seed))

    @Test
    fun `sessions land on training days within the span, about three a week`() {
        val plan = plan(days = 28, seed = 1)
        val trainingDays = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)
        assertTrue(plan.sessions.all { it.epochDay in (monday - 27)..monday })
        assertTrue(plan.sessions.all { LocalDate.fromEpochDays(it.epochDay).dayOfWeek in trainingDays })
        // 12 possible training days in 4 weeks, minus the ~10% skip rate.
        assertTrue(plan.sessions.size in 8..12, "got ${plan.sessions.size} sessions")
        assertTrue(plan.sessions.all { it.sets.isNotEmpty() })
    }

    @Test
    fun `weights ramp up over a long span`() {
        val plan = plan(days = 90, seed = 2)
        val squats = plan.sessions
            .sortedBy { it.epochDay }
            .flatMap { s -> s.sets.filter { it.exerciseName == "Barbell Back Squat" } }
        assertTrue(squats.size >= 6)
        assertTrue(squats.first().actualWeight!! < squats.last().actualWeight!!)
    }

    @Test
    fun `bodyweight exercises have no weight and reps never exceed target`() {
        val sets = plan(days = 28, seed = 3).sessions.flatMap { it.sets }
        val hanging = sets.filter { it.exerciseName == "Hanging Knee Raise" }
        assertTrue(hanging.isNotEmpty())
        assertTrue(hanging.all { it.targetWeight == null && it.actualWeight == null })
        assertTrue(sets.all { it.actualReps in 1..it.targetReps })
    }

    @Test
    fun `imperial weights are whole five-pound multiples`() {
        val weights = plan(days = 28, seed = 4, unit = WeightUnit.IMPERIAL)
            .sessions.flatMap { it.sets }.mapNotNull { it.actualWeight }
        assertTrue(weights.isNotEmpty())
        assertTrue(weights.all { it % 5.0 == 0.0 }, "not all multiples of 5: $weights")
    }

    @Test
    fun `weigh-ins cover the span every few days near the starting weight`() {
        val weighIns = plan(days = 30, seed = 5, bodyWeight = 80.0).weighIns
        assertEquals(monday - 29, weighIns.first().epochDay)
        weighIns.zipWithNext().forEach { (a, b) ->
            assertTrue(b.epochDay - a.epochDay in 2..3)
        }
        assertTrue(weighIns.all { abs(it.weight - 80.0) < 5.0 })
    }

    @Test
    fun `insert flags rows and delete removes only test data`() = runBlocking {
        val now = nowMillis()
        db.profileDao().upsert(
            ProfileEntity(name = "Test", age = null, gender = null, weight = null, height = null, createdAt = now, updatedAt = now),
        )
        val realSessionId = db.workoutSessionDao().insert(
            WorkoutSessionEntity(
                profileId = PROFILE_ID, routineId = null,
                performedAt = now, completedAt = now, createdAt = now, updatedAt = now,
            ),
        )
        val realMetricId = db.bodyMetricDao().insert(
            BodyMetricEntity(profileId = PROFILE_ID, weight = 81.0, loggedAt = now, createdAt = now, updatedAt = now),
        )

        val plan = plan(days = 21, seed = 6)
        db.insertTestData(plan)

        assertEquals(plan.sessions.size, db.workoutSessionDao().observeTestDataCount().first())
        val testSession = db.workoutSessionDao().observeAll().first().first { it.testData }
        assertTrue(db.workoutSessionDetailDao().getForSession(testSession.id).isNotEmpty())

        db.workoutSessionDao().deleteTestData()
        db.bodyMetricDao().deleteTestData()

        assertEquals(0, db.workoutSessionDao().observeTestDataCount().first())
        // The details cascade away with their session.
        assertTrue(db.workoutSessionDetailDao().getForSession(testSession.id).isEmpty())
        // Real rows survive.
        assertNotNull(db.workoutSessionDao().findById(realSessionId))
        assertEquals(realMetricId, db.bodyMetricDao().latest(PROFILE_ID)?.id)
    }
}
