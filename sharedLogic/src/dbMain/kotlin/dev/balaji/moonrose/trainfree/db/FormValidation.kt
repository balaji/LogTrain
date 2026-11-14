package dev.balaji.moonrose.trainfree.db

const val MAX_PROGRAM_NAME_LENGTH = 50

/**
 * Input filter for program and routine names: letters, digits and spaces
 * only, capped at [MAX_PROGRAM_NAME_LENGTH] characters. Public so the native
 * SwiftUI name prompts can apply the same filtering as the Compose dialogs.
 */
fun filterProgramName(value: String): String =
    value.filter { it.isLetterOrDigit() || it == ' ' }.take(MAX_PROGRAM_NAME_LENGTH)

/**
 * Save-time backstop for program and routine names, applied in
 * [ProgramRepository] so every entry path is covered. Returns the trimmed
 * name or throws with a user-readable message (both UIs surface repository
 * failures in an error dialog).
 */
internal fun requireValidProgramName(name: String): String {
    val trimmed = name.trim()
    require(trimmed.isNotEmpty()) { "Name is required" }
    require(trimmed.length <= MAX_PROGRAM_NAME_LENGTH) {
        "Name must be at most $MAX_PROGRAM_NAME_LENGTH characters"
    }
    require(trimmed.all { it.isLetterOrDigit() || it == ' ' }) {
        "Name can contain only letters and numbers"
    }
    return trimmed
}

/** Input filter for non-negative integer fields (reps, age). */
internal fun String.filterDigits(): String = filter(Char::isDigit)

/** Input filter for non-negative decimal fields (weight, height): digits plus at most one dot. */
internal fun String.filterDecimal(): String {
    val filtered = filter { it.isDigit() || it == '.' }
    val firstDot = filtered.indexOf('.')
    return if (firstDot == -1) {
        filtered
    } else {
        filtered.substring(0, firstDot + 1) + filtered.substring(firstDot + 1).replace(".", "")
    }
}

internal const val MAX_DURATION_HOURS = 23

// Longest sensible distance, e.g. "9999.99".
internal const val MAX_DISTANCE_CHARS = 7

/**
 * Input filter for the single h:mm duration box. The keypad only offers
 * digits; the colon is inserted automatically before the last two digits
 * once more than two are typed, so "130" renders as "1:30" and one or two
 * bare digits read as minutes. Minutes are clamped to 59 and hours to
 * [MAX_DURATION_HOURS].
 */
internal fun String.filterDurationText(): String {
    val digits = filter(Char::isDigit).take(4)
    if (digits.length <= 2) return digits
    val hours = digits.dropLast(2).toInt().coerceAtMost(MAX_DURATION_HOURS)
    val minutes = digits.takeLast(2).toInt().coerceAtMost(59)
    return "$hours:${minutes.toString().padStart(2, '0')}"
}

/** Input filter for distance: decimal like weight, length-capped rather than reformatted mid-typing. */
internal fun String.filterDistance(): String = filterDecimal().take(MAX_DISTANCE_CHARS)

/** Seconds for an h:mm box value (bare digits are minutes), or null when blank. */
internal fun parseDurationSeconds(text: String): Int? {
    if (text.isBlank()) return null
    val colon = text.indexOf(':')
    if (colon == -1) return (text.toIntOrNull() ?: 0) * 60
    val hours = text.substring(0, colon).toIntOrNull() ?: 0
    val minutes = text.substring(colon + 1).toIntOrNull() ?: 0
    return hours * 3600 + minutes * 60
}

/**
 * Stored seconds back into h:mm box text; blank for null or zero so the box
 * shows its placeholder. Sub-hour values render as bare minutes, matching
 * what typing them produces; a sub-minute remainder (nothing writes one
 * today) is dropped.
 */
internal fun formatDurationText(seconds: Int?): String {
    if (seconds == null) return ""
    val totalMinutes = seconds / 60
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours > 0 -> "$hours:${minutes.toString().padStart(2, '0')}"
        minutes > 0 -> minutes.toString()
        else -> ""
    }
}

/** "1:05"-style display of a stored duration, for read-only summaries. */
internal fun formatDurationHhMm(seconds: Int): String {
    val totalMinutes = seconds / 60
    return "${totalMinutes / 60}:${(totalMinutes % 60).toString().padStart(2, '0')}"
}
