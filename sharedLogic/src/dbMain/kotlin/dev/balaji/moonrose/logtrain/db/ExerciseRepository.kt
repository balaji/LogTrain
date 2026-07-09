package dev.balaji.moonrose.logtrain.db

import androidx.sqlite.SQLiteException
import dev.balaji.moonrose.logtrain.model.Exercise
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class ExerciseUpdateError {
    DuplicateName,

    /** Seed exercises are read-only; sample programs reference them by name. */
    Builtin,
}

enum class ExerciseDeleteError {
    /** Seed exercises are read-only; sample programs reference them by name. */
    Builtin,

    /** Referenced by a routine's work sets or by workout history (FKs are NO_ACTION). */
    InUse,
}

object ExerciseRepository {
    val exercises: Flow<List<Exercise>> =
        AppDb.db.exerciseDao().observeAll().map { entities -> entities.map { it.toDomain() } }

    suspend fun findById(id: Long): Exercise? = AppDb.db.exerciseDao().findById(id)?.toDomain()

    suspend fun create(
        name: String,
        description: String?,
        exerciseType: String?,
        bodyPart: String?,
        equipment: String?,
    ): ExerciseUpdateError? {
        return try {
            val now = nowMillis()
            AppDb.db.exerciseDao().insert(
                ExerciseEntity(
                    name = name,
                    description = description,
                    exerciseType = exerciseType,
                    bodyPart = bodyPart,
                    otherBodyParts = null,
                    equipment = equipment,
                    createdAt = now,
                    updatedAt = now,
                ),
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

    suspend fun update(
        id: Long,
        name: String,
        description: String?,
        exerciseType: String?,
        bodyPart: String?,
        equipment: String?,
    ): ExerciseUpdateError? {
        if (AppDb.db.exerciseDao().findById(id)?.builtin == true) return ExerciseUpdateError.Builtin
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

    suspend fun delete(id: Long): ExerciseDeleteError? {
        if (AppDb.db.exerciseDao().findById(id)?.builtin == true) return ExerciseDeleteError.Builtin
        return try {
            AppDb.db.exerciseDao().delete(id)
            null
        } catch (e: SQLiteException) {
            if (e.message?.contains("FOREIGN KEY", ignoreCase = true) == true) {
                ExerciseDeleteError.InUse
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
    builtin = builtin,
)
