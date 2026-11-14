package dev.leeeet.wallpapermoe

import android.app.Application
import android.content.Context
import dev.leeeet.wallpapermoe.data.Backups
import dev.leeeet.wallpapermoe.data.Blobs
import dev.leeeet.wallpapermoe.data.claimStorage
import dev.leeeet.wallpapermoe.data.Store
import dev.leeeet.wallpapermoe.net.Http
import dev.leeeet.wallpapermoe.source.Folders
import dev.leeeet.wallpapermoe.source.NekosBest
import dev.leeeet.wallpapermoe.source.Pandora
import dev.leeeet.wallpapermoe.source.Sampler
import dev.leeeet.wallpapermoe.source.Wallhaven
import dev.leeeet.wallpapermoe.service.Changer
import java.io.File
import dev.leeeet.wallpapermoe.model.initialData
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class MoeApp : Application() {
    val graph by lazy { Graph(this) }
}

class Graph(context: Context) {
    private val state = claimStorage(context, File(context.filesDir, "state.json"))
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val blobs = Blobs(File(context.filesDir, "blobs"))
    val store = Store(
        state,
        initialData(context.getString(R.string.kind_keep_ready), context.getString(R.string.kind_nekos_best)),
        blobs,
        scope,
    )
    val folders = Folders(context)
    val nekosBest: NekosBest
    val pandora: Pandora
    val changer: Changer
    val backups = Backups(context, store, blobs)

    init {
        val http = Http()
        nekosBest = NekosBest(http)
        pandora = Pandora(
            http,
            installId = { store.update { if (it.installId.isEmpty()) it.copy(installId = UUID.randomUUID().toString()) else it }.installId },
            screenWidth = { context.resources.displayMetrics.widthPixels },
            locale = { context.resources.configuration.locales[0].toString() },
        )
        val sampler = Sampler(store, blobs, http, Wallhaven(http), nekosBest, pandora, folders)
        changer = Changer(context, store, sampler, scope)
    }
}
