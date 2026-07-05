package dev.balaji.moonrose.logtrain.model

import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

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

@OptIn(ExperimentalObjCName::class)
data class Exercise(
    val id: Long,
    val name: String,
    // "description" collides with NSObject's auto-generated `description` (toString())
    // on the Swift side; see ExerciseFormUiState for the same fix.
    @ObjCName("descriptionText")
    val description: String?,
    val exerciseType: String?,
    val bodyPart: String?,
    val equipment: String?,
)
