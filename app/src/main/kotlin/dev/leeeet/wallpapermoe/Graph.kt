package dev.leeeet.wallpapermoe

import android.content.Context
import dev.leeeet.wallpapermoe.data.Prefs
import dev.leeeet.wallpapermoe.image.ImagePipeline
import dev.leeeet.wallpapermoe.provider.nekosbest.NekosBestDownloader
import dev.leeeet.wallpapermoe.provider.pandora.PandoraApi
import dev.leeeet.wallpapermoe.provider.wallhaven.WallhavenApi
import dev.leeeet.wallpapermoe.source.Providers
import dev.leeeet.wallpapermoe.source.SourceRepository
import dev.leeeet.wallpapermoe.source.Storage
import dev.leeeet.wallpapermoe.wallpaper.WallpaperApplier
import dev.leeeet.wallpapermoe.wallpaper.WallpaperArchive
import java.io.File
import java.util.Locale

/** Hand-rolled service locator; everything in it is process-scoped. */
object Graph {

    @Volatile private var initialised = false

    lateinit var prefs: Prefs private set
    lateinit var sources: SourceRepository private set
    lateinit var applier: WallpaperApplier private set
    lateinit var images: ImagePipeline private set
    lateinit var providers: Providers private set
    lateinit var rotator: Rotator private set
    lateinit var archive: WallpaperArchive private set

    /** A copy of the wallpaper on screen, kept for the app to display. */
    lateinit var currentWallpaper: File private set

    fun init(context: Context) {
        if (initialised) return
        synchronized(this) {
            if (initialised) return
            val app = context.applicationContext

            prefs = Prefs(app)
            applier = WallpaperApplier(app)
            images = ImagePipeline(File(app.cacheDir, "downloads"))
            currentWallpaper = File(app.filesDir, "current.wp")
            archive = WallpaperArchive(File(app.filesDir, "wallpapers"))

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
                applier = applier,
                images = images,
                providers = providers,
                storage = storage,
                archive = archive,
                currentFile = currentWallpaper,
            )
            initialised = true
        }
    }
}
