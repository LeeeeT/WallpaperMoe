package dev.leeeet.wallpapermoe.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.ui.semantics.clearAndSetSemantics
import dev.leeeet.wallpapermoe.model.Placement
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.leeeet.wallpapermoe.R
import dev.leeeet.wallpapermoe.model.AppData
import dev.leeeet.wallpapermoe.model.Kind
import dev.leeeet.wallpapermoe.model.Source
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudioScreen(model: MoeViewModel, data: AppData, open: (Route) -> Unit) {
    var adding by rememberSaveable { mutableStateOf(false) }
    var deleting by rememberSaveable { mutableStateOf<String?>(null) }
    var order by remember(data.sources) { mutableStateOf(data.sources) }
    val haptic = LocalHapticFeedback.current
    val listState = rememberLazyListState()
    val reorder = rememberReorderableLazyListState(listState) { from, to ->
        val fromIndex = order.indexOfFirst { it.id == from.key }
        val toIndex = order.indexOfFirst { it.id == to.key }
        if (fromIndex >= 0 && toIndex >= 0) {
            order = order.moved(fromIndex, toIndex)
            haptic.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
        }
    }
    val commit = { list: List<Source> -> model.orderSources(list.map(Source::id)) }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.nav_studio)) }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { adding = true },
                icon = { Icon(Icons.Filled.Add, null) },
                text = { Text(stringResource(R.string.action_add_source)) },
                shape = MaterialTheme.shapes.large,
            )
        },
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (order.isEmpty()) {
                EmptyState(Icons.Outlined.AccountTree, stringResource(R.string.studio_empty), Modifier.align(Alignment.Center))
            } else {
                LazyColumn(
                    modifier = Modifier.contentWidth().fillMaxHeight().align(Alignment.TopCenter),
                    state = listState,
                    contentPadding = PaddingValues(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item {
                        Text(
                            pluralStringResource(R.plurals.source_library_count, order.size, order.size),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    val selected = setOfNotNull(data.home.sourceId, data.lock?.sourceId)
                    items(order, key = Source::id) { source ->
                        ReorderableItem(reorder, key = source.id) { dragging ->
                            val elevation by animateDpAsState(if (dragging) 8.dp else 0.dp)
                            val index = order.indexOf(source)
                            val earlier = stringResource(R.string.action_move_earlier)
                            val later = stringResource(R.string.action_move_later)
                            Surface(
                                shape = MaterialTheme.shapes.medium,
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                border = if (source.id in selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                                shadowElevation = elevation,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .semantics {
                                        customActions = listOfNotNull(
                                            CustomAccessibilityAction(earlier) {
                                                commit(order.moved(index, index - 1))
                                                true
                                            }.takeIf { index > 0 },
                                            CustomAccessibilityAction(later) {
                                                commit(order.moved(index, index + 1))
                                                true
                                            }.takeIf { index < order.lastIndex },
                                        )
                                    },
                            ) {
                                StudioRow(
                                    source = source,
                                    data = data,
                                    selected = source.id in selected,
                                    onOpen = { open(Route.Editor(source.id, source.recipe.kind)) },
                                    onPlace = { model.place(source.id, it) },
                                    onDelete = { deleting = source.id },
                                    handle = {
                                        Icon(
                                            Icons.Filled.DragHandle,
                                            null,
                                            Modifier
                                                .draggableHandle(
                                                    onDragStarted = { haptic.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate) },
                                                    onDragStopped = {
                                                        haptic.performHapticFeedback(HapticFeedbackType.GestureEnd)
                                                        commit(order)
                                                    },
                                                )
                                                .clearAndSetSemantics {}
                                                .padding(12.dp),
                                        )
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    deleting?.let(data::source)?.let { source ->
        DeleteSourceDialog(source, data, onDelete = { model.deleteSource(source.id) }, onDismiss = { deleting = null })
    }
    if (adding) {
        KindSheet(
            title = stringResource(R.string.source_picker_title),
            onPick = {
                adding = false
                open(Route.Editor(null, it))
            },
            onDismiss = { adding = false },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StudioRow(
    source: Source,
    data: AppData,
    selected: Boolean,
    onOpen: () -> Unit,
    onPlace: (Placement) -> Unit,
    onDelete: () -> Unit,
    handle: @Composable () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    var placing by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier
                .combinedClickable(
                    onClick = onOpen,
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        placing = true
                    },
                )
                .semantics { this.selected = selected }
                .padding(start = 14.dp, top = 14.dp, end = 4.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SourceGlyph(source.recipe.kind)
            Column(Modifier.weight(1f).padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(source.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    stringResource(source.recipe.kind.label),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    source.summary(data),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            handle()
            IconButton(onClick = onDelete) { Icon(Icons.Filled.Close, stringResource(R.string.action_delete)) }
        }
        DropdownMenu(expanded = placing, onDismissRequest = { placing = false }) {
            for ((placement, label) in listOf(
                Placement.HOME to R.string.studio_use_home,
                Placement.LOCK to R.string.studio_use_lock,
                Placement.BOTH to R.string.studio_use_both,
            )) {
                DropdownMenuItem(
                    text = { Text(stringResource(label)) },
                    onClick = {
                        placing = false
                        onPlace(placement)
                    },
                )
            }
        }
    }
}

private val creatable = listOf(
    Kind.KEEP_READY,
    Kind.COMBINE,
    Kind.FAVORITES,
    Kind.FOLDER,
    Kind.WALLHAVEN,
    Kind.NEKOS_BEST,
    Kind.PANDORA,
)

@Composable
fun KindSheet(title: String, onPick: (Kind) -> Unit, onDismiss: () -> Unit) {
    MoeSheet(title, onDismiss) {
        SheetList(Arrangement.spacedBy(8.dp)) {
            items(creatable) { kind ->
                SourceRow(kind, stringResource(kind.label), stringResource(kind.pickerDetail), onClick = { onPick(kind) })
            }
        }
    }
}
