package dev.balaji.moonrose.logtrain.db

import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import dev.balaji.moonrose.logtrain.calendar.WeekMath
import dev.balaji.moonrose.logtrain.model.SamplePrograms
import dev.balaji.moonrose.logtrain.model.WeightUnit
import kotlin.math.roundToInt
import kotlin.random.Random
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

/*
 * Developer-only generator of plausible workout history, reachable from the
 * Developer section of the profile screen. Generated rows are flagged with the
 * test_data column so they can be wiped in one statement without touching real
 * history. Planning (pure, seeded-Random friendly) is separated from
 * persistence so the shape of the data can be unit tested.
 */

internal data class PlannedSet(
    val exerciseName: String,
    val targetWeight: Double?,
    val targetReps: Int,
    val actualWeight: Double?,
    val actualReps: Int,
)

internal data class PlannedSession(
    val epochDay: Long,
    val startMinuteOfDay: Int,
    val durationMinutes: Int,
    val sets: List<PlannedSet>,
)

internal data class PlannedWeighIn(
    val epochDay: Long,
    val minuteOfDay: Int,
    val weight: Double,
)

internal data class TestDataPlan(
    val sessions: List<PlannedSession>,
    val weighIns: List<PlannedWeighIn>,
)

/**
 * Starting working weights in kg for an average-built male getting back into
 * training. Exercises absent from this map (the hanging ab work) are logged
 * as bodyweight: null weight, reps only.
 */
private val START_WEIGHTS_KG = mapOf(
    "Barbell Back Squat" to 60.0,
    "Barbell Bench Press" to 50.0,
    "Barbell Bent-Over Row" to 40.0,
    "Barbell Overhead Press" to 30.0,
    "Conventional Deadlift" to 80.0,
    "Barbell Incline Bench Press" to 40.0,
    "Lat Pulldown" to 45.0,
    "Dumbbell Lateral Raise" to 8.0,
    "Barbell Front Squat" to 40.0,
    "Dumbbell Bench Press" to 22.5,
    "Seated Cable Row" to 45.0,
    "Barbell Curl" to 20.0,
    "Cable Crunch" to 25.0,
)

private val TRAINING_DAYS = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)

private const val KG_PER_LB = 0.45359237

/** Default starting bodyweight when the profile has none: ~82 kg / 180 lb. */
private const val DEFAULT_BODY_WEIGHT_KG = 82.0

/**
 * Plans [days] calendar days of history ending today: roughly three sessions a
 * week rotating through the 3 Day Full Body sample routines with gradual
 * progressive overload, plus a bodyweight entry every few days. All weights
 * are in the user's display unit, like the real logging paths.
 */
internal fun planTestData(
    days: Int,
    todayEpochDay: Long,
    weightUnit: WeightUnit,
    startBodyWeight: Double?,
    random: Random,
): TestDataPlan {
    require(days > 0) { "days must be positive" }
    val routines = SamplePrograms.all.first { it.id == "full-body-3day" }.routines
    val firstDay = todayEpochDay - days + 1

    val sessions = mutableListOf<PlannedSession>()
    var rotation = 0
    for (day in firstDay..todayEpochDay) {
        if (LocalDate.fromEpochDays(day).dayOfWeek !in TRAINING_DAYS) continue
        // Life happens: roughly one session in ten is skipped.
        if (random.nextDouble() < 0.1) continue
        val routine = routines[rotation % routines.size]
        rotation++

        val sets = mutableListOf<PlannedSet>()
        for (workSet in routine.workSets) {
            val weight = workingWeight(workSet.exerciseName, day - firstDay, weightUnit, random)
            workSet.sets.forEachIndexed { index, set ->
                val lastSet = index == workSet.sets.lastIndex
                // The final set of an exercise misses a rep or two now and then.
                val missed = if (lastSet && random.nextDouble() < 0.25) random.nextInt(1, 3) else 0
                sets += PlannedSet(
                    exerciseName = workSet.exerciseName,
                    targetWeight = weight,
                    targetReps = set.targetReps,
                    actualWeight = weight,
                    actualReps = (set.targetReps - missed).coerceAtLeast(1),
                )
            }
        }
        sessions += PlannedSession(
            epochDay = day,
            // Evening sessions between 17:00 and 19:00.
            startMinuteOfDay = 17 * 60 + random.nextInt(0, 121),
            durationMinutes = 45 + random.nextInt(0, 31),
            sets = sets,
        )
    }

    val weighIns = mutableListOf<PlannedWeighIn>()
    val unitScale = if (weightUnit == WeightUnit.IMPERIAL) 1 / KG_PER_LB else 1.0
    var bodyWeight = startBodyWeight ?: (DEFAULT_BODY_WEIGHT_KG * unitScale)
    var day = firstDay
    while (day <= todayEpochDay) {
        weighIns += PlannedWeighIn(
            epochDay = day,
            // Morning weigh-ins between 06:30 and 08:00.
            minuteOfDay = 6 * 60 + 30 + random.nextInt(0, 91),
            weight = (bodyWeight * 10).roundToInt() / 10.0,
        )
        val gap = random.nextInt(2, 4)
        // Slow recomposition drift downward with day-to-day noise.
        bodyWeight += (-0.02 * gap + random.nextDouble(-0.3, 0.3)) * unitScale
        day += gap
    }

    return TestDataPlan(sessions = sessions, weighIns = weighIns)
}

/**
 * Weight for one exercise on the session [dayOffset] days into the plan:
 * a linear ramp of about 0.45% of the starting weight per calendar day,
 * capped at +45%, with an occasional one-step off day, rounded to the
 * plates/pins available in the unit.
 */
private fun workingWeight(
    exerciseName: String,
    dayOffset: Long,
    weightUnit: WeightUnit,
    random: Random,
): Double? {
    val startKg = START_WEIGHTS_KG[exerciseName] ?: return null
    val ramp = 1 + minOf(0.0045 * dayOffset, 0.45)
    var kg = startKg * ramp
    val step = when {
        weightUnit == WeightUnit.IMPERIAL -> 5 * KG_PER_LB
        startKg < 15.0 -> 1.0
        else -> 2.5
    }
    if (random.nextDouble() < 0.15) kg -= step
    val steps = (kg / step).roundToInt().coerceAtLeast(1)
    val rounded = steps * step
    return if (weightUnit == WeightUnit.IMPERIAL) {
        (rounded / KG_PER_LB).roundToInt().toDouble()
    } else {
        rounded
    }
}

object TestDataRepository {
    /** Number of generated sessions currently in the database; 0 when none. */
    val testSessionCount: Flow<Int> = AppDb.db.workoutSessionDao().observeTestDataCount()

    suspend fun generate(days: Int) {
        val unitSetting = AppDb.db.userSettingDao().find(SETTING_WEIGHT_UNIT)
        val plan = planTestData(
            days = days,
            todayEpochDay = WeekMath.todayEpochDay(),
            weightUnit = WeightUnit.fromValue(unitSetting?.value),
            startBodyWeight = AppDb.db.profileDao().observe().first()?.weight,
            random = Random(nowMillis()),
        )
        AppDb.db.insertTestData(plan)
    }

    suspend fun deleteAll() {
        AppDb.db.workoutSessionDao().deleteTestData()
        AppDb.db.bodyMetricDao().deleteTestData()
    }
}

/**
 * Persists a plan as completed ad-hoc sessions (no routine or program rows, so
 * deletion only ever touches workout_sessions and body_metrics; details cascade).
 * Takes the database explicitly so tests can run it against a temporary one.
 */
internal suspend fun AppDatabase.insertTestData(plan: TestDataPlan) {
    // Resolved before the transaction opens so an unknown name fails without
    // writing anything, like insertSampleProgram.
    val exerciseIds = plan.sessions
        .flatMap { it.sets }
        .map { it.exerciseName }
        .distinct()
        .associateWith { name ->
            checkNotNull(exerciseDao().findByName(name)) {
                "Test data references unknown exercise \"$name\""
            }.id
        }

    val now = nowMillis()
    val zone = TimeZone.currentSystemDefault()
    useWriterConnection { transactor ->
        transactor.immediateTransaction {
            for (session in plan.sessions) {
                val performedAt = startOfDayMillis(session.epochDay, zone) +
                    session.startMinuteOfDay * 60_000L
                val sessionId = workoutSessionDao().insert(
                    WorkoutSessionEntity(
                        profileId = PROFILE_ID,
                        routineId = null,
                        performedAt = performedAt,
                        completedAt = performedAt + session.durationMinutes * 60_000L,
                        testData = true,
                        createdAt = now,
                        updatedAt = now,
                    ),
                )
                session.sets.forEachIndexed { index, set ->
                    workoutSessionDetailDao().insert(
                        WorkoutSessionDetailEntity(
                            workoutSessionId = sessionId,
                            workSetDetailId = null,
                            exerciseId = exerciseIds.getValue(set.exerciseName),
                            position = index + 1,
                            targetWeight = set.targetWeight,
                            targetReps = set.targetReps,
                            targetDuration = null,
                            actualWeight = set.actualWeight,
                            actualReps = set.actualReps,
                            actualDuration = null,
                            completed = true,
                            createdAt = now,
                            updatedAt = now,
                        ),
                    )
                }
            }
            for (weighIn in plan.weighIns) {
                bodyMetricDao().insert(
                    BodyMetricEntity(
                        profileId = PROFILE_ID,
                        weight = weighIn.weight,
                        loggedAt = startOfDayMillis(weighIn.epochDay, zone) +
                            weighIn.minuteOfDay * 60_000L,
                        testData = true,
                        createdAt = now,
                        updatedAt = now,
                    ),
                )
            }
        }
    }
}
