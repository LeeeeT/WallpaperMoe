package dev.leeeet.wallpapermoe.data

import android.content.Context
import java.io.File

fun claimStorage(context: Context, state: File): File {
    if (!state.exists()) {
        context.filesDir.listFiles()?.forEach(File::deleteRecursively)
        File(context.dataDir, "databases").deleteRecursively()
    }
    return state
}
