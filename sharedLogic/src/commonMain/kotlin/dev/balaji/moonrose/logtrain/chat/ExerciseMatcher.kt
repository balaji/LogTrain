package dev.balaji.moonrose.logtrain.chat

import dev.balaji.moonrose.logtrain.model.Exercise

sealed interface ExerciseMatchResult {
    data class Matched(val exercise: Exercise) : ExerciseMatchResult
    data class Ambiguous(val candidates: List<Exercise>) : ExerciseMatchResult
    data object NotFound : ExerciseMatchResult
}

/**
 * Pure-Kotlin fuzzy exercise matcher and catalog filter. Resolves casual
 * gym phrasing ("bench", "OHP", "RDL", "squat", "legs") against [Exercise]
 * catalog entries, breaking ties using the user's actual training history.
 */
object ExerciseMatcher {
    private val ABBREVIATIONS = mapOf(
        "bp" to listOf("bench", "press"),
        "ohp" to listOf("overhead", "press"),
        "rdl" to listOf("romanian", "deadlift"),
        "sldl" to listOf("stiff", "leg", "deadlift"),
        "dl" to listOf("deadlift"),
        "bb" to listOf("barbell"),
        "db" to listOf("dumbbell"),
        "kb" to listOf("kettlebell"),
        "ez" to listOf("ez", "bar"),
        "bw" to listOf("body", "weight"),
        "bodyweight" to listOf("body", "weight"),
        "pullup" to listOf("pull", "up"),
        "pullups" to listOf("pull", "up"),
        "chinup" to listOf("chin", "up"),
        "chinups" to listOf("chin", "up"),
        "pushup" to listOf("push", "up"),
        "pushups" to listOf("push", "up"),
    )

    private val EQUIPMENT_PREFIXES = listOf(
        "barbell ",
        "dumbbell ",
        "cable ",
        "machine ",
        "smith machine ",
        "kettlebell ",
        "bodyweight ",
        "resistance band ",
        "band ",
    )

    private val EQUIPMENT_ALIASES = mapOf(
        "bb" to "barbell",
        "db" to "dumbbell",
        "dumbbells" to "dumbbell",
        "kb" to "kettlebell",
        "kettlebells" to "kettlebell",
        "bw" to "body weight",
        "bodyweight" to "body weight",
        "cables" to "cable",
        "bands" to "band",
        "smith" to "smith machine",
    )

    private val BODY_PART_GROUPS = mapOf(
        "legs" to setOf("thighs", "quads", "hamstrings", "glutes", "calves"),
        "quads" to setOf("thighs"),
        "quadriceps" to setOf("thighs"),
        "arms" to setOf("biceps", "triceps", "forearms"),
        "abs" to setOf("core"),
        "upper body" to setOf("chest", "back", "shoulders", "biceps", "triceps", "forearms"),
        "lower body" to setOf("thighs", "hamstrings", "glutes", "calves"),
        "posterior chain" to setOf("back", "hamstrings", "glutes"),
    )

    fun resolve(
        query: String,
        exercises: List<Exercise>,
        trainedSetCounts: Map<Long, Int> = emptyMap(),
    ): ExerciseMatchResult {
        val rawNorm = normalize(query)
        if (rawNorm.isEmpty()) return ExerciseMatchResult.NotFound

        val queryTokens = expandTokens(rawNorm)
        val expandedQuery = queryTokens.joinToString(" ")

        val scored = exercises.mapNotNull { exercise ->
            val score = scoreExercise(
                exercise = exercise,
                rawNorm = rawNorm,
                expandedQuery = expandedQuery,
                queryTokens = queryTokens,
            )
            if (score <= 0) null else ScoredExercise(exercise, score, trainedSetCounts[exercise.id] ?: 0)
        }.sortedWith(
            compareByDescending<ScoredExercise> { it.score }
                .thenByDescending { it.trainedSets }
                .thenBy { it.exercise.name.length }
                .thenBy { it.exercise.name },
        )

        if (scored.isEmpty()) return ExerciseMatchResult.NotFound
        val top = scored.first()
        if (top.score >= 1000) return ExerciseMatchResult.Matched(top.exercise)

        // When multiple variants score comparably (e.g. "Bench Press" matching
        // Barbell & Dumbbell Bench Press), prefer the only one the user trains.
        val close = scored.filter { top.score - it.score <= 60 }
        if (close.size == 1) return ExerciseMatchResult.Matched(top.exercise)

        val trainedClose = close.filter { it.trainedSets > 0 }
        if (trainedClose.size == 1) return ExerciseMatchResult.Matched(trainedClose.first().exercise)

        // If the top candidate has clearly higher training volume than others in
        // a strict tie (e.g. 50 sets vs 2), surface Ambiguous when >=2 trained
        // candidates exist so the user/model sees both rather than guessing silently.
        return ExerciseMatchResult.Ambiguous(close.take(5).map { it.exercise })
    }

    fun search(
        exercises: List<Exercise>,
        query: String? = null,
        bodyPart: String? = null,
        equipment: String? = null,
        exerciseType: String? = null,
        trainedSetCounts: Map<Long, Int> = emptyMap(),
        limit: Int = 8,
    ): List<Exercise> {
        val targetParts = bodyPart?.let(::expandBodyParts).orEmpty()
        val targetEquip = equipment?.let(::normalizeEquipment)
        val targetType = exerciseType?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
        val rawNorm = query?.let(::normalize).orEmpty()
        val queryTokens = if (rawNorm.isEmpty()) emptyList() else expandTokens(rawNorm)
        val expandedQuery = queryTokens.joinToString(" ")

        return exercises.mapNotNull { ex ->
            if (targetType != null && ex.exerciseType?.lowercase() != targetType) return@mapNotNull null

            if (targetEquip != null) {
                val exEquip = ex.equipment?.let(::normalizeEquipment)
                if (exEquip != targetEquip) return@mapNotNull null
            }

            var partScore = 0
            if (targetParts.isNotEmpty()) {
                val primary = ex.bodyPart?.lowercase()
                val secondary = ex.otherBodyParts.map { it.lowercase() }.toSet()
                partScore = when {
                    primary != null && primary in targetParts -> 200
                    secondary.any { it in targetParts } -> 80
                    else -> return@mapNotNull null
                }
            }

            var queryScore = 0
            if (queryTokens.isNotEmpty()) {
                queryScore = scoreExercise(ex, rawNorm, expandedQuery, queryTokens)
                if (queryScore <= 0) return@mapNotNull null
            }

            ScoredExercise(ex, partScore + queryScore, trainedSetCounts[ex.id] ?: 0)
        }
            .sortedWith(
                compareByDescending<ScoredExercise> { it.score }
                    .thenByDescending { it.trainedSets }
                    .thenBy { it.exercise.name },
            )
            .take(limit.coerceIn(1, 25))
            .map { it.exercise }
    }

    private fun scoreExercise(
        exercise: Exercise,
        rawNorm: String,
        expandedQuery: String,
        queryTokens: List<String>,
    ): Int {
        val nameNorm = normalize(exercise.name)
        if (nameNorm == rawNorm || nameNorm == expandedQuery) return 1000

        val strippedName = stripEquipmentPrefix(nameNorm)
        if (strippedName == rawNorm || strippedName == expandedQuery) return 900

        val nameTokens = nameNorm.split(' ').filter { it.isNotEmpty() }
        if (nameTokens.isEmpty()) return 0

        if (containsSubphrase(nameNorm, expandedQuery) || containsSubphrase(nameNorm, rawNorm)) {
            val startsBonus = if (nameNorm.startsWith(expandedQuery) || nameNorm.startsWith(rawNorm)) 40 else 0
            return 720 + startsBonus - (nameTokens.size - queryTokens.size).coerceAtLeast(0) * 5
        }

        var matchedTokens = 0
        var exactTokens = 0
        for (qt in queryTokens) {
            val exact = nameTokens.any { nt -> tokenMatchesExact(qt, nt) }
            if (exact) {
                matchedTokens++
                exactTokens++
            } else if (nameTokens.any { nt -> nt.startsWith(qt) || (qt.length >= 3 && nt.contains(qt)) }) {
                matchedTokens++
            }
        }
        if (matchedTokens == 0) return 0
        // Require every token in multi-word queries to match at least partially,
        // otherwise "bench press" would match "Leg Press".
        if (matchedTokens < queryTokens.size) return 0

        val coverageBonus = (exactTokens * 40) / nameTokens.size.coerceAtLeast(1)
        return 400 + exactTokens * 60 + matchedTokens * 30 + coverageBonus
    }

    private fun tokenMatchesExact(queryToken: String, nameToken: String): Boolean {
        if (queryToken == nameToken) return true
        // Simple plural normalization ("squats" <-> "squat", "curls" <-> "curl").
        if (queryToken.endsWith("s") && queryToken.dropLast(1) == nameToken) return true
        if (nameToken.endsWith("s") && nameToken.dropLast(1) == queryToken) return true
        return false
    }

    private fun containsSubphrase(haystack: String, needle: String): Boolean {
        if (needle.isEmpty()) return false
        return " $haystack ".contains(" $needle ")
    }

    private fun stripEquipmentPrefix(nameNorm: String): String {
        for (prefix in EQUIPMENT_PREFIXES) {
            if (nameNorm.startsWith(prefix)) return nameNorm.removePrefix(prefix)
        }
        return nameNorm
    }

    private fun expandTokens(normalized: String): List<String> =
        normalized.split(' ')
            .filter { it.isNotEmpty() }
            .flatMap { token -> ABBREVIATIONS[token] ?: listOf(token) }

    private fun expandBodyParts(raw: String): Set<String> {
        val norm = normalize(raw)
        if (norm.isEmpty()) return emptySet()
        return BODY_PART_GROUPS[norm] ?: setOf(norm)
    }

    private fun normalizeEquipment(raw: String): String {
        val norm = normalize(raw)
        return EQUIPMENT_ALIASES[norm] ?: norm
    }

    internal fun normalize(text: String): String {
        val sb = StringBuilder(text.length)
        var lastWasSpace = true
        for (ch in text.lowercase()) {
            when {
                ch.isLetterOrDigit() -> {
                    sb.append(ch)
                    lastWasSpace = false
                }
                !lastWasSpace -> {
                    sb.append(' ')
                    lastWasSpace = true
                }
            }
        }
        return sb.toString().trim()
    }

    private data class ScoredExercise(
        val exercise: Exercise,
        val score: Int,
        val trainedSets: Int,
    )
}
