package dev.balaji.moonrose.trainfree.screens

import androidx.compose.runtime.Composable
import dev.balaji.moonrose.trainfree.model.SetField
import dev.balaji.moonrose.trainfree.model.WeightUnit
import dev.balaji.moonrose.trainfree.sharedui.resources.*
import org.jetbrains.compose.resources.stringResource

/**
 * Header label for one measured-value column of a set row, shared by the
 * routine editor and the session screen. Distance follows the weight unit
 * setting — label only, values are stored as entered, matching how the
 * weight column already treats kg/lb.
 */
@Composable
internal fun setFieldColumnLabel(field: SetField, weightUnit: WeightUnit): String = when (field) {
    SetField.REPS -> stringResource(Res.string.column_reps)
    SetField.DURATION -> stringResource(Res.string.column_time)
    SetField.DISTANCE -> stringResource(
        when (weightUnit) {
            WeightUnit.METRIC -> Res.string.column_km
            WeightUnit.IMPERIAL -> Res.string.column_mi
        },
    )
}
