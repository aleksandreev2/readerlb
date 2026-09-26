package com.readerlb.app.storage

import android.database.Cursor
import android.database.MatrixCursor
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.DocumentsProvider
import com.readerlb.app.storage.bridge.ReaderLbBridgeFileBackend
import java.io.FileNotFoundException

/** Presents the active verified RanobeLib backend through Android's document API. */
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
        cursor.newRow().apply {
            add(DocumentsContract.Root.COLUMN_ROOT_ID, "book")
            add(DocumentsContract.Root.COLUMN_DOCUMENT_ID, "book")
            add(DocumentsContract.Root.COLUMN_TITLE, "RanobeLib")
            add(DocumentsContract.Root.COLUMN_FLAGS, DocumentsContract.Root.FLAG_SUPPORTS_CREATE)
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
        val cursor =
            MatrixCursor(
                projection
                    ?: documentColumns
            )
        val parent =
            relative(
                parentDocumentId
            )
        RanobeLibBackends
            .requireBackend()
            .listEntries(parent)
            .forEach {
                    entry ->
                addDocument(
                    cursor = cursor,
                    documentId =
                        parentDocumentId +
                            "/" +
                            entry.name,
                    entry = entry
                )
            }
        return cursor
    }

    override fun openDocument(
        documentId: String,
        mode: String,
        signal: CancellationSignal?
    ): ParcelFileDescriptor {
        val backend =
            RanobeLibBackends
                .requireBackend()
        val path =
            relative(documentId)

        return if (
            backend is
                ReaderLbBridgeFileBackend
        ) {
            backend.openProxy(
                requireNotNull(context),
                path,
                mode
            )
        } else {
            backend.open(
                path,
                mode
            )
        }
    }

    override fun createDocument(
        parentDocumentId: String,
        mimeType: String,
        displayName: String
    ): String {
        require(displayName.isNotBlank() && '/' !in displayName && '\\' !in displayName &&
            displayName != "." && displayName != "..") { "Invalid document name" }
        val id = "$parentDocumentId/$displayName"
        if (!RanobeLibBackends.requireBackend().create(
                relative(id), mimeType == DocumentsContract.Document.MIME_TYPE_DIR
            )
        ) throw FileNotFoundException(displayName)
        return id
    }

    override fun deleteDocument(documentId: String) {
        if (!RanobeLibBackends.requireBackend().delete(relative(documentId))) {
            throw FileNotFoundException(documentId)
        }
    }

    override fun renameDocument(documentId: String, displayName: String): String {
        require(displayName.isNotBlank() && '/' !in displayName && '\\' !in displayName &&
            displayName != "." && displayName != "..") { "Invalid document name" }
        val renamed = documentId.substringBeforeLast('/') + "/" + displayName
        if (!RanobeLibBackends.requireBackend().rename(relative(documentId), relative(renamed))) {
            throw FileNotFoundException(documentId)
        }
        return renamed
    }

    override fun isChildDocument(parentDocumentId: String, documentId: String): Boolean =
        documentId.startsWith("$parentDocumentId/")

    private fun addDocument(
        cursor: MatrixCursor,
        documentId: String,
        entry: RanobeLibFileEntry? = null
    ) {
        val path =
            relative(documentId)
        val remote =
            RanobeLibBackends
                .requireBackend()

        val resolved =
            entry
                ?: run {
                    if (
                        !remote.exists(path)
                    ) {
                        return
                    }

                    RanobeLibFileEntry(
                        name =
                            if (
                                path.isEmpty()
                            ) {
                                "book"
                            } else {
                                path.substringAfterLast(
                                    '/'
                                )
                            },
                        isDirectory =
                            remote.isDirectory(
                                path
                            ),
                        length =
                            remote.length(path),
                        lastModified =
                            remote.lastModified(
                                path
                            )
                    )
                }

        val flags =
            if (
                resolved.isDirectory
            ) {
                DocumentsContract.Document
                    .FLAG_DIR_SUPPORTS_CREATE or
                    DocumentsContract.Document
                        .FLAG_SUPPORTS_DELETE or
                    DocumentsContract.Document
                        .FLAG_SUPPORTS_RENAME
            } else {
                DocumentsContract.Document
                    .FLAG_SUPPORTS_WRITE or
                    DocumentsContract.Document
                        .FLAG_SUPPORTS_DELETE or
                    DocumentsContract.Document
                        .FLAG_SUPPORTS_RENAME
            }

        cursor.newRow().apply {
            add(
                DocumentsContract.Document
                    .COLUMN_DOCUMENT_ID,
                documentId
            )
            add(
                DocumentsContract.Document
                    .COLUMN_DISPLAY_NAME,
                resolved.name
            )
            add(
                DocumentsContract.Document
                    .COLUMN_MIME_TYPE,
                if (
                    resolved.isDirectory
                ) {
                    DocumentsContract.Document
                        .MIME_TYPE_DIR
                } else {
                    mimeType(path)
                }
            )
            add(
                DocumentsContract.Document
                    .COLUMN_SIZE,
                resolved.length
            )
            add(
                DocumentsContract.Document
                    .COLUMN_LAST_MODIFIED,
                resolved.lastModified
            )
            add(
                DocumentsContract.Document
                    .COLUMN_FLAGS,
                flags
            )
        }
    }

    private fun relative(documentId: String): String = when {
        documentId == "book" -> ""
        documentId.startsWith("book/") -> documentId.removePrefix("book/")
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
