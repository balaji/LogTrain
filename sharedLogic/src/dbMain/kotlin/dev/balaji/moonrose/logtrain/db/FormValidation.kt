package dev.balaji.moonrose.logtrain.db

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
