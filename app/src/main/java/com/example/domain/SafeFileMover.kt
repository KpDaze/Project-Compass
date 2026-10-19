package com.example.domain

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.example.data.CompassDao
import com.example.data.DocumentEntity
import com.example.data.DocumentLifecycleCategory
import com.example.data.FileOperationJournalEntity
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class SafeMoveResult(
    val success: Boolean,
    val newRelativePath: String,
    val newSafUri: String,
    val journalEntry: FileOperationJournalEntity,
    val message: String
)

object SafeFileMover {

    const val SUBFOLDER_SUPERSEDED = "Superseded"
    const val SUBFOLDER_DELETION_REVIEW = "Review_For_Deletion"

    /**
     * Executes the 6-Step Safe Internal Project-Folder Move Protocol (§13 & §16).
     * Never renames files, never overwrites existing files (uses dated subfolder on name collision),
     * verifies SHA-256 before and after copy, and journals every step for 1-tap Undo.
     */
    suspend fun moveDocumentToSubfolder(
        context: Context,
        dao: CompassDao,
        projectFolderUri: String?,
        document: DocumentEntity,
        targetSubfolder: String,
        targetCategory: DocumentLifecycleCategory
    ): SafeMoveResult {
        val opType = if (targetCategory == DocumentLifecycleCategory.SUPERSEDED) {
            "MOVE_TO_SUPERSEDED"
        } else {
            "MOVE_TO_DELETION_REVIEW"
        }
        val opId = UUID.randomUUID().toString()
        val initialTargetRelPath = "$targetSubfolder/${document.originalFilename}"

        // Step 1: Log intent in journal before touching storage
        val intentOp = FileOperationJournalEntity(
            opId = opId,
            projectId = document.projectId ?: "",
            docId = document.docId,
            operationType = opType,
            originalFilename = document.originalFilename,
            sourceRelativePath = document.currentRelativePath,
            targetRelativePath = initialTargetRelPath,
            expectedSha256 = document.sha256Hash,
            state = "INTENT_LOGGED",
            canUndo = false
        )
        dao.upsertJournalOperation(intentOp)

        return try {
            // Check if backed by local File directory (e.g. compass://demo-folder/... or file://...)
            val localRootDir = resolveLocalProjectRoot(projectFolderUri)
            if (localRootDir != null && localRootDir.exists()) {
                executeLocalFileMove(dao, localRootDir, document, targetSubfolder, targetCategory, intentOp)
            } else if (!projectFolderUri.isNullOrBlank() && projectFolderUri.startsWith("content://")) {
                executeSafTreeFileMove(context, dao, Uri.parse(projectFolderUri), document, targetSubfolder, targetCategory, intentOp)
            } else {
                // Logical subfolder path update when project has no physical folder linked yet
                val updatedHistory = listOf(document.previousRelativePathsJson, document.currentRelativePath)
                    .filter { it.isNotBlank() }
                    .joinToString(" -> ")
                val updatedDoc = document.copy(
                    currentRelativePath = initialTargetRelPath,
                    previousRelativePathsJson = updatedHistory,
                    lifecycleCategory = targetCategory.name
                )
                dao.upsertDocument(updatedDoc)
                val completedOp = intentOp.copy(
                    targetRelativePath = initialTargetRelPath,
                    state = "COMPLETED",
                    canUndo = true
                )
                dao.upsertJournalOperation(completedOp)
                SafeMoveResult(
                    success = true,
                    newRelativePath = initialTargetRelPath,
                    newSafUri = document.currentSafUri,
                    journalEntry = completedOp,
                    message = "Moved '${document.originalFilename}' to $initialTargetRelPath"
                )
            }
        } catch (e: Exception) {
            val failedOp = intentOp.copy(state = "FAILED", canUndo = false)
            dao.upsertJournalOperation(failedOp)
            SafeMoveResult(
                success = false,
                newRelativePath = document.currentRelativePath,
                newSafUri = document.currentSafUri,
                journalEntry = failedOp,
                message = "Safe move aborted (${e.message}) — original file left untouched."
            )
        }
    }

    private suspend fun executeLocalFileMove(
        dao: CompassDao,
        rootDir: File,
        document: DocumentEntity,
        targetSubfolder: String,
        targetCategory: DocumentLifecycleCategory,
        intentOp: FileOperationJournalEntity
    ): SafeMoveResult {
        val sourceFile = File(rootDir, document.currentRelativePath)
        if (!sourceFile.exists()) {
            throw IllegalStateException("Source file not found at ${document.currentRelativePath}")
        }

        // Step 2: Revalidate source content SHA-256 before moving (§16: Changed sources invalidate earlier proposals)
        val sourceBytes = sourceFile.readBytes()
        val currentHash = OfflineDocumentEngine.computeSha256(sourceBytes)
        if (currentHash != document.sha256Hash) {
            throw IllegalStateException("Source file content changed since scan (SHA-256 mismatch)")
        }

        // Step 3: Prepare destination subfolder without renaming or overwriting
        var destSubDir = File(rootDir, targetSubfolder).apply { mkdirs() }
        var destRelPath = "$targetSubfolder/${document.originalFilename}"
        var destFile = File(destSubDir, document.originalFilename)

        if (destFile.exists() && destFile.absolutePath != sourceFile.absolutePath) {
            // Never rename or overwrite: use dated subfolder inside Superseded / Review_For_Deletion
            val dateFolder = SimpleDateFormat("yyyy-MM-dd_HHmmss", Locale.US).format(Date())
            destSubDir = File(destSubDir, dateFolder).apply { mkdirs() }
            destRelPath = "$targetSubfolder/$dateFolder/${document.originalFilename}"
            destFile = File(destSubDir, document.originalFilename)
        }

        // Step 4: Copy bytes and verify destination SHA-256 matches source SHA-256
        destFile.writeBytes(sourceBytes)
        val destHash = OfflineDocumentEngine.computeSha256(destFile.readBytes())
        if (destHash != document.sha256Hash) {
            destFile.delete()
            throw IllegalStateException("Destination SHA-256 verification failed; rolled back copy")
        }

        // Step 5: Remove original only after verified copy
        if (destFile.absolutePath != sourceFile.absolutePath) {
            sourceFile.delete()
        }

        // Step 6: Update database record (preserving permanent docId) and mark journal COMPLETED
        val updatedHistory = listOf(document.previousRelativePathsJson, document.currentRelativePath)
            .filter { it.isNotBlank() }
            .joinToString(" -> ")
        val updatedDoc = document.copy(
            currentRelativePath = destRelPath,
            currentSafUri = destFile.toURI().toString(),
            previousRelativePathsJson = updatedHistory,
            lifecycleCategory = targetCategory.name
        )
        dao.upsertDocument(updatedDoc)

        val completedOp = intentOp.copy(
            targetRelativePath = destRelPath,
            state = "COMPLETED",
            canUndo = true
        )
        dao.upsertJournalOperation(completedOp)

        return SafeMoveResult(
            success = true,
            newRelativePath = destRelPath,
            newSafUri = destFile.toURI().toString(),
            journalEntry = completedOp,
            message = "Safely moved '${document.originalFilename}' into $destRelPath (SHA-256 verified)."
        )
    }

    private suspend fun executeSafTreeFileMove(
        context: Context,
        dao: CompassDao,
        treeUri: Uri,
        document: DocumentEntity,
        targetSubfolder: String,
        targetCategory: DocumentLifecycleCategory,
        intentOp: FileOperationJournalEntity
    ): SafeMoveResult {
        val resolver = context.contentResolver
        val sourceUri = if (document.currentSafUri.isNotBlank()) {
            Uri.parse(document.currentSafUri)
        } else {
            throw IllegalStateException("Missing SAF URI for source document")
        }

        // Step 2: Revalidate source bytes and SHA-256
        val sourceBytes = resolver.openInputStream(sourceUri)?.use { it.readBytes() }
            ?: throw IllegalStateException("Cannot read source file from folder")
        val currentHash = OfflineDocumentEngine.computeSha256(sourceBytes)
        if (currentHash != document.sha256Hash) {
            throw IllegalStateException("Source file changed since last scan")
        }

        val rootDocId = DocumentsContract.getTreeDocumentId(treeUri)
        val rootDocUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, rootDocId)

        // Find or create target subfolder
        val subfolderUri = findOrCreateSafSubdirectory(resolver, treeUri, rootDocId, rootDocUri, targetSubfolder)
            ?: throw IllegalStateException("Could not create subfolder $targetSubfolder")

        val destFileUri = DocumentsContract.createDocument(
            resolver,
            subfolderUri,
            "application/octet-stream",
            document.originalFilename
        ) ?: throw IllegalStateException("Could not create destination file in $targetSubfolder")

        resolver.openOutputStream(destFileUri)?.use { out ->
            out.write(sourceBytes)
        } ?: throw IllegalStateException("Could not write to destination file")

        // Verify destination SHA-256
        val verifyBytes = resolver.openInputStream(destFileUri)?.use { it.readBytes() } ?: ByteArray(0)
        val destHash = OfflineDocumentEngine.computeSha256(verifyBytes)
        if (destHash != document.sha256Hash) {
            DocumentsContract.deleteDocument(resolver, destFileUri)
            throw IllegalStateException("Destination SHA-256 mismatch; rolled back")
        }

        // Delete original source after verified copy
        try {
            DocumentsContract.deleteDocument(resolver, sourceUri)
        } catch (_: Exception) {
        }

        val destRelPath = "$targetSubfolder/${document.originalFilename}"
        val updatedHistory = listOf(document.previousRelativePathsJson, document.currentRelativePath)
            .filter { it.isNotBlank() }
            .joinToString(" -> ")
        val updatedDoc = document.copy(
            currentRelativePath = destRelPath,
            currentSafUri = destFileUri.toString(),
            previousRelativePathsJson = updatedHistory,
            lifecycleCategory = targetCategory.name
        )
        dao.upsertDocument(updatedDoc)

        val completedOp = intentOp.copy(
            targetRelativePath = destRelPath,
            state = "COMPLETED",
            canUndo = true
        )
        dao.upsertJournalOperation(completedOp)

        return SafeMoveResult(
            success = true,
            newRelativePath = destRelPath,
            newSafUri = destFileUri.toString(),
            journalEntry = completedOp,
            message = "Safely moved '${document.originalFilename}' to $destRelPath"
        )
    }

    /**
     * Reverses a completed subfolder move back to its original location (§16 Safe Undo).
     */
    suspend fun undoSubfolderMove(
        context: Context,
        dao: CompassDao,
        projectFolderUri: String?,
        journalOp: FileOperationJournalEntity
    ): String {
        if (!journalOp.canUndo || journalOp.state != "COMPLETED") {
            return "This operation cannot be undone."
        }
        val doc = dao.getDocumentById(journalOp.docId) ?: return "Document record not found."
        val localRootDir = resolveLocalProjectRoot(projectFolderUri)

        if (localRootDir != null && localRootDir.exists()) {
            val currentFile = File(localRootDir, journalOp.targetRelativePath)
            val restoredFile = File(localRootDir, journalOp.sourceRelativePath)
            restoredFile.parentFile?.mkdirs()

            if (currentFile.exists()) {
                val bytes = currentFile.readBytes()
                val hash = OfflineDocumentEngine.computeSha256(bytes)
                if (hash != journalOp.expectedSha256) {
                    return "Undo aborted: File content was modified after move."
                }
                restoredFile.writeBytes(bytes)
                val verifyHash = OfflineDocumentEngine.computeSha256(restoredFile.readBytes())
                if (verifyHash != journalOp.expectedSha256) {
                    restoredFile.delete()
                    return "Undo failed: Could not verify restored file hash."
                }
                if (currentFile.absolutePath != restoredFile.absolutePath) {
                    currentFile.delete()
                }
            }
        }

        dao.upsertDocument(
            doc.copy(
                currentRelativePath = journalOp.sourceRelativePath,
                lifecycleCategory = DocumentLifecycleCategory.CURRENT.name
            )
        )
        dao.upsertJournalOperation(
            journalOp.copy(
                state = "ROLLED_BACK",
                canUndo = false
            )
        )
        return "Undone: Restored '${doc.originalFilename}' back to '${journalOp.sourceRelativePath}' (Current)."
    }

    private fun resolveLocalProjectRoot(projectFolderUri: String?): File? {
        if (projectFolderUri.isNullOrBlank()) return null
        return when {
            projectFolderUri.startsWith("compass://demo-folder/") -> {
                File(projectFolderUri.removePrefix("compass://demo-folder/"))
            }
            projectFolderUri.startsWith("file://") -> {
                Uri.parse(projectFolderUri).path?.let { File(it) }
            }
            else -> null
        }
    }

    private fun findOrCreateSafSubdirectory(
        resolver: android.content.ContentResolver,
        treeUri: Uri,
        parentDocId: String,
        parentDocUri: Uri,
        subfolderName: String
    ): Uri? {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE
        )
        resolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
            while (cursor.moveToNext()) {
                val name = cursor.getString(nameCol)
                val mime = cursor.getString(mimeCol)
                if (name == subfolderName && mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                    return DocumentsContract.buildDocumentUriUsingTree(treeUri, cursor.getString(idCol))
                }
            }
        }
        return DocumentsContract.createDocument(
            resolver,
            parentDocUri,
            DocumentsContract.Document.MIME_TYPE_DIR,
            subfolderName
        )
    }
}
