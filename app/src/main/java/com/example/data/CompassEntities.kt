package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class ProjectType(val label: String) {
    SOFTWARE("Software & App"),
    CREATIVE("Creative & Writing"),
    PERSONAL("Personal & Life")
}

enum class ProjectStatus(val label: String) {
    ACTIVE("Active"),
    PAUSED("On Hold"),
    COMPLETED("Completed"),
    ARCHIVED("Archived")
}

enum class FolderPermissionState(val label: String) {
    UNLINKED("No Folder Linked"),
    CONNECTED("Folder Connected (Read-Only Stage 1)"),
    PERMISSION_NEEDED("Permission Lapsed — Knowledge Preserved")
}

enum class DocumentLifecycleCategory(val label: String) {
    CURRENT("Current"),
    SUPERSEDED("Superseded"),
    REVIEW_FOR_DELETION("Review for Deletion"),
    UNCERTAIN("Uncertain / Pending Review")
}

enum class BrainCategory(val label: String, val plainDescription: String) {
    PURPOSE("Project Purpose", "Why this project exists and what it aims to achieve"),
    REQUIREMENT("Requirements", "Agreed rules and features the project must keep"),
    DECISION("Decisions & Reasons", "Choices made and the reasoning behind them"),
    COMPLETED_WORK("Completed & Verified", "Work proven with evidence versus unverified AI claims"),
    TASK("Outstanding Tasks", "To-do items and work still remaining"),
    PROBLEM_ATTEMPT("Problems & Attempts", "Known roadblocks and what has been tried so far"),
    RESOURCE("Resources & Links", "Reference tools, conversations, and assets"),
    NEXT_STEP("Next Steps", "Immediate actions to resume progress")
}

enum class TruthStatus(val label: String, val plainExplanation: String) {
    SOURCE_CLAIM("Unverified Source Claim", "Found in a document or AI handover; not automatically true until verified"),
    PROPOSAL("Proposal to Review", "Suggested requirement, decision, or update awaiting your approval"),
    APPROVED("Approved Decision", "Explicitly approved by you as part of the Project Brain"),
    VERIFIED("Verified Result", "Tested and confirmed working with evidence"),
    UNCERTAIN("Uncertain / Conflicting", "Flagged as ambiguous or conflicting; needs clarification"),
    SUPERSEDED("Superseded History", "Replaced by a newer approved decision; kept for historical audit")
}

enum class ChangeType(val label: String) {
    ADDED("New Item Proposed"),
    WORDING_CHANGED("Wording Updated"),
    STATUS_CHANGED("Truth Status Changed"),
    OMITTED_IN_HANDOVER("Omitted in Newer Handover (Retained)"),
    CONTRADICTION_DETECTED("Contradicts Approved Knowledge"),
    DECISION_REVERSED("Previous Decision Reversed"),
    FILE_RETIRES_PREDECESSOR("Handover Succession Recorded"),
    EDITED_IN_PLACE("Document Edited in Place")
}

enum class ReviewStatus(val label: String) {
    PENDING_REVIEW("Needs Review"),
    APPROVED("Approved"),
    REJECTED("Rejected / Kept Original")
}

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey val projectId: String,
    val name: String,
    val purpose: String,
    val projectType: String = ProjectType.SOFTWARE.name,
    val status: String = ProjectStatus.ACTIVE.name,
    val resumePointSummary: String = "",
    val linkedFolderUri: String? = null,
    val linkedFolderDisplayName: String? = null,
    val permissionState: String = FolderPermissionState.UNLINKED.name,
    val externalLinksJson: String = "[]", // Format: "Title|URL\nTitle2|URL2"
    val createdAt: Long = System.currentTimeMillis(),
    val lastScannedAt: Long? = null
)

@Entity(tableName = "work_areas")
data class WorkAreaEntity(
    @PrimaryKey val workAreaId: String,
    val projectId: String,
    val name: String,
    val description: String,
    val displayOrder: Int = 0
)

@Entity(tableName = "documents")
data class DocumentEntity(
    @PrimaryKey val docId: String, // Permanent Compass UUID independent of path or filename
    val projectId: String?, // Null when in Unassigned Inbox
    val suggestedProjectId: String? = null,
    val suggestionReason: String? = null,
    val originalFilename: String,
    val currentRelativePath: String,
    val currentSafUri: String = "",
    val previousRelativePathsJson: String = "",
    val sha256Hash: String,
    val fileSizeBytes: Long,
    val lastModifiedTimestamp: Long,
    val formatLabel: String, // e.g., "Markdown (.md)", "Word (.docx)", "HTML (.html)"
    val lifecycleCategory: String = DocumentLifecycleCategory.CURRENT.name,
    val isHandoverReport: Boolean = false,
    val predecessorDocId: String? = null,
    val aiSourceName: String = "Not Stated", // Never invent attribution
    val conversationUrl: String = "",
    val workAreaId: String? = null,
    val missingOnDisk: Boolean = false,
    val editedInPlaceFlag: Boolean = false,
    val recordedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "document_snapshots")
data class DocumentSnapshotEntity(
    @PrimaryKey val snapshotId: String,
    val docId: String,
    val versionNumber: Int,
    val sha256Hash: String,
    val recordedAt: Long = System.currentTimeMillis(),
    val extractedPlainText: String,
    val lineCount: Int
)

@Entity(tableName = "brain_items")
data class BrainItemEntity(
    @PrimaryKey val brainItemId: String,
    val projectId: String,
    val workAreaId: String? = null,
    val category: String, // BrainCategory.name
    val title: String,
    val content: String,
    val reasonOrContext: String = "",
    val truthStatus: String = TruthStatus.SOURCE_CLAIM.name,
    val aiContributor: String = "Not Stated",
    val sourceDocId: String? = null,
    val exactSourceFilename: String = "Manual Entry",
    val sectionLineReference: String = "",
    val exactQuote: String = "",
    val supersededByItemId: String? = null,
    val recordedAt: Long = System.currentTimeMillis(),
    val documentedEventDate: String = ""
)

@Entity(tableName = "evidence_links")
data class EvidenceLinkEntity(
    @PrimaryKey val evidenceId: String,
    val brainItemId: String,
    val docId: String,
    val snapshotId: String,
    val exactFilenameAtTime: String,
    val sectionHeading: String,
    val lineRange: String,
    val exactQuote: String
)

@Entity(tableName = "change_trail_entries")
data class ChangeTrailEntity(
    @PrimaryKey val changeId: String,
    val projectId: String,
    val workAreaId: String? = null,
    val brainItemId: String? = null,
    val changeType: String, // ChangeType.name
    val title: String,
    val previousWording: String,
    val newWording: String,
    val documentedReason: String,
    val sourceDocId: String? = null,
    val predecessorDocId: String? = null,
    val exactSourceFilename: String,
    val sectionLineRef: String,
    val aiContributor: String = "Not Stated",
    val reviewStatus: String = ReviewStatus.PENDING_REVIEW.name,
    val recordedAt: Long = System.currentTimeMillis(),
    val documentedEventDate: String = ""
)

@Entity(tableName = "file_operation_journal")
data class FileOperationJournalEntity(
    @PrimaryKey val opId: String,
    val projectId: String,
    val docId: String,
    val operationType: String, // MOVE_TO_SUPERSEDED, MOVE_TO_DELETION_REVIEW, UNDO_MOVE
    val originalFilename: String,
    val sourceRelativePath: String,
    val targetRelativePath: String,
    val expectedSha256: String,
    val state: String, // INTENT_LOGGED, COMPLETED, ROLLED_BACK, FAILED
    val canUndo: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)
