package dev.leeeet.wallpapermoe.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Wallpaper
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import dev.leeeet.wallpapermoe.R
import dev.leeeet.wallpapermoe.data.moeJson
import dev.leeeet.wallpapermoe.model.Kind
import dev.leeeet.wallpapermoe.model.Screen
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.serialization.Serializable

@Serializable
sealed interface Route {
    @Serializable
    data object Tabs : Route

    @Serializable
    data object Favorites : Route

    @Serializable
    data class Viewer(val favorites: Boolean, val imageId: String) : Route

    @Serializable
    data class ScreenSettings(val screen: Screen) : Route

    @Serializable
    data class Editor(val sourceId: String?, val kind: Kind) : Route
}

enum class Tab { TODAY, STUDIO, SETTINGS }

val LocalSnackbar = staticCompositionLocalOf { SnackbarHostState() }

@Composable
fun MoeRoot(model: MoeViewModel = viewModel()) {
    val data = model.data.collectAsStateWithLifecycle().value
    val stack = rememberStack()
    var tab by rememberSaveable { mutableStateOf(Tab.TODAY) }
    val snackbar = remember { SnackbarHostState() }
    val resources by rememberUpdatedState(LocalResources.current)

    LifecycleResumeEffect(Unit) {
        model.refreshActive()
        onPauseOrDispose {}
    }
    LaunchedEffect(Unit) {
        model.messages.receiveAsFlow().collect { message ->
            snackbar.currentSnackbarData?.dismiss()
            val result = snackbar.showSnackbar(
                message = resources.getString(message.text),
                actionLabel = message.onResult?.let { resources.getString(R.string.action_undo) },
            )
            message.onResult?.invoke(result == SnackbarResult.ActionPerformed)
        }
    }

    if (data == null) return
    val open: (Route) -> Unit = { stack += it }
    val back: () -> Unit = { if (stack.size > 1) stack.removeAt(stack.lastIndex) }
    val select: (Tab) -> Unit = { tab = it }
    CompositionLocalProvider(LocalSnackbar provides snackbar) {
        NavDisplay(
            backStack = stack,
            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface),
            onBack = { back() },
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            popTransitionSpec = { fadeIn() togetherWith fadeOut() },
            predictivePopTransitionSpec = { _ -> fadeIn() togetherWith fadeOut() },
            entryProvider = entryProvider {
                entry<Route.Tabs> {
                    BackHandler(enabled = tab != Tab.TODAY) { tab = Tab.TODAY }
                    TabFrame(tab, select) { shown ->
                        when (shown) {
                            Tab.TODAY -> TodayScreen(model, data, open, select)
                            Tab.STUDIO -> StudioScreen(model, data, open)
                            Tab.SETTINGS -> SettingsScreen(model, data)
                        }
                    }
                }
                entry<Route.Favorites> { FavoritesScreen(model, data, open, back) }
                entry<Route.Viewer> { ViewerScreen(it, model, data, back) }
                entry<Route.ScreenSettings> { ScreenPage(it.screen, model, data, back) }
                entry<Route.Editor> { EditorScreen(it, model, data, open, back) }
            },
        )
    }
}

@Composable
private fun TabFrame(tab: Tab, onSelect: (Tab) -> Unit, content: @Composable (Tab) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val rail = maxWidth >= RailBreakpoint
        Scaffold(
            containerColor = MaterialTheme.colorScheme.surface,
            contentWindowInsets = WindowInsets(0),
            snackbarHost = { SnackbarHost(LocalSnackbar.current) },
            bottomBar = { if (!rail) TabBar(tab, onSelect) },
        ) { padding ->
            Row(Modifier.fillMaxSize().padding(padding)) {
                if (rail) TabRail(tab, onSelect)
                AnimatedContent(
                    targetState = tab,
                    modifier = Modifier.weight(1f),
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "tab",
                ) { content(it) }
            }
        }
    }
}

private fun Tab.icon(selected: Boolean): ImageVector = when (this) {
    Tab.TODAY -> if (selected) Icons.Filled.Wallpaper else Icons.Outlined.Wallpaper
    Tab.STUDIO -> if (selected) Icons.Filled.AccountTree else Icons.Outlined.AccountTree
    Tab.SETTINGS -> if (selected) Icons.Filled.Settings else Icons.Outlined.Settings
}

@Composable
private fun Tab.label(): String = stringResource(
    when (this) {
        Tab.TODAY -> R.string.nav_today
        Tab.STUDIO -> R.string.nav_studio
        Tab.SETTINGS -> R.string.nav_settings
    },
)

@Composable
private fun TabBar(selected: Tab, onSelect: (Tab) -> Unit) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
        for (tab in Tab.entries) {
            val label = tab.label()
            NavigationBarItem(
                selected = selected == tab,
                onClick = { onSelect(tab) },
                icon = { Icon(tab.icon(selected == tab), label) },
                label = { Text(label, maxLines = 1) },
                colors = NavigationBarItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.primaryContainer),
            )
        }
    }
}

@Composable
private fun TabRail(selected: Tab, onSelect: (Tab) -> Unit) {
    NavigationRail(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
        Spacer(Modifier.weight(1f))
        for (tab in Tab.entries) {
            val label = tab.label()
            NavigationRailItem(
                selected = selected == tab,
                onClick = { onSelect(tab) },
                icon = { Icon(tab.icon(selected == tab), label) },
                label = { Text(label, maxLines = 1) },
                colors = NavigationRailItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.primaryContainer),
            )
        }
        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun rememberStack(): SnapshotStateList<Route> = rememberSaveable(
    saver = listSaver(
        save = { stack -> stack.map { moeJson.encodeToString<Route>(it) } },
        restore = { saved -> saved.map { moeJson.decodeFromString<Route>(it) }.toMutableStateList() },
    ),
) { mutableStateListOf(Route.Tabs) }
