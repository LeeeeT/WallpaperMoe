package dev.leeeet.wallpapermoe.source

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class Folders(private val context: Context) {
    data class Entry(val uri: Uri, val id: String, val name: String)

    fun keep(tree: Uri) =
        context.contentResolver.takePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION)

    fun label(tree: Uri): String =
        DocumentsContract.getTreeDocumentId(tree).substringAfter(':').trimEnd('/').substringAfterLast('/').ifEmpty { "/" }

    suspend fun images(tree: Uri): List<Entry> = withContext(Dispatchers.IO) {
        val found = ArrayList<Entry>()
        val pending = ArrayDeque(listOf(DocumentsContract.getTreeDocumentId(tree) to 0))
        while (pending.isNotEmpty() && found.size < MAX_IMAGES) {
            val (folder, depth) = pending.removeFirst()
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, folder)
            val columns = arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE)
            context.contentResolver.query(children, columns, null, null, null)?.use { cursor ->
                while (cursor.moveToNext() && found.size < MAX_IMAGES) {
                    val id = cursor.getString(0)
                    val name = cursor.getString(1).orEmpty()
                    val mime = cursor.getString(2).orEmpty()
                    when {
                        mime == Document.MIME_TYPE_DIR -> if (depth < MAX_DEPTH) pending += id to depth + 1
                        mime.startsWith("image/") ->
                            found += Entry(DocumentsContract.buildDocumentUriUsingTree(tree, id), id, name)
                    }
                }
            }
        }
        found.sortedBy(Entry::id)
    }

    fun open(uri: Uri): InputStream? = context.contentResolver.openInputStream(uri)

    private companion object {
        const val MAX_IMAGES = 5000
        const val MAX_DEPTH = 4
    }
}
