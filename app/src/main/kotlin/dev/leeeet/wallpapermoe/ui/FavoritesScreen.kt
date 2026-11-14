package dev.leeeet.wallpapermoe.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.leeeet.wallpapermoe.R
import dev.leeeet.wallpapermoe.model.AppData
import dev.leeeet.wallpapermoe.model.Image
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyGridState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen(model: MoeViewModel, data: AppData, open: (Route) -> Unit, back: () -> Unit) {
    Scaffold(
        snackbarHost = { SnackbarHost(LocalSnackbar.current) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.favorites_title))
                        if (data.favorites.size > 1) {
                            Text(
                                stringResource(R.string.favorites_reorder_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (data.favorites.isEmpty()) {
                EmptyState(Icons.Filled.Favorite, stringResource(R.string.favorites_empty), Modifier.align(Alignment.Center))
            } else {
                FavoritesGrid(
                    favorites = data.favorites,
                    model = model,
                    onPreview = { open(Route.Viewer(favorites = true, imageId = it.id)) },
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            }
        }
    }
}

@Composable
private fun FavoritesGrid(favorites: List<Image>, model: MoeViewModel, onPreview: (Image) -> Unit, modifier: Modifier) {
    var ordered by remember(favorites) { mutableStateOf(favorites) }
    val haptics = LocalHapticFeedback.current
    val grid = rememberLazyGridState()
    val reorder = rememberReorderableLazyGridState(grid) { from, to ->
        ordered = ordered.moved(from.index, to.index)
        haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
    }
    val commit = { list: List<Image> -> model.orderFavorites(list.map(Image::id)) }
    val earlier = stringResource(R.string.action_move_earlier)
    val later = stringResource(R.string.action_move_later)

    LazyVerticalGrid(
        columns = GridCells.Adaptive(148.dp),
        modifier = modifier.contentWidth().fillMaxHeight(),
        state = grid,
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        itemsIndexed(ordered, key = { _, image -> image.id }) { index, image ->
            ReorderableItem(reorder, key = image.id) { dragging ->
                val elevation by animateDpAsState(if (dragging) 8.dp else 0.dp, label = "elevation")
                Surface(
                    shape = MaterialTheme.shapes.large,
                    shadowElevation = elevation,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(0.7f)
                        .longPressDraggableHandle(
                            onDragStarted = { haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate) },
                            onDragStopped = {
                                haptics.performHapticFeedback(HapticFeedbackType.GestureEnd)
                                commit(ordered)
                            },
                        )
                        .semantics {
                            customActions = listOfNotNull(
                                CustomAccessibilityAction(earlier) {
                                    ordered = ordered.moved(index, index - 1).also(commit)
                                    true
                                }.takeIf { index > 0 },
                                CustomAccessibilityAction(later) {
                                    ordered = ordered.moved(index, index + 1).also(commit)
                                    true
                                }.takeIf { index < ordered.lastIndex },
                            )
                        },
                ) {
                    WallpaperThumbnail(
                        image = image,
                        file = model.file(image),
                        favorite = true,
                        onClick = { onPreview(image) },
                        onFavorite = { model.removeFavorite(image) },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

fun <T> List<T>.moved(from: Int, to: Int): List<T> = toMutableList().apply { add(to, removeAt(from)) }
