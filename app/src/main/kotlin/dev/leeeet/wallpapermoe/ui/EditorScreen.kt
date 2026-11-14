package dev.leeeet.wallpapermoe.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.leeeet.wallpapermoe.R
import dev.leeeet.wallpapermoe.data.moeJson
import dev.leeeet.wallpapermoe.model.AppData
import dev.leeeet.wallpapermoe.model.Channel
import dev.leeeet.wallpapermoe.model.CombineMode
import dev.leeeet.wallpapermoe.model.Kind
import dev.leeeet.wallpapermoe.model.Pick
import dev.leeeet.wallpapermoe.model.Prepare
import dev.leeeet.wallpapermoe.model.Problem
import dev.leeeet.wallpapermoe.model.Recipe
import dev.leeeet.wallpapermoe.model.Refresh
import dev.leeeet.wallpapermoe.model.Save
import dev.leeeet.wallpapermoe.model.Sorting
import dev.leeeet.wallpapermoe.model.Source
import dev.leeeet.wallpapermoe.model.eligibleInputs
import dev.leeeet.wallpapermoe.model.newRecipe
import dev.leeeet.wallpapermoe.model.problems
import dev.leeeet.wallpapermoe.model.users
import java.util.UUID
import sh.calvin.reorderable.ReorderableColumn

private val SourceSaver = Saver<Source, String>(
    save = { moeJson.encodeToString(it) },
    restore = { moeJson.decodeFromString<Source>(it) },
)

private class EditorContext(
    val data: AppData,
    val draft: Source,
    val problems: List<Problem>,
    val model: MoeViewModel,
    val open: (Route) -> Unit,
    val update: (Recipe) -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(route: Route.Editor, model: MoeViewModel, data: AppData, open: (Route) -> Unit, back: () -> Unit) {
    val original = rememberSaveable(saver = SourceSaver) {
        data.source(route.sourceId) ?: Source(UUID.randomUUID().toString(), "", route.kind.newRecipe())
    }
    var draft by rememberSaveable(stateSaver = SourceSaver) { mutableStateOf(original) }
    var discarding by rememberSaveable { mutableStateOf(false) }
    var deleting by rememberSaveable { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    val saved = data.source(original.id)
    val problems = data.problems(draft)
    val changed = draft != original
    val dirty = saved == null || changed
    val leave = { if (changed) discarding = true else back() }

    BackHandler(enabled = changed) { discarding = true }
    Scaffold(
        snackbarHost = { SnackbarHost(LocalSnackbar.current) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        draft.name.trim().ifEmpty { stringResource(draft.recipe.kind.label) },
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = leave) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) }
                },
                actions = {
                    if (saved != null) {
                        Box {
                            IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, stringResource(R.string.action_more)) }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.action_delete)) },
                                    leadingIcon = { Icon(Icons.Filled.Delete, null) },
                                    onClick = {
                                        menu = false
                                        deleting = true
                                    },
                                )
                            }
                        }
                    }
                },
            )
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
                Box(Modifier.fillMaxWidth().navigationBarsPadding(), contentAlignment = Alignment.Center) {
                    Row(
                        Modifier.contentWidth().padding(start = 16.dp, top = 10.dp, end = 16.dp, bottom = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        OutlinedButton(onClick = back, modifier = Modifier.weight(1f)) {
                            Text(stringResource(if (dirty && saved != null) R.string.action_discard else R.string.action_cancel))
                        }
                        Button(
                            onClick = {
                                model.saveSource(draft)
                                back()
                            },
                            enabled = dirty && problems.isEmpty(),
                            modifier = Modifier.weight(1f),
                        ) { Text(stringResource(R.string.action_save_source)) }
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).imePadding()) {
            Column(
                Modifier
                    .contentWidth()
                    .align(Alignment.TopCenter)
                    .verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                val duplicate = Problem.NAME_TAKEN in problems
                OutlinedTextField(
                    value = draft.name,
                    onValueChange = { draft = draft.copy(name = it) },
                    label = { Text(stringResource(R.string.source_name_label)) },
                    supportingText = {
                        Text(stringResource(if (duplicate) Problem.NAME_TAKEN.label else R.string.source_name_optional_hint))
                    },
                    isError = duplicate,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.titleMedium,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth(),
                )
                val context = EditorContext(data, draft, problems, model, open) { draft = draft.copy(recipe = it) }
                when (val recipe = draft.recipe) {
                    is Recipe.Wallhaven -> WallhavenEditor(recipe, context)
                    is Recipe.NekosBest -> NekosBestEditor(recipe, context)
                    is Recipe.Pandora -> PandoraEditor(recipe, context)
                    is Recipe.Folder -> FolderEditor(recipe, context)
                    is Recipe.Favorites -> PickGroup(recipe.pick) { context.update(recipe.copy(pick = it)) }
                    is Recipe.KeepReady -> KeepReadyEditor(recipe, context)
                    is Recipe.Combine -> CombineEditor(recipe, context)
                }
            }
        }
    }

    if (deleting && saved != null) {
        DeleteSourceDialog(
            source = saved,
            data = data,
            onDelete = {
                model.deleteSource(saved.id)
                back()
            },
            onDismiss = { deleting = false },
        )
    }
    if (discarding) {
        AlertDialog(
            onDismissRequest = { discarding = false },
            title = { Text(stringResource(R.string.dialog_unsaved_title)) },
            confirmButton = {
                TextButton(onClick = {
                    discarding = false
                    back()
                }) { Text(stringResource(R.string.action_discard_changes)) }
            },
            dismissButton = { TextButton({ discarding = false }) { Text(stringResource(R.string.action_keep_editing)) } },
        )
    }
}

@Composable
private fun PickGroup(pick: Pick, onChange: (Pick) -> Unit) {
    OptionGroup(stringResource(R.string.selection_mode_title)) {
        for (option in Pick.entries) {
            RadioRow(stringResource(option.label), option == pick, onClick = { onChange(option) })
        }
    }
}

@Composable
private fun ChipGroup(title: String, error: String?, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { content() }
        if (error != null) InlineError(error)
    }
}

@Composable
private fun Toggle(label: Int, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(stringResource(label)) })
}

@Composable
private fun problemText(context: EditorContext, problem: Problem): String? =
    if (problem in context.problems) stringResource(problem.label) else null

@Composable
private fun WallhavenEditor(recipe: Recipe.Wallhaven, context: EditorContext) {
    val update = context.update
    var sorting by rememberSaveable { mutableStateOf(false) }
    OutlinedTextField(
        value = recipe.query,
        onValueChange = { update(recipe.copy(query = it)) },
        label = { Text(stringResource(R.string.wallhaven_search)) },
        supportingText = { Text(stringResource(R.string.wallhaven_search_hint)) },
        singleLine = true,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    )
    ChipGroup(stringResource(R.string.wallhaven_categories), problemText(context, Problem.WALLHAVEN_CATEGORY)) {
        Toggle(R.string.wallhaven_general, recipe.general) { update(recipe.copy(general = !recipe.general)) }
        Toggle(R.string.wallhaven_anime, recipe.anime) { update(recipe.copy(anime = !recipe.anime)) }
        Toggle(R.string.wallhaven_people, recipe.people) { update(recipe.copy(people = !recipe.people)) }
    }
    ChipGroup(
        stringResource(R.string.wallhaven_purity),
        problemText(context, Problem.WALLHAVEN_PURITY) ?: problemText(context, Problem.WALLHAVEN_KEY),
    ) {
        Toggle(R.string.wallhaven_sfw, recipe.sfw) { update(recipe.copy(sfw = !recipe.sfw)) }
        Toggle(R.string.wallhaven_sketchy, recipe.sketchy) { update(recipe.copy(sketchy = !recipe.sketchy)) }
        Toggle(R.string.wallhaven_nsfw, recipe.nsfw) { update(recipe.copy(nsfw = !recipe.nsfw)) }
    }
    ChipGroup(stringResource(R.string.wallhaven_aspect_ratio), problemText(context, Problem.WALLHAVEN_ASPECT)) {
        Toggle(R.string.wallhaven_portrait, recipe.portrait) { update(recipe.copy(portrait = !recipe.portrait)) }
        Toggle(R.string.wallhaven_landscape, recipe.landscape) { update(recipe.copy(landscape = !recipe.landscape)) }
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.wallhaven_order), style = MaterialTheme.typography.titleMedium)
        PickerField(stringResource(recipe.sorting.label), onClick = { sorting = true })
    }
    if (sorting) {
        OptionSheet(
            title = stringResource(R.string.wallhaven_order),
            options = Sorting.entries,
            selected = recipe.sorting,
            onSelect = { update(recipe.copy(sorting = it)) },
            onDismiss = { sorting = false },
        ) { stringResource(it.label) }
    }
}

@Composable
private fun ChosenGroup(title: String, chosen: List<String>, error: String?, action: String, onChoose: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHeader(title)
        if (chosen.isNotEmpty()) Text(chosen.joinToString(), style = MaterialTheme.typography.bodyMedium)
        if (error != null) InlineError(error)
        Button(onClick = onChoose, modifier = Modifier.fillMaxWidth()) { Text(action) }
    }
}

@Composable
private fun <T : Any> RemoteSheet(
    title: String,
    failure: String,
    load: suspend () -> T?,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.(T) -> Unit,
) {
    var attempt by remember { mutableIntStateOf(0) }
    val result by produceState<Result<T>?>(null, attempt) {
        value = null
        value = load()?.let { Result.success(it) } ?: Result.failure(IllegalStateException())
    }
    MoeSheet(title, onDismiss) {
        val loaded = result
        when {
            loaded == null -> SheetLoading()
            loaded.isFailure -> SheetFailure(failure) { attempt++ }
            else -> content(loaded.getOrThrow())
        }
    }
}

@Composable
private fun NekosBestEditor(recipe: Recipe.NekosBest, context: EditorContext) {
    var choosing by rememberSaveable { mutableStateOf(false) }
    val title = stringResource(R.string.nekos_best_categories_title)
    ChosenGroup(
        title = title,
        chosen = recipe.categories.sorted().map { it.categoryLabel() },
        error = problemText(context, Problem.NEKOS_BEST_CATEGORY),
        action = stringResource(R.string.action_choose_categories),
        onChoose = { choosing = true },
    )
    PickGroup(recipe.pick) { context.update(recipe.copy(pick = it)) }
    if (choosing) {
        RemoteSheet(title, stringResource(R.string.categories_unavailable), context.model::categories, { choosing = false }) { categories ->
            SheetList {
                items((categories + recipe.categories).distinct().sorted()) { category ->
                    val selected = category in recipe.categories
                    CheckRow(category.categoryLabel(), selected, onClick = {
                        context.update(
                            recipe.copy(categories = if (selected) recipe.categories - category else (recipe.categories + category).sorted()),
                        )
                    })
                }
            }
        }
    }
}

@Composable
private fun PandoraEditor(recipe: Recipe.Pandora, context: EditorContext) {
    var choosing by rememberSaveable { mutableStateOf(false) }
    val title = stringResource(R.string.pandora_channels_title)
    ChosenGroup(
        title = title,
        chosen = recipe.channels.map(Channel::name),
        error = problemText(context, Problem.PANDORA_CHANNEL),
        action = stringResource(R.string.action_choose_channels),
        onChoose = { choosing = true },
    )
    if (choosing) {
        RemoteSheet(title, stringResource(R.string.channels_unavailable), context.model::channels, { choosing = false }) { listings ->
            SheetList {
                items(listings, key = { it.id }) { listing ->
                    val selected = recipe.channels.any { it.id == listing.id }
                    CheckRow(
                        label = listing.name,
                        checked = selected,
                        onClick = {
                            context.update(
                                recipe.copy(
                                    channels = if (selected) {
                                        recipe.channels.filterNot { it.id == listing.id }
                                    } else {
                                        recipe.channels + Channel(listing.id, listing.name)
                                    },
                                ),
                            )
                        },
                        detail = listing.subscribers?.let { pluralStringResource(R.plurals.pandora_subscribers, it, it) },
                    )
                }
            }
        }
    }
}

@Composable
private fun FolderEditor(recipe: Recipe.Folder, context: EditorContext) {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree ->
        if (tree != null) context.update(context.model.folder(tree, recipe))
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHeader(stringResource(R.string.folder_title), stringResource(R.string.folder_detail))
        if (recipe.label.isEmpty()) {
            EmptyReference(stringResource(R.string.folder_none))
        } else {
            Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    SourceGlyph(Kind.FOLDER)
                    Text(recipe.label, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        problemText(context, Problem.FOLDER_MISSING)?.let { InlineError(it) }
        Button(onClick = { launcher.launch(null) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_choose_folder)) }
    }
    PickGroup(recipe.pick) { context.update(recipe.copy(pick = it)) }
}

@Composable
private fun EmptyReference(message: String) {
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Text(
            message,
            Modifier.padding(14.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ReferenceRow(
    source: Source,
    data: AppData,
    onOpen: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
    elevation: Dp = 0.dp,
    handle: (@Composable () -> Unit)? = null,
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
        shadowElevation = elevation,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(start = 14.dp, top = 8.dp, end = 4.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            SourceGlyph(source.recipe.kind)
            Column(Modifier.weight(1f).clickable(onClick = onOpen).padding(horizontal = 12.dp, vertical = 8.dp)) {
                Text(source.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    source.summary(data),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            handle?.invoke()
            IconButton(onClick = onRemove) { Icon(Icons.Filled.Close, stringResource(R.string.action_remove_reference)) }
        }
    }
}

@Composable
private fun ReferenceActions(candidates: List<Source>, keepOpen: Boolean = false, onChoose: (Source) -> Unit) {
    var choosing by rememberSaveable { mutableStateOf(false) }
    Button(onClick = { choosing = true }, enabled = candidates.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.action_choose_source))
    }
    if (choosing) {
        MoeSheet(stringResource(R.string.source_reference_picker_title), { choosing = false }) {
            SheetList(Arrangement.spacedBy(8.dp)) {
                items(candidates, key = Source::id) { source ->
                    SourceRow(source.recipe.kind, source.name, stringResource(source.recipe.kind.label), onClick = {
                        if (!keepOpen || candidates.size == 1) choosing = false
                        onChoose(source)
                    })
                }
            }
        }
    }
}

@Composable
private fun KeepReadyEditor(recipe: Recipe.KeepReady, context: EditorContext) {
    val update = context.update
    var refreshing by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHeader(stringResource(R.string.source_slot_title))
        val input = context.data.source(recipe.sourceId)
        if (input == null) {
            EmptyReference(stringResource(R.string.source_slot_empty))
        } else {
            ReferenceRow(
                source = input,
                data = context.data,
                onOpen = { context.open(Route.Editor(input.id, input.recipe.kind)) },
                onRemove = { update(recipe.copy(sourceId = null)) },
            )
        }
        (problemText(context, Problem.INPUT_CYCLE) ?: problemText(context, Problem.INPUT_MISSING))?.let { InlineError(it) }
        ReferenceActions(context.data.eligibleInputs(context.draft)) { update(recipe.copy(sourceId = it.id)) }
    }
    SwitchGroup(
        title = stringResource(R.string.source_name_prefetch),
        detail = stringResource(R.string.prefetch_description),
        checked = recipe.prepare != null,
        onChange = { update(recipe.copy(prepare = if (it) Prepare() else null)) },
    ) {
        recipe.prepare?.let { prepare ->
            LabeledIntSlider(
                label = stringResource(R.string.prefetch_images_ready),
                value = prepare.count,
                range = Prepare.COUNT,
                onChange = { update(recipe.copy(prepare = prepare.copy(count = it))) },
            )
        }
    }
    SwitchGroup(
        title = stringResource(R.string.source_name_cache),
        detail = stringResource(R.string.cache_description),
        checked = recipe.save != null,
        onChange = { update(recipe.copy(save = if (it) Save() else null)) },
    ) {
        recipe.save?.let { save ->
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                LabeledIntSlider(
                    label = stringResource(R.string.cache_images_to_keep),
                    value = save.count,
                    range = Save.COUNT,
                    increment = Save.STEP,
                    onChange = { update(recipe.copy(save = save.copy(count = it))) },
                )
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.cache_refresh_interval), style = MaterialTheme.typography.bodyMedium)
                    PickerField(stringResource(save.refresh.label), onClick = { refreshing = true })
                }
            }
            if (refreshing) {
                OptionSheet(
                    title = stringResource(R.string.cache_refresh_interval),
                    options = Refresh.entries,
                    selected = save.refresh,
                    onSelect = { update(recipe.copy(save = save.copy(refresh = it))) },
                    onDismiss = { refreshing = false },
                ) { stringResource(it.label) }
            }
        }
    }
    problemText(context, Problem.KEEP_READY_OPTION)?.let { InlineError(it) }
}

@Composable
private fun CombineEditor(recipe: Recipe.Combine, context: EditorContext) {
    val update = context.update
    val haptic = LocalHapticFeedback.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHeader(stringResource(R.string.source_list_title))
        val inputs = recipe.sourceIds.mapNotNull(context.data::source)
        if (inputs.isEmpty()) {
            EmptyReference(stringResource(R.string.source_slot_empty))
        } else {
            ReorderableColumn(
                list = inputs,
                onSettle = { from, to -> update(recipe.copy(sourceIds = inputs.map(Source::id).moved(from, to))) },
                onMove = { haptic.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick) },
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) { _, input, dragging ->
                key(input.id) {
                    ReorderableItem {
                        val elevation by animateDpAsState(if (dragging) 8.dp else 0.dp)
                        ReferenceRow(
                            source = input,
                            data = context.data,
                            onOpen = { context.open(Route.Editor(input.id, input.recipe.kind)) },
                            onRemove = { update(recipe.copy(sourceIds = recipe.sourceIds - input.id)) },
                            elevation = elevation,
                            handle = {
                                Icon(
                                    Icons.Filled.DragHandle,
                                    null,
                                    Modifier
                                        .draggableHandle(
                                            onDragStarted = { haptic.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate) },
                                            onDragStopped = { haptic.performHapticFeedback(HapticFeedbackType.GestureEnd) },
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
        (problemText(context, Problem.COMBINE_SOURCES) ?: problemText(context, Problem.INPUT_CYCLE) ?: problemText(context, Problem.INPUT_MISSING))
            ?.let { InlineError(it) }
        ReferenceActions(context.data.eligibleInputs(context.draft).filterNot { it.id in recipe.sourceIds }, keepOpen = true) {
            update(recipe.copy(sourceIds = recipe.sourceIds + it.id))
        }
    }
    OptionGroup(stringResource(R.string.selection_mode_title)) {
        for (mode in CombineMode.entries) {
            RadioRow(
                label = stringResource(mode.label),
                selected = mode == recipe.mode,
                onClick = { update(recipe.copy(mode = mode)) },
                detail = stringResource(mode.detail),
            )
        }
    }
}

@Composable
fun DeleteSourceDialog(source: Source, data: AppData, onDelete: () -> Unit, onDismiss: () -> Unit) {
    val users = data.sources.users(source.id)
    if (users.isEmpty()) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.dialog_delete_source_title, source.name)) },
            confirmButton = {
                TextButton(onClick = {
                    onDismiss()
                    onDelete()
                }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = { TextButton(onDismiss) { Text(stringResource(R.string.action_cancel)) } },
        )
    } else {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.dialog_delete_blocked_title)) },
            text = { Text(stringResource(R.string.dialog_delete_blocked_body, source.name, users.joinToString { it.name })) },
            confirmButton = { TextButton(onDismiss) { Text(stringResource(R.string.action_close)) } },
        )
    }
}
