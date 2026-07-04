package dev.balaji.moonrose.logtrain.db

import dev.balaji.moonrose.logtrain.model.Plan
import dev.balaji.moonrose.logtrain.model.Routine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

object PlanRepository {
    val plans: Flow<List<Plan>> =
        AppDb.db.planDao().observeAll().map { entities -> entities.map { it.toDomain() } }

    val routines: Flow<List<Routine>> =
        AppDb.db.routineDao().observeAll().map { entities -> entities.map { it.toDomain() } }

    suspend fun createPlan(name: String) {
        val now = nowMillis()
        AppDb.db.planDao().insert(
            PlanEntity(profileId = PROFILE_ID, name = name, createdAt = now, updatedAt = now),
        )
    }

    suspend fun renamePlan(id: Long, name: String) {
        AppDb.db.planDao().rename(id, name, nowMillis())
    }

    /** Routines are removed by the plan_id foreign key's ON DELETE CASCADE. */
    suspend fun deletePlan(id: Long) {
        AppDb.db.planDao().delete(id)
    }

    suspend fun createRoutine(planId: Long, name: String) {
        val now = nowMillis()
        val dao = AppDb.db.routineDao()
        dao.insert(
            RoutineEntity(
                planId = planId,
                name = name,
                position = dao.nextPosition(planId),
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

private fun PlanEntity.toDomain() = Plan(id = id, name = name)

private fun RoutineEntity.toDomain() =
    Routine(id = id, name = name, planId = planId, position = position)
