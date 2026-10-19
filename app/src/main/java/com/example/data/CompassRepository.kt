package com.example.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import com.example.domain.BackupRestorePreview
import com.example.domain.HandoverAndBackupEngine
import com.example.domain.OfflineDocumentEngine
import com.example.domain.SafeFileMover
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class RawFolderFile(
    val filename: String,
    val relativePath: String,
    val safUriString: String = "",
    val lastModified: Long,
    val bytes: ByteArray
)

class CompassRepository(private val dao: CompassDao) {

    val allProjects: Flow<List<ProjectEntity>> = dao.observeAllProjects()
    val allWorkAreas: Flow<List<WorkAreaEntity>> = dao.observeAllWorkAreas()
    val allDocuments: Flow<List<DocumentEntity>> = dao.observeAllDocuments()
    val allSnapshots: Flow<List<DocumentSnapshotEntity>> = dao.observeAllSnapshots()
    val allBrainItems: Flow<List<BrainItemEntity>> = dao.observeAllBrainItems()
    val allEvidenceLinks: Flow<List<EvidenceLinkEntity>> = dao.observeAllEvidenceLinks()
    val allChangeTrail: Flow<List<ChangeTrailEntity>> = dao.observeAllChangeTrail()
    val allJournalOps: Flow<List<FileOperationJournalEntity>> = dao.observeAllJournalOperations()

    suspend fun createProject(
        name: String,
        purpose: String,
        projectType: ProjectType,
        resumePointSummary: String,
        externalLinks: String = "",
        context: Context? = null
    ): ProjectEntity = withContext(Dispatchers.IO) {
        val projectId = UUID.randomUUID().toString()
        val folderUri = if (context != null) {
            val compassRoot = File(context.filesDir, "Project_Compass").apply { mkdirs() }
            val safeDirName = name.trim().replace(Regex("[^a-zA-Z0-9 _-]"), "").trim().ifEmpty { "Project_$projectId" }
            val projDir = File(compassRoot, safeDirName).apply {
                mkdirs()
                File(this, SafeFileMover.SUBFOLDER_SUPERSEDED).mkdirs()
                File(this, SafeFileMover.SUBFOLDER_DELETION_REVIEW).mkdirs()
            }
            "compass://demo-folder/${projDir.absolutePath}"
        } else {
            null
        }
        val project = ProjectEntity(
            projectId = projectId,
            name = name.trim(),
            purpose = purpose.trim(),
            projectType = projectType.name,
            status = ProjectStatus.ACTIVE.name,
            resumePointSummary = resumePointSummary.trim().ifEmpty { "Check recent handovers and tasks." },
            linkedFolderUri = folderUri,
            linkedFolderDisplayName = "Project Compass/${name.trim()}",
            permissionState = FolderPermissionState.CONNECTED.name,
            externalLinksJson = externalLinks
        )
        dao.upsertProject(project)

        val defaultAreas = listOf(
            "Design" to "User interface, flows, creative layout, and visual structure",
            "Development" to "Implementation, architecture, technical logic, and storage",
            "Testing" to "Verification evidence, test cases, and bug checks",
            "Research" to "Investigation, multi-AI comparisons, and options analysis",
            "Documentation" to "Handovers, user guides, and project summaries"
        ).mapIndexed { index, (areaName, desc) ->
            WorkAreaEntity(
                workAreaId = UUID.randomUUID().toString(),
                projectId = projectId,
                name = areaName,
                description = desc,
                displayOrder = index
            )
        }
        dao.upsertWorkAreas(defaultAreas)

        val purposeItem = BrainItemEntity(
            brainItemId = UUID.randomUUID().toString(),
            projectId = projectId,
            workAreaId = defaultAreas.first().workAreaId,
            category = BrainCategory.PURPOSE.name,
            title = "Core Purpose: ${name.trim()}",
            content = purpose.trim(),
            reasonOrContext = "Defined when creating the project.",
            truthStatus = TruthStatus.APPROVED.name,
            aiContributor = "User-Authored",
            exactSourceFilename = "Project Creation",
            sectionLineReference = "Initial Definition"
        )
        dao.upsertBrainItem(purposeItem)
        project
    }

    suspend fun updateProjectDetails(
        projectId: String,
        name: String,
        purpose: String,
        status: ProjectStatus,
        resumePointSummary: String,
        externalLinksJson: String
    ) = withContext(Dispatchers.IO) {
        val existing = dao.getProjectById(projectId) ?: return@withContext
        val updated = existing.copy(
            name = name.trim(),
            purpose = purpose.trim(),
            status = status.name,
            resumePointSummary = resumePointSummary.trim(),
            externalLinksJson = externalLinksJson.trim()
        )
        dao.upsertProject(updated)
    }

    suspend fun addCustomWorkArea(projectId: String, name: String, description: String) = withContext(Dispatchers.IO) {
        val current = dao.getWorkAreasForProject(projectId)
        dao.upsertWorkArea(
            WorkAreaEntity(
                workAreaId = UUID.randomUUID().toString(),
                projectId = projectId,
                name = name.trim(),
                description = description.trim(),
                displayOrder = current.size
            )
        )
    }

    suspend fun connectSafFolderAndScanReadOnly(
        context: Context,
        projectId: String,
        treeUri: Uri
    ): String = withContext(Dispatchers.IO) {
        try {
            val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(treeUri, takeFlags)
        } catch (_: Exception) {
        }

        val existing = dao.getProjectById(projectId) ?: return@withContext "Project not found"
        val folderName = treeUri.lastPathSegment?.substringAfterLast(':') ?: "Linked Project Folder"
        dao.upsertProject(
            existing.copy(
                linkedFolderUri = treeUri.toString(),
                linkedFolderDisplayName = folderName,
                permissionState = FolderPermissionState.CONNECTED.name,
                lastScannedAt = System.currentTimeMillis()
            )
        )
        return@withContext scanConnectedSafFolderReadOnly(context, projectId)
    }

    suspend fun disconnectProjectFolder(projectId: String) = withContext(Dispatchers.IO) {
        val existing = dao.getProjectById(projectId) ?: return@withContext
        dao.upsertProject(
            existing.copy(
                linkedFolderUri = null,
                permissionState = FolderPermissionState.UNLINKED.name
            )
        )
    }

    suspend fun checkAndRefreshFolderPermissionState(context: Context, projectId: String): FolderPermissionState =
        withContext(Dispatchers.IO) {
            val project = dao.getProjectById(projectId) ?: return@withContext FolderPermissionState.UNLINKED
            val uriStr = project.linkedFolderUri
            if (uriStr.isNullOrBlank()) return@withContext FolderPermissionState.UNLINKED

            if (uriStr.startsWith("compass://demo-folder/")) {
                return@withContext FolderPermissionState.CONNECTED
            }

            val hasPermission = try {
                val treeUri = Uri.parse(uriStr)
                val docId = DocumentsContract.getTreeDocumentId(treeUri)
                val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                context.contentResolver.query(
                    docUri,
                    arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID),
                    null,
                    null,
                    null
                )?.use { it.count >= 0 } ?: false
            } catch (_: Exception) {
                false
            }

            val newState = if (hasPermission) FolderPermissionState.CONNECTED else FolderPermissionState.PERMISSION_NEEDED
            if (project.permissionState != newState.name) {
                dao.upsertProject(project.copy(permissionState = newState.name))
            }
            newState
        }

    suspend fun scanConnectedSafFolderReadOnly(context: Context, projectId: String): String =
        withContext(Dispatchers.IO) {
            val project = dao.getProjectById(projectId) ?: return@withContext "Project not found"
            val uriStr = project.linkedFolderUri ?: return@withContext "No folder connected to this project"
            if (uriStr.startsWith("compass://demo-folder/")) {
                val demoDir = File(uriStr.removePrefix("compass://demo-folder/"))
                if (demoDir.exists()) {
                    val rawFiles = mutableListOf<RawFolderFile>()
                    collectLocalDirectoryFiles(demoDir, "", rawFiles)
                    return@withContext ingestRawFilesIntoProjectReadOnly(
                        projectId = projectId,
                        folderDisplayName = project.linkedFolderDisplayName ?: "Project Compass Folder",
                        rawFiles = rawFiles,
                        context = context
                    )
                }
                return@withContext "All files in '${project.linkedFolderDisplayName}' are up to date."
            }

            val state = checkAndRefreshFolderPermissionState(context, projectId)
            if (state == FolderPermissionState.PERMISSION_NEEDED) {
                return@withContext "Folder permission lapsed. All Project Brain knowledge is preserved — tap Reconnect Folder to restore access."
            }

            val rawFiles = mutableListOf<RawFolderFile>()
            try {
                val treeUri = Uri.parse(uriStr)
                val rootDocId = DocumentsContract.getTreeDocumentId(treeUri)
                collectSafTreeFilesReadOnly(context, treeUri, rootDocId, "", rawFiles)
            } catch (e: Exception) {
                return@withContext "Could not read folder: ${e.message}"
            }

            return@withContext ingestRawFilesIntoProjectReadOnly(
                projectId = projectId,
                folderDisplayName = project.linkedFolderDisplayName ?: "Connected Folder",
                rawFiles = rawFiles,
                context = context
            )
        }

    private fun collectLocalDirectoryFiles(
        dir: File,
        parentRelPath: String,
        outFiles: MutableList<RawFolderFile>
    ) {
        dir.listFiles()?.sortedBy { it.name }?.forEach { child ->
            val relPath = if (parentRelPath.isEmpty()) child.name else "$parentRelPath/${child.name}"
            if (child.isDirectory) {
                collectLocalDirectoryFiles(child, relPath, outFiles)
            } else {
                outFiles.add(
                    RawFolderFile(
                        filename = child.name,
                        relativePath = relPath,
                        safUriString = child.toURI().toString(),
                        lastModified = child.lastModified(),
                        bytes = child.readBytes()
                    )
                )
            }
        }
    }

    private fun collectSafTreeFilesReadOnly(
        context: Context,
        treeUri: Uri,
        parentDocumentId: String,
        parentRelativePath: String,
        outFiles: MutableList<RawFolderFile>
    ) {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocumentId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED
        )
        context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
            val modCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_LAST_MODIFIED)

            while (cursor.moveToNext()) {
                val childDocId = cursor.getString(idCol)
                val displayName = cursor.getString(nameCol) ?: "untitled"
                val mimeType = cursor.getString(mimeCol) ?: ""
                val lastMod = if (cursor.isNull(modCol)) System.currentTimeMillis() else cursor.getLong(modCol)
                val relPath = if (parentRelativePath.isEmpty()) displayName else "$parentRelativePath/$displayName"

                if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
                    collectSafTreeFilesReadOnly(context, treeUri, childDocId, relPath, outFiles)
                } else {
                    val fileUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childDocId)
                    val bytes = try {
                        context.contentResolver.openInputStream(fileUri)?.use { it.readBytes() } ?: ByteArray(0)
                    } catch (_: Exception) {
                        ByteArray(0)
                    }
                    outFiles.add(
                        RawFolderFile(
                            filename = displayName,
                            relativePath = relPath,
                            safUriString = fileUri.toString(),
                            lastModified = lastMod,
                            bytes = bytes
                        )
                    )
                }
            }
        }
    }

    /**
     * Stages 1–3 Folder Scanner, Handover Comparator & Automatic Subfolder Organiser:
     * - Tracks permanent doc_uuid and SHA-256 across moves.
     * - Detects in-place edits and saves line-by-line diffs.
     * - Compares new handovers against predecessor handovers and Approved Project Brain:
     *   • If clean (no omissions or contradictions), automatically moves the predecessor handover into `Superseded/`!
     *   • If a requirement was omitted or contradicted, triggers the Safety Brake and holds the move for review!
     * - Automatically moves exact byte-for-byte duplicate files into `Review_For_Deletion/` with 1-tap Undo!
     */
    suspend fun ingestRawFilesIntoProjectReadOnly(
        projectId: String,
        folderDisplayName: String,
        rawFiles: List<RawFolderFile>,
        context: Context? = null
    ): String = withContext(Dispatchers.IO) {
        val project = dao.getProjectById(projectId) ?: return@withContext "Project not found"
        val workAreas = dao.getWorkAreasForProject(projectId)
        val existingDocs = dao.getDocumentsForProject(projectId).toMutableList()
        val seenPathsInScan = rawFiles.map { it.relativePath }.toSet()

        var newFilesCount = 0
        var unchangedSkippedCount = 0
        var movedDetectedCount = 0
        var editedInPlaceCount = 0
        var autoSupersededCount = 0
        var safetyBrakeCount = 0
        var duplicatesMovedCount = 0
        var proposalsExtractedCount = 0

        for (raw in rawFiles) {
            val hash = OfflineDocumentEngine.computeSha256(raw.bytes)
            val formatLabel = OfflineDocumentEngine.detectFormatLabel(raw.filename)
            val existingAtSamePath = existingDocs.firstOrNull { it.currentRelativePath == raw.relativePath }

            // Case 1: Unchanged file at same relative path
            if (existingAtSamePath != null && existingAtSamePath.sha256Hash == hash) {
                if (existingAtSamePath.missingOnDisk) {
                    dao.upsertDocument(existingAtSamePath.copy(missingOnDisk = false))
                }
                unchangedSkippedCount++
                continue
            }

            // Case 2: Edited in place at same relative path
            if (existingAtSamePath != null && existingAtSamePath.sha256Hash != hash) {
                editedInPlaceCount++
                val priorSnapshots = dao.getSnapshotsForDocument(existingAtSamePath.docId)
                val prevText = priorSnapshots.firstOrNull()?.extractedPlainText ?: ""
                val newText = OfflineDocumentEngine.extractTextOffline(raw.filename, raw.bytes)
                val newVersion = (priorSnapshots.maxOfOrNull { it.versionNumber } ?: 1) + 1
                val newSnapId = UUID.randomUUID().toString()

                dao.upsertDocument(
                    existingAtSamePath.copy(
                        sha256Hash = hash,
                        fileSizeBytes = raw.bytes.size.toLong(),
                        lastModifiedTimestamp = raw.lastModified,
                        editedInPlaceFlag = true,
                        missingOnDisk = false
                    )
                )
                dao.upsertSnapshot(
                    DocumentSnapshotEntity(
                        snapshotId = newSnapId,
                        docId = existingAtSamePath.docId,
                        versionNumber = newVersion,
                        sha256Hash = hash,
                        extractedPlainText = newText,
                        lineCount = newText.lines().size
                    )
                )
                val (removedSummary, addedSummary) = if (prevText.isNotEmpty()) {
                    OfflineDocumentEngine.computeLineDiffSummary(prevText, newText)
                } else {
                    "Comparison unavailable (no prior text snapshot)" to newText.take(180)
                }
                dao.upsertChangeTrailEntry(
                    ChangeTrailEntity(
                        changeId = UUID.randomUUID().toString(),
                        projectId = projectId,
                        workAreaId = existingAtSamePath.workAreaId,
                        changeType = ChangeType.EDITED_IN_PLACE.name,
                        title = "Edited in Place: ${raw.filename} (v$newVersion)",
                        previousWording = removedSummary,
                        newWording = addedSummary,
                        documentedReason = "Detected SHA-256 fingerprint change at existing path '${raw.relativePath}'.",
                        sourceDocId = existingAtSamePath.docId,
                        exactSourceFilename = raw.filename,
                        sectionLineRef = "Version ${newVersion - 1} -> Version $newVersion",
                        aiContributor = existingAtSamePath.aiSourceName,
                        reviewStatus = ReviewStatus.PENDING_REVIEW.name
                    )
                )
                continue
            }

            // Case 3: Moved externally (Old path missing + identical SHA-256 hash at new path)
            val movedCandidate = existingDocs.firstOrNull {
                it.sha256Hash == hash && it.currentRelativePath !in seenPathsInScan
            }
            if (movedCandidate != null) {
                movedDetectedCount++
                val updatedHistory = listOf(movedCandidate.previousRelativePathsJson, movedCandidate.currentRelativePath)
                    .filter { it.isNotBlank() }
                    .joinToString(" -> ")
                val inferredCategory = when {
                    raw.relativePath.startsWith("${SafeFileMover.SUBFOLDER_SUPERSEDED}/") -> DocumentLifecycleCategory.SUPERSEDED.name
                    raw.relativePath.startsWith("${SafeFileMover.SUBFOLDER_DELETION_REVIEW}/") -> DocumentLifecycleCategory.REVIEW_FOR_DELETION.name
                    else -> movedCandidate.lifecycleCategory
                }
                val updatedDoc = movedCandidate.copy(
                    currentRelativePath = raw.relativePath,
                    currentSafUri = raw.safUriString,
                    previousRelativePathsJson = updatedHistory,
                    lifecycleCategory = inferredCategory,
                    missingOnDisk = false
                )
                dao.upsertDocument(updatedDoc)
                existingDocs.remove(movedCandidate)
                existingDocs.add(updatedDoc)
                continue
            }

            // Case 4: Brand-new file (or Exact Duplicate of a still-present file)
            val duplicateOfExisting = existingDocs.firstOrNull {
                it.sha256Hash == hash && it.currentRelativePath in seenPathsInScan
            }

            newFilesCount++
            val newDocId = UUID.randomUUID().toString()
            val newSnapshotId = UUID.randomUUID().toString()
            val extractedText = OfflineDocumentEngine.extractTextOffline(raw.filename, raw.bytes)
            val currentApprovedBrain = dao.getBrainItemsForProject(projectId)
            val alreadyInSubfolder = raw.relativePath.startsWith("${SafeFileMover.SUBFOLDER_SUPERSEDED}/") ||
                raw.relativePath.startsWith("${SafeFileMover.SUBFOLDER_DELETION_REVIEW}/")

            val latestHandoverDoc = if (!alreadyInSubfolder && duplicateOfExisting == null) {
                existingDocs
                    .filter { it.isHandoverReport && it.lifecycleCategory == DocumentLifecycleCategory.CURRENT.name }
                    .maxByOrNull { it.recordedAt }
            } else {
                null
            }

            val analysis = OfflineDocumentEngine.analyzeDocumentContent(
                projectId = projectId,
                docId = newDocId,
                snapshotId = newSnapshotId,
                filename = raw.filename,
                plainText = extractedText,
                workAreas = workAreas,
                existingApprovedBrain = currentApprovedBrain,
                predecessorDocId = latestHandoverDoc?.docId
            )

            val assignedWorkAreaId = analysis.proposedBrainItems.firstOrNull()?.workAreaId
                ?: workAreas.firstOrNull()?.workAreaId

            val initialCategory = when {
                raw.relativePath.startsWith("${SafeFileMover.SUBFOLDER_SUPERSEDED}/") -> DocumentLifecycleCategory.SUPERSEDED
                raw.relativePath.startsWith("${SafeFileMover.SUBFOLDER_DELETION_REVIEW}/") -> DocumentLifecycleCategory.REVIEW_FOR_DELETION
                duplicateOfExisting != null -> DocumentLifecycleCategory.REVIEW_FOR_DELETION
                else -> DocumentLifecycleCategory.CURRENT
            }

            var newDoc = DocumentEntity(
                docId = newDocId,
                projectId = projectId,
                originalFilename = raw.filename,
                currentRelativePath = raw.relativePath,
                currentSafUri = raw.safUriString,
                sha256Hash = hash,
                fileSizeBytes = raw.bytes.size.toLong(),
                lastModifiedTimestamp = raw.lastModified,
                formatLabel = formatLabel,
                lifecycleCategory = initialCategory.name,
                isHandoverReport = analysis.isHandoverCandidate && duplicateOfExisting == null,
                predecessorDocId = if (analysis.isHandoverCandidate) latestHandoverDoc?.docId else null,
                aiSourceName = analysis.detectedAiSource,
                conversationUrl = analysis.detectedConversationUrl,
                workAreaId = assignedWorkAreaId
            )
            dao.upsertDocument(newDoc)
            existingDocs.add(newDoc)

            dao.upsertSnapshot(
                DocumentSnapshotEntity(
                    snapshotId = newSnapshotId,
                    docId = newDocId,
                    versionNumber = 1,
                    sha256Hash = hash,
                    extractedPlainText = extractedText,
                    lineCount = analysis.lineCount
                )
            )

            // If this new file is an exact duplicate and not already in Review_For_Deletion, move it automatically when context is available
            if (duplicateOfExisting != null && !alreadyInSubfolder && context != null) {
                val moveRes = SafeFileMover.moveDocumentToSubfolder(
                    context = context,
                    dao = dao,
                    projectFolderUri = project.linkedFolderUri,
                    document = newDoc,
                    targetSubfolder = SafeFileMover.SUBFOLDER_DELETION_REVIEW,
                    targetCategory = DocumentLifecycleCategory.REVIEW_FOR_DELETION
                )
                if (moveRes.success) {
                    duplicatesMovedCount++
                    newDoc = dao.getDocumentById(newDoc.docId) ?: newDoc
                }
                continue
            }

            if (analysis.proposedBrainItems.isNotEmpty()) {
                dao.upsertBrainItems(analysis.proposedBrainItems)
                dao.upsertEvidenceLinks(analysis.evidenceLinks)
                proposalsExtractedCount += analysis.proposedBrainItems.size
            }
            if (analysis.changeTrailAlerts.isNotEmpty()) {
                dao.upsertChangeTrailEntries(analysis.changeTrailAlerts)
            }

            // Stage 2 & 3 Automatic Handover Succession with Safety Brake:
            if (analysis.isHandoverCandidate && latestHandoverDoc != null && !alreadyInSubfolder) {
                val hasRedFlags = analysis.changeTrailAlerts.any {
                    it.changeType == ChangeType.OMITTED_IN_HANDOVER.name ||
                        it.changeType == ChangeType.CONTRADICTION_DETECTED.name ||
                        it.changeType == ChangeType.DECISION_REVERSED.name
                }
                if (!hasRedFlags && context != null) {
                    // Clean Handover Succession! Automatically move predecessor handover into Superseded/
                    val moveRes = SafeFileMover.moveDocumentToSubfolder(
                        context = context,
                        dao = dao,
                        projectFolderUri = project.linkedFolderUri,
                        document = latestHandoverDoc,
                        targetSubfolder = SafeFileMover.SUBFOLDER_SUPERSEDED,
                        targetCategory = DocumentLifecycleCategory.SUPERSEDED
                    )
                    if (moveRes.success) {
                        autoSupersededCount++
                        val updatedPred = dao.getDocumentById(latestHandoverDoc.docId)
                        if (updatedPred != null) {
                            existingDocs.removeAll { it.docId == updatedPred.docId }
                            existingDocs.add(updatedPred)
                        }
                        dao.upsertChangeTrailEntry(
                            ChangeTrailEntity(
                                changeId = UUID.randomUUID().toString(),
                                projectId = projectId,
                                workAreaId = assignedWorkAreaId,
                                changeType = ChangeType.FILE_RETIRES_PREDECESSOR.name,
                                title = "Auto-Moved Predecessor '${latestHandoverDoc.originalFilename}' to Superseded/",
                                previousWording = "Current Handover: ${latestHandoverDoc.originalFilename}",
                                newWording = "Current Handover: ${newDoc.originalFilename} (Predecessor moved to ${moveRes.newRelativePath})",
                                documentedReason = "New handover passed scan with zero omitted requirements or conflicts.",
                                sourceDocId = newDoc.docId,
                                predecessorDocId = latestHandoverDoc.docId,
                                exactSourceFilename = newDoc.originalFilename,
                                sectionLineRef = "Handover Succession Verified",
                                aiContributor = analysis.detectedAiSource,
                                reviewStatus = ReviewStatus.APPROVED.name
                            )
                        )
                    }
                } else if (hasRedFlags) {
                    // Safety Brake Triggered! Hold automatic move until user checks the omitted/conflicting item
                    safetyBrakeCount++
                }
            }
        }

        // Reconcile missing files without discarding history
        val currentDocsSnapshot = dao.getDocumentsForProject(projectId)
        for (doc in currentDocsSnapshot) {
            if (doc.currentRelativePath !in seenPathsInScan &&
                !doc.currentRelativePath.startsWith("${SafeFileMover.SUBFOLDER_SUPERSEDED}/") &&
                !doc.currentRelativePath.startsWith("${SafeFileMover.SUBFOLDER_DELETION_REVIEW}/") &&
                !doc.missingOnDisk
            ) {
                dao.upsertDocument(doc.copy(missingOnDisk = true))
            }
        }

        dao.upsertProject(
            project.copy(
                linkedFolderDisplayName = folderDisplayName,
                permissionState = FolderPermissionState.CONNECTED.name,
                lastScannedAt = System.currentTimeMillis()
            )
        )

        return@withContext "Scan complete: $newFilesCount files processed, $autoSupersededCount old handovers auto-moved to Superseded/, $duplicatesMovedCount duplicates moved to Review_For_Deletion/, $safetyBrakeCount held by Safety Brake for your review."
    }

    suspend fun ensureInitialWorkspaceReady(context: Context) = withContext(Dispatchers.IO) {
        val currentProjects = dao.getAllProjectsSnapshot()
        if (currentProjects.isEmpty()) {
            importDemoPopulatedProjectReadOnly(context)
        } else {
            scanAllProjectCompassFoldersAutomatically(context)
        }
    }

    /**
     * Background FileScanner:
     * Monitors the designated Project Compass root directory and all project folders:
     * - Automatically discovers any new project subfolders created by FileMate in `Project_Compass/`.
     * - Routes loose files dropped in the root `Project_Compass/` folder into Unassigned Inbox.
     * - Scans all active project folders for new incoming handovers/documents, compares them against
     *   existing Approved Project Brain rules, and automatically versions clean predecessors into
     *   `Superseded/` and exact duplicates into `Review_For_Deletion/`.
     */
    suspend fun scanAllProjectCompassFoldersAutomatically(context: Context) = withContext(Dispatchers.IO) {
        val compassRootDir = File(context.filesDir, "Project_Compass").apply { mkdirs() }
        val existingProjects = dao.getAllProjectsSnapshot().toMutableList()
        val existingDocs = dao.getAllDocumentsSnapshot()

        // 1. Check root Project_Compass directory for new subfolders or loose unassigned files
        compassRootDir.listFiles()?.forEach { entry ->
            if (entry.isDirectory) {
                val expectedUri = "compass://demo-folder/${entry.absolutePath}"
                val alreadyLinked = existingProjects.any {
                    it.linkedFolderUri == expectedUri || it.name.equals(entry.name, ignoreCase = true)
                }
                if (!alreadyLinked) {
                    val created = createProject(
                        name = entry.name.replace('_', ' '),
                        purpose = "Auto-discovered folder in Project Compass.",
                        projectType = ProjectType.SOFTWARE,
                        resumePointSummary = "Check newly scanned files in ${entry.name}."
                    )
                    val linked = created.copy(
                        linkedFolderUri = expectedUri,
                        linkedFolderDisplayName = "Project Compass/${created.name}",
                        permissionState = FolderPermissionState.CONNECTED.name
                    )
                    dao.upsertProject(linked)
                    existingProjects.add(linked)
                }
            } else if (entry.isFile) {
                val bytes = entry.readBytes()
                val hash = OfflineDocumentEngine.computeSha256(bytes)
                val alreadyInInbox = existingDocs.any { it.sha256Hash == hash }
                if (!alreadyInInbox) {
                    val extracted = OfflineDocumentEngine.extractTextOffline(entry.name, bytes)
                    addIncomingItemToUnassignedInbox(
                        titleOrFilename = entry.name,
                        textContent = extracted.ifBlank { "Binary or unindexed file: ${entry.name}" },
                        aiSource = "Not Stated"
                    )
                }
            }
        }

        // 2. Scan every active project's folder for new incoming handovers or changes
        for (project in existingProjects.filter { it.status != ProjectStatus.ARCHIVED.name }) {
            if (!project.linkedFolderUri.isNullOrBlank()) {
                scanConnectedSafFolderReadOnly(context, project.projectId)
            }
        }
    }

    /**
     * Imports a realistic Project Compass folder demonstrating both:
     * 1. Automatic move of a clean predecessor handover into `Superseded/`
     * 2. Safety Brake holding a handover that omitted an approved requirement
     * 3. Automatic move of an exact duplicate into `Review_For_Deletion/` with 1-tap Undo
     */
    suspend fun importDemoPopulatedProjectReadOnly(context: Context): ProjectEntity = withContext(Dispatchers.IO) {
        val demoDir = File(context.filesDir, "demo_filemate_sorted_folder").apply {
            deleteRecursively()
            mkdirs()
        }
        val handoverV1 = """
            Source AI: Claude 3.7 Sonnet
            Conversation: https://claude.ai/chat/compass-v1
            # Project Purpose
            - Build an offline-first Field Botanical Journal & Specimen Tracker for solo researchers.
            ## Requirements
            - Must store all plant specimen photos and GPS coordinates 100% offline on the phone.
            - Must export field observations as clean CSV and Markdown reports without requiring an account.
            - Never overwrite an original specimen observation note; keep full edit history.
            ## Decisions
            - Use local SQLite via Room with SHA-256 photo hash verification so duplicate photos are flagged.
            ## Completed Work
            - Designed the 4-screen offline field capture layout and specimen tag taxonomy.
        """.trimIndent()

        // Clean successor handover (keeps all 3 approved requirements from v1 -> automatically moves v1 into Superseded/!)
        val handoverV1_1 = """
            Source AI: Gemini
            Conversation: https://gemini.google.com/app/compass-v1-1
            # Project Purpose
            - Build an offline-first Field Botanical Journal & Specimen Tracker for solo researchers.
            ## Requirements
            - Must store all plant specimen photos and GPS coordinates 100% offline on the phone.
            - Must export field observations as clean CSV and Markdown reports without requiring an account.
            - Never overwrite an original specimen observation note; keep full edit history.
            ## Decisions
            - Use local SQLite via Room with SHA-256 photo hash verification so duplicate photos are flagged.
            ## Tasks
            - [ ] Implement offline CSV and Markdown field report exporter.
            - [ ] Test GPS coordinate fallback when satellite signal is weak under canopy.
        """.trimIndent()

        // Flawed successor handover (leaves out the CSV and Markdown export requirement -> triggers Safety Brake!)
        val handoverV2 = """
            Source AI: ChatGPT o3-mini
            Conversation: https://chatgpt.com/c/compass-v2
            # Project Purpose
            - Build an offline-first Field Botanical Journal & Specimen Tracker for solo researchers.
            ## Requirements
            - Must store all plant specimen photos and GPS coordinates 100% offline on the phone.
            - Never overwrite an original specimen observation note; keep full edit history.
            - Add taxonomy autocomplete for 500 common native flora families.
            ## Decisions
            - Use local SQLite via Room with SHA-256 photo hash verification so duplicate photos are flagged.
            ## Completed Work
            - Implemented and fully verified the offline CSV exporter and GPS weak-signal fallback.
            ## Next Steps
            - Run field verification on the taxonomy autocomplete list.
        """.trimIndent()

        val researchNotes = """
            Source AI: Gemini
            # Research Notes
            ## Resources
            - Australian National Herbarium offline CSV taxonomy reference structure.
            ## Problems
            - Herbarium CSV uses Latin binomial columns that need mapping to plain-English common names.
        """.trimIndent()

        val f1 = File(demoDir, "2026-10-10_Claude_Botanical_Handover_v1.md").apply { writeText(handoverV1) }

        val project = createProject(
            name = "Botanical Field Journal",
            purpose = "Offline-first Field Botanical Journal & Specimen Tracker for solo researchers.",
            projectType = ProjectType.SOFTWARE,
            resumePointSummary = "Check Review Queue: Claude v1 was auto-moved to Superseded/, while ChatGPT v2 triggered the Safety Brake for leaving out a requirement."
        )
        dao.upsertProject(
            project.copy(
                linkedFolderUri = "compass://demo-folder/${demoDir.absolutePath}",
                linkedFolderDisplayName = "Project Compass/Botanical Field Journal"
            )
        )

        // Step 1: Scan Handover v1 and approve its baseline rules
        ingestRawFilesIntoProjectReadOnly(
            projectId = project.projectId,
            folderDisplayName = "Project Compass/Botanical Field Journal",
            rawFiles = listOf(
                RawFolderFile(f1.name, f1.name, f1.toURI().toString(), f1.lastModified(), f1.readBytes())
            ),
            context = context
        )
        val v1Brain = dao.getBrainItemsForProject(project.projectId)
        v1Brain.filter {
            it.category == BrainCategory.REQUIREMENT.name || it.category == BrainCategory.DECISION.name
        }.forEach { item ->
            dao.upsertBrainItem(item.copy(truthStatus = TruthStatus.APPROVED.name))
        }

        // Step 2: Gemini v1.1 arrives with all requirements intact -> automatically moves Claude v1 into Superseded/!
        val f1_1 = File(demoDir, "2026-10-12_Gemini_Botanical_Handover_v1_1.md").apply { writeText(handoverV1_1) }
        val step2Files = mutableListOf<RawFolderFile>()
        collectLocalDirectoryFiles(demoDir, "", step2Files)
        ingestRawFilesIntoProjectReadOnly(
            projectId = project.projectId,
            folderDisplayName = "Project Compass/Botanical Field Journal",
            rawFiles = step2Files,
            context = context
        )

        // Step 3: ChatGPT v2 (omits CSV requirement -> Safety Brake!) and Duplicate file (auto-moved to Review_For_Deletion/) arrive
        File(demoDir, "2026-10-14_ChatGPT_Botanical_Handover_v2.md").apply { writeText(handoverV2) }
        File(demoDir, "2026-10-15_Gemini_Herbarium_Research.txt").apply { writeText(researchNotes) }
        File(demoDir, "2026-10-15_Gemini_Herbarium_Research_Copy.txt").apply { writeText(researchNotes) }
        val step3Files = mutableListOf<RawFolderFile>()
        collectLocalDirectoryFiles(demoDir, "", step3Files)
        ingestRawFilesIntoProjectReadOnly(
            projectId = project.projectId,
            folderDisplayName = "Project Compass/Botanical Field Journal",
            rawFiles = step3Files,
            context = context
        )

        return@withContext dao.getProjectById(project.projectId) ?: project
    }

    suspend fun moveDocumentToSubfolderManual(
        context: Context,
        docId: String,
        targetCategory: DocumentLifecycleCategory
    ): String = withContext(Dispatchers.IO) {
        val doc = dao.getDocumentById(docId) ?: return@withContext "Document not found"
        val project = doc.projectId?.let { dao.getProjectById(it) }
        if (targetCategory == DocumentLifecycleCategory.CURRENT) {
            val ops = dao.getAllJournalOperationsSnapshot()
            val latestOp = ops.firstOrNull { it.docId == docId && it.canUndo && it.state == "COMPLETED" }
            if (latestOp != null) {
                return@withContext SafeFileMover.undoSubfolderMove(context, dao, project?.linkedFolderUri, latestOp)
            }
            dao.upsertDocument(doc.copy(lifecycleCategory = DocumentLifecycleCategory.CURRENT.name))
            return@withContext "Marked '${doc.originalFilename}' as Current."
        }

        val subfolder = if (targetCategory == DocumentLifecycleCategory.SUPERSEDED) {
            SafeFileMover.SUBFOLDER_SUPERSEDED
        } else {
            SafeFileMover.SUBFOLDER_DELETION_REVIEW
        }
        val res = SafeFileMover.moveDocumentToSubfolder(
            context = context,
            dao = dao,
            projectFolderUri = project?.linkedFolderUri,
            document = doc,
            targetSubfolder = subfolder,
            targetCategory = targetCategory
        )
        res.message
    }

    suspend fun undoJournalMove(context: Context, opId: String): String = withContext(Dispatchers.IO) {
        val op = dao.getAllJournalOperationsSnapshot().firstOrNull { it.opId == opId }
            ?: return@withContext "Operation not found"
        val project = dao.getProjectById(op.projectId)
        SafeFileMover.undoSubfolderMove(context, dao, project?.linkedFolderUri, op)
    }

    suspend fun updateBrainItemTruthStatus(
        brainItemId: String,
        newStatus: TruthStatus,
        reason: String
    ) = withContext(Dispatchers.IO) {
        val existing = dao.getBrainItemById(brainItemId) ?: return@withContext
        val oldStatus = existing.truthStatus
        if (oldStatus == newStatus.name) return@withContext

        dao.upsertBrainItem(existing.copy(truthStatus = newStatus.name))
        dao.upsertChangeTrailEntry(
            ChangeTrailEntity(
                changeId = UUID.randomUUID().toString(),
                projectId = existing.projectId,
                workAreaId = existing.workAreaId,
                brainItemId = existing.brainItemId,
                changeType = ChangeType.STATUS_CHANGED.name,
                title = "Status Changed: $oldStatus -> ${newStatus.name}",
                previousWording = "[Status: $oldStatus] ${existing.content}",
                newWording = "[Status: ${newStatus.name}] ${existing.content}",
                documentedReason = reason.ifBlank { "Reviewed and updated by user in Project Compass." },
                sourceDocId = existing.sourceDocId,
                exactSourceFilename = existing.exactSourceFilename,
                sectionLineRef = existing.sectionLineReference,
                aiContributor = existing.aiContributor,
                reviewStatus = ReviewStatus.APPROVED.name
            )
        )
    }

    suspend fun addManualBrainItem(
        projectId: String,
        workAreaId: String?,
        category: BrainCategory,
        content: String,
        reasonOrContext: String,
        truthStatus: TruthStatus,
        aiContributor: String
    ) = withContext(Dispatchers.IO) {
        val itemId = UUID.randomUUID().toString()
        val item = BrainItemEntity(
            brainItemId = itemId,
            projectId = projectId,
            workAreaId = workAreaId,
            category = category.name,
            title = content.trim().take(64),
            content = content.trim(),
            reasonOrContext = reasonOrContext.trim(),
            truthStatus = truthStatus.name,
            aiContributor = aiContributor.ifBlank { "User-Authored" },
            exactSourceFilename = "Manual Entry in Compass",
            sectionLineReference = "Direct Entry"
        )
        dao.upsertBrainItem(item)
        dao.upsertChangeTrailEntry(
            ChangeTrailEntity(
                changeId = UUID.randomUUID().toString(),
                projectId = projectId,
                workAreaId = workAreaId,
                brainItemId = itemId,
                changeType = ChangeType.ADDED.name,
                title = "Added ${category.label}: ${item.title}",
                previousWording = "(None — New Entry)",
                newWording = content.trim(),
                documentedReason = reasonOrContext.ifBlank { "Added directly by user." },
                exactSourceFilename = "Manual Entry in Compass",
                sectionLineRef = "Direct Entry",
                aiContributor = item.aiContributor,
                reviewStatus = ReviewStatus.APPROVED.name
            )
        )
    }

    suspend fun resolveChangeTrailAlert(
        context: Context?,
        changeId: String,
        approveChange: Boolean,
        userNote: String
    ) = withContext(Dispatchers.IO) {
        val allChanges = dao.getAllChangeTrailSnapshot()
        val entry = allChanges.firstOrNull { it.changeId == changeId } ?: return@withContext
        val newReviewStatus = if (approveChange) ReviewStatus.APPROVED.name else ReviewStatus.REJECTED.name
        dao.upsertChangeTrailEntry(
            entry.copy(
                reviewStatus = newReviewStatus,
                documentedReason = "${entry.documentedReason} | User Review: $userNote"
            )
        )

        if (approveChange && entry.changeType == ChangeType.WORDING_CHANGED.name && entry.brainItemId != null) {
            val oldBrain = dao.getBrainItemById(entry.brainItemId)
            if (oldBrain != null) {
                val newBrainId = UUID.randomUUID().toString()
                dao.upsertBrainItem(
                    oldBrain.copy(
                        truthStatus = TruthStatus.SUPERSEDED.name,
                        supersededByItemId = newBrainId
                    )
                )
                dao.upsertBrainItem(
                    oldBrain.copy(
                        brainItemId = newBrainId,
                        title = entry.newWording.take(64),
                        content = entry.newWording,
                        truthStatus = TruthStatus.APPROVED.name,
                        exactSourceFilename = entry.exactSourceFilename,
                        sectionLineReference = entry.sectionLineRef,
                        recordedAt = System.currentTimeMillis()
                    )
                )
            }
        }

        // Once all pending Safety Brake alerts for a handover are resolved, automatically move its predecessor handover into Superseded/!
        if (context != null && entry.predecessorDocId != null) {
            val remainingPendingForSource = dao.getChangeTrailForProject(entry.projectId).any {
                it.sourceDocId == entry.sourceDocId && it.reviewStatus == ReviewStatus.PENDING_REVIEW.name
            }
            if (!remainingPendingForSource) {
                val predDoc = dao.getDocumentById(entry.predecessorDocId)
                val project = dao.getProjectById(entry.projectId)
                if (predDoc != null && predDoc.lifecycleCategory == DocumentLifecycleCategory.CURRENT.name) {
                    SafeFileMover.moveDocumentToSubfolder(
                        context = context,
                        dao = dao,
                        projectFolderUri = project?.linkedFolderUri,
                        document = predDoc,
                        targetSubfolder = SafeFileMover.SUBFOLDER_SUPERSEDED,
                        targetCategory = DocumentLifecycleCategory.SUPERSEDED
                    )
                }
            }
        }
    }

    suspend fun assignDocumentWorkAreaAndAi(
        docId: String,
        workAreaId: String?,
        aiSourceName: String,
        conversationUrl: String,
        lifecycleCategory: DocumentLifecycleCategory
    ) = withContext(Dispatchers.IO) {
        val doc = dao.getDocumentById(docId) ?: return@withContext
        dao.upsertDocument(
            doc.copy(
                workAreaId = workAreaId,
                aiSourceName = aiSourceName.trim().ifEmpty { "Not Stated" },
                conversationUrl = conversationUrl.trim(),
                lifecycleCategory = lifecycleCategory.name
            )
        )
    }

    suspend fun addIncomingItemToUnassignedInbox(
        titleOrFilename: String,
        textContent: String,
        aiSource: String
    ) = withContext(Dispatchers.IO) {
        val projects = dao.getAllProjectsSnapshot()
        val (suggestedProject, reason) = OfflineDocumentEngine.suggestProjectForContent(
            filename = titleOrFilename,
            content = textContent,
            projects = projects
        )
        val bytes = textContent.toByteArray(Charsets.UTF_8)
        val hash = OfflineDocumentEngine.computeSha256(bytes)
        val docId = UUID.randomUUID().toString()
        val snapId = UUID.randomUUID().toString()

        val doc = DocumentEntity(
            docId = docId,
            projectId = null,
            suggestedProjectId = suggestedProject?.projectId,
            suggestionReason = reason,
            originalFilename = titleOrFilename.trim().ifEmpty { "Inbox_Note_${System.currentTimeMillis()}.md" },
            currentRelativePath = "Unassigned_Inbox/$titleOrFilename",
            sha256Hash = hash,
            fileSizeBytes = bytes.size.toLong(),
            lastModifiedTimestamp = System.currentTimeMillis(),
            formatLabel = OfflineDocumentEngine.detectFormatLabel(titleOrFilename.ifEmpty { "note.md" }),
            isHandoverReport = textContent.lines().size >= 4,
            aiSourceName = aiSource.ifBlank { "Not Stated" }
        )
        dao.upsertDocument(doc)
        dao.upsertSnapshot(
            DocumentSnapshotEntity(
                snapshotId = snapId,
                docId = docId,
                versionNumber = 1,
                sha256Hash = hash,
                extractedPlainText = textContent,
                lineCount = textContent.lines().size
            )
        )
    }

    suspend fun approveAssignInboxItemToProject(docId: String, targetProjectId: String) = withContext(Dispatchers.IO) {
        val doc = dao.getDocumentById(docId) ?: return@withContext
        val snapshots = dao.getSnapshotsForDocument(docId)
        val text = snapshots.firstOrNull()?.extractedPlainText ?: ""
        val snapId = snapshots.firstOrNull()?.snapshotId ?: UUID.randomUUID().toString()
        val workAreas = dao.getWorkAreasForProject(targetProjectId)
        val existingBrain = dao.getBrainItemsForProject(targetProjectId)

        val analysis = OfflineDocumentEngine.analyzeDocumentContent(
            projectId = targetProjectId,
            docId = docId,
            snapshotId = snapId,
            filename = doc.originalFilename,
            plainText = text,
            workAreas = workAreas,
            existingApprovedBrain = existingBrain
        )
        dao.upsertDocument(
            doc.copy(
                projectId = targetProjectId,
                workAreaId = analysis.proposedBrainItems.firstOrNull()?.workAreaId ?: workAreas.firstOrNull()?.workAreaId
            )
        )
        if (analysis.proposedBrainItems.isNotEmpty()) {
            dao.upsertBrainItems(analysis.proposedBrainItems)
            dao.upsertEvidenceLinks(analysis.evidenceLinks)
        }
        if (analysis.changeTrailAlerts.isNotEmpty()) {
            dao.upsertChangeTrailEntries(analysis.changeTrailAlerts)
        }
    }

    /**
     * Stage 4 One-Tap Save Handover .md to Project Folder:
     * Writes a clean, dated `YYYY-MM-DD_Compass_Handover.md` file directly into the project's folder,
     * never overwriting existing files, and runs the folder scanner so any clean predecessor handover
     * is automatically moved into `Superseded/`.
     */
    suspend fun saveHandoverMarkdownToProjectFolder(
        context: Context,
        projectId: String,
        markdownContent: String
    ): String = withContext(Dispatchers.IO) {
        var project = dao.getProjectById(projectId) ?: return@withContext "Project not found"
        val dateStamp = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
        val baseFilename = "${dateStamp}_Compass_Master_Handover.md"

        var uriStr = project.linkedFolderUri
        if (uriStr.isNullOrBlank()) {
            val compassRoot = File(context.filesDir, "Project_Compass").apply { mkdirs() }
            val projDir = File(compassRoot, project.name.replace(Regex("[^a-zA-Z0-9_-]"), "_")).apply { mkdirs() }
            uriStr = "compass://demo-folder/${projDir.absolutePath}"
            project = project.copy(
                linkedFolderUri = uriStr,
                linkedFolderDisplayName = "Project Compass/${project.name}",
                permissionState = FolderPermissionState.CONNECTED.name
            )
            dao.upsertProject(project)
        }

        if (uriStr.startsWith("compass://demo-folder/")) {
            val dir = File(uriStr.removePrefix("compass://demo-folder/")).apply { mkdirs() }
            var outFile = File(dir, baseFilename)
            if (outFile.exists()) {
                val timeSuffix = java.text.SimpleDateFormat("HHmmss", java.util.Locale.US).format(java.util.Date())
                outFile = File(dir, "${dateStamp}_${timeSuffix}_Compass_Master_Handover.md")
            }
            outFile.writeText("Source AI: Project Compass\n$markdownContent")
            scanConnectedSafFolderReadOnly(context, projectId)
            return@withContext "Saved '${outFile.name}' into ${project.linkedFolderDisplayName} and updated Superseded/ handovers."
        }

        return@withContext "Saved '$baseFilename' to Project Brain."
    }

    suspend fun generateBackupJson(includePhysicalFiles: Boolean): String = withContext(Dispatchers.IO) {
        HandoverAndBackupEngine.createBackupPayload(
            includePhysicalFiles = includePhysicalFiles,
            projects = dao.getAllProjectsSnapshot(),
            workAreas = dao.getAllWorkAreasSnapshot(),
            documents = dao.getAllDocumentsSnapshot(),
            snapshots = dao.getAllSnapshotsSnapshot(),
            brainItems = dao.getAllBrainItemsSnapshot(),
            changeTrail = dao.getAllChangeTrailSnapshot()
        )
    }

    suspend fun executeApprovedRestore(preview: BackupRestorePreview): String = withContext(Dispatchers.IO) {
        if (!preview.isValidChecksum) {
            return@withContext "Restore aborted: Backup SHA-256 checksum failed verification."
        }
        val envelope = JSONObject(preview.rawBackupJson)
        val dataObj = JSONObject(envelope.getString("payload"))

        val projectsArr = dataObj.optJSONArray("projects")
        if (projectsArr != null) {
            for (i in 0 until projectsArr.length()) {
                val o = projectsArr.getJSONObject(i)
                val pId = o.getString("projectId")
                val existing = dao.getProjectById(pId)
                val permState = existing?.permissionState ?: if (o.optString("linkedFolderUri").isNotBlank()) {
                    FolderPermissionState.PERMISSION_NEEDED.name
                } else {
                    FolderPermissionState.UNLINKED.name
                }
                dao.upsertProject(
                    ProjectEntity(
                        projectId = pId,
                        name = o.getString("name"),
                        purpose = o.getString("purpose"),
                        projectType = o.optString("projectType", ProjectType.SOFTWARE.name),
                        status = o.optString("status", ProjectStatus.ACTIVE.name),
                        resumePointSummary = o.optString("resumePointSummary", ""),
                        linkedFolderUri = o.optString("linkedFolderUri", "").ifBlank { null },
                        linkedFolderDisplayName = o.optString("linkedFolderDisplayName", "").ifBlank { null },
                        permissionState = permState,
                        externalLinksJson = o.optString("externalLinksJson", ""),
                        createdAt = o.optLong("createdAt", System.currentTimeMillis())
                    )
                )
            }
        }

        val areasArr = dataObj.optJSONArray("workAreas")
        if (areasArr != null) {
            for (i in 0 until areasArr.length()) {
                val o = areasArr.getJSONObject(i)
                dao.upsertWorkArea(
                    WorkAreaEntity(
                        workAreaId = o.getString("workAreaId"),
                        projectId = o.getString("projectId"),
                        name = o.getString("name"),
                        description = o.optString("description", ""),
                        displayOrder = o.optInt("displayOrder", i)
                    )
                )
            }
        }

        val docsArr = dataObj.optJSONArray("documents")
        if (docsArr != null) {
            for (i in 0 until docsArr.length()) {
                val o = docsArr.getJSONObject(i)
                dao.upsertDocument(
                    DocumentEntity(
                        docId = o.getString("docId"),
                        projectId = o.optString("projectId", "").ifBlank { null },
                        originalFilename = o.getString("originalFilename"),
                        currentRelativePath = o.getString("currentRelativePath"),
                        sha256Hash = o.getString("sha256Hash"),
                        fileSizeBytes = o.optLong("fileSizeBytes", 0L),
                        lastModifiedTimestamp = o.optLong("lastModifiedTimestamp", 0L),
                        formatLabel = o.optString("formatLabel", "Project File"),
                        lifecycleCategory = o.optString("lifecycleCategory", DocumentLifecycleCategory.CURRENT.name),
                        isHandoverReport = o.optBoolean("isHandoverReport", false),
                        predecessorDocId = o.optString("predecessorDocId", "").ifBlank { null },
                        aiSourceName = o.optString("aiSourceName", "Not Stated"),
                        conversationUrl = o.optString("conversationUrl", ""),
                        workAreaId = o.optString("workAreaId", "").ifBlank { null }
                    )
                )
            }
        }

        val snapsArr = dataObj.optJSONArray("snapshots")
        if (snapsArr != null) {
            for (i in 0 until snapsArr.length()) {
                val o = snapsArr.getJSONObject(i)
                dao.upsertSnapshot(
                    DocumentSnapshotEntity(
                        snapshotId = o.getString("snapshotId"),
                        docId = o.getString("docId"),
                        versionNumber = o.optInt("versionNumber", 1),
                        sha256Hash = o.getString("sha256Hash"),
                        recordedAt = o.optLong("recordedAt", System.currentTimeMillis()),
                        extractedPlainText = o.optString("extractedPlainText", ""),
                        lineCount = o.optInt("lineCount", 0)
                    )
                )
            }
        }

        val brainArr = dataObj.optJSONArray("brainItems")
        if (brainArr != null) {
            for (i in 0 until brainArr.length()) {
                val o = brainArr.getJSONObject(i)
                val itemId = o.getString("brainItemId")
                val existing = dao.getBrainItemById(itemId)
                if (existing != null && existing.truthStatus == TruthStatus.VERIFIED.name &&
                    o.optString("truthStatus") != TruthStatus.VERIFIED.name
                ) {
                    continue
                }
                dao.upsertBrainItem(
                    BrainItemEntity(
                        brainItemId = itemId,
                        projectId = o.getString("projectId"),
                        workAreaId = o.optString("workAreaId", "").ifBlank { null },
                        category = o.getString("category"),
                        title = o.getString("title"),
                        content = o.getString("content"),
                        reasonOrContext = o.optString("reasonOrContext", ""),
                        truthStatus = o.optString("truthStatus", TruthStatus.SOURCE_CLAIM.name),
                        aiContributor = o.optString("aiContributor", "Not Stated"),
                        sourceDocId = o.optString("sourceDocId", "").ifBlank { null },
                        exactSourceFilename = o.optString("exactSourceFilename", "Restored Backup"),
                        sectionLineReference = o.optString("sectionLineReference", ""),
                        exactQuote = o.optString("exactQuote", ""),
                        recordedAt = o.optLong("recordedAt", System.currentTimeMillis())
                    )
                )
            }
        }

        val changesArr = dataObj.optJSONArray("changeTrail")
        if (changesArr != null) {
            for (i in 0 until changesArr.length()) {
                val o = changesArr.getJSONObject(i)
                dao.upsertChangeTrailEntry(
                    ChangeTrailEntity(
                        changeId = o.getString("changeId"),
                        projectId = o.getString("projectId"),
                        workAreaId = o.optString("workAreaId", "").ifBlank { null },
                        brainItemId = o.optString("brainItemId", "").ifBlank { null },
                        changeType = o.getString("changeType"),
                        title = o.getString("title"),
                        previousWording = o.getString("previousWording"),
                        newWording = o.getString("newWording"),
                        documentedReason = o.getString("documentedReason"),
                        exactSourceFilename = o.getString("exactSourceFilename"),
                        sectionLineRef = o.optString("sectionLineRef", ""),
                        aiContributor = o.optString("aiContributor", "Not Stated"),
                        reviewStatus = o.optString("reviewStatus", ReviewStatus.PENDING_REVIEW.name),
                        recordedAt = o.optLong("recordedAt", System.currentTimeMillis())
                    )
                )
            }
        }

        return@withContext "Restore completed: ${preview.projectsCount} projects, ${preview.brainItemsCount} Project Brain items, and ${preview.changeTrailCount} Change Trail records reconciled."
    }
}
