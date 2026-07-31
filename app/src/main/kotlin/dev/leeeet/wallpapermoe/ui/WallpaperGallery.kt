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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import dev.leeeet.wallpapermoe.R
import dev.leeeet.wallpapermoe.wallpaper.SavedWallpaper

@Composable
internal fun HistorySection(
    history: List<SavedWallpaper>,
    favoriteUrls: Set<String>,
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
                        favorite = wallpaper.originalUrl in favoriteUrls,
                        onPreview = { onPreview(wallpaper) },
                        onFavorite = {
                            onFavorite(wallpaper, wallpaper.originalUrl in favoriteUrls)
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
                            favorite = wallpaper.originalUrl !in pendingRemovals,
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
        var loading by remember(wallpaper.originalUrl) { mutableStateOf(true) }
        var failed by remember(wallpaper.originalUrl) { mutableStateOf(false) }
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            AsyncImage(
                model = wallpaper.originalUrl,
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
