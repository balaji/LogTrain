package dev.balaji.moonrose.logtrain.db

import androidx.sqlite.SQLiteException
import dev.balaji.moonrose.logtrain.model.Exercise
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class ExerciseUpdateError {
    DuplicateName,
}

object ExerciseRepository {
    val exercises: Flow<List<Exercise>> =
        AppDb.db.exerciseDao().observeAll().map { entities -> entities.map { it.toDomain() } }

    suspend fun findById(id: Long): Exercise? = AppDb.db.exerciseDao().findById(id)?.toDomain()

    suspend fun update(
        id: Long,
        name: String,
        description: String?,
        exerciseType: String?,
        bodyPart: String?,
        equipment: String?,
    ): ExerciseUpdateError? {
        return try {
            AppDb.db.exerciseDao().update(
                id = id,
                name = name,
                description = description,
                exerciseType = exerciseType,
                bodyPart = bodyPart,
                equipment = equipment,
                now = nowMillis(),
            )
            null
        } catch (e: SQLiteException) {
            if (e.message?.contains("UNIQUE", ignoreCase = true) == true) {
                ExerciseUpdateError.DuplicateName
            } else {
                throw e
            }
        }
    }
}

private fun ExerciseEntity.toDomain() = Exercise(
    id = id,
    name = name,
    description = description,
    exerciseType = exerciseType,
    bodyPart = bodyPart,
    equipment = equipment,
)
