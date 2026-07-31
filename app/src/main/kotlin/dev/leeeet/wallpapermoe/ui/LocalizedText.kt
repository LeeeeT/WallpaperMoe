package dev.leeeet.wallpapermoe.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.leeeet.wallpapermoe.R
import dev.leeeet.wallpapermoe.Rotator
import dev.leeeet.wallpapermoe.data.RotationInterval
import dev.leeeet.wallpapermoe.source.SampleFailure
import dev.leeeet.wallpapermoe.source.SourceProvider
import dev.leeeet.wallpapermoe.source.SourceKind
import dev.leeeet.wallpapermoe.source.WallhavenSorting
import dev.leeeet.wallpapermoe.wallpaper.ApplyFailure

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
        RotationInterval.M15 -> R.string.interval_15_minutes
        RotationInterval.M30 -> R.string.interval_30_minutes
        RotationInterval.H1 -> R.string.interval_1_hour
        RotationInterval.H3 -> R.string.interval_3_hours
        RotationInterval.H6 -> R.string.interval_6_hours
        RotationInterval.H12 -> R.string.interval_12_hours
        RotationInterval.H24 -> R.string.interval_24_hours
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
    },
)

@Composable
fun Rotator.Failure.localizedMessage(): String = when (this) {
    is Rotator.Failure.Sampling -> reason.localizedMessage()
    is Rotator.Failure.Applying -> reason.localizedMessage()
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
    is SampleFailure.CandidatesUnavailable ->
        stringResource(R.string.failure_candidates_unavailable, provider.localizedName())
    SampleFailure.CacheEmpty -> stringResource(R.string.failure_cache_empty)
    SampleFailure.FavoritesEmpty -> stringResource(R.string.failure_favorites_empty)
    SampleFailure.Unexpected -> stringResource(R.string.failure_unexpected)
}

@Composable
private fun ApplyFailure.localizedMessage(): String = stringResource(
    when (this) {
        ApplyFailure.NO_TARGET -> R.string.failure_no_target
        ApplyFailure.IMAGE_MISSING -> R.string.failure_image_missing
        ApplyFailure.SYSTEM_REJECTED -> R.string.failure_apply
    },
)

@Composable
fun DraftProblem.localizedMessage(): String = when (this) {
    DraftProblem.BlankName -> stringResource(R.string.validation_source_name_blank)
    DraftProblem.DuplicateName -> stringResource(R.string.validation_source_name_duplicate)
    DraftProblem.PandoraChannelMissing -> stringResource(R.string.validation_pandora_channel)
    DraftProblem.WallhavenCategoryMissing -> stringResource(R.string.validation_wallhaven_category)
    DraftProblem.WallhavenPurityMissing -> stringResource(R.string.validation_wallhaven_purity)
    DraftProblem.WallhavenNsfwKeyMissing -> stringResource(R.string.validation_wallhaven_nsfw_key)
    DraftProblem.CacheCapacityInvalid -> stringResource(R.string.validation_cache_capacity)
    DraftProblem.CacheRefreshInvalid -> stringResource(R.string.validation_cache_refresh)
    DraftProblem.PrefetchDepthInvalid -> stringResource(R.string.validation_prefetch_depth)
    DraftProblem.KeepReadySourceMissing -> stringResource(R.string.validation_keep_ready_source)
    DraftProblem.CombineEmpty -> stringResource(R.string.validation_combine_empty)
    DraftProblem.MissingReference -> stringResource(R.string.validation_source_reference_missing)
    DraftProblem.ReferenceCycle -> stringResource(R.string.validation_source_reference_cycle)
}

@Composable
fun SourceKind.localizedName(): String = stringResource(
    when (this) {
        SourceKind.FAVORITES -> R.string.source_name_favorites
        SourceKind.PANDORA -> R.string.source_name_pandora
        SourceKind.WALLHAVEN -> R.string.source_name_wallhaven
        SourceKind.KEEP_READY -> R.string.source_name_keep_ready
        SourceKind.COMBINE -> R.string.source_name_combine
    },
)
