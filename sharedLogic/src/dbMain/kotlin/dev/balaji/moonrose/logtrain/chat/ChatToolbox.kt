package dev.balaji.moonrose.logtrain.chat

import dev.balaji.moonrose.logtrain.calendar.WeekMath
import dev.balaji.moonrose.logtrain.db.AppDb
import dev.balaji.moonrose.logtrain.db.BodyMetricsRepository
import dev.balaji.moonrose.logtrain.db.CompletedSetRow
import dev.balaji.moonrose.logtrain.db.ExerciseRepository
import dev.balaji.moonrose.logtrain.db.InsightsRepository
import dev.balaji.moonrose.logtrain.db.PROFILE_ID
import dev.balaji.moonrose.logtrain.db.ProfileRepository
import dev.balaji.moonrose.logtrain.db.RecentCompletedSessionRow
import dev.balaji.moonrose.logtrain.db.RoutineLastPerformedRow
import dev.balaji.moonrose.logtrain.db.SessionDetailWithExerciseRow
import dev.balaji.moonrose.logtrain.db.SetPoint
import dev.balaji.moonrose.logtrain.db.UserSettingsRepository
import dev.balaji.moonrose.logtrain.db.WorkSetDetailEntity
import dev.balaji.moonrose.logtrain.db.WorkSetEntity
import dev.balaji.moonrose.logtrain.db.bodyPartBreakdownFrom
import dev.balaji.moonrose.logtrain.db.convertedTo
import dev.balaji.moonrose.logtrain.db.millisToEpochDay
import dev.balaji.moonrose.logtrain.db.personalRecordsFrom
import dev.balaji.moonrose.logtrain.db.progressionFrom
import dev.balaji.moonrose.logtrain.db.singleExerciseWeeklyDistance
import dev.balaji.moonrose.logtrain.db.singleExerciseWeeklyDuration
import dev.balaji.moonrose.logtrain.db.singleExerciseWeeklyVolume
import dev.balaji.moonrose.logtrain.db.startOfDayMillis
import dev.balaji.moonrose.logtrain.db.toSetPoint
import dev.balaji.moonrose.logtrain.insights.BodyPartSlice
import dev.balaji.moonrose.logtrain.insights.BodyWeightTrend
import dev.balaji.moonrose.logtrain.insights.InsightMath.formatNumber
import dev.balaji.moonrose.logtrain.insights.TrainingFrequency
import dev.balaji.moonrose.logtrain.insights.WeeklyVolumePoint
import dev.balaji.moonrose.logtrain.model.Exercise
import dev.balaji.moonrose.logtrain.model.ExerciseType
import dev.balaji.moonrose.logtrain.model.HeightConversion
import dev.balaji.moonrose.logtrain.model.Profile
import dev.balaji.moonrose.logtrain.model.Program
import dev.balaji.moonrose.logtrain.model.Routine
import dev.balaji.moonrose.logtrain.model.SetType
import dev.balaji.moonrose.logtrain.model.UserSettings
import dev.balaji.moonrose.logtrain.model.WeightUnit
import kotlinx.coroutines.flow.first
import kotlinx.datetime.TimeZone

/**
 * Deterministic, read-only toolbox for the on-device AI chat assistant.
 *
 * Every numeric calculation, unit formatting, date formatting, and fuzzy
 * exercise resolution happens here in Kotlin so the language model never
 * has to compute or convert statistics. All outputs are compact and bounded
 * to fit comfortably inside a ~4K-token on-device context window.
 */
object ChatToolbox {

    /** Weekly volume, training frequency/streaks, and primary body-part distribution. */
    suspend fun getTrainingSummary(weeks: Int = 8): ToolOutput {
        val clampedWeeks = weeks.coerceIn(1, 12)
        val settings = UserSettingsRepository.settings.first()
        val volume = InsightsRepository.weeklyVolume(clampedWeeks)
        val frequency = InsightsRepository.trainingFrequency()
        val bodyParts = InsightsRepository.bodyPartBreakdown(days = 90)
        return trainingSummaryFrom(
            volume = volume,
            frequency = frequency,
            bodyParts = bodyParts,
            unit = settings.weightUnit,
        )
    }

    /**
     * Personal records across exercises (or for one fuzzy-matched [exerciseQuery]).
     * Strength exercises rank by estimated 1RM (using the user's configured formula);
     * duration/distance exercises report best single-set time or distance when queried.
     */
    suspend fun getPersonalRecords(
        exerciseQuery: String? = null,
        limit: Int = 5,
    ): ToolOutput {
        val clampedLimit = limit.coerceIn(1, 12)
        val settings = UserSettingsRepository.settings.first()
        val query = exerciseQuery?.trim()?.takeIf { it.isNotEmpty() }

        if (query == null) {
            val prs = InsightsRepository.personalRecords(limit = clampedLimit)
            return personalRecordsFrom(prs, settings.weightUnit, settings.oneRmFormula.value)
        }

        return when (val match = resolveExercise(query)) {
            is ExerciseMatchResult.NotFound ->
                ToolOutput("No exercise matching \"$query\" was found in the catalog.")
            is ExerciseMatchResult.Ambiguous -> ambiguousOutput(query, match.candidates)
            is ExerciseMatchResult.Matched -> {
                val sets = loadExerciseSets(match.exercise.id, startMillis = 0L, targetUnit = settings.weightUnit)
                exercisePrFrom(match.exercise, sets, settings)
            }
        }
    }

    /**
     * Progression over the last [weeks] weeks for one fuzzy-matched exercise,
     * plus its most recent working sessions for concrete weight/rep recall.
     */
    suspend fun getExerciseProgression(
        exerciseQuery: String,
        weeks: Int = 12,
    ): ToolOutput {
        val clampedWeeks = weeks.coerceIn(2, 24)
        val query = exerciseQuery.trim()
        if (query.isEmpty()) return ToolOutput("Specify an exercise name to check progression.")

        return when (val match = resolveExercise(query)) {
            is ExerciseMatchResult.NotFound ->
                ToolOutput("No exercise matching \"$query\" was found in the catalog.")
            is ExerciseMatchResult.Ambiguous -> ambiguousOutput(query, match.candidates)
            is ExerciseMatchResult.Matched -> {
                val settings = UserSettingsRepository.settings.first()
                val today = WeekMath.todayEpochDay()
                val firstWeekStart = WeekMath.weekStartOnOrBefore(today, settings.weekStart) - 7L * (clampedWeeks - 1)
                val sets = loadExerciseSets(match.exercise.id, startOfDayMillis(firstWeekStart), settings.weightUnit)
                exerciseProgressionFrom(
                    exercise = match.exercise,
                    sets = sets,
                    weeks = clampedWeeks,
                    todayEpochDay = today,
                    settings = settings,
                )
            }
        }
    }

    /**
     * Recent completed workout sessions with routine name, duration, and working sets
     * (warm-ups excluded). Optionally filter by [routineQuery] or trailing [days].
     */
    suspend fun getRecentWorkouts(
        limit: Int = 5,
        routineQuery: String? = null,
        days: Int? = null,
    ): ToolOutput {
        val clampedLimit = limit.coerceIn(1, 10)
        val settings = UserSettingsRepository.settings.first()
        val sinceMillis = days?.let { startOfDayMillis(WeekMath.todayEpochDay() - it.coerceIn(1, 365)) } ?: 0L
        // Fetch extra rows when filtering by routine name in Kotlin so the limit applies post-filter.
        val routineFilter = routineQuery?.trim()?.takeIf { it.isNotEmpty() }
        val fetchLimit = if (routineFilter != null) 40 else clampedLimit
        val sessions = AppDb.db.workoutSessionDao()
            .getRecentCompletedWithRoutine(PROFILE_ID, sinceMillis, fetchLimit)
            .let { rows ->
                if (routineFilter == null) rows
                else rows.filter { it.routineName?.contains(routineFilter, ignoreCase = true) == true }
            }
            .take(clampedLimit)

        if (sessions.isEmpty()) {
            return ToolOutput(
                if (routineFilter != null) "No completed workouts found matching routine \"$routineFilter\"."
                else "No completed workouts logged yet.",
            )
        }

        val details = AppDb.db.workoutSessionDetailDao()
            .getCompletedForSessions(sessions.map { it.id })
        val zone = TimeZone.currentSystemDefault()
        return recentWorkoutsFrom(
            sessions = sessions,
            details = details,
            zone = zone,
            unit = settings.weightUnit,
        )
    }

    /** Completed workouts performed on one specific [epochDay], in chronological order. */
    suspend fun getWorkoutsForDay(epochDay: Long): ToolOutput {
        val settings = UserSettingsRepository.settings.first()
        val zone = TimeZone.currentSystemDefault()
        val startMillis = startOfDayMillis(epochDay, zone)
        val endMillis = startOfDayMillis(epochDay + 1L, zone)
        val sessions = AppDb.db.workoutSessionDao()
            .getCompletedWithRoutineInRange(PROFILE_ID, startMillis, endMillis)
        if (sessions.isEmpty()) {
            return ToolOutput("No completed workouts logged on ${shortDate(epochDay)}.")
        }
        val details = AppDb.db.workoutSessionDetailDao()
            .getCompletedForSessions(sessions.map { it.id })
        return recentWorkoutsFrom(
            sessions = sessions,
            details = details,
            zone = zone,
            unit = settings.weightUnit,
        )
    }

    /**
     * Programs, their routines in order, target exercises/sets, and when each routine
     * was last performed. Optionally narrow to a specific [routineQuery].
     */
    suspend fun getProgramsAndRoutines(routineQuery: String? = null): ToolOutput {
        val db = AppDb.db
        val settings = UserSettingsRepository.settings.first()
        val programs = db.programDao().getAll().map { Program(it.id, it.name) }
        val routines = db.routineDao().getAll().map { Routine(it.id, it.name, it.programId, it.position) }
        if (programs.isEmpty() || routines.isEmpty()) {
            return ToolOutput("No workout programs or routines have been created yet.")
        }

        val filter = routineQuery?.trim()?.takeIf { it.isNotEmpty() }
        val targetRoutines = if (filter != null) {
            routines.filter { it.name.contains(filter, ignoreCase = true) }
        } else {
            routines
        }
        if (targetRoutines.isEmpty()) {
            return ToolOutput("No routines matching \"$filter\" were found.")
        }

        val workSetsByRoutine = mutableMapOf<Long, List<WorkSetEntity>>()
        val allWorkSetIds = mutableListOf<Long>()
        for (routine in targetRoutines) {
            val ws = db.workSetDao().getForRoutine(routine.id)
            workSetsByRoutine[routine.id] = ws
            ws.forEach { allWorkSetIds.add(it.id) }
        }

        val detailsByWorkSet = if (allWorkSetIds.isEmpty()) {
            emptyMap()
        } else {
            db.workSetDetailDao().getForWorkSets(allWorkSetIds).groupBy { it.workSetId }
        }
        val exercisesById = ExerciseRepository.getAll().associateBy { it.id }
        val lastPerformed = db.workoutSessionDao().getLastPerformedByRoutine(PROFILE_ID)

        return programsAndRoutinesFrom(
            programs = programs,
            routines = targetRoutines,
            workSetsByRoutine = workSetsByRoutine,
            detailsByWorkSet = detailsByWorkSet,
            exercisesById = exercisesById,
            lastPerformed = lastPerformed,
            unit = settings.weightUnit,
            zone = TimeZone.currentSystemDefault(),
        )
    }

    /**
     * Searches the exercise catalog by name/abbreviation, body part (including
     * secondary muscles and group aliases like "legs" or "arms"), and equipment.
     * Set [includeDescription] = true when form instructions are needed.
     */
    suspend fun searchExercises(
        query: String? = null,
        bodyPart: String? = null,
        equipment: String? = null,
        includeDescription: Boolean = false,
        limit: Int = 8,
    ): ToolOutput {
        val exercises = ExerciseRepository.getAll()
        val counts = InsightsRepository.completedSetCountsByExercise()
        val matches = ExerciseMatcher.search(
            exercises = exercises,
            query = query,
            bodyPart = bodyPart,
            equipment = equipment,
            trainedSetCounts = counts,
            limit = limit,
        )
        return exerciseSearchFrom(matches, counts, includeDescription)
    }

    /** Body-weight trend, profile measurements, and BMI (computed via configured formula). */
    suspend fun getBodyMetricsAndProfile(days: Int = 90): ToolOutput {
        val clampedDays = days.coerceIn(7, 365)
        val settings = UserSettingsRepository.settings.first()
        val profile = ProfileRepository.profile.first()
        val trend = BodyMetricsRepository.trend(clampedDays)
        return bodyMetricsAndProfileFrom(profile, trend, settings)
    }

    // ---- Shared DB helpers ----

    private suspend fun resolveExercise(query: String): ExerciseMatchResult {
        val exercises = ExerciseRepository.getAll()
        val counts = InsightsRepository.completedSetCountsByExercise()
        return ExerciseMatcher.resolve(query, exercises, counts)
    }

    private suspend fun loadExerciseSets(
        exerciseId: Long,
        startMillis: Long,
        targetUnit: WeightUnit,
    ): List<SetPoint> {
        val zone = TimeZone.currentSystemDefault()
        return AppDb.db.workoutSessionDetailDao()
            .getCompletedSetsForExercise(PROFILE_ID, exerciseId, startMillis)
            .map { it.toSetPoint(zone).convertedTo(targetUnit) }
    }

    // ---- Pure formatting & analytics builders (unit-tested without Room) ----

    internal fun trainingSummaryFrom(
        volume: List<WeeklyVolumePoint>,
        frequency: TrainingFrequency,
        bodyParts: List<BodyPartSlice>,
        unit: WeightUnit,
    ): ToolOutput {
        val unitText = weightUnitLabel(unit)
        val totalSets = volume.sumOf { it.setCount }
        val lines = mutableListOf<String>()

        lines += "Consistency: current streak ${frequency.currentStreakWeeks} weeks, " +
            "longest ${frequency.longestStreakWeeks} weeks; " +
            "${frequency.sessionsLast7Days} sessions in last 7 days, " +
            "${frequency.sessionsLast30Days} in last 30 days " +
            "(avg ${formatNumber(frequency.avgPerWeek12w)}/week over 12 weeks)."

        if (totalSets == 0) {
            lines += "Volume: no weighted/completed sets recorded in the last ${volume.size} weeks."
        } else {
            val latest = volume.last()
            val prior = volume.dropLast(1).takeLast(4).filter { it.sessionCount > 0 }
            val priorAvg = if (prior.isNotEmpty()) prior.map { it.totalVolume }.average() else 0.0
            val vsAvg = if (priorAvg > 0.0) {
                val pct = (latest.totalVolume - priorAvg) / priorAvg * 100.0
                val sign = if (pct >= 0) "+" else ""
                " ($sign${formatNumber(pct, 0)}% vs 4-week avg ${formatNumber(priorAvg, 0)} $unitText)"
            } else {
                ""
            }
            lines += "This 7-day window: ${formatNumber(latest.totalVolume, 0)} $unitText, " +
                "${latest.setCount} sets in ${latest.sessionCount} sessions$vsAvg."
            val activeWeeks = volume.filter { it.setCount > 0 }.takeLast(4)
            if (activeWeeks.size > 1) {
                lines += "Recent weekly volume: " + activeWeeks.joinToString("; ") {
                    "${shortDate(it.weekStartEpochDay)}: ${formatNumber(it.totalVolume, 0)} $unitText (${it.setCount} sets)"
                }
            }
        }

        if (bodyParts.isNotEmpty()) {
            val top = bodyParts.take(6).joinToString(", ") { "${it.bodyPart}: ${it.setCount} sets" }
            lines += "90-day body-part distribution (primary): $top."
        }

        return ToolOutput(
            summary = lines.joinToString("\n"),
            sources = listOf(ChatSourceRef(kind = "progress", label = "Progress overview")),
        )
    }

    internal fun personalRecordsFrom(
        prs: List<dev.balaji.moonrose.logtrain.insights.ExercisePr>,
        unit: WeightUnit,
        formulaName: String,
    ): ToolOutput {
        if (prs.isEmpty()) {
            return ToolOutput("No completed weighted sets found yet to compute personal records.")
        }
        val unitText = weightUnitLabel(unit)
        val lines = mutableListOf("Top personal records (by estimated 1RM, $formulaName formula):")
        val sources = mutableListOf<ChatSourceRef>()
        for (pr in prs) {
            lines += "- ${pr.exerciseName}: best ${formatNumber(pr.weight)} $unitText x ${pr.reps} reps " +
                "(est. 1RM ${formatNumber(pr.est1Rm)} $unitText) on ${shortDate(pr.epochDay)}"
            sources += ChatSourceRef(kind = "exercise", label = pr.exerciseName, exerciseId = pr.exerciseId)
        }
        return ToolOutput(summary = lines.joinToString("\n"), sources = sources)
    }

    internal fun exercisePrFrom(
        exercise: Exercise,
        sets: List<SetPoint>,
        settings: UserSettings,
    ): ToolOutput {
        if (sets.isEmpty()) {
            return ToolOutput(
                summary = "No completed working sets logged for ${exercise.name} yet.",
                sources = listOf(ChatSourceRef(kind = "exercise", label = exercise.name, exerciseId = exercise.id)),
            )
        }
        val unitText = weightUnitLabel(settings.weightUnit)
        val distText = distanceUnitLabel(settings.weightUnit)
        val type = ExerciseType.fromValue(exercise.exerciseType) ?: ExerciseType.STRENGTH
        val lines = mutableListOf("Personal records for ${exercise.name}:")

        val weighted = sets.filter { it.weight > 0.0 && it.reps > 0 }
        if (weighted.isNotEmpty()) {
            val best1Rm = weighted.maxBy { settings.oneRmFormula.estimate(it.weight, it.reps) }
            val est = settings.oneRmFormula.estimate(best1Rm.weight, best1Rm.reps)
            lines += "- Best est. 1RM (${settings.oneRmFormula.value}): ${formatNumber(est)} $unitText " +
                "(${formatNumber(best1Rm.weight)} $unitText x ${best1Rm.reps} reps on ${shortDate(best1Rm.epochDay)})"
            val heaviest = weighted.maxWith(compareBy<SetPoint> { it.weight }.thenBy { it.reps })
            if (heaviest != best1Rm) {
                lines += "- Heaviest set: ${formatNumber(heaviest.weight)} $unitText x ${heaviest.reps} reps " +
                    "on ${shortDate(heaviest.epochDay)}"
            }
        }
        if (type == ExerciseType.DURATION || type == ExerciseType.REPS_DURATION || type == ExerciseType.DISTANCE_DURATION) {
            sets.filter { it.duration > 0 }.maxByOrNull { it.duration }?.let { best ->
                lines += "- Longest duration: ${formatDuration(best.duration)} on ${shortDate(best.epochDay)}"
            }
        }
        if (type == ExerciseType.DISTANCE || type == ExerciseType.REPS_DISTANCE || type == ExerciseType.DISTANCE_DURATION) {
            sets.filter { it.distance > 0.0 }.maxByOrNull { it.distance }?.let { best ->
                lines += "- Longest distance: ${formatNumber(best.distance, 2)} $distText on ${shortDate(best.epochDay)}"
            }
        }
        if (lines.size == 1) {
            lines += "- ${sets.size} completed sets logged, but no weighted/timed/distance records matched."
        }
        return ToolOutput(
            summary = lines.joinToString("\n"),
            sources = listOf(ChatSourceRef(kind = "exercise", label = exercise.name, exerciseId = exercise.id)),
        )
    }

    internal fun exerciseProgressionFrom(
        exercise: Exercise,
        sets: List<SetPoint>,
        weeks: Int,
        todayEpochDay: Long,
        settings: UserSettings,
    ): ToolOutput {
        val ref = ChatSourceRef(kind = "exercise", label = exercise.name, exerciseId = exercise.id)
        if (sets.isEmpty()) {
            return ToolOutput(
                summary = "No completed sets for ${exercise.name} in the last $weeks weeks.",
                sources = listOf(ref),
            )
        }

        val unitText = weightUnitLabel(settings.weightUnit)
        val distText = distanceUnitLabel(settings.weightUnit)
        val type = ExerciseType.fromValue(exercise.exerciseType) ?: ExerciseType.STRENGTH
        val lines = mutableListOf("${exercise.name} (last $weeks weeks, ${sets.size} working sets):")

        val prog = progressionFrom(sets, exercise.name, todayEpochDay, settings.weekStart, settings.oneRmFormula)
        if (prog != null) {
            val trendText = if (prog.plateau) {
                "plateau"
            } else {
                val sign = if (prog.slopePerWeek >= 0) "+" else ""
                "$sign${formatNumber(prog.slopePerWeek)} $unitText/week"
            }
            lines += "Est. 1RM trend: $trendText."
            val recent1Rm = prog.weeklyBest1Rm.takeLast(6)
            lines += "Weekly best est. 1RM: " + recent1Rm.joinToString("; ") {
                "${shortDate(it.epochDay)}: ${formatNumber(it.value)} $unitText"
            }
            val weeklyVol = singleExerciseWeeklyVolume(sets, weeks, todayEpochDay, settings.weekStart)
                .filter { it.value > 0.0 }
                .takeLast(4)
            if (weeklyVol.isNotEmpty()) {
                lines += "Recent weekly volume: " + weeklyVol.joinToString("; ") {
                    "${shortDate(it.epochDay)}: ${formatNumber(it.value, 0)} $unitText"
                }
            }
        }

        if (type == ExerciseType.DURATION || type == ExerciseType.REPS_DURATION || type == ExerciseType.DISTANCE_DURATION) {
            val weeklyDur = singleExerciseWeeklyDuration(sets, weeks, todayEpochDay, settings.weekStart)
                .filter { it.value > 0.0 }
                .takeLast(4)
            if (weeklyDur.isNotEmpty()) {
                lines += "Recent weekly duration: " + weeklyDur.joinToString("; ") {
                    "${shortDate(it.epochDay)}: ${formatDuration(it.value.toInt())}"
                }
            }
        }
        if (type == ExerciseType.DISTANCE || type == ExerciseType.REPS_DISTANCE || type == ExerciseType.DISTANCE_DURATION) {
            val weeklyDist = singleExerciseWeeklyDistance(sets, weeks, todayEpochDay, settings.weekStart)
                .filter { it.value > 0.0 }
                .takeLast(4)
            if (weeklyDist.isNotEmpty()) {
                lines += "Recent weekly distance: " + weeklyDist.joinToString("; ") {
                    "${shortDate(it.epochDay)}: ${formatNumber(it.value, 2)} $distText"
                }
            }
        }

        // Include up to 3 most recent sessions' actual sets for concrete recall.
        val recentSessions = sets.groupBy { it.sessionKey }
            .entries
            .sortedByDescending { it.key }
            .take(3)
        if (recentSessions.isNotEmpty()) {
            lines += "Most recent sessions:"
            for ((_, sessionSets) in recentSessions) {
                val day = shortDate(sessionSets.first().epochDay)
                val formattedSets = sessionSets.joinToString(", ") { s ->
                    formatSetValues(s.weight, s.reps, s.duration, s.distance, type, unitText, distText)
                }
                lines += "- $day: $formattedSets"
            }
        }

        return ToolOutput(summary = lines.joinToString("\n"), sources = listOf(ref))
    }

    internal fun recentWorkoutsFrom(
        sessions: List<RecentCompletedSessionRow>,
        details: List<SessionDetailWithExerciseRow>,
        zone: TimeZone,
        unit: WeightUnit,
    ): ToolOutput {
        val unitText = weightUnitLabel(unit)
        val distText = distanceUnitLabel(unit)
        val detailsBySession = details.groupBy { it.workoutSessionId }
        val lines = mutableListOf("Recent completed workouts (most recent first):")
        val sources = mutableListOf<ChatSourceRef>()
        val seenDays = mutableSetOf<Long>()

        for (session in sessions) {
            val epochDay = millisToEpochDay(session.performedAt, zone)
            val dateLabel = shortDate(epochDay)
            val title = session.routineName ?: "Workout"
            val durationMin = ((session.completedAt - session.performedAt) / 60_000L).coerceAtLeast(0L)
            val durText = if (durationMin > 0L) " (${durationMin} min)" else ""

            val sessionDetails = detailsBySession[session.id].orEmpty()
            val exerciseText = if (sessionDetails.isEmpty()) {
                "no working sets logged"
            } else {
                groupSessionExercises(sessionDetails).joinToString("; ") { (name, rows) ->
                    val type = ExerciseType.fromValue(rows.first().exerciseType) ?: ExerciseType.STRENGTH
                    val setSummary = rows.joinToString(", ") { r ->
                        val base = formatSetValues(
                            weight = r.actualWeight?.let { r.weightUnit.convertWeight(it, unit) } ?: 0.0,
                            reps = r.actualReps ?: 0,
                            duration = r.actualDuration ?: 0,
                            distance = r.actualDistance?.let { r.weightUnit.convertDistance(it, unit) } ?: 0.0,
                            type = type,
                            unitText = unitText,
                            distText = distText,
                        )
                        if (r.setType == SetType.DROP) "$base [drop]" else base
                    }
                    "$name: $setSummary"
                }
            }
            lines += "- $dateLabel · $title$durText — $exerciseText"
            if (seenDays.add(epochDay)) {
                sources += ChatSourceRef(kind = "day", label = "$dateLabel · $title", epochDay = epochDay)
            }
        }

        return ToolOutput(summary = lines.joinToString("\n"), sources = sources)
    }

    internal fun programsAndRoutinesFrom(
        programs: List<Program>,
        routines: List<Routine>,
        workSetsByRoutine: Map<Long, List<WorkSetEntity>>,
        detailsByWorkSet: Map<Long, List<WorkSetDetailEntity>>,
        exercisesById: Map<Long, Exercise>,
        lastPerformed: List<RoutineLastPerformedRow>,
        unit: WeightUnit,
        zone: TimeZone,
    ): ToolOutput {
        val unitText = weightUnitLabel(unit)
        val distText = distanceUnitLabel(unit)
        val programNames = programs.associate { it.id to it.name }
        val lastDayByRoutine = lastPerformed.associate {
            it.routineId to millisToEpochDay(it.lastPerformedAt, zone)
        }

        val lines = mutableListOf("Programs and routines:")
        val sources = mutableListOf<ChatSourceRef>()
        val byProgram = routines.groupBy { it.programId }

        for ((programId, programRoutines) in byProgram) {
            val programName = programNames[programId] ?: "Program"
            lines += "Program: $programName"
            sources += ChatSourceRef(kind = "program", label = programName)
            for (routine in programRoutines.sortedBy { it.position }) {
                val lastText = lastDayByRoutine[routine.id]?.let { "last done ${shortDate(it)}" } ?: "never performed"
                val workSets = workSetsByRoutine[routine.id].orEmpty()
                if (workSets.isEmpty()) {
                    lines += "- ${routine.name} ($lastText): no exercises"
                    continue
                }
                val exSummary = workSets.joinToString("; ") { ws ->
                    val exercise = exercisesById[ws.exerciseId]
                    val name = exercise?.name ?: "Exercise #${ws.exerciseId}"
                    val details = detailsByWorkSet[ws.id].orEmpty().filter { it.setType != SetType.WARMUP }
                    val allDetails = detailsByWorkSet[ws.id].orEmpty()
                    val workingCount = details.size
                    val targetPreview = details.firstOrNull()?.let { d ->
                        val type = ExerciseType.fromValue(exercise?.exerciseType) ?: ExerciseType.STRENGTH
                        formatSetValues(
                            weight = d.targetWeight?.let { d.weightUnit.convertWeight(it, unit) } ?: 0.0,
                            reps = d.targetReps ?: 0,
                            duration = d.targetDuration ?: 0,
                            distance = d.targetDistance?.let { d.weightUnit.convertDistance(it, unit) } ?: 0.0,
                            type = type,
                            unitText = unitText,
                            distText = distText,
                        ).takeIf { it.isNotEmpty() }?.let { " (target $it)" } ?: ""
                    }.orEmpty()
                    val countLabel = if (workingCount > 0) "$workingCount sets" else "${allDetails.size} warmup/empty"
                    "$name $countLabel$targetPreview"
                }
                lines += "- ${routine.name} ($lastText): $exSummary"
            }
        }

        return ToolOutput(summary = lines.joinToString("\n"), sources = sources)
    }

    internal fun exerciseSearchFrom(
        matches: List<Exercise>,
        trainedSetCounts: Map<Long, Int>,
        includeDescription: Boolean,
    ): ToolOutput {
        if (matches.isEmpty()) {
            return ToolOutput("No matching exercises found in the catalog.")
        }
        val lines = mutableListOf("Matching exercises (${matches.size}):")
        val sources = mutableListOf<ChatSourceRef>()
        for (ex in matches) {
            val meta = listOfNotNull(
                ex.bodyPart?.let { "primary: $it" },
                ex.otherBodyParts.takeIf { it.isNotEmpty() }?.let { "secondary: ${it.joinToString(", ")}" },
                ex.equipment?.let { "equipment: $it" },
                ex.exerciseType?.let { "type: $it" },
                trainedSetCounts[ex.id]?.takeIf { it > 0 }?.let { "$it logged sets" },
            ).joinToString(" | ")
            lines += "- ${ex.name} ($meta)"
            if (includeDescription && !ex.description.isNullOrBlank()) {
                val snippet = ex.description.trim().let {
                    if (it.length > 260) it.take(257).trimEnd() + "..." else it
                }
                lines += "  Form: $snippet"
            }
            sources += ChatSourceRef(kind = "exercise", label = ex.name, exerciseId = ex.id)
        }
        return ToolOutput(summary = lines.joinToString("\n"), sources = sources)
    }

    internal fun bodyMetricsAndProfileFrom(
        profile: Profile?,
        trend: BodyWeightTrend?,
        settings: UserSettings,
    ): ToolOutput {
        val unitText = weightUnitLabel(settings.weightUnit)
        val lines = mutableListOf<String>()

        if (profile != null) {
            val parts = buildList {
                add("name: ${profile.name}")
                profile.age?.let { add("age: $it") }
                profile.gender?.let { add("gender: $it") }
                profile.weight?.let {
                    add("current weight: ${formatNumber(settings.weightUnit.fromKilograms(it))} $unitText")
                }
                profile.height?.let {
                    val heightText = when (settings.weightUnit) {
                        WeightUnit.METRIC -> "${formatNumber(it, 0)} cm"
                        WeightUnit.IMPERIAL -> HeightConversion.cmToFeetInches(it).let { fi ->
                            "${fi.feet} ft ${fi.inches} in"
                        }
                    }
                    add("height: $heightText")
                }
            }
            lines += "Profile: ${parts.joinToString(", ")}."

            val weightKg = trend?.latest?.let { settings.weightUnit.toKilograms(it) } ?: profile.weight
            val heightCm = profile.height
            if (weightKg != null && weightKg > 0.0 && heightCm != null && heightCm > 0.0) {
                val bmi = settings.bmiFormula.bmi(weightKg, heightCm)
                val category = settings.bmiClassification.of(bmi).name.lowercase().replace('_', ' ')
                lines += "BMI (${settings.bmiFormula.value}, ${settings.bmiClassification.value}): " +
                    "${formatNumber(bmi)} ($category)."
            }
        } else {
            lines += "Profile: not configured."
        }

        if (trend != null) {
            val sign = if (trend.change30Days >= 0) "+" else ""
            lines += "Body-weight trend (${trend.points.size} entries): latest ${formatNumber(trend.latest)} $unitText " +
                "on ${shortDate(trend.latestEpochDay)}, " +
                "30-day change $sign${formatNumber(trend.change30Days)} $unitText, " +
                "slope ${formatNumber(trend.slopePerWeek, 2)} $unitText/week."
        } else {
            lines += "Body-weight log: no entries recorded in this window."
        }

        return ToolOutput(
            summary = lines.joinToString("\n"),
            sources = listOf(ChatSourceRef(kind = "progress", label = "Body metrics")),
        )
    }

    // ---- Formatting helpers ----

    private fun ambiguousOutput(query: String, candidates: List<Exercise>): ToolOutput {
        val names = candidates.joinToString(", ") { it.name }
        return ToolOutput(
            summary = "Multiple exercises match \"$query\": $names. Ask the user which one they mean or check each by exact name.",
            sources = candidates.map { ChatSourceRef(kind = "exercise", label = it.name, exerciseId = it.id) },
        )
    }

    private fun groupSessionExercises(
        details: List<SessionDetailWithExerciseRow>,
    ): List<Pair<String, List<SessionDetailWithExerciseRow>>> {
        val grouped = mutableListOf<Pair<String, MutableList<SessionDetailWithExerciseRow>>>()
        var currentExerciseId = -1L
        for (row in details) {
            if (row.exerciseId != currentExerciseId) {
                currentExerciseId = row.exerciseId
                grouped += row.exerciseName to mutableListOf(row)
            } else {
                grouped.last().second += row
            }
        }
        return grouped
    }

    private fun formatSetValues(
        weight: Double,
        reps: Int,
        duration: Int,
        distance: Double,
        type: ExerciseType,
        unitText: String,
        distText: String,
    ): String {
        val parts = mutableListOf<String>()
        if (weight > 0.0) parts += "${formatNumber(weight)} $unitText"
        when (type) {
            ExerciseType.STRENGTH -> if (reps > 0) parts += "${reps}r"
            ExerciseType.DURATION -> if (duration > 0) parts += formatDuration(duration)
            ExerciseType.DISTANCE -> if (distance > 0.0) parts += "${formatNumber(distance, 2)} $distText"
            ExerciseType.REPS_DURATION -> {
                if (reps > 0) parts += "${reps}r"
                if (duration > 0) parts += formatDuration(duration)
            }
            ExerciseType.REPS_DISTANCE -> {
                if (reps > 0) parts += "${reps}r"
                if (distance > 0.0) parts += "${formatNumber(distance, 2)} $distText"
            }
            ExerciseType.DISTANCE_DURATION -> {
                if (distance > 0.0) parts += "${formatNumber(distance, 2)} $distText"
                if (duration > 0) parts += formatDuration(duration)
            }
        }
        return when {
            parts.size >= 2 && weight > 0.0 && reps > 0 ->
                "${parts.first()} x ${parts.drop(1).joinToString(" / ")}"
            else -> parts.joinToString(" / ")
        }
    }

    private fun weightUnitLabel(unit: WeightUnit): String = when (unit) {
        WeightUnit.METRIC -> "kg"
        WeightUnit.IMPERIAL -> "lb"
    }

    private fun distanceUnitLabel(unit: WeightUnit): String = when (unit) {
        WeightUnit.METRIC -> "km"
        WeightUnit.IMPERIAL -> "mi"
    }

    internal fun formatDuration(seconds: Int): String {
        val totalMinutes = seconds / 60
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        val remSeconds = seconds % 60
        return when {
            hours > 0 && minutes > 0 -> "${hours}h ${minutes}m"
            hours > 0 -> "${hours}h"
            minutes > 0 && remSeconds > 0 -> "${minutes}m ${remSeconds}s"
            minutes > 0 -> "${minutes} min"
            else -> "${seconds}s"
        }
    }

    internal fun shortDate(epochDay: Long): String {
        val parts = WeekMath.dayParts(epochDay)
        return "${MONTH_ABBREVIATIONS[parts.monthOfYear - 1]} ${parts.dayOfMonth}, ${parts.year}"
    }

    private val MONTH_ABBREVIATIONS =
        listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
}
