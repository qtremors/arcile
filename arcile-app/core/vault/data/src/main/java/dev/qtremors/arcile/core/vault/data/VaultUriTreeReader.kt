package dev.qtremors.arcile.core.vault.data

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import dev.qtremors.arcile.core.vault.domain.VaultFailure

internal data class VaultImportSource(
    val uri: Uri,
    val name: String,
    val relativeParent: List<String>,
    val isDirectory: Boolean,
    val sizeBytes: Long?,
    val modifiedAtMillis: Long?,
    val mimeType: String?
)

internal class VaultUriTreeReader(
    private val resolver: ContentResolver
) {
    fun collect(uriStrings: List<String>): List<VaultImportSource> {
        require(uriStrings.isNotEmpty()) { "At least one import source is required" }
        return uriStrings.flatMap { value ->
            val uri = Uri.parse(value)
            if (DocumentsContract.isTreeUri(uri)) collectTree(uri) else listOf(readSingle(uri))
        }
    }

    private fun collectTree(treeUri: Uri): List<VaultImportSource> {
        val rootDocumentId = DocumentsContract.getTreeDocumentId(treeUri)
        val rootUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, rootDocumentId)
        val root = query(rootUri)
        if (!root.isDirectory) throw VaultFailure.ImportUnavailable("Selected folder is unavailable")
        val output = mutableListOf<VaultImportSource>()
        collectDirectory(treeUri, rootDocumentId, listOf(safeName(root.name)), output)
        return output
    }

    private fun collectDirectory(
        treeUri: Uri,
        documentId: String,
        relativePath: List<String>,
        output: MutableList<VaultImportSource>
    ) {
        val directoryUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)
        val directory = query(directoryUri)
        output += directory.toSource(directoryUri, relativePath)

        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, documentId)
        resolver.query(childrenUri, PROJECTION, null, null, null)?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
            val sizeIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_SIZE)
            val modifiedIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
            while (cursor.moveToNext()) {
                val childId = cursor.getString(idIndex)
                val name = safeName(cursor.getString(nameIndex) ?: "Imported item")
                val mime = cursor.getString(mimeIndex)
                val childUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childId)
                if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                    collectDirectory(treeUri, childId, relativePath + name, output)
                } else {
                    output += VaultImportSource(
                        uri = childUri,
                        name = name,
                        relativeParent = relativePath,
                        isDirectory = false,
                        sizeBytes = cursor.nullableLong(sizeIndex),
                        modifiedAtMillis = cursor.nullableLong(modifiedIndex),
                        mimeType = mime
                    )
                }
            }
        } ?: throw VaultFailure.ImportUnavailable("Unable to enumerate the selected folder")
    }

    private fun readSingle(uri: Uri): VaultImportSource {
        val row = query(uri)
        if (row.isDirectory) throw VaultFailure.ImportUnavailable("Use folder import for directories")
        return row.toSource(uri, emptyList())
    }

    private fun query(uri: Uri): DocumentRow {
        return resolver.query(uri, PROJECTION, null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            DocumentRow(
                name = safeName(cursor.getString(cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)) ?: "Imported item"),
                mimeType = cursor.getString(cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)),
                sizeBytes = cursor.nullableLong(cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_SIZE)),
                modifiedAtMillis = cursor.nullableLong(cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_LAST_MODIFIED))
            )
        } ?: throw VaultFailure.ImportUnavailable("Unable to read the selected item")
    }

    private fun DocumentRow.toSource(uri: Uri, relativeParent: List<String>) = VaultImportSource(
        uri = uri,
        name = name,
        relativeParent = relativeParent,
        isDirectory = isDirectory,
        sizeBytes = sizeBytes,
        modifiedAtMillis = modifiedAtMillis,
        mimeType = mimeType.takeUnless { isDirectory }
    )

    private fun safeName(value: String): String {
        val sanitized = value.replace('/', '_').replace('\\', '_').replace('\u0000', '_').trim()
        return sanitized.takeIf { it.isNotBlank() && it != "." && it != ".." } ?: "Imported item"
    }

    private data class DocumentRow(
        val name: String,
        val mimeType: String?,
        val sizeBytes: Long?,
        val modifiedAtMillis: Long?
    ) {
        val isDirectory: Boolean get() = mimeType == DocumentsContract.Document.MIME_TYPE_DIR
    }

    companion object {
        private val PROJECTION = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED
        )
    }
}

private fun android.database.Cursor.nullableLong(index: Int): Long? =
    if (index < 0 || isNull(index)) null else getLong(index).takeIf { it >= 0L }
