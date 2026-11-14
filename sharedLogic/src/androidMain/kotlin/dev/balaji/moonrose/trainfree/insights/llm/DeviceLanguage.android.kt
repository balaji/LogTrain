package dev.balaji.moonrose.trainfree.insights.llm

import java.util.Locale

internal actual fun currentLanguageTag(): String =
    Locale.getDefault().language.takeIf { it.isNotBlank() && it != "und" } ?: "en"
