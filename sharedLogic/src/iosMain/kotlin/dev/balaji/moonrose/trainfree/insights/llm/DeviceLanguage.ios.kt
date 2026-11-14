package dev.balaji.moonrose.trainfree.insights.llm

import platform.Foundation.NSLocale
import platform.Foundation.currentLocale
import platform.Foundation.languageCode

internal actual fun currentLanguageTag(): String =
    NSLocale.currentLocale.languageCode.takeIf { it.isNotBlank() } ?: "en"
