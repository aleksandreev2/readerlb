package com.readerlb.app.storage

import android.database.Cursor
import android.database.MatrixCursor
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.DocumentsProvider
import java.io.FileNotFoundException

/** Presents the verified Shizuku library through the same document API used by SAF. */
class RanobeLibDocumentsProvider : DocumentsProvider() {
    private val documentColumns = arrayOf(
        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        DocumentsContract.Document.COLUMN_MIME_TYPE,
        DocumentsContract.Document.COLUMN_SIZE,
        DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        DocumentsContract.Document.COLUMN_FLAGS
    )

    override fun onCreate(): Boolean = true

    override fun queryRoots(projection: Array<out String>?): Cursor {
        val cursor = MatrixCursor(projection ?: arrayOf(
            DocumentsContract.Root.COLUMN_ROOT_ID,
            DocumentsContract.Root.COLUMN_DOCUMENT_ID,
            DocumentsContract.Root.COLUMN_TITLE,
            DocumentsContract.Root.COLUMN_FLAGS
        ))
        for (kind in listOf("book", "manga")) {
            cursor.newRow().apply {
                add(DocumentsContract.Root.COLUMN_ROOT_ID, kind)
                add(DocumentsContract.Root.COLUMN_DOCUMENT_ID, kind)
                add(DocumentsContract.Root.COLUMN_TITLE, if (kind == "book") "RanobeLib" else "MangaLib")
                add(DocumentsContract.Root.COLUMN_FLAGS, DocumentsContract.Root.FLAG_SUPPORTS_CREATE)
            }
        }
        return cursor
    }

    override fun queryDocument(documentId: String, projection: Array<out String>?): Cursor {
        val cursor = MatrixCursor(projection ?: documentColumns)
        addDocument(cursor, documentId)
        return cursor
    }

    override fun queryChildDocuments(
        parentDocumentId: String,
        projection: Array<out String>?,
        sortOrder: String?
    ): Cursor {
        val cursor = MatrixCursor(projection ?: documentColumns)
        val parent = relative(parentDocumentId)
        ShizukuAccess.service().listEntries(parent).forEach { entry ->
            if (entry.length < 2) return@forEach
            addDocument(cursor, "$parentDocumentId/${entry.substring(1)}", entry[0] == 'D')
        }
        return cursor
    }

    override fun openDocument(
        documentId: String,
        mode: String,
        signal: CancellationSignal?
    ): ParcelFileDescriptor = ShizukuAccess.service().open(relative(documentId), mode)

    override fun createDocument(
        parentDocumentId: String,
        mimeType: String,
        displayName: String
    ): String {
        require(displayName.isNotBlank() && '/' !in displayName && '\\' !in displayName &&
            displayName != "." && displayName != "..") { "Invalid document name" }
        val id = "$parentDocumentId/$displayName"
        if (!ShizukuAccess.service().create(
                relative(id), mimeType == DocumentsContract.Document.MIME_TYPE_DIR
            )
        ) throw FileNotFoundException(displayName)
        return id
    }

    override fun deleteDocument(documentId: String) {
        if (!ShizukuAccess.service().delete(relative(documentId))) {
            throw FileNotFoundException(documentId)
        }
    }

    override fun renameDocument(documentId: String, displayName: String): String {
        require(displayName.isNotBlank() && '/' !in displayName && '\\' !in displayName &&
            displayName != "." && displayName != "..") { "Invalid document name" }
        val renamed = documentId.substringBeforeLast('/') + "/" + displayName
        if (!ShizukuAccess.service().rename(relative(documentId), relative(renamed))) {
            throw FileNotFoundException(documentId)
        }
        return renamed
    }

    override fun isChildDocument(parentDocumentId: String, documentId: String): Boolean =
        documentId.startsWith("$parentDocumentId/")

    private fun addDocument(cursor: MatrixCursor, documentId: String, knownDirectory: Boolean? = null) {
        val path = relative(documentId)
        val remote = ShizukuAccess.service()
        if (knownDirectory == null && !remote.exists(path)) return
        val directory = knownDirectory ?: remote.isDirectory(path)
        val flags = if (directory) {
            DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE or
                DocumentsContract.Document.FLAG_SUPPORTS_DELETE or
                DocumentsContract.Document.FLAG_SUPPORTS_RENAME
        } else {
            DocumentsContract.Document.FLAG_SUPPORTS_WRITE or
                DocumentsContract.Document.FLAG_SUPPORTS_DELETE or
                DocumentsContract.Document.FLAG_SUPPORTS_RENAME
        }
        cursor.newRow().apply {
            add(DocumentsContract.Document.COLUMN_DOCUMENT_ID, documentId)
            add(DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                if (documentId in setOf("files", "book", "manga")) documentId else path.substringAfterLast('/'))
            add(DocumentsContract.Document.COLUMN_MIME_TYPE,
                if (directory) DocumentsContract.Document.MIME_TYPE_DIR
                else mimeType(path))
            if (cursor.columnNames.contains(DocumentsContract.Document.COLUMN_SIZE)) {
                add(DocumentsContract.Document.COLUMN_SIZE, remote.length(path))
            }
            if (cursor.columnNames.contains(DocumentsContract.Document.COLUMN_LAST_MODIFIED)) {
                add(DocumentsContract.Document.COLUMN_LAST_MODIFIED, remote.lastModified(path))
            }
            add(DocumentsContract.Document.COLUMN_FLAGS, flags)
        }
    }

    private fun relative(documentId: String): String = when {
        documentId == "book" || documentId.startsWith("book/") -> documentId
        documentId == "manga" || documentId.startsWith("manga/") -> documentId
        documentId == "files" || documentId.startsWith("files/") -> documentId
        else -> throw FileNotFoundException(documentId)
    }

    private fun mimeType(path: String): String = when (path.substringAfterLast('.', "").lowercase()) {
        "json" -> "application/json"
        "txt" -> "text/plain"
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "webp" -> "image/webp"
        "zip" -> "application/zip"
        else -> "application/octet-stream"
    }
}
