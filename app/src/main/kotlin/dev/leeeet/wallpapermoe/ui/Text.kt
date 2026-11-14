package dev.leeeet.wallpapermoe.ui

import android.text.format.Formatter
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import dev.leeeet.wallpapermoe.R
import dev.leeeet.wallpapermoe.model.AppData
import dev.leeeet.wallpapermoe.model.CombineMode
import dev.leeeet.wallpapermoe.model.Frequency
import dev.leeeet.wallpapermoe.model.Kind
import dev.leeeet.wallpapermoe.model.Pick
import dev.leeeet.wallpapermoe.model.Problem
import dev.leeeet.wallpapermoe.model.Provider
import dev.leeeet.wallpapermoe.model.Recipe
import dev.leeeet.wallpapermoe.model.Refresh
import dev.leeeet.wallpapermoe.model.Screen
import dev.leeeet.wallpapermoe.model.Sorting
import dev.leeeet.wallpapermoe.model.Source
import dev.leeeet.wallpapermoe.source.Failure
import kotlin.math.roundToInt
import java.util.Locale

val Screen.label: Int
    @StringRes get() = when (this) {
        Screen.HOME -> R.string.screen_home
        Screen.LOCK -> R.string.screen_lock
    }

val Kind.label: Int
    @StringRes get() = when (this) {
        Kind.WALLHAVEN -> R.string.kind_wallhaven
        Kind.NEKOS_BEST -> R.string.kind_nekos_best
        Kind.PANDORA -> R.string.kind_pandora
        Kind.FOLDER -> R.string.kind_folder
        Kind.FAVORITES -> R.string.kind_favorites
        Kind.KEEP_READY -> R.string.kind_keep_ready
        Kind.COMBINE -> R.string.kind_combine
    }

val Kind.pickerDetail: Int
    @StringRes get() = when (this) {
        Kind.WALLHAVEN -> R.string.source_picker_wallhaven
        Kind.NEKOS_BEST -> R.string.source_picker_nekos_best
        Kind.PANDORA -> R.string.source_picker_pandora
        Kind.FOLDER -> R.string.kind_folder_detail
        Kind.FAVORITES -> R.string.source_picker_favorites
        Kind.KEEP_READY -> R.string.source_picker_keep_ready
        Kind.COMBINE -> R.string.source_picker_combine
    }

val Provider.label: Int
    @StringRes get() = when (this) {
        Provider.WALLHAVEN -> R.string.kind_wallhaven
        Provider.NEKOS_BEST -> R.string.kind_nekos_best
        Provider.PANDORA -> R.string.kind_pandora
        Provider.FOLDER -> R.string.kind_folder
    }

val Pick.label: Int
    @StringRes get() = when (this) {
        Pick.RANDOM -> R.string.source_name_random
        Pick.IN_TURN -> R.string.source_name_round_robin
    }

val Sorting.label: Int
    @StringRes get() = when (this) {
        Sorting.RANDOM -> R.string.sorting_random
        Sorting.NEWEST -> R.string.sorting_newest
        Sorting.TOPLIST -> R.string.sorting_toplist
        Sorting.VIEWS -> R.string.sorting_views
        Sorting.FAVORITES -> R.string.sorting_favorites
    }

val Refresh.label: Int
    @StringRes get() = when (this) {
        Refresh.NEVER -> R.string.cache_refresh_never
        Refresh.HOURLY -> R.string.cache_refresh_hourly
        Refresh.SIX_HOURS -> R.string.cache_refresh_6_hours
        Refresh.DAILY -> R.string.cache_refresh_daily
    }

val CombineMode.label: Int
    @StringRes get() = when (this) {
        CombineMode.RANDOM -> R.string.source_name_random
        CombineMode.IN_TURN -> R.string.source_name_round_robin
        CombineMode.FIRST_AVAILABLE -> R.string.source_name_fallthrough
    }

val CombineMode.detail: Int
    @StringRes get() = when (this) {
        CombineMode.RANDOM -> R.string.random_description
        CombineMode.IN_TURN -> R.string.round_robin_description
        CombineMode.FIRST_AVAILABLE -> R.string.fallthrough_description
    }

val Frequency.label: Int
    @StringRes get() = when (this) {
        Frequency.EVERY_SCREEN_OFF -> R.string.interval_every_screen_off
        Frequency.HOURLY -> R.string.interval_1_hour
        Frequency.SIX_HOURS -> R.string.interval_6_hours
        Frequency.DAILY -> R.string.interval_24_hours
    }

val Problem.label: Int
    @StringRes get() = when (this) {
        Problem.NAME_TAKEN -> R.string.problem_name_taken
        Problem.WALLHAVEN_CATEGORY -> R.string.problem_wallhaven_category
        Problem.WALLHAVEN_PURITY -> R.string.problem_wallhaven_purity
        Problem.WALLHAVEN_ASPECT -> R.string.problem_wallhaven_aspect
        Problem.WALLHAVEN_KEY -> R.string.problem_wallhaven_key
        Problem.NEKOS_BEST_CATEGORY -> R.string.problem_nekos_best_category
        Problem.PANDORA_CHANNEL -> R.string.problem_pandora_channel
        Problem.FOLDER_MISSING -> R.string.problem_folder_missing
        Problem.KEEP_READY_SOURCE -> R.string.problem_keep_ready_source
        Problem.KEEP_READY_OPTION -> R.string.problem_keep_ready_option
        Problem.COMBINE_SOURCES -> R.string.problem_combine_sources
        Problem.INPUT_MISSING -> R.string.problem_input_missing
        Problem.INPUT_CYCLE -> R.string.problem_input_cycle
    }

@Composable
fun Failure.message(): String = when (this) {
    Failure.NoSource -> stringResource(R.string.failure_no_source)
    is Failure.Unavailable -> stringResource(R.string.failure_unavailable, stringResource(provider.label))
    is Failure.Empty -> stringResource(R.string.failure_empty, stringResource(provider.label))
    is Failure.Download -> stringResource(R.string.failure_download, stringResource(provider.label))
    Failure.FolderDenied -> stringResource(R.string.failure_folder_denied)
    Failure.FavoritesEmpty -> stringResource(R.string.failure_favorites_empty)
    Failure.NothingSaved -> stringResource(R.string.failure_nothing_saved)
    Failure.Metered -> stringResource(R.string.failure_metered)
    Failure.Unexpected -> stringResource(R.string.failure_unexpected)
}

fun String.categoryLabel(): String = replaceFirstChar { it.titlecase(Locale.ROOT) }

@Composable
fun Source.summary(data: AppData): String = when (val recipe = recipe) {
    is Recipe.Wallhaven -> stringResource(
        R.string.summary_wallhaven,
        recipe.query.trim().ifEmpty { stringResource(R.string.summary_anything) },
        stringResource(recipe.sorting.label),
    )
    is Recipe.NekosBest -> stringResource(recipe.pick.label)
    is Recipe.Pandora -> recipe.channels.joinToString { it.name }
    is Recipe.Folder -> stringResource(R.string.summary_pair, recipe.label, stringResource(recipe.pick.label))
    is Recipe.Favorites -> stringResource(
        when (recipe.pick) {
            Pick.RANDOM -> R.string.favorites_source_summary_random
            Pick.IN_TURN -> R.string.favorites_source_summary_round_robin
        },
    )
    is Recipe.KeepReady -> listOfNotNull(
        data.source(recipe.sourceId)?.name,
        recipe.prepare?.let { pluralStringResource(R.plurals.summary_prefetch, it.count, it.count) },
        recipe.save?.let { save ->
            if (save.refresh == Refresh.NEVER) {
                pluralStringResource(R.plurals.summary_cache_never, save.count, save.count)
            } else {
                val hours = save.refresh.hours
                pluralStringResource(
                    R.plurals.summary_cache,
                    save.count,
                    save.count,
                    pluralStringResource(R.plurals.summary_cache_interval, hours, hours),
                )
            }
        },
    ).joinToString(" · ")
    is Recipe.Combine -> pluralStringResource(
        when (recipe.mode) {
            CombineMode.RANDOM -> R.plurals.summary_sources_random
            CombineMode.IN_TURN -> R.plurals.summary_sources_in_turn
            CombineMode.FIRST_AVAILABLE -> R.plurals.summary_sources_fallthrough
        },
        recipe.sourceIds.size,
        recipe.sourceIds.size,
    )
}

@Composable
fun percent(value: Float): String = stringResource(R.string.percent, (value * 100).roundToInt())

@Composable
fun bytes(value: Long): String = Formatter.formatShortFileSize(LocalContext.current, value)
