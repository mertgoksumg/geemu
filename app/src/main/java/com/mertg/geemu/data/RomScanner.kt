package com.mertg.geemu.data

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.mertg.geemu.model.RomEntry

class RomScanner(private val context: Context) {
    fun scan(treeUri: Uri, extensions: Set<String>, limit: Int = 600): List<RomEntry> {
        if (!DocumentsContract.isTreeUri(treeUri)) return emptyList()
        val rootId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull()
            ?: return emptyList()
        val output = mutableListOf<RomEntry>()
        scanChildren(treeUri, rootId, "", extensions, output, limit)
        return output.sortedBy { it.title.lowercase() }
    }

    private fun scanChildren(
        treeUri: Uri,
        parentDocumentId: String,
        parentPath: String,
        extensions: Set<String>,
        output: MutableList<RomEntry>,
        limit: Int
    ) {
        if (output.size >= limit) return
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocumentId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE
        )
        runCatching {
            context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow(projection[0])
                val nameIndex = cursor.getColumnIndexOrThrow(projection[1])
                val mimeIndex = cursor.getColumnIndexOrThrow(projection[2])
                while (cursor.moveToNext() && output.size < limit) {
                    val id = cursor.getString(idIndex)
                    val name = cursor.getString(nameIndex)
                    val mime = cursor.getString(mimeIndex)
                    val path = if (parentPath.isBlank()) name else "$parentPath/$name"
                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                        scanChildren(treeUri, id, path, extensions, output, limit)
                    } else if (name.substringAfterLast('.', "").lowercase() in extensions) {
                        output += RomEntry(
                            title = GameTitleCleaner.clean(name),
                            uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, id).toString(),
                            path = path
                        )
                    }
                }
            }
        }
    }

    companion object {
        fun persistFolderPermission(context: Context, uri: Uri) {
            val flags = IntentFlags.READ_WRITE
            runCatching { context.contentResolver.takePersistableUriPermission(uri, flags) }
        }
    }

    private object IntentFlags {
        const val READ_WRITE = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
            android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
    }
}
