@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)

package dev.leeeet.wallpapermoe.ui

import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.leeeet.wallpapermoe.R
import dev.leeeet.wallpapermoe.source.CombineMode
import dev.leeeet.wallpapermoe.source.SelectionMode
import dev.leeeet.wallpapermoe.source.SourceRecipe
import dev.leeeet.wallpapermoe.source.SourceDefinition
import dev.leeeet.wallpapermoe.source.SourceId
import dev.leeeet.wallpapermoe.source.SourceKind
import dev.leeeet.wallpapermoe.source.SourceLibrary
import dev.leeeet.wallpapermoe.source.WallhavenAspect
import dev.leeeet.wallpapermoe.source.WallhavenSorting
import dev.leeeet.wallpapermoe.source.kind
import dev.leeeet.wallpapermoe.source.referencesTo
import sh.calvin.reorderable.ReorderableColumn
import sh.calvin.reorderable.ReorderableCollectionItemScope
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.ReorderableListItemScope
import sh.calvin.reorderable.rememberReorderableLazyListState
import java.util.UUID
import kotlin.math.roundToInt

private const val STUDIO_HEADER_COUNT = 2

@Composable
fun SourceScreen(state: UiState, vm: MoeViewModel) {
    val draft = state.sourceDraft
    val draftId = state.sourceDraftId
    if (draft == null || draftId == null) SourceLibraryScreen(state.sourceLibrary, vm)
    else SourceDefinitionEditor(draftId, draft, state.sourceLibrary, vm)
}

@Composable
private fun SourceLibraryScreen(library: SourceLibrary, vm: MoeViewModel) {
    var pickingType by remember { mutableStateOf(false) }
    var sources by remember(library.sources) { mutableStateOf(library.sources) }
    val listState = rememberLazyListState()
    val haptic = LocalHapticFeedback.current
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        val fromIndex = from.index - STUDIO_HEADER_COUNT
        val toIndex = to.index - STUDIO_HEADER_COUNT
        if (fromIndex in sources.indices && toIndex in sources.indices) {
            sources = sources.moved(fromIndex, toIndex)
            haptic.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
        }
    }

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxHeight()
                .widthIn(max = MoeContentMaxWidth)
                .fillMaxWidth()
                .align(Alignment.TopCenter),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState,
                contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 104.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (library.sources.isEmpty()) {
                    item { EmptyStudio(onCreate = { pickingType = true }) }
                } else {
                    item {
                        Text(
                            stringResource(R.string.source_intro),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 4.dp),
                        )
                    }
                    item {
                        Text(
                            pluralStringResource(
                                R.plurals.source_library_count,
                                library.sources.size,
                                library.sources.size,
                            ),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    itemsIndexed(sources, key = { _, definition -> definition.id }) { index, definition ->
                        ReorderableItem(reorderState, key = definition.id) { isDragging ->
                            LibrarySourceCard(
                                definition = definition,
                                library = library,
                                vm = vm,
                                modifier = Modifier.reorderActions(
                                    onMoveEarlier = if (index == 0) null else {
                                        {
                                            sources = sources.moved(index, index - 1).also {
                                                vm.reorderSources(it.map(SourceDefinition::id))
                                            }
                                        }
                                    },
                                    onMoveLater = if (index == sources.lastIndex) null else {
                                        {
                                            sources = sources.moved(index, index + 1).also {
                                                vm.reorderSources(it.map(SourceDefinition::id))
                                            }
                                        }
                                    },
                                ),
                                dragScope = this,
                                isDragging = isDragging,
                                onDragStopped = {
                                    vm.reorderSources(sources.map(SourceDefinition::id))
                                },
                            )
                        }
                    }
                }
            }

            ExtendedFloatingActionButton(
                onClick = { pickingType = true },
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.action_add_source)) },
                shape = MaterialTheme.shapes.large,
            )
        }
    }

    if (pickingType) {
        SourceTypePicker(
            onDismiss = { pickingType = false },
            onPick = {
                vm.createSource(it)
                pickingType = false
            },
        )
    }
}

@Composable
private fun EmptyStudio(onCreate: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(
            Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            Text(stringResource(R.string.source_library_empty), style = MaterialTheme.typography.bodyMedium)
            Button(onClick = onCreate) { Text(stringResource(R.string.action_create_first_source)) }
        }
    }
}

@Composable
private fun LibrarySourceCard(
    definition: SourceDefinition,
    library: SourceLibrary,
    vm: MoeViewModel,
    modifier: Modifier,
    dragScope: ReorderableCollectionItemScope,
    isDragging: Boolean,
    onDragStopped: () -> Unit,
) {
    var deleteDialog by remember { mutableStateOf(false) }
    val references = library.referencesTo(definition.id)
    val active = definition.id == library.activeSourceId
    val haptic = LocalHapticFeedback.current
    val activateLabel = stringResource(R.string.action_activate_named_source, definition.name)
    val elevation by animateDpAsState(if (isDragging) 8.dp else 0.dp)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                role = Role.Button,
                onLongClickLabel = activateLabel,
                onLongClick = if (active) null else {
                    {
                        vm.setActiveSource(definition.id)
                        haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                    }
                },
                onClick = { vm.editSource(definition.id) },
            ),
        shape = MaterialTheme.shapes.medium,
        elevation = CardDefaults.cardElevation(defaultElevation = elevation),
        colors = CardDefaults.cardColors(
            containerColor = if (active) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Row(
            Modifier.padding(start = 14.dp, top = 14.dp, bottom = 14.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SourceGlyph(definition.recipe.kind.localizedName().take(1))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        definition.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (active) {
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ) {
                            Text(
                                stringResource(R.string.source_active_badge),
                                Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                }
                Text(
                    definition.recipe.kind.localizedName(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    definition.localizedSummary(library),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box(
                modifier = with(dragScope) {
                    Modifier
                        .size(48.dp)
                        .draggableHandle(
                            onDragStarted = {
                                haptic.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                            },
                            onDragStopped = {
                                haptic.performHapticFeedback(HapticFeedbackType.GestureEnd)
                                onDragStopped()
                            },
                        )
                        .clearAndSetSemantics {}
                },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.DragHandle, contentDescription = null)
            }
            IconButton(onClick = { deleteDialog = true }) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = stringResource(R.string.action_delete_named_source, definition.name),
                )
            }
        }
    }

    if (deleteDialog) {
        if (references.isEmpty()) {
            AlertDialog(
                onDismissRequest = { deleteDialog = false },
                icon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                title = { Text(stringResource(R.string.dialog_delete_source_title, definition.name)) },
                text = { Text(stringResource(R.string.dialog_delete_source_body)) },
                confirmButton = {
                    TextButton(onClick = {
                        deleteDialog = false
                        vm.deleteSource(definition.id)
                    }) { Text(stringResource(R.string.action_delete)) }
                },
                dismissButton = {
                    TextButton(onClick = { deleteDialog = false }) {
                        Text(stringResource(R.string.action_cancel))
                    }
                },
            )
        } else {
            AlertDialog(
                onDismissRequest = { deleteDialog = false },
                title = { Text(stringResource(R.string.dialog_delete_blocked_title)) },
                text = {
                    Text(stringResource(
                        R.string.dialog_delete_blocked_body,
                        definition.name,
                        references.joinToString(", ") { it.name },
                    ))
                },
                confirmButton = {
                    TextButton(onClick = { deleteDialog = false }) {
                        Text(stringResource(R.string.action_cancel))
                    }
                },
            )
        }
    }
}

@Composable
private fun SourceDefinitionEditor(
    draftId: SourceId,
    draft: SourceDraft,
    library: SourceLibrary,
    vm: MoeViewModel,
) {
    val problem = draft.validationProblem(draftId, library)
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(draft.recipe.kind.localizedName(), style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(R.string.source_editor_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = vm::requestEditorBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, tonalElevation = 3.dp) {
                Box(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier
                            .widthIn(max = MoeContentMaxWidth)
                            .fillMaxWidth()
                            .align(Alignment.Center)
                            .navigationBarsPadding()
                            .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        problem?.let {
                            Text(
                                it.localizedMessage(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(onClick = vm::discardSourceChanges, modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.action_discard))
                            }
                            Button(
                                onClick = vm::saveSource,
                                enabled = problem == null,
                                modifier = Modifier.weight(1f),
                            ) { Text(stringResource(R.string.action_save_source)) }
                        }
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxHeight()
                    .widthIn(max = MoeContentMaxWidth)
                    .fillMaxWidth()
                    .align(Alignment.TopCenter),
                contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item {
                    OutlinedTextField(
                        value = draft.name,
                        onValueChange = { vm.updateSourceDraft(draft.copy(name = it)) },
                        label = { Text(stringResource(R.string.source_name_label)) },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                    )
                }
                item { ConfigEditor(draftId, draft, library, vm) }
            }
        }
    }
}

@Composable
private fun ConfigEditor(
    draftId: SourceId,
    draft: SourceDraft,
    library: SourceLibrary,
    vm: MoeViewModel,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        when (val config = draft.recipe) {
            is SourceRecipeDraft.Favorites -> {
                EditorDescription(R.string.favorites_source_description)
                OptionGroup(stringResource(R.string.favorites_source_mode)) {
                    SelectionMode.entries.forEach { mode ->
                        SelectionModeRow(mode, config.mode == mode) {
                            vm.updateSourceDraft(draft.copy(recipe = config.copy(mode = mode)))
                        }
                    }
                }
            }
            is SourceRecipeDraft.Pandora -> PandoraEditor(draftId, draft, config, vm)
            is SourceRecipeDraft.Wallhaven -> WallhavenEditor(draft, config, vm)
            is SourceRecipeDraft.NekosBest -> NekosBestEditor(draftId, draft, config, vm)
            is SourceRecipeDraft.KeepReady -> {
                EditorDescription(R.string.keep_ready_description)
                SingleReferenceEditor(draftId, config.sourceId, library, vm) {
                    vm.updateSourceDraft(draft.copy(recipe = config.copy(sourceId = it)))
                }
                KeepReadyOption(
                    title = stringResource(R.string.source_name_cache),
                    detail = stringResource(R.string.cache_description),
                    enabled = config.cacheEnabled,
                    onEnabledChange = {
                        vm.updateSourceDraft(draft.copy(recipe = config.copy(cacheEnabled = it)))
                    },
                ) {
                        SliderRow(
                            stringResource(R.string.cache_images_to_keep),
                            config.cacheCapacity,
                            CACHE_CAPACITY_RANGE,
                            CACHE_CAPACITY_STEP,
                        ) {
                            vm.updateSourceDraft(draft.copy(recipe = config.copy(cacheCapacity = it)))
                        }
                        CacheRefreshPicker(config.cacheRefresh) {
                            vm.updateSourceDraft(draft.copy(recipe = config.copy(cacheRefresh = it)))
                        }
                }
                KeepReadyOption(
                    title = stringResource(R.string.source_name_prefetch),
                    detail = stringResource(R.string.prefetch_description),
                    enabled = config.prefetchEnabled,
                    onEnabledChange = {
                        vm.updateSourceDraft(draft.copy(recipe = config.copy(prefetchEnabled = it)))
                    },
                ) {
                        Stepper(
                            stringResource(R.string.prefetch_images_ready),
                            config.prefetchDepth,
                            1..20,
                            1,
                        ) {
                            vm.updateSourceDraft(draft.copy(recipe = config.copy(prefetchDepth = it)))
                        }
                }
            }
            is SourceRecipeDraft.Combine -> {
                EditorDescription(R.string.combine_description)
                OptionGroup(stringResource(R.string.combine_mode)) {
                    CombineMode.entries.forEach { mode ->
                        CombineModeRow(mode, selected = config.mode == mode) {
                            vm.updateSourceDraft(draft.copy(recipe = config.copy(mode = mode)))
                        }
                    }
                }
                ManyEditor(
                    draftId,
                    config.sourceIds,
                    stringResource(R.string.combine_sources_detail),
                    library,
                    vm,
                ) { vm.updateSourceDraft(draft.copy(recipe = config.copy(sourceIds = it))) }
            }
        }
    }
}

@Composable
private fun NekosBestEditor(
    draftId: SourceId,
    draft: SourceDraft,
    config: SourceRecipeDraft.NekosBest,
    vm: MoeViewModel,
) {
    var picking by remember(draftId) { mutableStateOf(false) }
    EditorDescription(R.string.nekos_best_source_description)
    OptionGroup(stringResource(R.string.nekos_best_source_mode)) {
        SelectionMode.entries.forEach { mode ->
            SelectionModeRow(mode, config.mode == mode) {
                vm.updateSourceDraft(draft.copy(recipe = config.copy(mode = mode)))
            }
        }
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Text(
            if (config.categories.isEmpty()) stringResource(R.string.nekos_best_no_categories)
            else config.categories.joinToString(", ") { it.categoryLabel() },
            modifier = Modifier.padding(14.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Button(onClick = { picking = true }, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.action_choose_categories))
    }
    if (picking) {
        NekosBestCategoryPicker(config, vm, { picking = false }) {
            vm.updateSourceDraft(draft.copy(recipe = it))
        }
    }
}

@Composable
private fun NekosBestCategoryPicker(
    config: SourceRecipeDraft.NekosBest,
    vm: MoeViewModel,
    onDismiss: () -> Unit,
    onChange: (SourceRecipeDraft.NekosBest) -> Unit,
) {
    val categories by vm.nekosBestCategories.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.loadNekosBestCategories() }
    var selected by remember { mutableStateOf(config.categories.toSet()) }
    val listMaxHeight = LocalConfiguration.current.screenHeightDp.dp * .6f

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = expandedSheetState()) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(stringResource(R.string.nekos_best_categories_title), style = MaterialTheme.typography.titleLarge)
            when (val value = categories) {
                RemoteListState.Idle, RemoteListState.Loading -> Box(
                    Modifier.fillMaxWidth().height(160.dp),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }
                RemoteListState.Failed -> {
                    EmptyReference(stringResource(R.string.categories_unavailable))
                    TextButton(onClick = { vm.loadNekosBestCategories(force = true) }) {
                        Text(stringResource(R.string.action_try_again))
                    }
                }
                is RemoteListState.Ready -> {
                    val choices = (value.items + config.categories).distinct().sorted()
                    LazyColumn(Modifier.heightIn(max = listMaxHeight)) {
                        items(choices, key = { it }) { category ->
                            val toggle = {
                                selected = if (category in selected) selected - category else selected + category
                            }
                            Row(
                                Modifier.fillMaxWidth().clickable { toggle() }.padding(vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(category in selected, onCheckedChange = { toggle() })
                                Text(category.categoryLabel(), style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                }
            }
            Button(
                onClick = {
                    val available = when (val value = categories) {
                        is RemoteListState.Ready -> value.items
                        else -> config.categories
                    }
                    onChange(config.copy(categories = (available + selected).distinct().filter { it in selected }))
                    onDismiss()
                },
                enabled = selected.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.action_save_selection)) }
        }
    }
}

private fun String.categoryLabel(): String = replace('_', ' ').replace('-', ' ')
    .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }

@Composable
private fun SelectionModeRow(mode: SelectionMode, selected: Boolean, onSelect: () -> Unit) {
    val title = when (mode) {
        SelectionMode.RANDOM -> stringResource(R.string.source_name_random)
        SelectionMode.ROUND_ROBIN -> stringResource(R.string.source_name_round_robin)
    }
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onSelect).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Text(title, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun CombineModeRow(mode: CombineMode, selected: Boolean, onSelect: () -> Unit) {
    val title = when (mode) {
        CombineMode.RANDOM -> stringResource(R.string.source_name_random)
        CombineMode.ROUND_ROBIN -> stringResource(R.string.source_name_round_robin)
        CombineMode.FALLTHROUGH -> stringResource(R.string.source_name_fallthrough)
    }
    val detail = when (mode) {
        CombineMode.RANDOM -> stringResource(R.string.random_description)
        CombineMode.ROUND_ROBIN -> stringResource(R.string.round_robin_description)
        CombineMode.FALLTHROUGH -> stringResource(R.string.fallthrough_description)
    }
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onSelect).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun EditorDescription(@StringRes detail: Int) {
    Text(
        stringResource(detail),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun KeepReadyOption(
    title: String,
    detail: String,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onEnabledChange(!enabled) }
                    .padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = enabled, onCheckedChange = onEnabledChange)
            }
            if (enabled) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    content = content,
                )
            }
        }
    }
}

@Composable
private fun SingleReferenceEditor(
    draftId: SourceId,
    sourceId: SourceId?,
    library: SourceLibrary,
    vm: MoeViewModel,
    onSource: (SourceId?) -> Unit,
) {
    var choosing by remember(draftId) { mutableStateOf(false) }
    var creating by remember(draftId) { mutableStateOf(false) }
    SectionHeading(stringResource(R.string.source_slot_title), stringResource(R.string.source_slot_detail))
    val selected = library.sources.firstOrNull { it.id == sourceId }
    if (selected == null) {
        EmptyReference(stringResource(R.string.source_slot_empty))
    } else {
        ReferenceRow(selected, library, { vm.editSource(selected.id) }, { onSource(null) })
    }
    ReferenceActions({ choosing = true }, { creating = true })
    if (choosing) {
        SourceReferencePicker(draftId, library, { choosing = false }) {
            onSource(it)
            choosing = false
        }
    }
    if (creating) {
        SourceTypePicker(
            onDismiss = { creating = false },
            onPick = {
                vm.createSource(it, attachToCurrent = true)
                creating = false
            },
        )
    }
}

@Composable
private fun ManyEditor(
    draftId: SourceId,
    sourceIds: List<SourceId>,
    description: String,
    library: SourceLibrary,
    vm: MoeViewModel,
    onSources: (List<SourceId>) -> Unit,
) {
    var choosing by remember(draftId) { mutableStateOf(false) }
    var creating by remember(draftId) { mutableStateOf(false) }
    var references by remember(draftId) {
        mutableStateOf(sourceIds.map(::EditableReference))
    }
    val haptic = LocalHapticFeedback.current
    LaunchedEffect(sourceIds) {
        if (references.map(EditableReference::sourceId) != sourceIds) {
            references = references.reconciledWith(sourceIds)
        }
    }
    fun update(next: List<EditableReference>) {
        references = next
        onSources(next.map(EditableReference::sourceId))
    }

    SectionHeading(stringResource(R.string.source_list_title), description)
    if (references.isEmpty()) EmptyReference(stringResource(R.string.source_list_empty))
    ReorderableColumn(
        list = references,
        onSettle = { from, to -> update(references.moved(from, to)) },
        onMove = { haptic.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick) },
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) { index, reference, isDragging ->
        key(reference.key) {
            ReorderableItem {
                val definition = library.sources.firstOrNull {
                    it.id == reference.sourceId
                } ?: return@ReorderableItem
                ReferenceRow(
                    definition = definition,
                    library = library,
                    onOpen = { vm.editSource(reference.sourceId) },
                    onRemove = { update(references.filterNot { it.key == reference.key }) },
                    modifier = Modifier.reorderActions(
                        onMoveEarlier = if (index == 0) null else {
                            { update(references.moved(index, index - 1)) }
                        },
                        onMoveLater = if (index == references.lastIndex) null else {
                            { update(references.moved(index, index + 1)) }
                        },
                    ),
                    dragScope = this,
                    isDragging = isDragging,
                )
            }
        }
    }
    ReferenceActions({ choosing = true }, { creating = true })
    if (choosing) {
        SourceReferencePicker(draftId, library, { choosing = false }) {
            update(references + EditableReference(it))
            choosing = false
        }
    }
    if (creating) {
        SourceTypePicker(
            onDismiss = { creating = false },
            onPick = {
                vm.createSource(it, attachToCurrent = true)
                creating = false
            },
        )
    }
}

private data class EditableReference(
    val sourceId: SourceId,
    val key: String = UUID.randomUUID().toString(),
)

private fun List<EditableReference>.reconciledWith(sourceIds: List<SourceId>): List<EditableReference> {
    val unused = toMutableList()
    return sourceIds.map { sourceId ->
        val existing = unused.indexOfFirst { it.sourceId == sourceId }
        if (existing == -1) EditableReference(sourceId) else unused.removeAt(existing)
    }
}

@Composable
private fun EmptyReference(message: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Text(message, Modifier.padding(14.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ReferenceActions(onChoose: () -> Unit, onCreate: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Button(onClick = onChoose, modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.action_choose_source))
        }
        OutlinedButton(onClick = onCreate, modifier = Modifier.weight(1f)) {
            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.action_create_source))
        }
    }
}

@Composable
private fun ReferenceRow(
    definition: SourceDefinition,
    library: SourceLibrary,
    onOpen: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
    dragScope: ReorderableListItemScope? = null,
    isDragging: Boolean = false,
) {
    val haptic = LocalHapticFeedback.current
    val elevation by animateDpAsState(if (isDragging) 8.dp else 0.dp)
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
        shadowElevation = elevation,
    ) {
        Row(
            Modifier.padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SourceGlyph(definition.recipe.kind.localizedName().take(1), Modifier.size(40.dp))
            Column(Modifier.weight(1f).clickable(onClick = onOpen).padding(horizontal = 12.dp)) {
                Text(definition.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    definition.localizedSummary(library),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            dragScope?.let { scope ->
                Box(
                    modifier = with(scope) {
                        Modifier
                            .size(48.dp)
                            .draggableHandle(
                                onDragStarted = {
                                    haptic.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                                },
                                onDragStopped = {
                                    haptic.performHapticFeedback(HapticFeedbackType.GestureEnd)
                                },
                            )
                            .clearAndSetSemantics {}
                    },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.DragHandle, contentDescription = null)
                }
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_remove_reference))
            }
        }
    }
}

@Composable
private fun SourceReferencePicker(
    ownerId: SourceId,
    library: SourceLibrary,
    onDismiss: () -> Unit,
    onPick: (SourceId) -> Unit,
) {
    val candidates = library.sources.filter { library.canReference(ownerId, it.id) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = expandedSheetState()) {
        BoxWithConstraints(Modifier.fillMaxWidth().navigationBarsPadding()) {
            val listMaxHeight = maxHeight * .55f
            Column(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(stringResource(R.string.source_reference_picker_title), style = MaterialTheme.typography.titleLarge)
                Text(
                    stringResource(R.string.source_reference_picker_detail),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (candidates.isEmpty()) {
                    EmptyReference(stringResource(R.string.source_reference_picker_empty))
                } else {
                    LazyColumn(
                        Modifier.heightIn(max = listMaxHeight),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(candidates, key = SourceDefinition::id) { definition ->
                            Surface(
                                modifier = Modifier.fillMaxWidth().clickable { onPick(definition.id) },
                                shape = MaterialTheme.shapes.medium,
                                color = MaterialTheme.colorScheme.surfaceContainer,
                            ) {
                                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    SourceGlyph(definition.recipe.kind.localizedName().take(1), Modifier.size(40.dp))
                                    Column {
                                        Text(definition.name, style = MaterialTheme.typography.titleMedium)
                                        Text(definition.recipe.kind.localizedName(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PandoraEditor(
    draftId: SourceId,
    draft: SourceDraft,
    config: SourceRecipeDraft.Pandora,
    vm: MoeViewModel,
) {
    var picking by remember(draftId) { mutableStateOf(false) }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Text(
            if (config.channelIds.isEmpty()) stringResource(R.string.pandora_no_channels)
            else config.channelLabels.ifEmpty { config.channelIds }.joinToString(", "),
            modifier = Modifier.padding(14.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Button(onClick = { picking = true }, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.action_choose_channels))
    }
    if (picking) {
        ChannelPicker(config, vm, { picking = false }) {
            vm.updateSourceDraft(draft.copy(recipe = it))
        }
    }
}

@Composable
private fun ChannelPicker(
    config: SourceRecipeDraft.Pandora,
    vm: MoeViewModel,
    onDismiss: () -> Unit,
    onChange: (SourceRecipeDraft.Pandora) -> Unit,
) {
    val channels by vm.channels.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.loadChannels() }
    var selected by remember { mutableStateOf(config.channelIds.toSet()) }
    val listMaxHeight = LocalConfiguration.current.screenHeightDp.dp * .6f

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = expandedSheetState()) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(stringResource(R.string.pandora_channels_title), style = MaterialTheme.typography.titleLarge)
            when (val value = channels) {
                RemoteListState.Idle, RemoteListState.Loading -> Box(
                    Modifier.fillMaxWidth().height(160.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
                RemoteListState.Failed -> {
                    EmptyReference(stringResource(R.string.channels_unavailable))
                    TextButton(onClick = { vm.loadChannels(force = true) }) {
                        Text(stringResource(R.string.action_try_again))
                    }
                }
                is RemoteListState.Ready -> LazyColumn(Modifier.heightIn(max = listMaxHeight)) {
                    items(value.items, key = { it.id }) { channel ->
                        val toggle = {
                            selected = if (channel.id in selected) selected - channel.id else selected + channel.id
                        }
                        Row(
                            Modifier.fillMaxWidth().clickable { toggle() }.padding(vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(channel.id in selected, onCheckedChange = { toggle() })
                            Column(Modifier.weight(1f)) {
                                Text(channel.name, style = MaterialTheme.typography.bodyLarge)
                                channel.subscribers?.let {
                                    Text(
                                        pluralStringResource(R.plurals.pandora_subscribers, it, it),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Button(
                onClick = {
                    val labels = when (val ready = channels) {
                        is RemoteListState.Ready ->
                            ready.items.filter { it.id in selected }.map { it.name }
                        else -> emptyList()
                    }
                    onChange(config.copy(channelIds = selected.toList(), channelLabels = labels))
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.action_save_selection)) }
        }
    }
}

@Composable
private fun WallhavenEditor(draft: SourceDraft, config: SourceRecipeDraft.Wallhaven, vm: MoeViewModel) {
    fun update(value: SourceRecipeDraft.Wallhaven) = vm.updateSourceDraft(draft.copy(recipe = value))
    OutlinedTextField(
        value = config.query,
        onValueChange = { update(config.copy(query = it)) },
        label = { Text(stringResource(R.string.wallhaven_search)) },
        placeholder = { Text(stringResource(R.string.wallhaven_search_hint)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
    )
    CompactChipGroup(stringResource(R.string.wallhaven_categories)) {
        FilterChip(
            selected = config.general,
            onClick = { update(config.copy(general = !config.general)) },
            label = { Text(stringResource(R.string.wallhaven_general)) },
        )
        FilterChip(
            selected = config.anime,
            onClick = { update(config.copy(anime = !config.anime)) },
            label = { Text(stringResource(R.string.wallhaven_anime)) },
        )
        FilterChip(
            selected = config.people,
            onClick = { update(config.copy(people = !config.people)) },
            label = { Text(stringResource(R.string.wallhaven_people)) },
        )
    }
    CompactChipGroup(stringResource(R.string.wallhaven_purity)) {
        FilterChip(
            selected = config.sfw,
            onClick = { update(config.copy(sfw = !config.sfw)) },
            label = { Text(stringResource(R.string.wallhaven_sfw)) },
        )
        FilterChip(
            selected = config.sketchy,
            onClick = { update(config.copy(sketchy = !config.sketchy)) },
            label = { Text(stringResource(R.string.wallhaven_sketchy)) },
        )
        FilterChip(
            selected = config.nsfw,
            onClick = { update(config.copy(nsfw = !config.nsfw)) },
            label = { Text(stringResource(R.string.wallhaven_nsfw)) },
        )
    }
    CompactChipGroup(stringResource(R.string.wallhaven_aspect_ratio)) {
        WallhavenAspect.entries.forEach { aspect ->
            FilterChip(
                selected = aspect in config.aspects,
                onClick = {
                    val aspects = if (aspect in config.aspects) {
                        config.aspects - aspect
                    } else {
                        config.aspects + aspect
                    }
                    update(config.copy(aspects = aspects))
                },
                label = {
                    Text(
                        stringResource(
                            when (aspect) {
                                WallhavenAspect.PORTRAIT -> R.string.wallhaven_portrait
                                WallhavenAspect.LANDSCAPE -> R.string.wallhaven_landscape
                            },
                        ),
                    )
                },
            )
        }
    }
    WallhavenOrderSelector(config.sorting) {
        update(config.copy(sorting = it))
    }
    OutlinedTextField(
        value = config.apiKey,
        onValueChange = { update(config.copy(apiKey = it)) },
        label = { Text(stringResource(R.string.wallhaven_api_key)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
    )
}

@Composable
private fun CompactChipGroup(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            content()
        }
    }
}

@Composable
private fun WallhavenOrderSelector(
    selected: WallhavenSorting,
    onSelect: (WallhavenSorting) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.wallhaven_order), style = MaterialTheme.typography.titleMedium)
        Box {
            Surface(
                modifier = Modifier.fillMaxWidth().clickable { expanded = true },
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            ) {
                Row(
                    Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(selected.localizedLabel(), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null)
                }
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.widthIn(min = 220.dp),
            ) {
                WallhavenSorting.entries.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.localizedLabel()) },
                        onClick = {
                            onSelect(option)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun OptionGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
            content()
        }
    }
}

private data class CreatableSource(val kind: SourceKind, @StringRes val description: Int)

private val creatable = listOf(
    CreatableSource(SourceKind.KEEP_READY, R.string.source_picker_keep_ready),
    CreatableSource(SourceKind.COMBINE, R.string.source_picker_combine),
    CreatableSource(SourceKind.FAVORITES, R.string.source_picker_favorites),
    CreatableSource(SourceKind.WALLHAVEN, R.string.source_picker_wallhaven),
    CreatableSource(SourceKind.NEKOS_BEST, R.string.source_picker_nekos_best),
    CreatableSource(SourceKind.PANDORA, R.string.source_picker_pandora),
)

@Composable
private fun SourceTypePicker(onDismiss: () -> Unit, onPick: (SourceKind) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = expandedSheetState()) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(stringResource(R.string.source_picker_title), style = MaterialTheme.typography.titleLarge)
            LazyColumn(
                Modifier.heightIn(max = LocalConfiguration.current.screenHeightDp.dp * .6f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(creatable, key = { it.kind }) { source ->
                    Surface(
                        modifier = Modifier.fillMaxWidth().clickable { onPick(source.kind) },
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceContainer,
                    ) {
                        Row(
                            Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            SourceGlyph(source.kind.localizedName().take(1))
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(source.kind.localizedName(), style = MaterialTheme.typography.titleMedium)
                                Text(
                                    stringResource(source.description),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SourceDefinition.localizedSummary(library: SourceLibrary): String = when (val value = recipe) {
    is SourceRecipe.Favorites -> when (value.mode) {
        SelectionMode.RANDOM -> stringResource(R.string.favorites_source_summary_random)
        SelectionMode.ROUND_ROBIN -> stringResource(R.string.favorites_source_summary_round_robin)
    }
    is SourceRecipe.Pandora -> when {
        value.channelLabels.isNotEmpty() -> value.channelLabels.joinToString(", ")
        else -> pluralStringResource(R.plurals.summary_channels, value.channelIds.size, value.channelIds.size)
    }
    is SourceRecipe.Wallhaven -> stringResource(
        R.string.summary_wallhaven,
        value.query.ifBlank { stringResource(R.string.summary_anything) },
        value.sorting.localizedLabel(),
    )
    is SourceRecipe.NekosBest -> stringResource(
        R.string.summary_nekos_best,
        value.categories.joinToString(", ") { it.categoryLabel() },
        when (value.mode) {
            SelectionMode.RANDOM -> stringResource(R.string.source_name_random)
            SelectionMode.ROUND_ROBIN -> stringResource(R.string.source_name_round_robin)
        },
    )
    is SourceRecipe.Combine -> when (value.mode) {
        CombineMode.RANDOM -> value.sourceIds.summary(R.plurals.summary_sources_random)
        CombineMode.ROUND_ROBIN -> value.sourceIds.summary(R.plurals.summary_sources_in_turn)
        CombineMode.FALLTHROUGH -> value.sourceIds.summary(R.plurals.summary_sources_fallthrough)
    }
    is SourceRecipe.KeepReady -> {
        val cacheSummary = value.cache?.let { cache ->
            if (cache.refreshHours == 0) {
                pluralStringResource(
                    R.plurals.summary_cache_never,
                    cache.capacity,
                    cache.capacity,
                )
            } else {
                pluralStringResource(
                    R.plurals.summary_cache,
                    cache.capacity,
                    cache.capacity,
                    pluralStringResource(
                        R.plurals.summary_cache_interval,
                        cache.refreshHours,
                        cache.refreshHours,
                    ),
                )
            }
        }
        val prefetchSummary = if (value.prefetch == null) null else pluralStringResource(
            R.plurals.summary_prefetch,
            value.prefetch.depth,
            value.prefetch.depth,
        )
        val detail = listOfNotNull(cacheSummary, prefetchSummary)
            .ifEmpty { listOf(stringResource(R.string.summary_keep_ready_passthrough)) }
            .joinToString(" · ")
        stringResource(R.string.summary_source_reference, library.nameOf(value.sourceId), detail)
    }
}

private fun SourceLibrary.nameOf(id: SourceId): String = sources.first { it.id == id }.name

@Composable
private fun List<SourceId>.summary(@PluralsRes plural: Int): String = pluralStringResource(plural, size, size)

@Composable
private fun SliderRow(label: String, value: Int, range: IntRange, step: Int, onChange: (Int) -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                Text("$value", style = MaterialTheme.typography.titleMedium)
            }
            Slider(
                value = value.toFloat(),
                onValueChange = { onChange(it.roundToInt()) },
                valueRange = range.first.toFloat()..range.last.toFloat(),
                // Slider counts the stops between the ends, not the ends themselves.
                steps = (range.last - range.first) / step - 1,
            )
        }
    }
}

@Composable
private fun CacheRefreshPicker(selected: CacheRefresh, onChange: (CacheRefresh) -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(stringResource(R.string.cache_refresh_interval), style = MaterialTheme.typography.bodyMedium)
            CacheRefresh.entries.forEach { option ->
                Row(
                    Modifier.fillMaxWidth().clickable { onChange(option) },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    RadioButton(selected = option == selected, onClick = { onChange(option) })
                    Text(option.localizedLabel(), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

@Composable
private fun Stepper(label: String, value: Int, range: IntRange, step: Int, onChange: (Int) -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            StepButton(
                symbol = "−",
                onClick = { onChange((value - step).coerceIn(range)) },
                enabled = value > range.first,
            )
            Box(Modifier.width(32.dp), contentAlignment = Alignment.Center) {
                Text("$value", style = MaterialTheme.typography.titleMedium)
            }
            StepButton(
                symbol = "+",
                onClick = { onChange((value + step).coerceIn(range)) },
                enabled = value < range.last,
            )
        }
    }
}

@Composable
private fun StepButton(symbol: String, enabled: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.size(40.dp).clickable(enabled = enabled, onClick = onClick),
        shape = MaterialTheme.shapes.small,
        color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (enabled) MaterialTheme.colorScheme.onPrimary
        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .38f),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(symbol, style = MaterialTheme.typography.titleMedium)
        }
    }
}
