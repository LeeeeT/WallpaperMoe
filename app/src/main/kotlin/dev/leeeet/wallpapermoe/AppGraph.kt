package dev.leeeet.wallpapermoe

import android.app.Application
import dev.leeeet.wallpapermoe.data.Prefs
import dev.leeeet.wallpapermoe.provider.nekosbest.NekosBestDownloader
import dev.leeeet.wallpapermoe.provider.pandora.PandoraApi
import dev.leeeet.wallpapermoe.provider.wallhaven.WallhavenApi
import dev.leeeet.wallpapermoe.source.Providers
import dev.leeeet.wallpapermoe.source.SourceRepository
import dev.leeeet.wallpapermoe.source.Storage
import dev.leeeet.wallpapermoe.service.WallpaperRuntime
import dev.leeeet.wallpapermoe.wallpaper.WallpaperArchive
import dev.leeeet.wallpapermoe.wallpaper.GallerySaver
import dev.leeeet.wallpapermoe.wallpaper.MediaStoreGallerySink
import java.io.File
import java.util.Locale

/** Process-scoped dependencies, owned by [MoeApplication]. */
class AppGraph(app: Application) {
    val prefs: Prefs
    val sources: SourceRepository
    val providers: Providers
    val rotator: Rotator
    val archive: WallpaperArchive
    internal val gallerySaver: GallerySaver

    /** A copy of the wallpaper on screen, kept for the app to display. */
    val currentWallpaper: File

    init {
        prefs = Prefs(app)
        currentWallpaper = File(app.filesDir, "current.wp")
        archive = WallpaperArchive(File(app.filesDir, "wallpapers"))
        gallerySaver = GallerySaver(
            sink = MediaStoreGallerySink(app.contentResolver),
        )

        val storage = Storage(File(app.filesDir, "sources"))
        sources = SourceRepository(File(app.filesDir, "sources.json"), storage)

        val metrics = app.resources.displayMetrics
        providers = Providers(
            nekosBest = NekosBestDownloader(),
            pandora = PandoraApi(
                installId = { prefs.installId() },
                screenWidthPx = metrics.widthPixels,
                locale = Locale.getDefault().let { "${it.language}_${it.country}" },
                appVersionName = BuildConfig.VERSION_NAME,
                appVersionCode = BuildConfig.VERSION_CODE,
            ),
            wallhaven = WallhavenApi(),
        )

        rotator = Rotator(
            prefs = prefs,
            repository = sources,
            providers = providers,
            storage = storage,
            archive = archive,
            currentFile = currentWallpaper,
            onWallpaperChanged = WallpaperRuntime::redraw,
        )
    }
}
