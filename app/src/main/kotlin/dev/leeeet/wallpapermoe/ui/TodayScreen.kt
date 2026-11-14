package dev.leeeet.wallpapermoe.ui

import android.app.WallpaperManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.leeeet.wallpapermoe.service.renderEffect
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.leeeet.wallpapermoe.R
import dev.leeeet.wallpapermoe.model.AppData
import dev.leeeet.wallpapermoe.model.Image
import dev.leeeet.wallpapermoe.model.Screen
import dev.leeeet.wallpapermoe.source.Failure
import dev.leeeet.wallpapermoe.service.MoeWallpaperService
import dev.leeeet.wallpapermoe.service.Status
import java.io.File

private enum class PrimaryAction { CREATE_SOURCE, CHOOSE_SOURCE, ACTIVATE, CHANGE }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(model: MoeViewModel, data: AppData, open: (Route) -> Unit, select: (Tab) -> Unit) {
    val statuses by model.status.collectAsState()
    val active by model.active.collectAsState()
    var screen by rememberSaveable { mutableStateOf(Screen.HOME) }
    var choosingSource by rememberSaveable { mutableStateOf(false) }
    var clearing by rememberSaveable { mutableStateOf(false) }
    val target = data.feedOwner(screen)
    val status = statuses[target] ?: Status.Idle

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.nav_today)) })
        },
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(
                modifier = Modifier.contentWidth().align(Alignment.TopCenter),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item { ScreenSwitch(screen) { screen = it } }
                item {
                    Hero(
                        model = model,
                        data = data,
                        screen = screen,
                        status = status,
                        active = active,
                        onChange = { model.changeNow(target) },
                        onChooseSource = { choosingSource = true },
                        onCreateSource = { select(Tab.STUDIO) },
                        onSettings = { open(Route.ScreenSettings(screen)) },
                    )
                }
                if (status is Status.Failed) {
                    item { FailurePanel(status.failure.message(), active) { model.changeNow(target) } }
                }
                item {
                    HistorySection(
                        history = data.history,
                        isFavorite = data::isFavorite,
                        file = model::file,
                        onFavorites = { open(Route.Favorites) },
                        onClear = { clearing = true },
                        onPreview = { open(Route.Viewer(favorites = false, imageId = it.id)) },
                        onFavorite = model::toggleFavorite,
                    )
                }
            }
        }
    }

    if (choosingSource) {
        SourceSelectionSheet(data, target, onSelect = {
            model.chooseSource(target, it)
            choosingSource = false
        }, onDismiss = { choosingSource = false })
    }
    if (clearing) {
        AlertDialog(
            onDismissRequest = { clearing = false },
            title = { Text(stringResource(R.string.history_clear_title)) },
            text = { Text(stringResource(R.string.history_clear_body)) },
            confirmButton = {
                TextButton(onClick = {
                    model.clearHistory()
                    clearing = false
                }) { Text(stringResource(R.string.action_clear)) }
            },
            dismissButton = { TextButton({ clearing = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun ScreenSwitch(screen: Screen, onSelect: (Screen) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        Screen.entries.forEachIndexed { index, option ->
            SegmentedButton(
                selected = option == screen,
                onClick = { onSelect(option) },
                shape = SegmentedButtonDefaults.itemShape(index, Screen.entries.size),
            ) { Text(stringResource(option.label)) }
        }
    }
}

@Composable
private fun Hero(
    model: MoeViewModel,
    data: AppData,
    screen: Screen,
    status: Status,
    active: Boolean,
    onChange: () -> Unit,
    onChooseSource: () -> Unit,
    onCreateSource: () -> Unit,
    onSettings: () -> Unit,
) {
    val context = LocalContext.current
    val source = data.source(data.activeFeed(screen).sourceId)
    val current = data.shown(screen)?.image
    val appearance = data.appearance(screen)
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 380.dp)
            .clip(MaterialTheme.shapes.extraLarge)
            .background(
                Brush.linearGradient(listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.tertiaryContainer)),
            ),
    ) {
        if (current != null) {
            AsyncImage(
                model = model.file(current),
                contentDescription = stringResource(R.string.today_current_description),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer { renderEffect = appearance.renderEffect(size.width.toInt())?.asComposeRenderEffect() },
            )
        }
        Box(
            Modifier.matchParentSize().background(
                Brush.verticalGradient(
                    0f to Color.Transparent,
                    0.52f to Color.Black.copy(alpha = 0.34f),
                    0.7f to Color.Black.copy(alpha = 0.76f),
                    1f to Color.Black.copy(alpha = 0.9f),
                ),
            ),
        )
        StatusPill(status, source != null, active, Modifier.align(Alignment.TopStart).padding(16.dp))
        ScrimIconButton(
            icon = Icons.Filled.Tune,
            contentDescription = stringResource(R.string.screen_settings, stringResource(screen.label)),
            onClick = onSettings,
            modifier = Modifier.align(Alignment.TopEnd).padding(10.dp),
        )
        Column(Modifier.align(Alignment.BottomStart).padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    stringResource(screen.label),
                    style = MaterialTheme.typography.labelLarge.heroShadow(),
                    color = Color.White.copy(alpha = 0.85f),
                )
                Text(
                    source?.name ?: stringResource(R.string.today_no_active_source),
                    style = MaterialTheme.typography.headlineMedium.heroShadow(),
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (source != null) {
                    Text(
                        stringResource(source.recipe.kind.label),
                        style = MaterialTheme.typography.bodyMedium.heroShadow(),
                        color = Color.White.copy(alpha = 0.85f),
                    )
                }
            }
            val action = when {
                data.sources.isEmpty() -> PrimaryAction.CREATE_SOURCE
                source == null -> PrimaryAction.CHOOSE_SOURCE
                !active -> PrimaryAction.ACTIVATE
                else -> PrimaryAction.CHANGE
            }
            HeroActions(
                action = action,
                working = action == PrimaryAction.CHANGE && status == Status.Working,
                onPrimary = when (action) {
                    PrimaryAction.CREATE_SOURCE -> onCreateSource
                    PrimaryAction.CHOOSE_SOURCE -> onChooseSource
                    PrimaryAction.ACTIVATE -> { -> activate(context) }
                    PrimaryAction.CHANGE -> onChange
                },
                onChooseSource = onChooseSource,
            )
        }
    }
}

private fun TextStyle.heroShadow(): TextStyle = copy(shadow = Shadow(Color.Black.copy(alpha = 0.75f), blurRadius = 10f))

@Composable
private fun HeroActions(action: PrimaryAction, working: Boolean, onPrimary: () -> Unit, onChooseSource: () -> Unit) {
    val primary: @Composable (Modifier) -> Unit = { modifier ->
        Button(onClick = onPrimary, enabled = !working, modifier = modifier.height(50.dp), shape = MaterialTheme.shapes.medium) {
            if (working) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Icon(
                    when (action) {
                        PrimaryAction.CREATE_SOURCE, PrimaryAction.CHOOSE_SOURCE -> Icons.AutoMirrored.Filled.List
                        PrimaryAction.ACTIVATE -> Icons.Filled.Wallpaper
                        PrimaryAction.CHANGE -> Icons.Filled.Refresh
                    },
                    contentDescription = null,
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(
                    when (action) {
                        PrimaryAction.CREATE_SOURCE -> R.string.action_create_first_source
                        PrimaryAction.CHOOSE_SOURCE -> R.string.action_choose_source
                        PrimaryAction.ACTIVATE -> R.string.action_activate_wallpaper
                        PrimaryAction.CHANGE -> R.string.action_change_now
                    },
                ),
                maxLines = 1,
            )
        }
    }
    val secondary: @Composable (Modifier) -> Unit = { modifier ->
        OutlinedButton(
            onClick = onChooseSource,
            modifier = modifier.height(50.dp),
            shape = MaterialTheme.shapes.medium,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
        ) { Text(stringResource(R.string.action_choose_source), maxLines = 1) }
    }
    val withChooser = action == PrimaryAction.ACTIVATE || action == PrimaryAction.CHANGE
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        when {
            !withChooser -> primary(Modifier.fillMaxWidth())
            maxWidth < 360.dp * fontScale -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                primary(Modifier.fillMaxWidth())
                secondary(Modifier.fillMaxWidth())
            }
            else -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                primary(Modifier.weight(1f))
                secondary(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun StatusPill(status: Status, hasSource: Boolean, active: Boolean, modifier: Modifier) {
    val label = stringResource(
        when {
            status == Status.Working -> R.string.today_sampling
            status is Status.Failed && status.failure == Failure.Metered -> R.string.status_waiting
            status is Status.Failed -> R.string.today_needs_attention
            !hasSource -> R.string.today_not_ready
            active -> R.string.today_ready
            else -> R.string.today_inactive
        },
    )
    Surface(modifier = modifier, color = Color.Black.copy(alpha = 0.42f), contentColor = Color.White, shape = CircleShape) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (status == Status.Working) {
                CircularProgressIndicator(Modifier.size(14.dp), color = Color.White, strokeWidth = 2.dp)
            } else {
                Box(Modifier.size(8.dp).clip(CircleShape).background(Color.White))
            }
            Text(label, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun FailurePanel(message: String, active: Boolean, onRetry: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(start = 16.dp, top = 16.dp, end = 8.dp, bottom = 6.dp)) {
            Text(message, style = MaterialTheme.typography.bodyMedium)
            if (active) {
                TextButton(onClick = onRetry, modifier = Modifier.align(Alignment.End)) { Text(stringResource(R.string.action_try_again)) }
            } else {
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HistorySection(
    history: List<Image>,
    isFavorite: (Image) -> Boolean,
    file: (Image) -> File,
    onFavorites: () -> Unit,
    onClear: () -> Unit,
    onPreview: (Image) -> Unit,
    onFavorite: (Image) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.history_title), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            if (history.isNotEmpty()) TextButton(onClick = onClear) { Text(stringResource(R.string.action_clear)) }
            OutlinedButton(onClick = onFavorites) {
                Icon(Icons.Filled.Favorite, null, Modifier.size(18.dp))
                Text(stringResource(R.string.favorites_title), Modifier.padding(start = 6.dp))
            }
        }
        if (history.isEmpty()) {
            Text(
                stringResource(R.string.history_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val gap = 10.dp
                val columns = ((maxWidth + gap) / (148.dp + gap)).toInt().coerceAtLeast(2)
                val width = (maxWidth - gap * (columns - 1) - columns.dp) / columns
                FlowRow(horizontalArrangement = Arrangement.spacedBy(gap), verticalArrangement = Arrangement.spacedBy(gap)) {
                    for (image in history) {
                        WallpaperThumbnail(
                            image = image,
                            file = file(image),
                            favorite = isFavorite(image),
                            onClick = { onPreview(image) },
                            onFavorite = { onFavorite(image) },
                            modifier = Modifier.size(width, width / 0.7f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun WallpaperThumbnail(image: Image, file: File, favorite: Boolean, onClick: () -> Unit, onFavorite: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onClick),
    ) {
        AsyncImage(
            model = file,
            contentDescription = image.artist?.let { stringResource(R.string.wallpaper_thumbnail_by_artist, it) }
                ?: stringResource(R.string.wallpaper_thumbnail_description),
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        FavoriteButton(favorite, onFavorite, Modifier.align(Alignment.TopEnd).padding(6.dp))
    }
}

@Composable
fun SourceSelectionSheet(data: AppData, screen: Screen, onSelect: (String) -> Unit, onDismiss: () -> Unit) {
    val current = data.activeFeed(screen).sourceId
    val title = when {
        data.lock == null -> R.string.today_source_picker_title
        screen == Screen.HOME -> R.string.picker_title_home
        else -> R.string.picker_title_lock
    }
    MoeSheet(stringResource(title), onDismiss) {
        Text(
            stringResource(R.string.today_source_picker_detail),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (data.sources.isEmpty()) {
            Text(stringResource(R.string.picker_empty), style = MaterialTheme.typography.bodyMedium)
        } else {
            SheetList(Arrangement.spacedBy(8.dp)) {
                items(data.sources.size, key = { data.sources[it].id }) { index ->
                    val source = data.sources[index]
                    SourceRow(
                        kind = source.recipe.kind,
                        title = source.name,
                        detail = stringResource(source.recipe.kind.label),
                        onClick = { onSelect(source.id) },
                    ) { RadioButton(selected = source.id == current, onClick = null) }
                }
            }
        }
    }
}

private fun activate(context: Context) {
    val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
        .putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, MoeWallpaperService.component(context))
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        try {
            context.startActivity(Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, R.string.activate_unavailable, Toast.LENGTH_LONG).show()
        }
    }
}
