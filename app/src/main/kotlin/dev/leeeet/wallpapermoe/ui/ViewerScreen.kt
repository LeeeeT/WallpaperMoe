package dev.leeeet.wallpapermoe.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import dev.leeeet.wallpapermoe.R
import me.saket.telephoto.zoomable.DoubleClickToZoomListener
import me.saket.telephoto.zoomable.ZoomLimit
import me.saket.telephoto.zoomable.ZoomSpec
import me.saket.telephoto.zoomable.coil3.ZoomableAsyncImage
import me.saket.telephoto.zoomable.rememberZoomableImageState
import me.saket.telephoto.zoomable.rememberZoomableState
import dev.leeeet.wallpapermoe.model.AppData
import dev.leeeet.wallpapermoe.model.Image
import dev.leeeet.wallpapermoe.model.Screen

@Composable
fun ViewerScreen(route: Route.Viewer, model: MoeViewModel, data: AppData, back: () -> Unit) {
    val images = remember {
        if (route.favorites) data.favorites else (data.shown.values.map { it.image } + data.history).distinctBy(Image::id)
    }
    if (images.isEmpty()) {
        LaunchedEffect(Unit) { back() }
        return
    }
    val pager = rememberPagerState(initialPage = images.indexOfFirst { it.id == route.imageId }.coerceAtLeast(0)) { images.size }
    var chrome by rememberSaveable { mutableStateOf(true) }
    val image = images[pager.currentPage]
    val context = LocalContext.current
    val view = LocalView.current
    val window = LocalActivity.current?.window
    val snackbar = LocalSnackbar.current

    DisposableEffect(window) {
        val bars = window?.let { WindowCompat.getInsetsController(it, view) }
        val light = bars?.isAppearanceLightStatusBars
        bars?.isAppearanceLightStatusBars = false
        onDispose { if (light != null) bars.isAppearanceLightStatusBars = light }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(state = pager, key = { images[it].id }) { page ->
            ZoomableAsyncImage(
                model = model.file(images[page]),
                contentDescription = stringResource(R.string.wallpaper_preview_description),
                state = rememberZoomableImageState(rememberZoomableState(UnlimitedZoom)),
                onClick = { chrome = !chrome },
                onDoubleClick = ToggleZoom,
                modifier = Modifier.fillMaxSize(),
            )
        }
        AnimatedVisibility(chrome, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.TopStart)) {
            ScrimIconButton(Icons.Filled.Close, stringResource(R.string.action_close), back, Modifier.statusBarsPadding().padding(12.dp))
        }
        AnimatedVisibility(chrome, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.TopEnd)) {
            Row(Modifier.statusBarsPadding().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ScrimIconButton(Icons.Filled.Share, stringResource(R.string.action_share), { context.startActivity(model.shareIntent(image)) })
                ScrimIconButton(Icons.Filled.SaveAlt, stringResource(R.string.action_save_to_gallery), { model.saveToGallery(image) })
                FavoriteButton(data.isFavorite(image), { model.toggleFavorite(image) })
            }
        }
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SnackbarHost(snackbar, Modifier.fillMaxWidth())
            AnimatedVisibility(chrome, enter = fadeIn(), exit = fadeOut()) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Attribution(image) { link ->
                        try {
                            context.startActivity(Intent(Intent.ACTION_VIEW, link.toUri()))
                        } catch (e: ActivityNotFoundException) {
                            model.notify(R.string.link_open_failure)
                        }
                    }
                    ApplyButton(data, onApply = { screen -> model.show(screen, image) })
                }
            }
        }
    }
}

@Composable
private fun Attribution(image: Image, onOpen: (String) -> Unit) {
    val artist = image.artist
    val source = image.pageUrl
    if (artist == null && source == null && image.artistUrl == null) return
    Surface(shape = MaterialTheme.shapes.large, color = Color.Black.copy(alpha = 0.72f), contentColor = Color.White) {
        Row(Modifier.padding(horizontal = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            val artistLabel = artist?.let { stringResource(R.string.wallpaper_artist, it) }
                ?: image.artistUrl?.let { stringResource(R.string.wallpaper_artist_unknown) }
            if (artistLabel != null) {
                val url = image.artistUrl
                Text(
                    artistLabel,
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .then(if (url != null) Modifier.clickable { onOpen(url) } else Modifier)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (source != null) {
                TextButton(onClick = { onOpen(source) }) {
                    Text(stringResource(R.string.action_view_source), color = Color.White, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun ApplyButton(data: AppData, onApply: (Screen) -> Unit) {
    var choosing by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth()) {
        Button(
            onClick = { if (data.lock == null) onApply(Screen.HOME) else choosing = true },
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
        ) { Text(stringResource(R.string.action_set_wallpaper)) }
        DropdownMenu(expanded = choosing, onDismissRequest = { choosing = false }) {
            for (screen in Screen.entries) {
                DropdownMenuItem(
                    text = { Text(stringResource(screen.label)) },
                    onClick = {
                        onApply(screen)
                        choosing = false
                    },
                )
            }
        }
    }
}

private const val DOUBLE_CLICK_ZOOM = 2.5f

private val UnlimitedZoom = ZoomSpec(maximum = ZoomLimit(Float.POSITIVE_INFINITY))

private val ToggleZoom = DoubleClickToZoomListener { state, centroid ->
    if (state.contentTransformation.scaleMetadata.userZoom > 1.01f) state.resetZoom() else state.zoomBy(DOUBLE_CLICK_ZOOM, centroid)
}

