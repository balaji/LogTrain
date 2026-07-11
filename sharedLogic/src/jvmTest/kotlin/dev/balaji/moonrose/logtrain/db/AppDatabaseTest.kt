package dev.balaji.moonrose.logtrain.db

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import dev.balaji.moonrose.logtrain.calendar.WeekStart
import dev.balaji.moonrose.logtrain.insights.OneRmFormula
import dev.balaji.moonrose.logtrain.model.Appearance
import dev.balaji.moonrose.logtrain.model.ProgressionCardConfig
import dev.balaji.moonrose.logtrain.model.SampleProgram
import dev.balaji.moonrose.logtrain.model.SamplePrograms
import dev.balaji.moonrose.logtrain.model.SampleRoutine
import dev.balaji.moonrose.logtrain.model.SampleSet
import dev.balaji.moonrose.logtrain.model.SampleWorkSet
import dev.balaji.moonrose.logtrain.model.UserSettings
import dev.balaji.moonrose.logtrain.model.WeightUnit
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class AppDatabaseTest {
    private val dbFile = File.createTempFile("logtrain-test", ".db")
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

    @Test
    fun `profile upsert and observe`() = runBlocking {
        assertNull(db.profileDao().observe().first())

        val now = nowMillis()
        db.profileDao().upsert(
            ProfileEntity(name = "Balaji", age = 35, gender = "Male", weight = 80.0, height = 175.0, createdAt = now, updatedAt = now),
        )
        val profile = assertNotNull(db.profileDao().observe().first())
        assertEquals("Balaji", profile.name)
        assertEquals(PROFILE_ID, profile.id)
    }

    @Test
    fun `deleting a program cascades to its routines`() = runBlocking {
        val now = nowMillis()
        db.profileDao().upsert(
            ProfileEntity(name = "Test", age = null, gender = null, weight = null, height = null, createdAt = now, updatedAt = now),
        )
        val programId = db.programDao().insert(
            ProgramEntity(profileId = PROFILE_ID, name = "PPL", createdAt = now, updatedAt = now),
        )
        db.routineDao().insert(
            RoutineEntity(programId = programId, name = "Push", position = 1, createdAt = now, updatedAt = now),
        )
        db.routineDao().insert(
            RoutineEntity(programId = programId, name = "Pull", position = 2, createdAt = now, updatedAt = now),
        )
        assertEquals(2, db.routineDao().observeAll().first().size)
        assertEquals(3, db.routineDao().nextPosition(programId))

        db.programDao().delete(programId)
        assertTrue(db.programDao().observeAll().first().isEmpty())
        assertTrue(db.routineDao().observeAll().first().isEmpty(), "routines should cascade-delete")
    }

    @Test
    fun `completed sessions join routine names and order newest first`() = runBlocking {
        val now = nowMillis()
        db.profileDao().upsert(
            ProfileEntity(name = "Test", age = null, gender = null, weight = null, height = null, createdAt = now, updatedAt = now),
        )
        val programId = db.programDao().insert(
            ProgramEntity(profileId = PROFILE_ID, name = "PPL", createdAt = now, updatedAt = now),
        )
        val routineId = db.routineDao().insert(
            RoutineEntity(programId = programId, name = "Push", position = 1, createdAt = now, updatedAt = now),
        )
        db.workoutSessionDao().insert(
            WorkoutSessionEntity(profileId = PROFILE_ID, routineId = routineId, performedAt = 1000, completedAt = 2000, createdAt = now, updatedAt = now),
        )
        db.workoutSessionDao().insert(
            WorkoutSessionEntity(profileId = PROFILE_ID, routineId = null, performedAt = 3000, completedAt = 4000, createdAt = now, updatedAt = now),
        )
        // In progress: must not appear.
        db.workoutSessionDao().insert(
            WorkoutSessionEntity(profileId = PROFILE_ID, routineId = routineId, performedAt = 5000, completedAt = null, createdAt = now, updatedAt = now),
        )

        val rows = db.workoutSessionDao().observeCompletedWithRoutineName(PROFILE_ID).first()
        assertEquals(listOf(3000L, 1000L), rows.map { it.performedAt })
        assertEquals(listOf(null, "Push"), rows.map { it.routineName })
    }

    @Test
    fun `exercise catalog is seeded on create`() = runBlocking {
        val exercises = db.exerciseDao().observeAll().first()
        assertTrue(exercises.size >= 15, "expected seeded exercises, got ${exercises.size}")
        assertTrue(exercises.any { it.name == "Barbell Bench Press" })
    }

    @Test
    fun `seeded exercises are builtin and findable by name`() = runBlocking {
        assertTrue(db.exerciseDao().observeAll().first().all { it.builtin })
        assertNotNull(db.exerciseDao().findByName("Barbell Bench Press")).let {
            assertTrue(it.builtin)
        }
        assertNull(db.exerciseDao().findByName("No Such Exercise"))
    }

    @Test
    fun `every sample program exercise resolves against the seed catalog`() = runBlocking {
        val names = SamplePrograms.all
            .flatMap { it.routines }
            .flatMap { it.workSets }
            .map { it.exerciseName }
            .distinct()
        assertTrue(names.isNotEmpty())
        for (name in names) {
            assertNotNull(db.exerciseDao().findByName(name), "sample references unknown exercise \"$name\"")
        }
    }

    @Test
    fun `copying a sample materializes the full program tree`() = runBlocking {
        seedProfile()
        val sample = SamplePrograms.all.first { it.daysPerWeek == 3 }
        // The display names the UI would have shown for the sample's keys.
        val routineNames = sample.routines.mapIndexed { i, _ -> "Routine ${i + 1}" }

        val programId = db.insertSampleProgram(sample, "Full Body", routineNames)

        val program = assertNotNull(db.programDao().findById(programId))
        assertEquals("Full Body", program.name)

        val routines = db.routineDao().observeAll().first().filter { it.programId == programId }
        assertEquals(routineNames, routines.map { it.name })
        assertEquals(List(sample.routines.size) { it + 1 }, routines.map { it.position })

        routines.forEachIndexed { routineIdx, routine ->
            val sampleRoutine = sample.routines[routineIdx]
            val workSets = db.workSetDao().getForRoutine(routine.id)
            assertEquals(sampleRoutine.workSets.size, workSets.size)
            assertEquals(List(workSets.size) { it + 1 }, workSets.map { it.position })

            workSets.forEachIndexed { workSetIdx, workSet ->
                val sampleWorkSet = sampleRoutine.workSets[workSetIdx]
                val exercise = assertNotNull(db.exerciseDao().findById(workSet.exerciseId))
                assertEquals(sampleWorkSet.exerciseName, exercise.name)
                assertTrue(exercise.builtin)

                val details = db.workSetDetailDao().getForWorkSet(workSet.id)
                assertEquals(sampleWorkSet.sets.size, details.size)
                assertEquals(List(details.size) { it + 1 }, details.map { it.position })
                assertEquals(sampleWorkSet.sets.map { it.targetReps }, details.map { it.targetReps })
                assertTrue(details.all { it.targetWeight == null && it.targetDuration == null })
            }
        }
    }

    @Test
    fun `copying the same sample twice yields two independent programs`() = runBlocking {
        seedProfile()
        val sample = SamplePrograms.all.first()
        val routineNames = sample.routines.mapIndexed { i, _ -> "Routine ${i + 1}" }

        val first = db.insertSampleProgram(sample, "Full Body", routineNames)
        val second = db.insertSampleProgram(sample, "Full Body", routineNames)

        assertTrue(first != second)
        val routines = db.routineDao().observeAll().first()
        assertEquals(sample.routines.size, routines.count { it.programId == first })
        assertEquals(sample.routines.size, routines.count { it.programId == second })

        // Deleting one copy leaves the other intact.
        db.programDao().delete(first)
        assertNotNull(db.programDao().findById(second))
        assertEquals(sample.routines.size, db.routineDao().observeAll().first().size)
    }

    @Test
    fun `copying a sample with an unknown exercise writes nothing`() = runBlocking {
        seedProfile()
        val broken = SampleProgram(
            id = "broken",
            daysPerWeek = 1,
            routines = listOf(
                SampleRoutine(
                    "day-one",
                    listOf(
                        SampleWorkSet("Barbell Bench Press", listOf(SampleSet(targetReps = 5))),
                        SampleWorkSet("No Such Exercise", listOf(SampleSet(targetReps = 5))),
                    ),
                ),
            ),
        )

        assertFailsWith<IllegalStateException> {
            db.insertSampleProgram(broken, "Broken Sample", listOf("Day One"))
        }
        assertTrue(db.programDao().observeAll().first().isEmpty(), "no program should be written")
        assertTrue(db.routineDao().observeAll().first().isEmpty(), "no routine should be written")
    }

    @Test
    fun `user settings default when table is empty and round-trip enum keys`() = runBlocking {
        assertEquals(UserSettings(), db.userSettingDao().observeAll().first().toUserSettings())

        val now = nowMillis()
        db.userSettingDao().upsert(UserSettingEntity(SETTING_WEEK_START, WeekStart.MONDAY.value, now, now))
        db.userSettingDao().upsert(UserSettingEntity(SETTING_APPEARANCE, Appearance.DARK.value, now, now))
        db.userSettingDao().upsert(UserSettingEntity(SETTING_WEIGHT_UNIT, WeightUnit.IMPERIAL.value, now, now))
        db.userSettingDao().upsert(UserSettingEntity(SETTING_NOTIFICATIONS_ENABLED, "true", now, now))
        db.userSettingDao().upsert(UserSettingEntity(SETTING_ONE_RM_FORMULA, OneRmFormula.BRZYCKI.value, now, now))

        val settings = db.userSettingDao().observeAll().first().toUserSettings()
        assertEquals(WeekStart.MONDAY, settings.weekStart)
        assertEquals(Appearance.DARK, settings.appearance)
        assertEquals(WeightUnit.IMPERIAL, settings.weightUnit)
        assertEquals(true, settings.notificationsEnabled)
        assertEquals(OneRmFormula.BRZYCKI, settings.oneRmFormula)
    }

    @Test
    fun `user setting upsert replaces the value for an existing key`() = runBlocking {
        val now = nowMillis()
        db.userSettingDao().upsert(UserSettingEntity(SETTING_APPEARANCE, "dark", now, now))
        db.userSettingDao().upsert(UserSettingEntity(SETTING_APPEARANCE, "light", now, now + 1))

        val rows = db.userSettingDao().observeAll().first()
        assertEquals(1, rows.size)
        assertEquals("light", rows.single().value)
    }

    @Test
    fun `unknown setting values fall back to defaults`() = runBlocking {
        val now = nowMillis()
        db.userSettingDao().upsert(UserSettingEntity(SETTING_APPEARANCE, "sepia", now, now))
        db.userSettingDao().upsert(UserSettingEntity(SETTING_ONE_RM_FORMULA, "landers", now, now))
        val settings = db.userSettingDao().observeAll().first().toUserSettings()
        assertEquals(Appearance.SYSTEM, settings.appearance)
        assertEquals(OneRmFormula.EPLEY, settings.oneRmFormula)
    }

    @Test
    fun `progression card configs round-trip and decode leniently`() {
        val configs = listOf(
            ProgressionCardConfig(exercises = listOf("Bench Press", "Overhead Press"), showPctChange = true),
            ProgressionCardConfig(exercises = listOf("Squat")),
        )
        assertEquals(configs, decodeProgressionCards(encodeProgressionCards(configs)))

        assertTrue(decodeProgressionCards(null).isEmpty())
        assertTrue(decodeProgressionCards("").isEmpty())
        // A card with an axis field but no exercises is dropped; garbage is one abs card.
        assertTrue(decodeProgressionCards("pct").isEmpty())
        assertEquals(
            listOf(ProgressionCardConfig(exercises = listOf("garbage"), showPctChange = false)),
            decodeProgressionCards("not-an-axis\u001Fgarbage"),
        )
        // Caps: at most 3 cards, at most 3 exercises per card.
        val oversized = List(5) { i -> ProgressionCardConfig(exercises = List(5) { "E$i-$it" }) }
        val decoded = decodeProgressionCards(encodeProgressionCards(oversized))
        assertEquals(3, decoded.size)
        assertTrue(decoded.all { it.exercises.size == 3 })
    }

    @Test
    fun `bar weights round-trip and decode leniently`() {
        val weights = mapOf(
            BarWeightKey(1L, WeightUnit.METRIC) to 15.0,
            BarWeightKey(1L, WeightUnit.IMPERIAL) to 45.0,
            BarWeightKey(7L, WeightUnit.METRIC) to 20.0,
        )
        assertEquals(weights, decodeBarWeights(encodeBarWeights(weights)))

        assertTrue(decodeBarWeights(null).isEmpty())
        assertTrue(decodeBarWeights("").isEmpty())
        // Malformed records are dropped, valid ones kept.
        val valid = encodeBarWeights(mapOf(BarWeightKey(3L, WeightUnit.METRIC) to 25.0))
        val mixed = listOf("garbage", valid, "9\u001Fstone\u001F20", "1\u001Fmetric\u001Fheavy")
            .joinToString("\u001E")
        assertEquals(mapOf(BarWeightKey(3L, WeightUnit.METRIC) to 25.0), decodeBarWeights(mixed))
    }

    @Test
    fun `previous sets come from the latest completed session containing the exercise`() = runBlocking {
        seedProfile()
        val now = nowMillis()
        val bench = assertNotNull(db.exerciseDao().findByName("Barbell Bench Press"))
        val other = db.exerciseDao().insert(
            ExerciseEntity(
                name = "Test Movement", description = null, exerciseType = null,
                bodyPart = null, otherBodyParts = null, equipment = null,
                createdAt = now, updatedAt = now,
            ),
        )

        suspend fun insertSession(performedAt: Long, completedAt: Long?): Long =
            db.workoutSessionDao().insert(
                WorkoutSessionEntity(
                    profileId = PROFILE_ID, routineId = null, performedAt = performedAt,
                    completedAt = completedAt, createdAt = now, updatedAt = now,
                ),
            )

        suspend fun insertSet(sessionId: Long, exerciseId: Long, position: Int, weight: Double?, reps: Int?) {
            db.workoutSessionDetailDao().insert(
                WorkoutSessionDetailEntity(
                    workoutSessionId = sessionId, workSetDetailId = null, exerciseId = exerciseId,
                    position = position, targetWeight = null, targetReps = null, targetDuration = null,
                    actualWeight = weight, actualReps = reps, actualDuration = null,
                    completed = true, createdAt = now, updatedAt = now,
                ),
            )
        }

        // Older completed session: must be superseded by the newer one.
        val old = insertSession(performedAt = 1000, completedAt = 1500)
        insertSet(old, bench.id, position = 1, weight = 40.0, reps = 10)

        val latest = insertSession(performedAt = 2000, completedAt = 2500)
        insertSet(latest, bench.id, position = 1, weight = 50.0, reps = 5)
        insertSet(latest, other, position = 2, weight = 20.0, reps = 12)
        insertSet(latest, bench.id, position = 3, weight = 55.0, reps = 3)

        // Newer but in progress: must not be picked up.
        val inProgress = insertSession(performedAt = 3000, completedAt = null)
        insertSet(inProgress, bench.id, position = 1, weight = 60.0, reps = 1)

        val previous = db.workoutSessionDetailDao().getLatestCompletedSets(PROFILE_ID, bench.id)
        assertEquals(listOf(50.0 to 5, 55.0 to 3), previous.map { it.actualWeight to it.actualReps })

        // Only the requested exercise's sets from that session.
        val otherPrevious = db.workoutSessionDetailDao().getLatestCompletedSets(PROFILE_ID, other)
        assertEquals(listOf(20.0 to 12), otherPrevious.map { it.actualWeight to it.actualReps })

        // An exercise never performed has no previous sets.
        val fresh = db.exerciseDao().insert(
            ExerciseEntity(
                name = "Never Done", description = null, exerciseType = null,
                bodyPart = null, otherBodyParts = null, equipment = null,
                createdAt = now, updatedAt = now,
            ),
        )
        assertTrue(db.workoutSessionDetailDao().getLatestCompletedSets(PROFILE_ID, fresh).isEmpty())
    }

    private suspend fun seedProfile() {
        val now = nowMillis()
        db.profileDao().upsert(
            ProfileEntity(name = "Test", age = null, gender = null, weight = null, height = null, createdAt = now, updatedAt = now),
        )
    }
}
