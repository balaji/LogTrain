package dev.balaji.moonrose.logtrain.insights.llm

import dev.balaji.moonrose.logtrain.calendar.WeekMath
import dev.balaji.moonrose.logtrain.db.BodyMetricsRepository
import dev.balaji.moonrose.logtrain.db.InsightsRepository
import dev.balaji.moonrose.logtrain.db.ProfileRepository
import dev.balaji.moonrose.logtrain.db.UserSettingsRepository
import dev.balaji.moonrose.logtrain.insights.InsightMath.formatNumber
import dev.balaji.moonrose.logtrain.model.WeightUnit
import kotlinx.coroutines.flow.first

data class InsightToolSpec(val name: String, val description: String)

/**
 * Executes the chat tools against the deterministic insight repositories.
 * Kotlin is the source of truth for tool behavior; platform providers
 * declare thin schema wrappers (FoundationModels Tool structs on iOS) that
 * delegate here by name. Results are compact line-oriented plain text so a
 * ~4K-token on-device context is never at risk, and every number carries
 * its unit label so the model has nothing to compute.
 */
object InsightToolExecutor {
    val specs: List<InsightToolSpec> = listOf(
        InsightToolSpec("getWeeklyVolume", "Total training volume per week for recent weeks."),
        InsightToolSpec("getPersonalRecords", "Best set per exercise by estimated one-rep max."),
        InsightToolSpec("getExerciseProgression", "Weekly best estimated 1RM trend for one exercise."),
        InsightToolSpec("getTrainingFrequency", "Workout streaks and session counts."),
        InsightToolSpec("getBodyWeightTrend", "Logged body-weight entries and trend."),
        InsightToolSpec("getProfileSummary", "The user's profile: age, gender, height, current weight."),
    )

    /** Args arrive as strings (blank = absent); unknown names return a correction hint. */
    suspend fun call(name: String, args: Map<String, String>): String = when (name) {
        "getWeeklyVolume" -> weeklyVolume(args["weeks"]?.toIntOrNull() ?: 8)
        "getPersonalRecords" -> personalRecords(args["exerciseName"]?.takeIf { it.isNotBlank() })
        "getExerciseProgression" -> progression(args["exerciseName"].orEmpty())
        "getTrainingFrequency" -> frequency()
        "getBodyWeightTrend" -> bodyWeight(args["days"]?.toIntOrNull() ?: 90)
        "getProfileSummary" -> profileSummary()
        else -> "Unknown tool '$name'."
    }

    /** Compact system-prompt block: who the user is and what data exists. */
    suspend fun chatInstructions(): String {
        val unit = unitLabel()
        val profile = ProfileRepository.profile.first()
        val freq = InsightsRepository.trainingFrequency()
        val exercises = InsightsRepository.trainedExerciseNames(limit = 10)
        return buildString {
            append("User: ")
            if (profile != null) {
                append(profile.name)
                profile.age?.let { append(", age $it") }
                profile.gender?.let { append(", $it") }
                profile.weight?.let { append(", ${formatNumber(it)} $unit") }
            } else {
                append("no profile yet")
            }
            append(". Weights are in $unit.\n")
            append("Data: ${freq.sessionsLast30Days} workouts in the last 30 days.")
            if (exercises.isNotEmpty()) {
                append(" Exercises with history: ${exercises.joinToString(", ")}.")
            }
        }
    }

    private suspend fun unitLabel(): String =
        when (UserSettingsRepository.settings.first().weightUnit) {
            WeightUnit.METRIC -> "kg"
            WeightUnit.IMPERIAL -> "lb"
        }

    private suspend fun weeklyVolume(weeks: Int): String {
        val unit = unitLabel()
        val points = InsightsRepository.weeklyVolume(weeks.coerceIn(1, 12))
        if (points.all { it.setCount == 0 }) return "No completed workouts in this period."
        return points.joinToString("\n") { p ->
            "week of ${dayTitle(p.weekStartEpochDay)}: " +
                "${formatNumber(p.totalVolume, 0)} $unit, ${p.setCount} sets, ${p.sessionCount} sessions"
        }
    }

    private suspend fun personalRecords(exerciseName: String?): String {
        val unit = unitLabel()
        val prs = InsightsRepository.personalRecords(limit = 5, exerciseName = exerciseName)
        if (prs.isEmpty()) return noDataFor(exerciseName)
        return prs.joinToString("\n") { pr ->
            "${pr.exerciseName}: ${formatNumber(pr.weight)} $unit x ${pr.reps} " +
                "(est. 1RM ${formatNumber(pr.est1Rm)} $unit) on ${dayTitle(pr.epochDay)}"
        }
    }

    private suspend fun progression(exerciseName: String): String {
        val unit = unitLabel()
        val p = InsightsRepository.exerciseProgression(exerciseName)
            ?: return noDataFor(exerciseName)
        val series = p.weeklyBest1Rm.joinToString("\n") {
            "week of ${dayTitle(it.epochDay)}: est. 1RM ${formatNumber(it.value)} $unit"
        }
        val trend = when {
            p.plateau -> "trend: plateau"
            else -> {
                val sign = if (p.slopePerWeek >= 0) "+" else ""
                "trend: $sign${formatNumber(p.slopePerWeek)} $unit/week"
            }
        }
        return "${p.exerciseName}\n$series\n$trend"
    }

    private suspend fun frequency(): String {
        val f = InsightsRepository.trainingFrequency()
        return "current streak: ${f.currentStreakWeeks} weeks\n" +
            "longest streak: ${f.longestStreakWeeks} weeks\n" +
            "sessions last 7 days: ${f.sessionsLast7Days}\n" +
            "sessions last 30 days: ${f.sessionsLast30Days}\n" +
            "average sessions/week (12 weeks): ${formatNumber(f.avgPerWeek12w)}"
    }

    private suspend fun bodyWeight(days: Int): String {
        val unit = unitLabel()
        val trend = BodyMetricsRepository.trend(days.coerceIn(7, 180))
            ?: return "No body-weight entries logged in this period."
        val sign = if (trend.change30Days >= 0) "+" else ""
        return "latest: ${formatNumber(trend.latest)} $unit " +
            "on ${dayTitle(trend.latestEpochDay)}\n" +
            "30-day change: $sign${formatNumber(trend.change30Days)} $unit\n" +
            "trend: ${formatNumber(trend.slopePerWeek, 2)} $unit/week over ${trend.points.size} entries"
    }

    private suspend fun profileSummary(): String {
        val unit = unitLabel()
        val p = ProfileRepository.profile.first() ?: return "No profile has been set up."
        return listOfNotNull(
            "name: ${p.name}",
            p.age?.let { "age: $it" },
            p.gender?.let { "gender: $it" },
            p.height?.let { "height: ${formatNumber(it)}" },
            p.weight?.let { "current weight: ${formatNumber(it)} $unit" },
        ).joinToString("\n")
    }

    private suspend fun noDataFor(exerciseName: String?): String {
        val known = InsightsRepository.trainedExerciseNames(limit = 10)
        val prefix = if (exerciseName.isNullOrBlank()) {
            "No completed sets logged yet."
        } else {
            "No data for '$exerciseName'."
        }
        return if (known.isEmpty()) prefix else "$prefix Exercises with history: ${known.joinToString(", ")}."
    }
}

/**
 * English "Jul 7, 2026", fine here because this text is consumed by the
 * language model rather than shown in the UI; the UI layers localize dates
 * themselves from [WeekMath.dayParts].
 */
private fun dayTitle(epochDay: Long): String {
    val parts = WeekMath.dayParts(epochDay)
    return "${MONTH_ABBREVIATIONS[parts.monthOfYear - 1]} ${parts.dayOfMonth}, ${parts.year}"
}

private val MONTH_ABBREVIATIONS =
    listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
