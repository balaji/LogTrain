package dev.balaji.moonrose.trainfree.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.balaji.moonrose.trainfree.model.SetType
import dev.balaji.moonrose.trainfree.sharedui.resources.*
import org.jetbrains.compose.resources.stringResource

/**
 * The leading cell of a set row: a working-set number, or a marker for the
 * warm-up and drop sets that don't take one. Tapping it opens the set-type
 * menu — the only way to change a set's type, in both the routine editor and a
 * live session.
 */
@Composable
internal fun SetTypeCell(
    setType: SetType,
    workingSetNumber: Int,
    onSetTypeChange: (SetType) -> Unit,
    cellWidth: Dp,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val menuLabel = stringResource(Res.string.set_type)

    Box(modifier = modifier) {
        Text(
            setTypeMarker(setType) ?: "$workingSetNumber",
            modifier = Modifier
                .width(cellWidth)
                .clickable { expanded = true }
                .semantics { contentDescription = menuLabel }
                .padding(vertical = 8.dp),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
            color = setTypeColor(setType),
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            SetType.entries.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Text(
                            setTypeLabel(option),
                            color = if (option == setType) {
                                setTypeColor(option)
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        )
                    },
                    onClick = {
                        expanded = false
                        onSetTypeChange(option)
                    },
                )
            }
        }
    }
}

@Composable
private fun setTypeColor(setType: SetType): Color = when (setType) {
    SetType.NORMAL -> MaterialTheme.colorScheme.onSurface
    SetType.WARMUP -> MaterialTheme.colorScheme.tertiary
    SetType.DROP -> MaterialTheme.colorScheme.secondary
}
