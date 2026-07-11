package dev.balaji.moonrose.logtrain.insights.llm

import java.util.Locale

internal actual fun currentLanguageTag(): String = Locale.getDefault().language
