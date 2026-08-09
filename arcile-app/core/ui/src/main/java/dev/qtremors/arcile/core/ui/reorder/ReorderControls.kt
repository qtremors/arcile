package dev.qtremors.arcile.core.ui.reorder

import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import dev.qtremors.arcile.core.ui.R

private enum class ReorderFocusTarget {
    Up,
    Down
}

private data class PendingReorderFocus(
    val target: ReorderFocusTarget,
    val positionBeforeMove: Int
)

/**
 * Stateless move controls for an item in an ordered collection.
 *
 * The caller owns and updates the collection. This composable owns only transient focus so
 * keyboard and accessibility users stay on the action they invoked after the item moves.
 */
@Composable
fun ReorderControls(
    itemLabel: String,
    position: Int,
    itemCount: Int,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    modifier: Modifier = Modifier
) {
    require(position in 0 until itemCount) {
        "position ($position) must be within an item count of $itemCount"
    }

    val upFocusRequester = remember { FocusRequester() }
    val downFocusRequester = remember { FocusRequester() }
    var focusAfterMove by remember { mutableStateOf<PendingReorderFocus?>(null) }
    val positionDescription = stringResource(
        R.string.reorder_position,
        itemLabel,
        position + 1,
        itemCount
    )

    LaunchedEffect(position, focusAfterMove) {
        val pendingFocus = focusAfterMove ?: return@LaunchedEffect
        if (position == pendingFocus.positionBeforeMove) return@LaunchedEffect
        when (pendingFocus.target) {
            ReorderFocusTarget.Up -> {
                if (position > 0) upFocusRequester.requestFocus()
                else downFocusRequester.requestFocus()
            }
            ReorderFocusTarget.Down -> {
                if (position < itemCount - 1) downFocusRequester.requestFocus()
                else upFocusRequester.requestFocus()
            }
        }
        focusAfterMove = null
    }

    Row(
        modifier = modifier.semantics {
            stateDescription = positionDescription
            liveRegion = LiveRegionMode.Polite
        }
    ) {
        IconButton(
            onClick = {
                focusAfterMove = PendingReorderFocus(ReorderFocusTarget.Up, position)
                onMoveUp()
            },
            enabled = position > 0,
            modifier = Modifier.focusRequester(upFocusRequester)
        ) {
            Icon(
                imageVector = Icons.Default.ArrowUpward,
                contentDescription = stringResource(R.string.reorder_move_up, itemLabel)
            )
        }
        IconButton(
            onClick = {
                focusAfterMove = PendingReorderFocus(ReorderFocusTarget.Down, position)
                onMoveDown()
            },
            enabled = position < itemCount - 1,
            modifier = Modifier.focusRequester(downFocusRequester)
        ) {
            Icon(
                imageVector = Icons.Default.ArrowDownward,
                contentDescription = stringResource(R.string.reorder_move_down, itemLabel)
            )
        }
    }
}
