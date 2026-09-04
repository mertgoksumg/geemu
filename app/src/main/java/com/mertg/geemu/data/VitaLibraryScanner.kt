package com.mertg.geemu.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.mertg.geemu.model.RomEntry
import java.nio.ByteBuffer
import java.nio.ByteOrder

class VitaLibraryScanner(private val context: Context) {
    private data class Document(val id: String, val name: String, val mime: String)

    fun scan(treeUri: Uri): List<RomEntry> {
        if (!DocumentsContract.isTreeUri(treeUri)) return emptyList()
        val rootId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull() ?: return emptyList()
        val root = Document(rootId, "", DocumentsContract.Document.MIME_TYPE_DIR)
        val appDirectory = if (looksLikeAppDirectory(treeUri, root)) root
            else findAppDirectory(treeUri, rootId, "", 0) ?: return emptyList()
        return children(treeUri, appDirectory.id)
            .filter { it.mime == DocumentsContract.Document.MIME_TYPE_DIR }
            .mapNotNull { gameDirectory -> readGame(treeUri, gameDirectory) }
            .sortedBy { it.title.lowercase() }
    }

    private fun findAppDirectory(treeUri: Uri, parentId: String, path: String, depth: Int): Document? {
        if (depth > 7) return null
        for (child in children(treeUri, parentId)) {
            if (child.mime != DocumentsContract.Document.MIME_TYPE_DIR) continue
            val nextPath = if (path.isBlank()) child.name else "$path/${child.name}"
            if (nextPath.lowercase().endsWith("ux0/app") || looksLikeAppDirectory(treeUri, child)) return child
            findAppDirectory(treeUri, child.id, nextPath, depth + 1)?.let { return it }
        }
        return null
    }

    private fun looksLikeAppDirectory(treeUri: Uri, directory: Document): Boolean =
        children(treeUri, directory.id).any {
            it.mime == DocumentsContract.Document.MIME_TYPE_DIR && TITLE_ID.matches(it.name)
        }

    private fun readGame(treeUri: Uri, gameDirectory: Document): RomEntry? {
        val titleId = gameDirectory.name.uppercase()
        if (!TITLE_ID.matches(titleId)) return null
        val sceSys = children(treeUri, gameDirectory.id).firstOrNull {
            it.mime == DocumentsContract.Document.MIME_TYPE_DIR && it.name.equals("sce_sys", true)
        }
        val files = sceSys?.let { children(treeUri, it.id) }.orEmpty()
        val param = files.firstOrNull { it.name.equals("param.sfo", true) }
        val icon = files.firstOrNull { it.name.equals("icon0.png", true) }
        val title = param?.let { readSfoTitle(documentUri(treeUri, it.id)) }
            ?.let(GameTitleCleaner::clean)
            ?.takeIf { it.isNotBlank() }
            ?: titleId
        return RomEntry(
            title = title,
            uri = documentUri(treeUri, gameDirectory.id).toString(),
            path = "ux0/app/$titleId",
            artworkUri = icon?.let { documentUri(treeUri, it.id).toString() },
            launchId = titleId
        )
    }

    private fun children(treeUri: Uri, parentId: String): List<Document> {
        val uri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentId)
        val columns = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE
        )
        return runCatching {
            buildList {
                context.contentResolver.query(uri, columns, null, null, null)?.use { cursor ->
                    while (cursor.moveToNext()) {
                        add(Document(cursor.getString(0), cursor.getString(1), cursor.getString(2)))
                    }
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun documentUri(treeUri: Uri, id: String): Uri =
        DocumentsContract.buildDocumentUriUsingTree(treeUri, id)

    private fun readSfoTitle(uri: Uri): String? = runCatching {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
        if (bytes.size < 20 || bytes[1].toInt().toChar() != 'P' || bytes[2].toInt().toChar() != 'S' || bytes[3].toInt().toChar() != 'F') return null
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val keyTableOffset = buffer.getInt(8)
        val dataTableOffset = buffer.getInt(12)
        val entryCount = buffer.getInt(16).coerceAtMost(256)
        repeat(entryCount) { index ->
            val entryOffset = 20 + index * 16
            if (entryOffset + 16 > bytes.size) return@repeat
            val keyOffset = buffer.getShort(entryOffset).toInt() and 0xffff
            val dataLength = buffer.getInt(entryOffset + 4)
            val dataOffset = buffer.getInt(entryOffset + 12)
            val key = cString(bytes, keyTableOffset + keyOffset, 64)
            if (key == "TITLE") return cString(bytes, dataTableOffset + dataOffset, dataLength)
        }
        null
    }.getOrNull()

    private fun cString(bytes: ByteArray, offset: Int, maxLength: Int): String {
        if (offset !in bytes.indices) return ""
        val endLimit = (offset + maxLength).coerceAtMost(bytes.size)
        var end = offset
        while (end < endLimit && bytes[end].toInt() != 0) end++
        return bytes.copyOfRange(offset, end).toString(Charsets.UTF_8)
    }

    companion object {
        private val TITLE_ID = Regex("(?i)[A-Z]{4}\\d{5}")
    }
}
