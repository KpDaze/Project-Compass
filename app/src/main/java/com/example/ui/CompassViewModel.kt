package com.example.ui

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.BrainCategory
import com.example.data.BrainItemEntity
import com.example.data.ChangeTrailEntity
import com.example.data.CompassRepository
import com.example.data.DocumentEntity
import com.example.data.DocumentLifecycleCategory
import com.example.data.DocumentSnapshotEntity
import com.example.data.FileOperationJournalEntity
import com.example.data.ProjectEntity
import com.example.data.ProjectStatus
import com.example.data.ProjectType
import com.example.data.TruthStatus
import com.example.data.WorkAreaEntity
import com.example.domain.BackupRestorePreview
import com.example.domain.HandoverAndBackupEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class TopLevelTab {
    MY_PROJECTS,
    UPDATES_REVIEW,
    UNASSIGNED_INBOX,
    SETTINGS_BACKUPS
}

enum class ProjectSectionTab(val label: String) {
    OVERVIEW("Overview"),
    BRAIN("Project Brain"),
    WORK_AREAS("Work Areas & AIs"),
    FILES("Files & Subfolders"),
    TASKS("Tasks & Next Steps"),
    CHANGE_TRAIL("Change Trail"),
    CLEANUP("Superseded & Undo")
}

data class CompassUiState(
    val projects: List<ProjectEntity> = emptyList(),
    val workAreas: List<WorkAreaEntity> = emptyList(),
    val documents: List<DocumentEntity> = emptyList(),
    val snapshots: List<DocumentSnapshotEntity> = emptyList(),
    val brainItems: List<BrainItemEntity> = emptyList(),
    val changeTrail: List<ChangeTrailEntity> = emptyList(),
    val journalOps: List<FileOperationJournalEntity> = emptyList(),
    val selectedTopTab: TopLevelTab = TopLevelTab.MY_PROJECTS,
    val selectedProjectId: String? = null,
    val selectedProjectSection: ProjectSectionTab = ProjectSectionTab.OVERVIEW,
    val selectedWorkAreaFilterId: String? = null,
    val statusBannerMessage: String? = null,
    val exportedMarkdownPreview: String? = null,
    val exportedMarkdownTitle: String = "",
    val latestBackupJson: String? = null,
    val restorePreview: BackupRestorePreview? = null
)

class CompassViewModel(private val repository: CompassRepository) : ViewModel() {

    private val _selectedTopTab = MutableStateFlow(TopLevelTab.MY_PROJECTS)
    private val _selectedProjectId = MutableStateFlow<String?>(null)
    private val _selectedProjectSection = MutableStateFlow(ProjectSectionTab.OVERVIEW)
    private val _selectedWorkAreaFilterId = MutableStateFlow<String?>(null)
    private val _statusBannerMessage = MutableStateFlow<String?>(null)
    private val _exportedMarkdownPreview = MutableStateFlow<String?>(null)
    private val _exportedMarkdownTitle = MutableStateFlow("")
    private val _latestBackupJson = MutableStateFlow<String?>(null)
    private val _restorePreview = MutableStateFlow<BackupRestorePreview?>(null)

    private val coreDataFlow = combine(
        repository.allProjects,
        repository.allWorkAreas,
        repository.allDocuments,
        repository.allSnapshots,
        repository.allBrainItems
    ) { projects, workAreas, docs, snapshots, brain ->
        CoreDataBundle(projects, workAreas, docs, snapshots, brain)
    }

    private val auditFlow = combine(
        repository.allChangeTrail,
        repository.allJournalOps
    ) { changes, ops ->
        changes to ops
    }

    private val navStateFlow = combine(
        _selectedTopTab,
        _selectedProjectId,
        _selectedProjectSection,
        _selectedWorkAreaFilterId,
        _statusBannerMessage
    ) { topTab, projId, projSec, areaId, banner ->
        NavStateBundle(topTab, projId, projSec, areaId, banner)
    }

    private val modalStateFlow = combine(
        _exportedMarkdownPreview,
        _exportedMarkdownTitle,
        _latestBackupJson,
        _restorePreview
    ) { mdPreview, mdTitle, backupJson, restorePrev ->
        ModalStateBundle(mdPreview, mdTitle, backupJson, restorePrev)
    }

    val uiState: StateFlow<CompassUiState> = combine(
        coreDataFlow,
        auditFlow,
        navStateFlow,
        modalStateFlow
    ) { core, (changes, ops), nav, modal ->
        CompassUiState(
            projects = core.projects,
            workAreas = core.workAreas,
            documents = core.docs,
            snapshots = core.snapshots,
            brainItems = core.brain,
            changeTrail = changes,
            journalOps = ops,
            selectedTopTab = nav.topTab,
            selectedProjectId = nav.projId,
            selectedProjectSection = nav.projSec,
            selectedWorkAreaFilterId = nav.areaId,
            statusBannerMessage = nav.banner,
            exportedMarkdownPreview = modal.mdPreview,
            exportedMarkdownTitle = modal.mdTitle,
            latestBackupJson = modal.backupJson,
            restorePreview = modal.restorePrev
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = CompassUiState()
    )

    fun selectTopTab(tab: TopLevelTab) {
        _selectedTopTab.value = tab
    }

    fun openProject(projectId: String, context: Context? = null) {
        _selectedProjectId.value = projectId
        _selectedProjectSection.value = ProjectSectionTab.OVERVIEW
        _selectedWorkAreaFilterId.value = null
        if (context != null) {
            viewModelScope.launch {
                repository.checkAndRefreshFolderPermissionState(context, projectId)
            }
        }
    }

    fun closeProjectWorkspace() {
        _selectedProjectId.value = null
        _selectedWorkAreaFilterId.value = null
    }

    fun selectProjectSection(section: ProjectSectionTab) {
        _selectedProjectSection.value = section
    }

    fun selectWorkAreaFilter(workAreaId: String?) {
        _selectedWorkAreaFilterId.value = workAreaId
    }

    fun dismissBanner() {
        _statusBannerMessage.value = null
    }

    fun dismissMarkdownPreview() {
        _exportedMarkdownPreview.value = null
    }

    fun dismissRestorePreview() {
        _restorePreview.value = null
    }

    fun createNewProject(
        name: String,
        purpose: String,
        projectType: ProjectType,
        resumePoint: String,
        context: Context? = null,
        openImmediately: Boolean = true
    ) {
        viewModelScope.launch {
            val created = repository.createProject(name, purpose, projectType, resumePoint, context = context)
            _statusBannerMessage.value = "Created folder 'Project Compass/${created.name}' with Superseded/ & Review_For_Deletion/ subfolders."
            if (openImmediately) {
                _selectedProjectId.value = created.projectId
                _selectedProjectSection.value = ProjectSectionTab.OVERVIEW
            }
        }
    }

    fun updateProject(
        projectId: String,
        name: String,
        purpose: String,
        status: ProjectStatus,
        resumePoint: String,
        externalLinks: String
    ) {
        viewModelScope.launch {
            repository.updateProjectDetails(projectId, name, purpose, status, resumePoint, externalLinks)
            _statusBannerMessage.value = "Saved changes to '$name'."
        }
    }

    fun addWorkArea(projectId: String, name: String, description: String) {
        viewModelScope.launch {
            repository.addCustomWorkArea(projectId, name, description)
            _statusBannerMessage.value = "Added Work Area '$name'."
        }
    }

    fun connectRootCompassFolder(context: Context, treeUri: Uri) {
        viewModelScope.launch {
            val msg = repository.connectRootCompassFolderOnPhone(context, treeUri)
            _statusBannerMessage.value = msg
        }
    }

    fun deleteProject(projectId: String, projectName: String) {
        viewModelScope.launch {
            if (_selectedProjectId.value == projectId) {
                _selectedProjectId.value = null
            }
            repository.deleteProjectCompletely(projectId)
            _statusBannerMessage.value = "Deleted project '$projectName'."
        }
    }

    fun connectSafFolder(context: Context, projectId: String, treeUri: Uri) {
        viewModelScope.launch {
            val msg = repository.connectSafFolderAndScanReadOnly(context, projectId, treeUri)
            _statusBannerMessage.value = msg
        }
    }

    fun disconnectFolder(projectId: String) {
        viewModelScope.launch {
            repository.disconnectProjectFolder(projectId)
            _statusBannerMessage.value = "Folder disconnected. All Project Brain knowledge stays preserved."
        }
    }

    fun refreshFolderReadOnly(context: Context, projectId: String) {
        viewModelScope.launch {
            val msg = repository.scanConnectedSafFolderReadOnly(context, projectId)
            _statusBannerMessage.value = msg
        }
    }

    private var scannerStarted = false

    fun ensureWorkspaceReady(context: Context) {
        val appContext = context.applicationContext
        viewModelScope.launch {
            repository.ensureInitialWorkspaceReady(appContext)
        }
        if (!scannerStarted) {
            scannerStarted = true
            viewModelScope.launch {
                while (true) {
                    kotlinx.coroutines.delay(8_000L)
                    repository.scanAllProjectCompassFoldersAutomatically(appContext)
                }
            }
        }
    }

    fun moveDocumentToSubfolder(context: Context, docId: String, targetCategory: DocumentLifecycleCategory) {
        viewModelScope.launch {
            val msg = repository.moveDocumentToSubfolderManual(context, docId, targetCategory)
            _statusBannerMessage.value = msg
        }
    }

    fun undoSubfolderMove(context: Context, opId: String) {
        viewModelScope.launch {
            val msg = repository.undoJournalMove(context, opId)
            _statusBannerMessage.value = msg
        }
    }

    fun updateBrainTruthStatus(brainItemId: String, newStatus: TruthStatus, reason: String) {
        viewModelScope.launch {
            repository.updateBrainItemTruthStatus(brainItemId, newStatus, reason)
            _statusBannerMessage.value = "Updated status to '${newStatus.label}'."
        }
    }

    fun addManualBrainEntry(
        projectId: String,
        workAreaId: String?,
        category: BrainCategory,
        content: String,
        reason: String,
        truthStatus: TruthStatus,
        aiContributor: String
    ) {
        viewModelScope.launch {
            repository.addManualBrainItem(projectId, workAreaId, category, content, reason, truthStatus, aiContributor)
            _statusBannerMessage.value = "Added '${category.label}' to Project Brain."
        }
    }

    fun resolveChangeAlert(context: Context, changeId: String, approve: Boolean, userNote: String) {
        viewModelScope.launch {
            repository.resolveChangeTrailAlert(context, changeId, approve, userNote)
            _statusBannerMessage.value = if (approve) {
                "Accepted change and moved predecessor handover to Superseded/."
            } else {
                "Kept original Approved rule and moved predecessor handover to Superseded/."
            }
        }
    }

    fun updateDocumentMetadata(
        context: Context,
        docId: String,
        workAreaId: String?,
        aiSource: String,
        conversationUrl: String,
        lifecycleCategory: DocumentLifecycleCategory
    ) {
        viewModelScope.launch {
            repository.assignDocumentWorkAreaAndAi(docId, workAreaId, aiSource, conversationUrl, lifecycleCategory)
            val moveMsg = repository.moveDocumentToSubfolderManual(context, docId, lifecycleCategory)
            _statusBannerMessage.value = moveMsg
        }
    }

    fun addInboxItem(titleOrFilename: String, textContent: String, aiSource: String) {
        viewModelScope.launch {
            repository.addIncomingItemToUnassignedInbox(titleOrFilename, textContent, aiSource)
            _statusBannerMessage.value = "Saved to Unassigned Inbox with project suggestion."
        }
    }

    fun approveInboxAssignment(docId: String, targetProjectId: String) {
        viewModelScope.launch {
            repository.approveAssignInboxItemToProject(docId, targetProjectId)
            _statusBannerMessage.value = "Moved to project and extracted handover items."
        }
    }

    fun generateHandoverMarkdown(projectId: String, workAreaId: String?) {
        val state = uiState.value
        val project = state.projects.firstOrNull { it.projectId == projectId } ?: return
        val areas = state.workAreas.filter { it.projectId == projectId }
        val docs = state.documents.filter { it.projectId == projectId }
        val brain = state.brainItems.filter { it.projectId == projectId }
        val trail = state.changeTrail.filter { it.projectId == projectId }

        val md = HandoverAndBackupEngine.generateMarkdownHandover(
            project = project,
            workAreas = areas,
            selectedWorkAreaId = workAreaId,
            documents = docs,
            brainItems = brain,
            changeTrail = trail
        )
        val areaName = areas.firstOrNull { it.workAreaId == workAreaId }?.name ?: "Complete Project"
        _exportedMarkdownTitle.value = "${project.name} — $areaName Handover (.md)"
        _exportedMarkdownPreview.value = md
    }

    fun saveExportedHandoverToFolder(context: Context) {
        val projectId = _selectedProjectId.value ?: return
        val md = _exportedMarkdownPreview.value ?: return
        viewModelScope.launch {
            val msg = repository.saveHandoverMarkdownToProjectFolder(context, projectId, md)
            _exportedMarkdownPreview.value = null
            _statusBannerMessage.value = msg
        }
    }

    fun createBackup(includePhysicalFiles: Boolean) {
        viewModelScope.launch {
            val json = repository.generateBackupJson(includePhysicalFiles)
            _latestBackupJson.value = json
            val modeLabel = if (includePhysicalFiles) "Full Archive Bundle" else "Project Brain Backup"
            _statusBannerMessage.value = "Created verified $modeLabel."
        }
    }

    fun previewRestoreFromBackupJson(rawJson: String) {
        val preview = HandoverAndBackupEngine.inspectAndPreviewBackup(rawJson)
        _restorePreview.value = preview
    }

    fun confirmAndExecuteRestore() {
        val preview = _restorePreview.value ?: return
        viewModelScope.launch {
            val resultMsg = repository.executeApprovedRestore(preview)
            _restorePreview.value = null
            _statusBannerMessage.value = resultMsg
        }
    }

    class Factory(private val repository: CompassRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return CompassViewModel(repository) as T
        }
    }
}

private data class CoreDataBundle(
    val projects: List<ProjectEntity>,
    val workAreas: List<WorkAreaEntity>,
    val docs: List<DocumentEntity>,
    val snapshots: List<DocumentSnapshotEntity>,
    val brain: List<BrainItemEntity>
)

private data class NavStateBundle(
    val topTab: TopLevelTab,
    val projId: String?,
    val projSec: ProjectSectionTab,
    val areaId: String?,
    val banner: String?
)

private data class ModalStateBundle(
    val mdPreview: String?,
    val mdTitle: String,
    val backupJson: String?,
    val restorePrev: BackupRestorePreview?
)
