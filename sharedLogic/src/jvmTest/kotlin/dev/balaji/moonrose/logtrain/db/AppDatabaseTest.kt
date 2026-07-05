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
    fun `deleting a plan cascades to its routines`() = runBlocking {
        val now = nowMillis()
        db.profileDao().upsert(
            ProfileEntity(name = "Test", age = null, gender = null, weight = null, height = null, createdAt = now, updatedAt = now),
        )
        val planId = db.planDao().insert(
            PlanEntity(profileId = PROFILE_ID, name = "PPL", createdAt = now, updatedAt = now),
        )
        db.routineDao().insert(
            RoutineEntity(planId = planId, name = "Push", position = 1, createdAt = now, updatedAt = now),
        )
        db.routineDao().insert(
            RoutineEntity(planId = planId, name = "Pull", position = 2, createdAt = now, updatedAt = now),
        )
        assertEquals(2, db.routineDao().observeAll().first().size)
        assertEquals(3, db.routineDao().nextPosition(planId))

        db.planDao().delete(planId)
        assertTrue(db.planDao().observeAll().first().isEmpty())
        assertTrue(db.routineDao().observeAll().first().isEmpty(), "routines should cascade-delete")
    }

    @Test
    fun `exercise catalog is seeded on create`() = runBlocking {
        val exercises = db.exerciseDao().observeAll().first()
        assertTrue(exercises.size >= 15, "expected seeded exercises, got ${exercises.size}")
        assertTrue(exercises.any { it.name == "Barbell Bench Press" })
    }
}
