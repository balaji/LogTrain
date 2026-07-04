package dev.balaji.moonrose.logtrain.db

import dev.balaji.moonrose.logtrain.model.Profile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** The single local profile row. */
const val PROFILE_ID = 1L

object ProfileRepository {
    val profile: Flow<Profile?> =
        AppDb.db.profileDao().observe().map { it?.toDomain() }

    suspend fun save(name: String, age: Int?, gender: String?, weight: Double?, height: Double?) {
        val now = nowMillis()
        val existing = AppDb.db.profileDao().observe().first()
        AppDb.db.profileDao().upsert(
            ProfileEntity(
                id = PROFILE_ID,
                name = name,
                age = age,
                gender = gender,
                weight = weight,
                height = height,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
            ),
        )
    }
}

private fun ProfileEntity.toDomain() =
    Profile(id = id, name = name, age = age, gender = gender, weight = weight, height = height)
