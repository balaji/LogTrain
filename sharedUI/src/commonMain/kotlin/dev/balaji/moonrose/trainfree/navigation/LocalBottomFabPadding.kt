package dev.balaji.moonrose.trainfree.navigation

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.dp

/**
 * Extra bottom clearance reserved for scrollable content when the floating
 * workout button overlays the bottom-end corner, so reaching the end of a list
 * or form scrolls final rows and controls clear of the FAB.
 */
val LocalBottomFabPadding = staticCompositionLocalOf { 0.dp }
