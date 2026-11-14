package dev.balaji.moonrose.trainfree.db

import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import dev.balaji.moonrose.trainfree.model.Program
import dev.balaji.moonrose.trainfree.model.Routine
import dev.balaji.moonrose.trainfree.model.SampleProgram
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

object ProgramRepository {
    val programs: Flow<List<Program>> =
        AppDb.db.programDao().observeAll().map { entities -> entities.map { it.toDomain() } }

    val routines: Flow<List<Routine>> =
        AppDb.db.routineDao().observeAll().map { entities -> entities.map { it.toDomain() } }

    suspend fun createProgram(name: String) {
        val validName = requireValidProgramName(name)
        val now = nowMillis()
        AppDb.db.programDao().insert(
            ProgramEntity(profileId = PROFILE_ID, name = validName, createdAt = now, updatedAt = now),
        )
    }

    suspend fun renameProgram(id: Long, name: String) {
        AppDb.db.programDao().rename(id, requireValidProgramName(name), nowMillis())
    }

    /** Routines are removed by the program_id foreign key's ON DELETE CASCADE. */
    suspend fun deleteProgram(id: Long) {
        AppDb.db.programDao().delete(id)
    }

    suspend fun createRoutine(programId: Long, name: String): Long {
        val validName = requireValidProgramName(name)
        val now = nowMillis()
        val dao = AppDb.db.routineDao()
        return dao.insert(
            RoutineEntity(
                programId = programId,
                name = validName,
                position = dao.nextPosition(programId),
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    suspend fun renameRoutine(id: Long, name: String) {
        AppDb.db.routineDao().rename(id, requireValidProgramName(name), nowMillis())
    }

    suspend fun deleteRoutine(id: Long) {
        AppDb.db.routineDao().delete(id)
    }

    /**
     * Materializes a [SampleProgram] into ordinary rows the user owns and can edit.
     * Returns the new program's id.
     *
     * Samples carry only language-neutral keys, so the caller passes the localized
     * [programName] and [routineNames] (aligned with [SampleProgram.routines]) it
     * displayed; those are what get persisted.
     *
     * Not named copySampleProgram: "copy" is an Objective-C method-family prefix, so
     * Kotlin/Native would export it to Swift as doCopySampleProgram.
     */
    suspend fun addSampleProgram(sample: SampleProgram, programName: String, routineNames: List<String>): Long =
        AppDb.db.insertSampleProgram(sample, programName, routineNames)
}

/**
 * The body of [ProgramRepository.addSampleProgram], taking the database explicitly
 * so tests can run it against a temporary one rather than the singleton.
 *
 * Copying the same sample twice yields two independent programs with the same name,
 * which is what [ProgramRepository.createProgram] already allows for hand-made programs.
 */
internal suspend fun AppDatabase.insertSampleProgram(
    sample: SampleProgram,
    programName: String,
    routineNames: List<String>,
): Long {
    require(routineNames.size == sample.routines.size) {
        "Sample \"${sample.id}\" has ${sample.routines.size} routines but ${routineNames.size} names were passed"
    }
    val validProgramName = requireValidProgramName(programName)
    val validRoutineNames = routineNames.map { requireValidProgramName(it) }

    // Resolved before the transaction opens so an unknown name fails without writing
    // anything. Seed exercises are builtin, so this cannot fail for a correct sample;
    // it exists to catch a typo in the sample definitions.
    val exerciseIds = sample.routines
        .flatMap { it.workSets }
        .map { it.exerciseName }
        .distinct()
        .associateWith { name ->
            checkNotNull(exerciseDao().findByName(name)) {
                "Sample program \"${sample.id}\" references unknown exercise \"$name\""
            }.id
        }

    val now = nowMillis()
    return useWriterConnection { transactor ->
        transactor.immediateTransaction {
            val programId = programDao().insert(
                ProgramEntity(
                    profileId = PROFILE_ID,
                    name = validProgramName,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
            sample.routines.forEachIndexed { routineIdx, routine ->
                val routineId = routineDao().insert(
                    RoutineEntity(
                        programId = programId,
                        name = validRoutineNames[routineIdx],
                        position = routineIdx + 1,
                        createdAt = now,
                        updatedAt = now,
                    ),
                )
                routine.workSets.forEachIndexed { workSetIdx, workSet ->
                    val workSetId = workSetDao().insert(
                        WorkSetEntity(
                            routineId = routineId,
                            exerciseId = exerciseIds.getValue(workSet.exerciseName),
                            position = workSetIdx + 1,
                            createdAt = now,
                            updatedAt = now,
                        ),
                    )
                    workSet.sets.forEachIndexed { setIdx, set ->
                        workSetDetailDao().insert(
                            WorkSetDetailEntity(
                                workSetId = workSetId,
                                position = setIdx + 1,
                                targetWeight = set.targetWeight,
                                targetReps = set.targetReps,
                                targetDuration = null,
                                targetDistance = null,
                                setType = set.setType,
                                createdAt = now,
                                updatedAt = now,
                            ),
                        )
                    }
                }
            }
            programId
        }
    }
}

private fun ProgramEntity.toDomain() = Program(id = id, name = name)

private fun RoutineEntity.toDomain() =
    Routine(id = id, name = name, programId = programId, position = position)
