package dev.balaji.moonrose.logtrain.db

import dev.balaji.moonrose.logtrain.model.Program
import dev.balaji.moonrose.logtrain.model.Routine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

object ProgramRepository {
    val programs: Flow<List<Program>> =
        AppDb.db.programDao().observeAll().map { entities -> entities.map { it.toDomain() } }

    val routines: Flow<List<Routine>> =
        AppDb.db.routineDao().observeAll().map { entities -> entities.map { it.toDomain() } }

    suspend fun createProgram(name: String) {
        val now = nowMillis()
        AppDb.db.programDao().insert(
            ProgramEntity(profileId = PROFILE_ID, name = name, createdAt = now, updatedAt = now),
        )
    }

    suspend fun renameProgram(id: Long, name: String) {
        AppDb.db.programDao().rename(id, name, nowMillis())
    }

    /** Routines are removed by the program_id foreign key's ON DELETE CASCADE. */
    suspend fun deleteProgram(id: Long) {
        AppDb.db.programDao().delete(id)
    }

    suspend fun createRoutine(programId: Long, name: String): Long {
        val now = nowMillis()
        val dao = AppDb.db.routineDao()
        return dao.insert(
            RoutineEntity(
                programId = programId,
                name = name,
                position = dao.nextPosition(programId),
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    suspend fun renameRoutine(id: Long, name: String) {
        AppDb.db.routineDao().rename(id, name, nowMillis())
    }

    suspend fun deleteRoutine(id: Long) {
        AppDb.db.routineDao().delete(id)
    }
}

private fun ProgramEntity.toDomain() = Program(id = id, name = name)

private fun RoutineEntity.toDomain() =
    Routine(id = id, name = name, programId = programId, position = position)
