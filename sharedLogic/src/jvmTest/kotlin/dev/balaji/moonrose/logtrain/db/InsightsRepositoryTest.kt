package dev.balaji.moonrose.logtrain.db

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import dev.balaji.moonrose.logtrain.calendar.WeekStart
import dev.balaji.moonrose.logtrain.insights.BodyWeightTrend
import dev.balaji.moonrose.logtrain.insights.InsightCard
import dev.balaji.moonrose.logtrain.insights.SparkPoint
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class InsightsRepositoryTest {
    private val dbFile = File.createTempFile("logtrain-insights-test", ".db")
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

    // A Sunday, so weeks align with the default week start.
    private val sunday = 3L

    private fun set(day: Long, exerciseId: Long = 1, name: String = "Bench", weight: Double = 100.0, reps: Int = 5) =
        SetPoint(epochDay = day, sessionKey = day, exerciseId = exerciseId, exerciseName = name, weight = weight, reps = reps)

    @Test
    fun `weekly volume buckets sets and includes empty weeks`() {
        val sets = listOf(
            set(sunday, weight = 100.0, reps = 10),      // week 0: 1000
            set(sunday + 1, weight = 50.0, reps = 10),   // week 0: 500
            set(sunday + 14, weight = 80.0, reps = 5),   // week 2: 400
        )
        val weeks = weeklyVolumeFrom(sets, weeks = 3, todayEpochDay = sunday + 15, weekStart = WeekStart.SUNDAY)
        assertEquals(3, weeks.size)
        assertEquals(listOf(1500.0, 0.0, 400.0), weeks.map { it.totalVolume })
        assertEquals(listOf(2, 0, 1), weeks.map { it.setCount })
        // Two sets on different days = two sessions in week 0.
        assertEquals(2, weeks.first().sessionCount)
    }

    @Test
    fun `personal records pick the best estimated 1RM per exercise`() {
        val sets = listOf(
            set(sunday, weight = 100.0, reps = 1),                          // 1RM 100
            set(sunday + 1, weight = 90.0, reps = 8),                       // 1RM 114 <- PR
            set(sunday + 2, exerciseId = 2, name = "Squat", weight = 140.0, reps = 3), // 1RM 154
            set(sunday + 3, weight = 0.0, reps = 10),                       // bodyweight rows are skipped
        )
        val prs = personalRecordsFrom(sets, limit = 5)
        assertEquals(listOf("Squat", "Bench"), prs.map { it.exerciseName })
        assertEquals(90.0, prs[1].weight)
        assertEquals(8, prs[1].reps)

        val benchOnly = personalRecordsFrom(sets, limit = 5, exerciseName = "bench")
        assertEquals(1, benchOnly.size)
    }

    @Test
    fun `progression reports weekly bests and detects a plateau`() {
        val sets = listOf(
            set(sunday, weight = 100.0, reps = 5),
            set(sunday + 7, weight = 100.0, reps = 5),
            set(sunday + 14, weight = 100.0, reps = 5),
        )
        val p = progressionFrom(sets, "Bench", todayEpochDay = sunday + 15, weekStart = WeekStart.SUNDAY)!!
        assertEquals(3, p.weeklyBest1Rm.size)
        assertTrue(p.plateau)

        assertNull(progressionFrom(sets, "Deadlift", sunday + 15, WeekStart.SUNDAY))
    }

    @Test
    fun `frequency counts sessions in rolling windows`() {
        val today = sunday + 100
        val sessionDays = listOf(today, today - 3, today - 10, today - 40)
        val freq = frequencyFrom(sessionDays, today, WeekStart.SUNDAY)
        assertEquals(2, freq.sessionsLast7Days)
        assertEquals(3, freq.sessionsLast30Days)
        assertEquals(4.0 / 12, freq.avgPerWeek12w)
    }

    @Test
    fun `cards are empty with no history and body weight card appears when provided`() {
        assertTrue(
            buildCards(emptyList(), emptyList(), bodyWeight = null, sunday, WeekStart.SUNDAY).isEmpty(),
        )

        val trend = BodyWeightTrend(
            points = listOf(SparkPoint(sunday, 80.0), SparkPoint(sunday + 30, 78.5)),
            latest = 78.5,
            latestEpochDay = sunday + 30,
            change30Days = -1.5,
            slopePerWeek = -0.35,
        )
        val cards = buildCards(emptyList(), emptyList(), trend, sunday + 30, WeekStart.SUNDAY)
        assertEquals(listOf("body_weight"), cards.map { it.id })
        assertEquals(InsightCard.HEADLINE_LATEST_BODY_WEIGHT, cards.single().headlineKey)
        assertEquals(listOf("78.5"), cards.single().headlineArgs)
        assertEquals(InsightCard.DETAIL_CHANGE_30_DAYS, cards.single().detailKey)
        assertEquals(listOf("-1.5"), cards.single().detailArgs)
        assertEquals("down", cards.single().trend)
    }

    @Test
    fun `cards cover volume frequency and top progressions with history`() {
        val sets = listOf(
            set(sunday, weight = 100.0, reps = 5),
            set(sunday + 7, weight = 102.5, reps = 5),
        )
        val cards = buildCards(sets, listOf(sunday, sunday + 7), null, sunday + 8, WeekStart.SUNDAY)
        assertEquals(listOf("weekly_volume", "frequency", "progression:1"), cards.map { it.id })
        assertEquals(InsightCard.HEADLINE_STREAK_WEEKS, cards[1].headlineKey)
        assertEquals(listOf("2"), cards[1].headlineArgs)
        assertEquals(InsightCard.HEADLINE_EST_1RM, cards[2].headlineKey)
        assertEquals("Bench", cards[2].exerciseName)
    }

    @Test
    fun `completed sets query joins names and excludes warmups incomplete sets and open sessions`() = runBlocking {
        val now = nowMillis()
        db.profileDao().upsert(
            ProfileEntity(name = "Test", age = null, gender = null, weight = null, height = null, createdAt = now, updatedAt = now),
        )
        val bench = db.exerciseDao().findByName("Barbell Bench Press")!!.id

        val completed = db.workoutSessionDao().insert(
            WorkoutSessionEntity(profileId = PROFILE_ID, routineId = null, performedAt = 1000, completedAt = 2000, createdAt = now, updatedAt = now),
        )
        val open = db.workoutSessionDao().insert(
            WorkoutSessionEntity(profileId = PROFILE_ID, routineId = null, performedAt = 3000, completedAt = null, createdAt = now, updatedAt = now),
        )

        fun detail(sessionId: Long, position: Int, weight: Double, warmup: Boolean = false, done: Boolean = true) =
            WorkoutSessionDetailEntity(
                workoutSessionId = sessionId, workSetDetailId = null, exerciseId = bench, position = position,
                targetWeight = null, targetReps = null, targetDuration = null,
                actualWeight = weight, actualReps = 5, actualDuration = null,
                warmup = warmup, completed = done, createdAt = now, updatedAt = now,
            )

        db.workoutSessionDetailDao().insert(detail(completed, 1, 100.0))
        db.workoutSessionDetailDao().insert(detail(completed, 2, 60.0, warmup = true))
        db.workoutSessionDetailDao().insert(detail(completed, 3, 100.0, done = false))
        db.workoutSessionDetailDao().insert(detail(open, 1, 100.0))

        val rows = db.workoutSessionDetailDao().getCompletedSets(PROFILE_ID, startMillis = 0)
        assertEquals(1, rows.size)
        assertEquals("Barbell Bench Press", rows.single().exerciseName)
        assertEquals(100.0, rows.single().actualWeight)
        assertEquals(1000, rows.single().performedAt)

        // The observe variant sees the same rows.
        assertEquals(rows, db.workoutSessionDetailDao().observeCompletedSets(PROFILE_ID, 0).first())

        // startMillis filters out older sessions.
        assertTrue(db.workoutSessionDetailDao().getCompletedSets(PROFILE_ID, startMillis = 1500).isEmpty())
    }
}
