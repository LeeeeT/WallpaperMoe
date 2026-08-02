@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)

package dev.leeeet.wallpapermoe.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest
import dev.leeeet.wallpapermoe.R
import dev.leeeet.wallpapermoe.net.APP_USER_AGENT
import dev.leeeet.wallpapermoe.wallpaper.SavedWallpaper

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
            model = wallpaper.prepared,
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
) {
    FullScreenDialog(onDismiss) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.favorites_title)) },
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
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(favorites, key = SavedWallpaper::id) { wallpaper ->
                        WallpaperThumbnail(
                            wallpaper = wallpaper,
                            favorite = wallpaper.id !in pendingRemovals,
                            onPreview = { onPreview(wallpaper) },
                            onFavorite = { onToggleRemoval(wallpaper) },
                            modifier = Modifier.fillMaxWidth().aspectRatio(.7f),
                        )
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
) {
    FullScreenDialog(onDismiss) {
        val context = LocalContext.current
        var loading by remember(wallpaper.id) { mutableStateOf(true) }
        var failed by remember(wallpaper.id) { mutableStateOf(false) }
        val preview = remember(wallpaper.id) {
            wallpaper.metadata.imageUrl?.let { url ->
                ImageRequest.Builder(context)
                    .data(url)
                    .httpHeaders(
                        NetworkHeaders.Builder()
                            .set("User-Agent", APP_USER_AGENT)
                            .build(),
                    )
                    .build()
            } ?: wallpaper.prepared
        }
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            AsyncImage(
                model = preview,
                contentDescription = stringResource(R.string.wallpaper_preview_description),
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
                onLoading = {
                    loading = true
                    failed = false
                },
                onSuccess = {
                    loading = false
                    failed = false
                },
                onError = {
                    loading = false
                    failed = true
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
            FavoriteButton(
                favorite = favorite,
                onClick = onFavorite,
                modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(12.dp),
            )
            WallpaperAttribution(wallpaper, Modifier.align(Alignment.BottomCenter))
        }
    }
}

@Composable
private fun WallpaperAttribution(wallpaper: SavedWallpaper, modifier: Modifier = Modifier) {
    val metadata = wallpaper.metadata
    if (metadata.artistName == null && metadata.artistUrl == null && metadata.sourceUrl == null) return
    val uriHandler = LocalUriHandler.current
    Surface(
        modifier = modifier.navigationBarsPadding().padding(12.dp),
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
                TextButton(onClick = { runCatching { uriHandler.openUri(metadata.artistUrl) } }) {
                    Text(artistLabel, color = Color.White)
                }
            } else if (metadata.artistName != null) {
                Text(artistLabel, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
            }
            metadata.sourceUrl?.let { sourceUrl ->
                TextButton(onClick = { runCatching { uriHandler.openUri(sourceUrl) } }) {
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
