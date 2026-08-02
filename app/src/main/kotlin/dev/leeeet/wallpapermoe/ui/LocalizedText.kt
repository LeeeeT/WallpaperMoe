package dev.leeeet.wallpapermoe.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.leeeet.wallpapermoe.R
import dev.leeeet.wallpapermoe.Rotator
import dev.leeeet.wallpapermoe.data.RotationInterval
import dev.leeeet.wallpapermoe.source.CacheRefresh
import dev.leeeet.wallpapermoe.source.SampleFailure
import dev.leeeet.wallpapermoe.source.SourceProvider
import dev.leeeet.wallpapermoe.source.SourceKind
import dev.leeeet.wallpapermoe.source.WallhavenSorting

@Composable
fun Tab.localizedLabel(): String = stringResource(
    when (this) {
        Tab.NOW -> R.string.nav_now
        Tab.SOURCE -> R.string.nav_source
        Tab.SETTINGS -> R.string.nav_settings
    },
)

@Composable
fun RotationInterval.localizedLabel(): String = stringResource(
    when (this) {
        RotationInterval.EVERY_LOCK -> R.string.interval_every_lock
        RotationInterval.H1 -> R.string.interval_1_hour
        RotationInterval.H6 -> R.string.interval_6_hours
        RotationInterval.H24 -> R.string.interval_24_hours
    },
)

@Composable
fun CacheRefresh.localizedLabel(): String = stringResource(
    when (this) {
        CacheRefresh.NEVER -> R.string.cache_refresh_never
        CacheRefresh.HOURLY -> R.string.cache_refresh_hourly
        CacheRefresh.SIX_HOURS -> R.string.cache_refresh_6_hours
        CacheRefresh.DAILY -> R.string.cache_refresh_daily
    },
)

@Composable
fun WallhavenSorting.localizedLabel(): String = stringResource(
    when (this) {
        WallhavenSorting.RANDOM -> R.string.sorting_random
        WallhavenSorting.DATE_ADDED -> R.string.sorting_newest
        WallhavenSorting.TOPLIST -> R.string.sorting_toplist
        WallhavenSorting.VIEWS -> R.string.sorting_views
        WallhavenSorting.FAVORITES -> R.string.sorting_favorites
    },
)

@Composable
private fun SourceProvider.localizedName(): String = stringResource(
    when (this) {
        SourceProvider.PANDORA -> R.string.source_name_pandora
        SourceProvider.WALLHAVEN -> R.string.source_name_wallhaven
        SourceProvider.NEKOS_BEST -> R.string.source_name_nekos_best
    },
)

@Composable
fun Rotator.Failure.localizedMessage(): String = when (this) {
    is Rotator.Failure.Sampling -> reason.localizedMessage()
    Rotator.Failure.Unexpected -> stringResource(R.string.failure_unexpected)
}

@Composable
private fun SampleFailure.localizedMessage(): String = when (this) {
    is SampleFailure.ProviderUnavailable ->
        stringResource(R.string.failure_provider_unavailable, provider.localizedName())
    is SampleFailure.ProviderReturnedNothing ->
        stringResource(R.string.failure_provider_empty, provider.localizedName())
    is SampleFailure.DownloadFailed ->
        stringResource(R.string.failure_download, provider.localizedName())
    SampleFailure.CacheEmpty -> stringResource(R.string.failure_cache_empty)
    SampleFailure.FavoritesEmpty -> stringResource(R.string.failure_favorites_empty)
    SampleFailure.Unexpected -> stringResource(R.string.failure_unexpected)
}

@Composable
fun DraftProblem.localizedMessage(): String = when (this) {
    DraftProblem.BlankName -> stringResource(R.string.validation_source_name_blank)
    DraftProblem.DuplicateName -> stringResource(R.string.validation_source_name_duplicate)
    DraftProblem.PandoraChannelMissing -> stringResource(R.string.validation_pandora_channel)
    DraftProblem.WallhavenCategoryMissing -> stringResource(R.string.validation_wallhaven_category)
    DraftProblem.WallhavenPurityMissing -> stringResource(R.string.validation_wallhaven_purity)
    DraftProblem.WallhavenAspectMissing -> stringResource(R.string.validation_wallhaven_aspect)
    DraftProblem.WallhavenNsfwKeyMissing -> stringResource(R.string.validation_wallhaven_nsfw_key)
    DraftProblem.NekosBestCategoryMissing -> stringResource(R.string.validation_nekos_best_category)
    DraftProblem.CacheCapacityInvalid -> stringResource(R.string.validation_cache_capacity)
    DraftProblem.PrefetchDepthInvalid -> stringResource(R.string.validation_prefetch_depth)
    DraftProblem.KeepReadySourceMissing -> stringResource(R.string.validation_keep_ready_source)
    DraftProblem.KeepReadyOptionMissing -> stringResource(R.string.validation_keep_ready_option)
    DraftProblem.CombineSourcesMissing -> stringResource(R.string.validation_combine_sources)
    DraftProblem.MissingReference -> stringResource(R.string.validation_source_reference_missing)
    DraftProblem.ReferenceCycle -> stringResource(R.string.validation_source_reference_cycle)
}

@Composable
fun SourceKind.localizedName(): String = stringResource(
    when (this) {
        SourceKind.FAVORITES -> R.string.source_name_favorites
        SourceKind.PANDORA -> R.string.source_name_pandora
        SourceKind.WALLHAVEN -> R.string.source_name_wallhaven
        SourceKind.NEKOS_BEST -> R.string.source_name_nekos_best
        SourceKind.KEEP_READY -> R.string.source_name_keep_ready
        SourceKind.COMBINE -> R.string.source_name_combine
    },
)
