@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package dev.leeeet.wallpapermoe.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import dev.leeeet.wallpapermoe.R
import dev.leeeet.wallpapermoe.Rotator
import dev.leeeet.wallpapermoe.data.RotationInterval
import dev.leeeet.wallpapermoe.source.SourceDefinition
import dev.leeeet.wallpapermoe.source.kind

@Composable
fun MoeRoot(state: UiState, vm: MoeViewModel) {
    BackHandler(
        enabled = state.sourceEditorOpen && state.pendingSourceExit == null,
        onBack = vm::requestEditorBack,
    )

    if (state.sourceEditorOpen) {
        SourceScreen(state, vm)
    } else {
        BoxWithConstraints(
            Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface),
        ) {
            if (maxWidth >= MoeNavigationRailBreakpoint) {
                Row(Modifier.fillMaxSize()) {
                    AppNavigationRail(state.tab, vm::selectTab)
                    DestinationContent(state, vm, Modifier.weight(1f))
                }
            } else {
                Scaffold(
                    containerColor = MaterialTheme.colorScheme.surface,
                    bottomBar = { AppNavigation(state.tab, vm::selectTab) },
                ) { padding ->
                    DestinationContent(state, vm, Modifier.padding(padding))
                }
            }
        }
    }

    if (state.pendingSourceExit != null) {
        AlertDialog(
            onDismissRequest = vm::cancelSourceExit,
            icon = { Icon(Icons.Filled.Refresh, contentDescription = null) },
            title = { Text(stringResource(R.string.dialog_unsaved_title)) },
            text = { Text(stringResource(R.string.dialog_unsaved_body)) },
            confirmButton = {
                TextButton(onClick = vm::discardAndExit) {
                    Text(stringResource(R.string.action_discard_changes))
                }
            },
            dismissButton = {
                TextButton(onClick = vm::cancelSourceExit) {
                    Text(stringResource(R.string.action_keep_editing))
                }
            },
        )
    }
}

@Composable
private fun DestinationContent(state: UiState, vm: MoeViewModel, modifier: Modifier = Modifier) {
    AnimatedContent(
        targetState = state.tab,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "destination",
        modifier = modifier.fillMaxSize(),
    ) { tab ->
        when (tab) {
            Tab.NOW -> NowScreen(state, vm)
            Tab.SOURCE -> SourceScreen(state, vm)
            Tab.SETTINGS -> SettingsScreen(state, vm)
        }
    }
}

@Composable
private fun AppNavigation(selected: Tab, onSelect: (Tab) -> Unit) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Tab.entries.forEach { tab ->
            val label = tab.localizedLabel()
            NavigationBarItem(
                selected = selected == tab,
                onClick = { onSelect(tab) },
                icon = {
                    Icon(
                        imageVector = when (tab) {
                            Tab.NOW -> Icons.Filled.Home
                            Tab.SOURCE -> Icons.AutoMirrored.Filled.List
                            Tab.SETTINGS -> Icons.Filled.Settings
                        },
                        contentDescription = label,
                    )
                },
                label = { Text(label, maxLines = 1) },
                colors = NavigationBarItemDefaults.colors(
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                ),
            )
        }
    }
}

@Composable
private fun AppNavigationRail(selected: Tab, onSelect: (Tab) -> Unit) {
    NavigationRail(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
        Spacer(Modifier.weight(1f))
        Tab.entries.forEach { tab ->
            val label = tab.localizedLabel()
            NavigationRailItem(
                selected = selected == tab,
                onClick = { onSelect(tab) },
                icon = {
                    Icon(
                        imageVector = when (tab) {
                            Tab.NOW -> Icons.Filled.Home
                            Tab.SOURCE -> Icons.AutoMirrored.Filled.List
                            Tab.SETTINGS -> Icons.Filled.Settings
                        },
                        contentDescription = label,
                    )
                },
                label = { Text(label, maxLines = 1) },
            )
        }
        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun NowScreen(state: UiState, vm: MoeViewModel) {
    val active = state.sourceLibrary.sources.firstOrNull {
        it.id == state.sourceLibrary.activeSourceId
    }
    var choosingSource by remember { mutableStateOf(false) }
    var showingFavorites by remember { mutableStateOf(false) }
    var previewing by remember { mutableStateOf<dev.leeeet.wallpapermoe.wallpaper.SavedWallpaper?>(null) }
    var pendingFavoriteRemovals by remember { mutableStateOf<Set<String>>(emptySet()) }
    val favoriteUrls = state.favorites.mapTo(mutableSetOf()) { it.originalUrl }

    fun closeFavorites() {
        val removals = pendingFavoriteRemovals
        showingFavorites = false
        previewing = null
        pendingFavoriteRemovals = emptySet()
        if (removals.isNotEmpty()) vm.removeFavorites(removals)
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxHeight()
                .widthIn(max = MoeContentMaxWidth)
                .fillMaxWidth()
                .align(Alignment.TopCenter),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 16.dp,
                top = 16.dp,
                end = 16.dp,
                bottom = 20.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                WallpaperHero(
                    state = state,
                    active = active,
                    onChooseSource = { choosingSource = true },
                    onRotate = vm::rotateNow,
                    current = state.history.firstOrNull(),
                    currentFavorite = state.history.firstOrNull()?.let {
                        it.originalUrl in favoriteUrls
                    } ?: false,
                    onFavorite = { wallpaper, favorite ->
                        if (favorite) vm.removeFavorite(wallpaper.originalUrl)
                        else vm.addFavorite(wallpaper)
                    },
                )
            }

            item {
                HistorySection(
                    history = state.history,
                    favoriteUrls = favoriteUrls,
                    onOpenFavorites = {
                        pendingFavoriteRemovals = emptySet()
                        showingFavorites = true
                    },
                    onPreview = { previewing = it },
                    onFavorite = { wallpaper, favorite ->
                        if (favorite) vm.removeFavorite(wallpaper.originalUrl)
                        else vm.addFavorite(wallpaper)
                    },
                )
            }

            item { SetupCard(rememberSetupState()) }

            item {
                StatusPanel(state)
            }

        }
    }

    if (choosingSource) {
        SourceSelectionSheet(
            state = state,
            onDismiss = { choosingSource = false },
            onSelect = {
                vm.setActiveSource(it.id)
                choosingSource = false
            },
            onOpenStudio = {
                choosingSource = false
                vm.selectTab(Tab.SOURCE)
            },
        )
    }


    if (showingFavorites) {
        FavoritesDialog(
            favorites = state.favorites,
            pendingRemovals = pendingFavoriteRemovals,
            onDismiss = ::closeFavorites,
            onPreview = { previewing = it },
            onToggleRemoval = { wallpaper ->
                pendingFavoriteRemovals = pendingFavoriteRemovals.toggle(wallpaper.originalUrl)
            },
        )
    }

    previewing?.let { wallpaper ->
        WallpaperPreviewDialog(
            wallpaper = wallpaper,
            favorite = if (showingFavorites && wallpaper.originalUrl in favoriteUrls) {
                wallpaper.originalUrl !in pendingFavoriteRemovals
            } else {
                wallpaper.originalUrl in favoriteUrls
            },
            onDismiss = { previewing = null },
            onFavorite = {
                if (showingFavorites && wallpaper.originalUrl in favoriteUrls) {
                    pendingFavoriteRemovals = pendingFavoriteRemovals.toggle(wallpaper.originalUrl)
                } else if (wallpaper.originalUrl in favoriteUrls) {
                    vm.removeFavorite(wallpaper.originalUrl)
                    previewing = state.history.firstOrNull { it.originalUrl == wallpaper.originalUrl }
                } else {
                    vm.addFavorite(wallpaper)
                }
            },
        )
    }
}

private fun Set<String>.toggle(value: String): Set<String> =
    if (value in this) this - value else this + value

@Composable
private fun WallpaperHero(
    state: UiState,
    active: SourceDefinition?,
    onChooseSource: () -> Unit,
    onRotate: () -> Unit,
    current: dev.leeeet.wallpapermoe.wallpaper.SavedWallpaper?,
    currentFavorite: Boolean,
    onFavorite: (dev.leeeet.wallpapermoe.wallpaper.SavedWallpaper, Boolean) -> Unit,
) {
    val context = LocalContext.current
    Box(
        Modifier
            .fillMaxWidth()
            .height(380.dp)
            .clip(MaterialTheme.shapes.extraLarge)
            .background(
                Brush.linearGradient(
                    listOf(
                        MaterialTheme.colorScheme.primaryContainer,
                        MaterialTheme.colorScheme.tertiaryContainer,
                    ),
                ),
            ),
    ) {
        state.currentWallpaper?.let { file ->
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(file)
                    .memoryCacheKey("current-${state.wallpaperVersion}")
                    .diskCacheKey("current-${state.wallpaperVersion}")
                    .build(),
                contentDescription = stringResource(R.string.now_current_wallpaper_description),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = if (state.currentWallpaper == null) .12f else .03f),
                    .48f to Color.Transparent,
                    1f to Color.Black.copy(alpha = .78f),
                ),
            ),
        )

        StatusPill(
            state = state,
            modifier = Modifier.align(Alignment.TopStart).padding(16.dp),
        )

        current?.let { wallpaper ->
            FavoriteButton(
                favorite = currentFavorite,
                onClick = { onFavorite(wallpaper, currentFavorite) },
                modifier = Modifier.align(Alignment.TopEnd).padding(12.dp),
            )
        }

        Column(
            modifier = Modifier.align(Alignment.BottomStart).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    stringResource(R.string.now_active_source),
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White.copy(alpha = .76f),
                )
                Text(
                    active?.name ?: stringResource(R.string.now_no_active_source_short),
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                active?.let {
                    Text(
                        it.recipe.kind.localizedName(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = .76f),
                    )
                }
            }
            HeroActions(state, onChooseSource, onRotate)
        }
    }
}

@Composable
private fun HeroActions(
    state: UiState,
    onChooseSource: () -> Unit,
    onRotate: () -> Unit,
) {
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth < 360.dp * fontScale) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ChangeNowButton(state, onRotate, Modifier.fillMaxWidth())
                ChooseSourceButton(onChooseSource, Modifier.fillMaxWidth())
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ChangeNowButton(state, onRotate, Modifier.weight(1f))
                ChooseSourceButton(onChooseSource, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ChangeNowButton(state: UiState, onRotate: () -> Unit, modifier: Modifier) {
    Button(
        onClick = onRotate,
        enabled = state.source != null && state.settings.enabled && state.status !is Rotator.Status.Working,
        modifier = modifier.height(50.dp),
        shape = MaterialTheme.shapes.medium,
    ) {
        if (state.status is Rotator.Status.Working) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        } else {
            Icon(Icons.Filled.Refresh, contentDescription = null)
        }
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.action_change_now), maxLines = 1)
    }
}

@Composable
private fun ChooseSourceButton(onChooseSource: () -> Unit, modifier: Modifier) {
    Button(
        onClick = onChooseSource,
        modifier = modifier.height(50.dp),
        shape = MaterialTheme.shapes.medium,
    ) {
        Text(stringResource(R.string.action_choose_active_source), maxLines = 1)
    }
}

@Composable
private fun StatusPill(state: UiState, modifier: Modifier = Modifier) {
    val (label, working) = when {
        state.status is Rotator.Status.Working -> stringResource(R.string.now_sampling) to true
        state.status is Rotator.Status.Failed -> stringResource(R.string.now_needs_attention) to false
        state.source == null -> stringResource(R.string.now_not_ready) to false
        else -> stringResource(R.string.now_ready) to false
    }
    Surface(
        modifier = modifier,
        color = Color.Black.copy(alpha = .42f),
        contentColor = Color.White,
        shape = CircleShape,
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (working) CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = Color.White)
            else Box(Modifier.size(8.dp).clip(CircleShape).background(Color.White))
            Text(label, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun StatusPanel(state: UiState) {
    AnimatedVisibility(
        visible = state.engineBlocked || state.status is Rotator.Status.Failed,
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        val message = when {
            state.engineBlocked -> stringResource(R.string.now_engine_blocked)
            state.status is Rotator.Status.Failed -> state.status.reason.localizedMessage()
            else -> ""
        }
        Surface(
            color = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
            shape = MaterialTheme.shapes.medium,
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.now_needs_attention), style = MaterialTheme.typography.titleMedium)
                Text(message, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun SourceSelectionSheet(
    state: UiState,
    onDismiss: () -> Unit,
    onSelect: (SourceDefinition) -> Unit,
    onOpenStudio: () -> Unit,
) {
    val listMaxHeight = LocalConfiguration.current.screenHeightDp.dp * .55f
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = expandedSheetState()) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(stringResource(R.string.now_active_source_picker_title), style = MaterialTheme.typography.titleLarge)
            Text(
                stringResource(R.string.now_source_picker_detail),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (state.sourceLibrary.sources.isEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Text(
                        stringResource(R.string.now_no_sources),
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = listMaxHeight),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.sourceLibrary.sources, key = SourceDefinition::id) { definition ->
                        val selected = definition.id == state.sourceLibrary.activeSourceId
                        Surface(
                            modifier = Modifier.fillMaxWidth().clickable { onSelect(definition) },
                            shape = MaterialTheme.shapes.medium,
                            color = if (selected) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceContainer,
                        ) {
                            Row(
                                Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                SourceGlyph(definition.recipe.kind.localizedName().take(1))
                                Column(Modifier.weight(1f)) {
                                    Text(definition.name, style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        definition.recipe.kind.localizedName(),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                RadioButton(selected = selected, onClick = { onSelect(definition) })
                            }
                        }
                    }
                }
            }
            Button(onClick = onOpenStudio, modifier = Modifier.align(Alignment.End)) {
                Text(stringResource(R.string.action_open_source_studio))
            }
        }
    }
}

/**
 * Sheets here are short enough to show whole, so they skip the half-open state
 * and never ask for a drag before the content below the fold is reachable.
 */
@Composable
internal fun expandedSheetState(): SheetState = rememberBottomSheetState(
    initialValue = SheetValue.Hidden,
    enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
)

@Composable
internal fun SourceGlyph(letter: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.size(40.dp),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(letter.uppercase(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SettingsScreen(state: UiState, vm: MoeViewModel) {
    var choosingInterval by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxHeight()
                .widthIn(max = MoeContentMaxWidth)
                .fillMaxWidth()
                .align(Alignment.TopCenter),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp, 16.dp, 16.dp, 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { SetupCard(rememberSetupState()) }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionHeading(stringResource(R.string.settings_destinations), stringResource(R.string.settings_destinations_detail))
                    DestinationToggle(
                        title = stringResource(R.string.settings_lock_screen),
                        detail = stringResource(R.string.settings_lock_screen_detail),
                        checked = state.settings.applyToLock,
                        onChange = vm::setApplyToLock,
                    )
                    DestinationToggle(
                        title = stringResource(R.string.settings_home_screen),
                        detail = stringResource(R.string.settings_home_screen_detail),
                        checked = state.settings.applyToHome,
                        onChange = vm::setApplyToHome,
                    )
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionHeading(stringResource(R.string.settings_how_often), stringResource(R.string.settings_interval_detail))
                    Surface(
                        modifier = Modifier.fillMaxWidth().clickable { choosingInterval = true },
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    ) {
                        Row(
                            Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(state.settings.interval.localizedLabel(), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null)
                        }
                    }
                }
            }
            item {
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.settings_footer),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (choosingInterval) {
        ModalBottomSheet(
            onDismissRequest = { choosingInterval = false },
            sheetState = expandedSheetState(),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(stringResource(R.string.settings_choose_rhythm), style = MaterialTheme.typography.titleLarge)
                LazyColumn(
                    modifier = Modifier.heightIn(max = LocalConfiguration.current.screenHeightDp.dp * .6f),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(RotationInterval.entries, key = { it }) { option ->
                        val selected = state.settings.interval == option
                        Surface(
                            modifier = Modifier.fillMaxWidth().clickable {
                                vm.setInterval(option)
                                choosingInterval = false
                            },
                            shape = MaterialTheme.shapes.medium,
                            color = if (selected) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceContainer,
                        ) {
                            Row(
                                Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(option.localizedLabel(), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                                RadioButton(selected = selected, onClick = null)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DestinationToggle(title: String, detail: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Card(
        onClick = { onChange(!checked) },
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = checked, onCheckedChange = onChange)
        }
    }
}

@Composable
internal fun SectionHeading(title: String, detail: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        detail?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
