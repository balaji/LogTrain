package dev.balaji.moonrose.logtrain.model

/**
 * Ready-made programs offered on the Programs screen so a new user has something
 * to train with before building their own. They are defined here rather than
 * seeded into the programs table: a seeded row is indistinguishable from a user
 * row, so it could never be safely refreshed once edited. Instead the user copies
 * one, which materializes ordinary program/routine/work-set rows they then own.
 *
 * Samples carry no display names or summaries: each UI layer maps the stable
 * [SampleProgram.id]/[SampleRoutine.id] keys to its own localized text, and the
 * copy flow passes the names the user actually saw to
 * ProgramRepository.addSampleProgram, which persists them.
 *
 * [SampleWorkSet.exerciseName] must exactly match a seeded exercise name; seed
 * exercises are builtin and therefore cannot be renamed away from under us.
 * (Exercise names are catalog data shown verbatim, not localized copy.)
 */
data class SampleProgram(
    /** Stable key: UI list identity and the localization key for name and summary; not persisted. */
    val id: String,
    val daysPerWeek: Int,
    val routines: List<SampleRoutine>,
)

data class SampleRoutine(
    /** Stable key: UI list identity and the localization key for the routine name. */
    val id: String,
    val workSets: List<SampleWorkSet>,
)

data class SampleWorkSet(
    val exerciseName: String,
    val sets: List<SampleSet>,
) {
    /**
     * Display form of the set scheme, e.g. "3×5", or "5/5/3" when the reps vary.
     * Not named setSummary: "set" is an Objective-C method-family prefix, like
     * the one that forced addSampleProgram's name.
     */
    val repsSummary: String
        get() {
            if (sets.isEmpty()) return ""
            val reps = sets.map { it.targetReps }
            return if (reps.distinct().size == 1) "${sets.size}×${reps.first()}" else reps.joinToString("/")
        }
}

data class SampleSet(
    val targetReps: Int,
    /** Left null in every sample: the user supplies their own load. */
    val targetWeight: Double? = null,
    val setType: SetType = SetType.NORMAL,
)

private fun sets(count: Int, reps: Int) = List(count) { SampleSet(targetReps = reps) }

private val fullBody3Day = SampleProgram(
    id = "full-body-3day",
    daysPerWeek = 3,
    routines = listOf(
        SampleRoutine(
            "full-body-a",
            listOf(
                SampleWorkSet("Barbell Back Squat", sets(3, 5)),
                SampleWorkSet("Barbell Bench Press", sets(3, 5)),
                SampleWorkSet("Barbell Bent-Over Row", sets(3, 8)),
                SampleWorkSet("Barbell Overhead Press", sets(3, 8)),
                SampleWorkSet("Hanging Knee Raise", sets(3, 12)),
            ),
        ),
        SampleRoutine(
            "full-body-b",
            listOf(
                SampleWorkSet("Conventional Deadlift", sets(3, 5)),
                SampleWorkSet("Barbell Incline Bench Press", sets(3, 8)),
                SampleWorkSet("Lat Pulldown", sets(3, 10)),
                SampleWorkSet("Dumbbell Lateral Raise", sets(3, 15)),
                SampleWorkSet("Hanging Leg Raise", sets(3, 12)),
            ),
        ),
        SampleRoutine(
            "full-body-c",
            listOf(
                SampleWorkSet("Barbell Front Squat", sets(3, 5)),
                SampleWorkSet("Dumbbell Bench Press", sets(3, 10)),
                SampleWorkSet("Seated Cable Row", sets(3, 10)),
                SampleWorkSet("Barbell Curl", sets(3, 12)),
                SampleWorkSet("Cable Crunch", sets(3, 15)),
            ),
        ),
    ),
)

private val upperLower4Day = SampleProgram(
    id = "upper-lower-4day",
    daysPerWeek = 4,
    routines = listOf(
        SampleRoutine(
            "upper-a",
            listOf(
                SampleWorkSet("Barbell Bench Press", sets(4, 6)),
                SampleWorkSet("Barbell Bent-Over Row", sets(4, 6)),
                SampleWorkSet("Barbell Overhead Press", sets(3, 8)),
                SampleWorkSet("Lat Pulldown", sets(3, 10)),
                SampleWorkSet("Barbell Curl", sets(3, 12)),
                SampleWorkSet("Skull Crusher", sets(3, 12)),
            ),
        ),
        SampleRoutine(
            "lower-a",
            listOf(
                SampleWorkSet("Barbell Back Squat", sets(4, 6)),
                SampleWorkSet("Romanian Deadlift", sets(3, 8)),
                SampleWorkSet("Leg Press", sets(3, 10)),
                SampleWorkSet("Lying Leg Curl", sets(3, 12)),
                SampleWorkSet("Standing Calf Raise", sets(4, 15)),
            ),
        ),
        SampleRoutine(
            "upper-b",
            listOf(
                SampleWorkSet("Barbell Incline Bench Press", sets(4, 8)),
                SampleWorkSet("Seated Cable Row", sets(4, 8)),
                SampleWorkSet("Dumbbell Shoulder Press", sets(3, 10)),
                SampleWorkSet("Pull-Up", sets(3, 8)),
                SampleWorkSet("Hammer Curl", sets(3, 12)),
                SampleWorkSet("Rope Pushdown", sets(3, 12)),
            ),
        ),
        SampleRoutine(
            "lower-b",
            listOf(
                SampleWorkSet("Conventional Deadlift", sets(3, 5)),
                SampleWorkSet("Barbell Front Squat", sets(3, 8)),
                SampleWorkSet("Leg Extension", sets(3, 15)),
                SampleWorkSet("Seated Leg Curl", sets(3, 12)),
                SampleWorkSet("Seated Calf Raise", sets(4, 15)),
            ),
        ),
    ),
)

private val pushPullLegs5Day = SampleProgram(
    id = "push-pull-legs-5day",
    daysPerWeek = 5,
    routines = listOf(
        SampleRoutine(
            "push",
            listOf(
                SampleWorkSet("Barbell Bench Press", sets(4, 6)),
                SampleWorkSet("Barbell Incline Bench Press", sets(3, 8)),
                SampleWorkSet("Barbell Overhead Press", sets(3, 8)),
                SampleWorkSet("Dumbbell Lateral Raise", sets(4, 15)),
                SampleWorkSet("Cable Triceps Pushdown", sets(3, 12)),
                SampleWorkSet("Overhead Dumbbell Triceps Extension", sets(3, 12)),
            ),
        ),
        SampleRoutine(
            "pull",
            listOf(
                SampleWorkSet("Conventional Deadlift", sets(3, 5)),
                SampleWorkSet("Pull-Up", sets(4, 8)),
                SampleWorkSet("Barbell Bent-Over Row", sets(3, 8)),
                SampleWorkSet("Seated Cable Row", sets(3, 10)),
                SampleWorkSet("Face Pull", sets(3, 15)),
                SampleWorkSet("Barbell Curl", sets(3, 12)),
            ),
        ),
        SampleRoutine(
            "legs",
            listOf(
                SampleWorkSet("Barbell Back Squat", sets(4, 6)),
                SampleWorkSet("Romanian Deadlift", sets(3, 8)),
                SampleWorkSet("Leg Press", sets(3, 12)),
                SampleWorkSet("Lying Leg Curl", sets(3, 12)),
                SampleWorkSet("Standing Calf Raise", sets(4, 15)),
            ),
        ),
        SampleRoutine(
            "upper",
            listOf(
                SampleWorkSet("Dumbbell Bench Press", sets(4, 10)),
                SampleWorkSet("Lat Pulldown", sets(4, 10)),
                SampleWorkSet("Dumbbell Shoulder Press", sets(3, 12)),
                SampleWorkSet("Chest-Supported Dumbbell Row", sets(3, 12)),
                SampleWorkSet("Hammer Curl", sets(3, 12)),
                SampleWorkSet("Skull Crusher", sets(3, 12)),
            ),
        ),
        SampleRoutine(
            "lower",
            listOf(
                SampleWorkSet("Barbell Front Squat", sets(4, 8)),
                SampleWorkSet("Barbell Hip Thrust", sets(3, 10)),
                SampleWorkSet("Leg Extension", sets(3, 15)),
                SampleWorkSet("Seated Leg Curl", sets(3, 12)),
                SampleWorkSet("Seated Calf Raise", sets(4, 15)),
            ),
        ),
    ),
)

/** Declared last so the programs it lists are initialized before it reads them. */
object SamplePrograms {
    val all: List<SampleProgram> = listOf(fullBody3Day, upperLower4Day, pushPullLegs5Day)
}
