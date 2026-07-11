package dev.balaji.moonrose.logtrain.model

/**
 * What a set counts as. Mutually exclusive by construction — a set is warm-up
 * or drop or neither, never two at once.
 *
 * Warm-up sets are excluded from volume, PRs and est-1RM; drop sets are real
 * work at a reduced load and count toward all of them. Neither consumes a
 * working-set number in the UI: they render as [key]-derived markers instead.
 *
 * [key] is the language-neutral value persisted in `set_type` and matched in
 * SQL — never display it; the shells map it to localized text.
 */
enum class SetType(val key: String) {
    NORMAL("normal"),
    WARMUP("warmup"),
    DROP("drop"),
    ;

    companion object {
        /** Unknown values (a downgraded app writing a future type) read as normal. */
        fun fromKey(key: String): SetType = entries.firstOrNull { it.key == key } ?: NORMAL
    }
}
