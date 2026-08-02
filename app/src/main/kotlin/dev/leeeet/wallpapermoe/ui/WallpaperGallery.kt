@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)

package dev.leeeet.wallpapermoe.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import dev.leeeet.wallpapermoe.R
import dev.leeeet.wallpapermoe.wallpaper.GallerySaveResult
import dev.leeeet.wallpapermoe.wallpaper.SavedWallpaper
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyGridState

@Composable
internal fun HistorySection(
    history: List<SavedWallpaper>,
    favoriteIds: Set<String>,
    onOpenFavorites: () -> Unit,
    onPreview: (SavedWallpaper) -> Unit,
    onFavorite: (SavedWallpaper, Boolean) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.history_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            Button(onClick = onOpenFavorites) {
                Icon(Icons.Filled.Favorite, contentDescription = null, modifier = Modifier.size(18.dp))
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
                val spacing = 10.dp
                val columns = ((maxWidth + spacing) / (148.dp + spacing)).toInt().coerceAtLeast(2)
                // Leave one physical rounding margin per column so FlowRow never wraps an item
                // whose exact dp widths land a fraction of a pixel over the available width.
                val itemWidth = (maxWidth - spacing * (columns - 1) - columns.dp) / columns
                FlowRow(
                    maxItemsInEachRow = columns,
                    horizontalArrangement = Arrangement.spacedBy(spacing),
                    verticalArrangement = Arrangement.spacedBy(spacing),
                ) {
                    history.forEach { wallpaper ->
                    WallpaperThumbnail(
                        wallpaper = wallpaper,
                        favorite = wallpaper.id in favoriteIds,
                        onPreview = { onPreview(wallpaper) },
                        onFavorite = {
                            onFavorite(wallpaper, wallpaper.id in favoriteIds)
                        },
                            modifier = Modifier.size(width = itemWidth, height = itemWidth / .7f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WallpaperThumbnail(
    wallpaper: SavedWallpaper,
    favorite: Boolean,
    onPreview: () -> Unit,
    onFavorite: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onPreview),
    ) {
        AsyncImage(
            model = wallpaper.file,
            contentDescription = stringResource(R.string.wallpaper_thumbnail_description),
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        FavoriteButton(
            favorite = favorite,
            onClick = onFavorite,
            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
        )
    }
}

@Composable
internal fun FavoriteButton(
    favorite: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.size(42.dp),
        shape = CircleShape,
        color = if (favorite) MaterialTheme.colorScheme.errorContainer else Color.Black.copy(alpha = .52f),
        contentColor = if (favorite) MaterialTheme.colorScheme.error else Color.White,
        shadowElevation = 2.dp,
    ) {
        IconButton(onClick = onClick) {
            Icon(
                if (favorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                contentDescription = stringResource(
                    if (favorite) R.string.action_remove_favorite else R.string.action_add_favorite,
                ),
            )
        }
    }
}

@Composable
internal fun FavoritesDialog(
    favorites: List<SavedWallpaper>,
    pendingRemovals: Set<String>,
    onDismiss: () -> Unit,
    onPreview: (SavedWallpaper) -> Unit,
    onToggleRemoval: (SavedWallpaper) -> Unit,
    onReorder: (List<String>) -> Unit,
) {
    var ordered by remember(favorites.map(SavedWallpaper::id)) { mutableStateOf(favorites) }
    val gridState = rememberLazyGridState()
    val haptic = LocalHapticFeedback.current
    val reorderState = rememberReorderableLazyGridState(gridState) { from, to ->
        ordered = ordered.moved(from.index, to.index)
        haptic.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
    }
    FullScreenDialog(onDismiss) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(stringResource(R.string.favorites_title))
                            Text(
                                stringResource(R.string.favorites_reorder_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.action_back),
                            )
                        }
                    },
                )
            },
        ) { padding ->
            if (favorites.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(padding).padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(R.string.favorites_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(148.dp),
                    modifier = Modifier.fillMaxSize().padding(padding),
                    state = gridState,
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    itemsIndexed(ordered, key = { _, wallpaper -> wallpaper.id }) { index, wallpaper ->
                        ReorderableItem(reorderState, key = wallpaper.id) { isDragging ->
                            val elevation by animateDpAsState(if (isDragging) 8.dp else 0.dp)
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(.7f)
                                    .longPressDraggableHandle(
                                        onDragStarted = {
                                            haptic.performHapticFeedback(
                                                HapticFeedbackType.GestureThresholdActivate,
                                            )
                                        },
                                        onDragStopped = {
                                            haptic.performHapticFeedback(HapticFeedbackType.GestureEnd)
                                            onReorder(ordered.map(SavedWallpaper::id))
                                        },
                                    )
                                    .reorderActions(
                                        onMoveEarlier = if (index == 0) null else {
                                            {
                                                ordered = ordered.moved(index, index - 1).also {
                                                    onReorder(it.map(SavedWallpaper::id))
                                                }
                                            }
                                        },
                                        onMoveLater = if (index == ordered.lastIndex) null else {
                                            {
                                                ordered = ordered.moved(index, index + 1).also {
                                                    onReorder(it.map(SavedWallpaper::id))
                                                }
                                            }
                                        },
                                    ),
                                shape = MaterialTheme.shapes.large,
                                shadowElevation = elevation,
                            ) {
                                WallpaperThumbnail(
                                    wallpaper = wallpaper,
                                    favorite = wallpaper.id !in pendingRemovals,
                                    onPreview = { onPreview(wallpaper) },
                                    onFavorite = { onToggleRemoval(wallpaper) },
                                    modifier = Modifier.fillMaxSize(),
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
internal fun WallpaperPreviewDialog(
    wallpaper: SavedWallpaper,
    favorite: Boolean,
    onDismiss: () -> Unit,
    onFavorite: () -> Unit,
    onSave: ((GallerySaveResult) -> Unit) -> Unit,
) {
    FullScreenDialog(onDismiss) {
        val snackbarHostState = remember { SnackbarHostState() }
        val scope = rememberCoroutineScope()
        val savedMessage = stringResource(R.string.gallery_save_success)
        val alreadySavedMessage = stringResource(R.string.gallery_save_already_saved)
        val failedMessage = stringResource(R.string.gallery_save_failure)
        val linkFailedMessage = stringResource(R.string.link_open_failure)
        val uriHandler = LocalUriHandler.current
        var loading by remember(wallpaper.id) { mutableStateOf(true) }
        var failed by remember(wallpaper.id) { mutableStateOf(false) }
        var saving by remember(wallpaper.id) { mutableStateOf(false) }
        var imageReady by remember(wallpaper.id) { mutableStateOf(false) }
        var transform by remember(wallpaper.id) { mutableStateOf(ImageTransform()) }
        var viewport by remember(wallpaper.id) { mutableStateOf(IntSize.Zero) }
        var imageSize by remember(wallpaper.id) { mutableStateOf(Size.Zero) }
        fun showMessage(message: String) {
            scope.launch {
                snackbarHostState.currentSnackbarData?.dismiss()
                snackbarHostState.showSnackbar(message, duration = SnackbarDuration.Short)
            }
        }
        fun openUri(uri: String) {
            try {
                uriHandler.openUri(uri)
            } catch (_: Exception) {
                showMessage(linkFailedMessage)
            }
        }
        LaunchedEffect(viewport, imageSize) {
            transform = transform.copy(
                offset = clampZoomOffset(transform.offset, transform.scale, viewport, imageSize),
            )
        }
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            AsyncImage(
                model = wallpaper.file,
                contentDescription = stringResource(R.string.wallpaper_preview_description),
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .onSizeChanged { viewport = it }
                    .pointerInput(wallpaper.id, viewport, imageSize) {
                        detectTransformGestures { centroid, pan, zoom, _ ->
                            transform = transform.withGesture(centroid, pan, zoom, viewport, imageSize)
                        }
                    }
                    .graphicsLayer {
                        scaleX = transform.scale
                        scaleY = transform.scale
                        translationX = transform.offset.x
                        translationY = transform.offset.y
                    },
                onLoading = {
                    loading = true
                    failed = false
                    imageReady = false
                },
                onSuccess = { state ->
                    loading = false
                    failed = false
                    imageReady = true
                    state.painter.intrinsicSize.takeIf(Size::hasArea)?.let { imageSize = it }
                },
                onError = {
                    loading = false
                    failed = true
                    imageReady = false
                },
            )
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = Color.White,
                )
            }
            if (failed) {
                Text(
                    stringResource(R.string.preview_unavailable),
                    modifier = Modifier.align(Alignment.Center).padding(32.dp),
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White,
                )
            }
            Surface(
                modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(12.dp).size(42.dp),
                shape = CircleShape,
                color = Color.Black.copy(alpha = .52f),
                contentColor = Color.White,
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_close))
                }
            }
            Row(
                modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Surface(
                    modifier = Modifier.size(42.dp),
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = .52f),
                    contentColor = Color.White,
                    shadowElevation = 2.dp,
                ) {
                    IconButton(
                        enabled = imageReady && !saving,
                        onClick = {
                            saving = true
                            onSave { result ->
                                saving = false
                                showMessage(
                                    when (result) {
                                        GallerySaveResult.Saved -> savedMessage
                                        GallerySaveResult.AlreadySaved -> alreadySavedMessage
                                        GallerySaveResult.Failed -> failedMessage
                                    },
                                )
                            }
                        },
                    ) {
                        Icon(
                            Icons.Filled.SaveAlt,
                            contentDescription = stringResource(R.string.action_save_to_gallery),
                        )
                    }
                }
                FavoriteButton(favorite = favorite, onClick = onFavorite)
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SnackbarHost(snackbarHostState, Modifier.fillMaxWidth())
                WallpaperAttribution(wallpaper, onOpenUri = ::openUri)
            }
        }
    }
}

@Composable
private fun WallpaperAttribution(
    wallpaper: SavedWallpaper,
    onOpenUri: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val metadata = wallpaper.metadata
    if (metadata.artistName == null && metadata.artistUrl == null && metadata.sourceUrl == null) return
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = Color.Black.copy(alpha = .72f),
        contentColor = Color.White,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val artistLabel = metadata.artistName?.let {
                stringResource(R.string.wallpaper_artist, it)
            } ?: stringResource(R.string.wallpaper_artist_unknown)
            if (metadata.artistUrl != null) {
                TextButton(onClick = { onOpenUri(metadata.artistUrl) }) {
                    Text(artistLabel, color = Color.White)
                }
            } else if (metadata.artistName != null) {
                Text(artistLabel, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
            }
            metadata.sourceUrl?.let { sourceUrl ->
                TextButton(onClick = { onOpenUri(sourceUrl) }) {
                    Text(stringResource(R.string.action_view_source), color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun FullScreenDialog(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Surface(Modifier.fillMaxSize().navigationBarsPadding(), color = MaterialTheme.colorScheme.surface) {
            content()
        }
    }
}
