package dev.balaji.moonrose.logtrain.db

import dev.balaji.moonrose.logtrain.model.Exercise
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

object ExerciseRepository {
    val exercises: Flow<List<Exercise>> =
        AppDb.db.exerciseDao().observeAll().map { entities -> entities.map { it.toDomain() } }
}

private fun ExerciseEntity.toDomain() =
    Exercise(id = id, name = name, bodyPart = bodyPart, equipment = equipment)