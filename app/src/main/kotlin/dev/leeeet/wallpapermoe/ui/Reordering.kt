package dev.leeeet.wallpapermoe.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import dev.leeeet.wallpapermoe.R

internal fun <T> List<T>.moved(from: Int, to: Int): List<T> {
    if (from == to) return this
    return toMutableList().apply { add(to, removeAt(from)) }
}

@Composable
internal fun Modifier.reorderActions(
    onMoveEarlier: (() -> Unit)?,
    onMoveLater: (() -> Unit)?,
): Modifier {
    val earlier = stringResource(R.string.action_move_earlier)
    val later = stringResource(R.string.action_move_later)
    return semantics {
        customActions = buildList {
            onMoveEarlier?.let { move ->
                add(
                    CustomAccessibilityAction(earlier) {
                        move()
                        true
                    },
                )
            }
            onMoveLater?.let { move ->
                add(
                    CustomAccessibilityAction(later) {
                        move()
                        true
                    },
                )
            }
        }
    }
}
