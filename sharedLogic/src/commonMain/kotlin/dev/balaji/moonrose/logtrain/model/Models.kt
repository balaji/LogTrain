package dev.balaji.moonrose.logtrain.model

/**
 * Domain models exposed to the UI layer. These are plain data classes (no Room
 * types) so they remain usable from the js target, which has no database.
 */
data class Profile(
    val id: Long,
    val name: String,
    val age: Int?,
    val gender: String?,
    val weight: Double?,
    val height: Double?,
)

data class Plan(
    val id: Long,
    val name: String,
)

data class Routine(
    val id: Long,
    val name: String,
    val planId: Long,
    val position: Int,
)

data class Exercise(
    val id: Long,
    val name: String,
    val bodyPart: String?,
    val equipment: String?,
)
