package com.example.domain

import com.example.data.BrainCategory
import com.example.data.BrainItemEntity
import com.example.data.ChangeTrailEntity
import com.example.data.DocumentEntity
import com.example.data.DocumentSnapshotEntity
import com.example.data.ProjectEntity
import com.example.data.TruthStatus
import com.example.data.WorkAreaEntity
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class BackupRestorePreview(
    val isValidChecksum: Boolean,
    val backupCreatedAt: String,
    val includesPhysicalFiles: Boolean,
    val projectsCount: Int,
    val workAreasCount: Int,
    val documentsCount: Int,
    val snapshotsCount: Int,
    val brainItemsCount: Int,
    val changeTrailCount: Int,
    val summaryMessage: String,
    val rawBackupJson: String
)

object HandoverAndBackupEngine {

    private fun formatTimestamp(millis: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
        return sdf.format(Date(millis))
    }

    /**
     * Generates a clean, portable Markdown handover (§18) for an entire project or a specific Work Area.
     * Strictly separates Approved/Verified knowledge from Unverified Source Claims and Open Proposals.
     */
    fun generateMarkdownHandover(
        project: ProjectEntity,
        workAreas: List<WorkAreaEntity>,
        selectedWorkAreaId: String?,
        documents: List<DocumentEntity>,
        brainItems: List<BrainItemEntity>,
        changeTrail: List<ChangeTrailEntity>
    ): String {
        val selectedArea = workAreas.firstOrNull { it.workAreaId == selectedWorkAreaId }
        val scopeLabel = selectedArea?.let { "Work Area: ${it.name}" } ?: "Complete Project Handover"
        val filteredBrain = if (selectedWorkAreaId != null) {
            brainItems.filter { it.workAreaId == selectedWorkAreaId || it.category == BrainCategory.PURPOSE.name }
        } else {
            brainItems
        }
        val filteredDocs = if (selectedWorkAreaId != null) {
            documents.filter { it.workAreaId == selectedWorkAreaId }
        } else {
            documents
        }
        val filteredChanges = if (selectedWorkAreaId != null) {
            changeTrail.filter { it.workAreaId == selectedWorkAreaId }
        } else {
            changeTrail
        }

        val sb = StringBuilder()
        sb.appendLine("# PROJECT COMPASS HANDOVER: ${project.name.uppercase()}")
        sb.appendLine("**Scope:** $scopeLabel")
        sb.appendLine("**Project Status:** ${project.status} | **Generated:** ${formatTimestamp(System.currentTimeMillis())}")
        sb.appendLine("**Folder Connection:** ${project.linkedFolderDisplayName ?: "Unlinked (Local Brain Only)"} (${project.permissionState})")
        sb.appendLine()

        sb.appendLine("## 1. Project Purpose & Resume Point")
        sb.appendLine("- **Purpose:** ${project.purpose}")
        sb.appendLine("- **Where to Resume:** ${project.resumePointSummary.ifBlank { "Review pending proposals and next steps below." }}")
        sb.appendLine()

        // Approved Requirements
        val approvedReqs = filteredBrain.filter {
            it.category == BrainCategory.REQUIREMENT.name &&
                (it.truthStatus == TruthStatus.APPROVED.name || it.truthStatus == TruthStatus.VERIFIED.name)
        }
        sb.appendLine("## 2. Approved Requirements (${approvedReqs.size})")
        if (approvedReqs.isEmpty()) {
            sb.appendLine("_No approved requirements recorded for this scope yet._")
        } else {
            approvedReqs.forEach { item ->
                sb.appendLine("- **[${item.truthStatus}]** ${item.content}")
                sb.appendLine("  - _Source Evidence:_ `${item.exactSourceFilename}` (${item.sectionLineReference.ifBlank { "Manual" }}) | _AI:_ ${item.aiContributor}")
            }
        }
        sb.appendLine()

        // Approved Decisions & Reasons
        val decisions = filteredBrain.filter {
            it.category == BrainCategory.DECISION.name &&
                (it.truthStatus == TruthStatus.APPROVED.name || it.truthStatus == TruthStatus.VERIFIED.name)
        }
        sb.appendLine("## 3. Approved Decisions & Reasons (${decisions.size})")
        if (decisions.isEmpty()) {
            sb.appendLine("_No approved decisions recorded yet._")
        } else {
            decisions.forEach { item ->
                sb.appendLine("- **[APPROVED DECISION]** ${item.content}")
                if (item.reasonOrContext.isNotBlank()) {
                    sb.appendLine("  - _Reason / Context:_ ${item.reasonOrContext}")
                }
                sb.appendLine("  - _Source Evidence:_ `${item.exactSourceFilename}` (${item.sectionLineReference.ifBlank { "Manual" }})")
            }
        }
        sb.appendLine()

        // Verified Progress vs Unverified Source Claims (§18: Never present unsupported claims as verified!)
        val verifiedWork = filteredBrain.filter {
            it.category == BrainCategory.COMPLETED_WORK.name && it.truthStatus == TruthStatus.VERIFIED.name
        }
        val unverifiedWorkClaims = filteredBrain.filter {
            it.category == BrainCategory.COMPLETED_WORK.name && it.truthStatus != TruthStatus.VERIFIED.name
        }
        sb.appendLine("## 4. Verified Progress vs. Unverified Claims")
        sb.appendLine("### 4A. Proven & Verified Work (${verifiedWork.size})")
        if (verifiedWork.isEmpty()) {
            sb.appendLine("_No work items have been marked Verified with evidence yet._")
        } else {
            verifiedWork.forEach { item ->
                sb.appendLine("- **[VERIFIED]** ${item.content} (_Source:_ `${item.exactSourceFilename}`, ${item.sectionLineReference})")
            }
        }
        sb.appendLine("### 4B. Unverified Source Claims — Do Not Assume Complete (${unverifiedWorkClaims.size})")
        if (unverifiedWorkClaims.isEmpty()) {
            sb.appendLine("_No unverified completion claims._")
        } else {
            unverifiedWorkClaims.forEach { item ->
                sb.appendLine("- **[UNVERIFIED CLAIM — ${item.truthStatus}]** ${item.content} (_Claimed by:_ ${item.aiContributor} in `${item.exactSourceFilename}`)")
            }
        }
        sb.appendLine()

        // Outstanding Tasks & Next Steps
        val tasksAndSteps = filteredBrain.filter {
            (it.category == BrainCategory.TASK.name || it.category == BrainCategory.NEXT_STEP.name) &&
                it.truthStatus != TruthStatus.SUPERSEDED.name
        }
        sb.appendLine("## 5. Outstanding Tasks & Next Steps (${tasksAndSteps.size})")
        if (tasksAndSteps.isEmpty()) {
            sb.appendLine("_No open tasks recorded._")
        } else {
            tasksAndSteps.forEach { item ->
                sb.appendLine("- [ ] **[${item.category}]** ${item.content} (_Status:_ ${item.truthStatus}, _Source:_ `${item.exactSourceFilename}`)")
            }
        }
        sb.appendLine()

        // Problems, Attempts & Open Proposals / Uncertainty
        val problemsAndUncertain = filteredBrain.filter {
            it.category == BrainCategory.PROBLEM_ATTEMPT.name ||
                it.truthStatus == TruthStatus.UNCERTAIN.name ||
                it.truthStatus == TruthStatus.PROPOSAL.name
        }
        sb.appendLine("## 6. Problems, Open Proposals & Uncertainty (${problemsAndUncertain.size})")
        if (problemsAndUncertain.isEmpty()) {
            sb.appendLine("_No open problems or unreviewed proposals._")
        } else {
            problemsAndUncertain.forEach { item ->
                sb.appendLine("- **[${item.truthStatus}]** ${item.content} (`${item.exactSourceFilename}`)")
            }
        }
        sb.appendLine()

        // Document Inventory & Fingerprints
        sb.appendLine("## 7. Linked Source Documents & Fingerprints (${filteredDocs.size})")
        filteredDocs.forEach { doc ->
            sb.appendLine("- `${doc.originalFilename}` | **Status:** ${doc.lifecycleCategory} | **AI:** ${doc.aiSourceName} | **SHA-256:** `${doc.sha256Hash.take(12)}…` | **Path:** `${doc.currentRelativePath}`")
        }
        sb.appendLine()

        // Recent Change Trail
        sb.appendLine("## 8. Recent Change Trail (${filteredChanges.take(10).size})")
        filteredChanges.take(10).forEach { ch ->
            sb.appendLine("- **[${ch.changeType} — ${ch.reviewStatus}]** ${ch.title}")
            sb.appendLine("  - _Previous:_ ${ch.previousWording}")
            sb.appendLine("  - _New:_ ${ch.newWording}")
            sb.appendLine("  - _Source:_ `${ch.exactSourceFilename}` (${ch.sectionLineRef}) | _Reason:_ ${ch.documentedReason}")
        }

        return sb.toString()
    }

    /**
     * Creates a verified Backup JSON payload with a cryptographic SHA-256 integrity checksum (§17).
     * Clearly records whether physical document file contents are bundled or metadata-only.
     */
    fun createBackupPayload(
        includePhysicalFiles: Boolean,
        projects: List<ProjectEntity>,
        workAreas: List<WorkAreaEntity>,
        documents: List<DocumentEntity>,
        snapshots: List<DocumentSnapshotEntity>,
        brainItems: List<BrainItemEntity>,
        changeTrail: List<ChangeTrailEntity>
    ): String {
        val dataObj = JSONObject()
        dataObj.put("includesPhysicalFiles", includePhysicalFiles)
        dataObj.put("backupModeExplanation", if (includePhysicalFiles) {
            "Full Archive Bundle: Includes Project Brain, Change Trail, comparison snapshots, and physical document text."
        } else {
            "Knowledge & Trail Backup (Metadata Only): Preserves all Projects, Project Brain, Change Trail, Document IDs, and comparison snapshots without duplicating physical folder files."
        })

        val projectsArr = JSONArray()
        projects.forEach { p ->
            projectsArr.put(JSONObject().apply {
                put("projectId", p.projectId)
                put("name", p.name)
                put("purpose", p.purpose)
                put("projectType", p.projectType)
                put("status", p.status)
                put("resumePointSummary", p.resumePointSummary)
                put("linkedFolderUri", p.linkedFolderUri ?: "")
                put("linkedFolderDisplayName", p.linkedFolderDisplayName ?: "")
                put("permissionState", p.permissionState)
                put("externalLinksJson", p.externalLinksJson)
                put("createdAt", p.createdAt)
            })
        }
        dataObj.put("projects", projectsArr)

        val areasArr = JSONArray()
        workAreas.forEach { w ->
            areasArr.put(JSONObject().apply {
                put("workAreaId", w.workAreaId)
                put("projectId", w.projectId)
                put("name", w.name)
                put("description", w.description)
                put("displayOrder", w.displayOrder)
            })
        }
        dataObj.put("workAreas", areasArr)

        val docsArr = JSONArray()
        documents.forEach { d ->
            docsArr.put(JSONObject().apply {
                put("docId", d.docId)
                put("projectId", d.projectId ?: "")
                put("originalFilename", d.originalFilename)
                put("currentRelativePath", d.currentRelativePath)
                put("sha256Hash", d.sha256Hash)
                put("fileSizeBytes", d.fileSizeBytes)
                put("lastModifiedTimestamp", d.lastModifiedTimestamp)
                put("formatLabel", d.formatLabel)
                put("lifecycleCategory", d.lifecycleCategory)
                put("isHandoverReport", d.isHandoverReport)
                put("predecessorDocId", d.predecessorDocId ?: "")
                put("aiSourceName", d.aiSourceName)
                put("conversationUrl", d.conversationUrl)
                put("workAreaId", d.workAreaId ?: "")
            })
        }
        dataObj.put("documents", docsArr)

        val snapsArr = JSONArray()
        snapshots.forEach { s ->
            snapsArr.put(JSONObject().apply {
                put("snapshotId", s.snapshotId)
                put("docId", s.docId)
                put("versionNumber", s.versionNumber)
                put("sha256Hash", s.sha256Hash)
                put("recordedAt", s.recordedAt)
                put("extractedPlainText", s.extractedPlainText)
                put("lineCount", s.lineCount)
            })
        }
        dataObj.put("snapshots", snapsArr)

        val brainArr = JSONArray()
        brainItems.forEach { b ->
            brainArr.put(JSONObject().apply {
                put("brainItemId", b.brainItemId)
                put("projectId", b.projectId)
                put("workAreaId", b.workAreaId ?: "")
                put("category", b.category)
                put("title", b.title)
                put("content", b.content)
                put("reasonOrContext", b.reasonOrContext)
                put("truthStatus", b.truthStatus)
                put("aiContributor", b.aiContributor)
                put("sourceDocId", b.sourceDocId ?: "")
                put("exactSourceFilename", b.exactSourceFilename)
                put("sectionLineReference", b.sectionLineReference)
                put("exactQuote", b.exactQuote)
                put("recordedAt", b.recordedAt)
            })
        }
        dataObj.put("brainItems", brainArr)

        val changesArr = JSONArray()
        changeTrail.forEach { c ->
            changesArr.put(JSONObject().apply {
                put("changeId", c.changeId)
                put("projectId", c.projectId)
                put("workAreaId", c.workAreaId ?: "")
                put("brainItemId", c.brainItemId ?: "")
                put("changeType", c.changeType)
                put("title", c.title)
                put("previousWording", c.previousWording)
                put("newWording", c.newWording)
                put("documentedReason", c.documentedReason)
                put("exactSourceFilename", c.exactSourceFilename)
                put("sectionLineRef", c.sectionLineRef)
                put("aiContributor", c.aiContributor)
                put("reviewStatus", c.reviewStatus)
                put("recordedAt", c.recordedAt)
            })
        }
        dataObj.put("changeTrail", changesArr)

        val serializedData = dataObj.toString()
        val checksum = OfflineDocumentEngine.computeSha256(serializedData.toByteArray(Charsets.UTF_8))

        val envelope = JSONObject()
        envelope.put("format", "PROJECT_COMPASS_BACKUP_V1")
        envelope.put("createdAt", formatTimestamp(System.currentTimeMillis()))
        envelope.put("sha256Checksum", checksum)
        envelope.put("payload", serializedData)
        return envelope.toString(2)
    }

    /**
     * Verifies backup integrity checksum and generates a Restore Impact Preview (§17)
     * before any database records are modified.
     */
    fun inspectAndPreviewBackup(rawBackupJson: String): BackupRestorePreview {
        return try {
            val envelope = JSONObject(rawBackupJson)
            val expectedChecksum = envelope.optString("sha256Checksum", "")
            val createdAt = envelope.optString("createdAt", "Unknown Date")
            val payloadStr = envelope.getString("payload")
            val actualChecksum = OfflineDocumentEngine.computeSha256(payloadStr.toByteArray(Charsets.UTF_8))
            val isValid = expectedChecksum.isNotEmpty() && expectedChecksum == actualChecksum

            val dataObj = JSONObject(payloadStr)
            val includesFiles = dataObj.optBoolean("includesPhysicalFiles", false)
            val pCount = dataObj.optJSONArray("projects")?.length() ?: 0
            val wCount = dataObj.optJSONArray("workAreas")?.length() ?: 0
            val dCount = dataObj.optJSONArray("documents")?.length() ?: 0
            val sCount = dataObj.optJSONArray("snapshots")?.length() ?: 0
            val bCount = dataObj.optJSONArray("brainItems")?.length() ?: 0
            val cCount = dataObj.optJSONArray("changeTrail")?.length() ?: 0

            val modeDesc = if (includesFiles) "Full Archive Bundle (includes physical files)" else "Metadata & Comparison Snapshot Backup (physical files stay in folders)"
            BackupRestorePreview(
                isValidChecksum = isValid,
                backupCreatedAt = createdAt,
                includesPhysicalFiles = includesFiles,
                projectsCount = pCount,
                workAreasCount = wCount,
                documentsCount = dCount,
                snapshotsCount = sCount,
                brainItemsCount = bCount,
                changeTrailCount = cCount,
                summaryMessage = if (isValid) {
                    "Integrity Verified (SHA-256 match). Mode: $modeDesc. Ready to restore $pCount projects, $bCount Brain records, $dCount document IDs, and $cCount Change Trail entries without overwriting unapproved data."
                } else {
                    "Warning: Backup SHA-256 checksum mismatch! This backup file may have been altered or corrupted."
                },
                rawBackupJson = rawBackupJson
            )
        } catch (e: Exception) {
            BackupRestorePreview(
                isValidChecksum = false,
                backupCreatedAt = "-",
                includesPhysicalFiles = false,
                projectsCount = 0,
                workAreasCount = 0,
                documentsCount = 0,
                snapshotsCount = 0,
                brainItemsCount = 0,
                changeTrailCount = 0,
                summaryMessage = "Invalid backup format: ${e.message}",
                rawBackupJson = rawBackupJson
            )
        }
    }

    /**
     * Stage 4 Quick Resume Paste Prompt:
     * Creates a concise, ready-to-paste brief for starting a new chat in Claude, Gemini, or ChatGPT.
     */
    fun generateQuickResumePrompt(
        project: ProjectEntity,
        brainItems: List<BrainItemEntity>,
        changeTrail: List<ChangeTrailEntity>
    ): String {
        val approvedRules = brainItems.filter {
            (it.category == BrainCategory.REQUIREMENT.name || it.category == BrainCategory.DECISION.name) &&
                (it.truthStatus == TruthStatus.APPROVED.name || it.truthStatus == TruthStatus.VERIFIED.name)
        }
        val verifiedDone = brainItems.filter {
            it.category == BrainCategory.COMPLETED_WORK.name && it.truthStatus == TruthStatus.VERIFIED.name
        }
        val unverifiedClaims = brainItems.filter {
            it.category == BrainCategory.COMPLETED_WORK.name && it.truthStatus != TruthStatus.VERIFIED.name
        }
        val nextSteps = brainItems.filter {
            (it.category == BrainCategory.TASK.name || it.category == BrainCategory.NEXT_STEP.name) &&
                it.truthStatus != TruthStatus.SUPERSEDED.name
        }

        return buildString {
            appendLine("# QUICK RESUME BRIEF — ${project.name}")
            appendLine("Purpose: ${project.purpose}")
            appendLine()
            appendLine("## Non-Negotiable Approved Rules (Do Not Omit or Change)")
            if (approvedRules.isEmpty()) {
                appendLine("- None locked yet.")
            } else {
                approvedRules.forEach { appendLine("- ${it.content}") }
            }
            appendLine()
            appendLine("## Verified Completed Work (Already Proven)")
            if (verifiedDone.isEmpty()) {
                appendLine("- None marked Verified yet.")
            } else {
                verifiedDone.forEach { appendLine("- [VERIFIED] ${it.content}") }
            }
            if (unverifiedClaims.isNotEmpty()) {
                appendLine()
                appendLine("## Unverified Claims from Prior AI (Needs Verification)")
                unverifiedClaims.forEach { appendLine("- [UNVERIFIED CLAIM by ${it.aiContributor}] ${it.content}") }
            }
            appendLine()
            appendLine("## Immediate Next Steps")
            if (nextSteps.isEmpty()) {
                appendLine("- ${project.resumePointSummary.ifBlank { "Continue next milestone." }}")
            } else {
                nextSteps.forEach { appendLine("- [ ] ${it.content}") }
            }
        }.trim()
    }

    /**
     * Stage 4 Side-by-Side Multi-AI Comparison Builder:
     * Summarizes each AI assistant's handovers, approved contributions, unverified claims, and flagged omissions.
     */
    fun buildMultiAiContributionsSummary(
        documents: List<DocumentEntity>,
        brainItems: List<BrainItemEntity>,
        changeTrail: List<ChangeTrailEntity>
    ): List<AiContributorSummary> {
        val allAiNames = (
            documents.map { it.aiSourceName } +
                brainItems.map { it.aiContributor } +
                changeTrail.map { it.aiContributor }
            )
            .map { it.trim() }
            .filter { it.isNotBlank() && it != "Not Stated" }
            .distinct()

        return allAiNames.map { aiName ->
            val aiDocs = documents.filter { it.aiSourceName.equals(aiName, ignoreCase = true) }
            val aiBrain = brainItems.filter { it.aiContributor.equals(aiName, ignoreCase = true) }
            val aiAlerts = changeTrail.filter { it.aiContributor.equals(aiName, ignoreCase = true) }
            AiContributorSummary(
                aiName = aiName,
                filesCount = aiDocs.size,
                filenames = aiDocs.map { "${it.originalFilename} (${it.lifecycleCategory})" },
                approvedCount = aiBrain.count {
                    it.truthStatus == TruthStatus.APPROVED.name || it.truthStatus == TruthStatus.VERIFIED.name
                },
                unverifiedOrProposalCount = aiBrain.count {
                    it.truthStatus == TruthStatus.PROPOSAL.name || it.truthStatus == TruthStatus.SOURCE_CLAIM.name
                },
                safetyAlertsCount = aiAlerts.count { it.reviewStatus == "PENDING_REVIEW" },
                keyItems = aiBrain.take(6)
            )
        }
    }
}

data class AiContributorSummary(
    val aiName: String,
    val filesCount: Int,
    val filenames: List<String>,
    val approvedCount: Int,
    val unverifiedOrProposalCount: Int,
    val safetyAlertsCount: Int,
    val keyItems: List<BrainItemEntity>
)

