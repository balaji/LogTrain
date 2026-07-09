package dev.balaji.moonrose.logtrain.db

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import dev.balaji.moonrose.logtrain.model.SampleProgram
import dev.balaji.moonrose.logtrain.model.SamplePrograms
import dev.balaji.moonrose.logtrain.model.SampleRoutine
import dev.balaji.moonrose.logtrain.model.SampleSet
import dev.balaji.moonrose.logtrain.model.SampleWorkSet
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
    fun `sample program names pass program name validation`() {
        for (sample in SamplePrograms.all) {
            assertEquals(sample.name, requireValidProgramName(sample.name))
            for (routine in sample.routines) {
                assertEquals(routine.name, requireValidProgramName(routine.name))
            }
        }
    }

    @Test
    fun `copying a sample materializes the full program tree`() = runBlocking {
        seedProfile()
        val sample = SamplePrograms.all.first { it.daysPerWeek == 3 }

        val programId = db.insertSampleProgram(sample)

        val program = assertNotNull(db.programDao().findById(programId))
        assertEquals(sample.name, program.name)

        val routines = db.routineDao().observeAll().first().filter { it.programId == programId }
        assertEquals(sample.routines.map { it.name }, routines.map { it.name })
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

        val first = db.insertSampleProgram(sample)
        val second = db.insertSampleProgram(sample)

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
            name = "Broken Sample",
            daysPerWeek = 1,
            summary = "References an exercise that is not in the catalog.",
            routines = listOf(
                SampleRoutine(
                    "Day One",
                    listOf(
                        SampleWorkSet("Barbell Bench Press", listOf(SampleSet(targetReps = 5))),
                        SampleWorkSet("No Such Exercise", listOf(SampleSet(targetReps = 5))),
                    ),
                ),
            ),
        )

        assertFailsWith<IllegalStateException> { db.insertSampleProgram(broken) }
        assertTrue(db.programDao().observeAll().first().isEmpty(), "no program should be written")
        assertTrue(db.routineDao().observeAll().first().isEmpty(), "no routine should be written")
    }

    private suspend fun seedProfile() {
        val now = nowMillis()
        db.profileDao().upsert(
            ProfileEntity(name = "Test", age = null, gender = null, weight = null, height = null, createdAt = now, updatedAt = now),
        )
    }
}
