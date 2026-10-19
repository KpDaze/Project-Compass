package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Verified
import com.example.domain.HandoverAndBackupEngine
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.BrainCategory
import com.example.data.BrainItemEntity
import com.example.data.ChangeTrailEntity
import com.example.data.DocumentEntity
import com.example.data.DocumentLifecycleCategory
import com.example.data.DocumentSnapshotEntity
import com.example.data.FileOperationJournalEntity
import com.example.data.ProjectEntity
import com.example.data.ProjectStatus
import com.example.data.ReviewStatus
import com.example.data.TruthStatus
import com.example.data.WorkAreaEntity
import com.example.ui.ProjectSectionTab
import com.example.ui.components.AddBrainItemDialog
import com.example.ui.components.CreateOrEditProjectDialog
import com.example.ui.components.DocumentInspectorDialog
import com.example.ui.components.TruthStatusBadge
import com.example.ui.theme.CompassRuby
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.StatusAlertBg
import com.example.ui.theme.StatusAlertText
import com.example.ui.theme.StatusApprovedBg
import com.example.ui.theme.StatusApprovedText
import com.example.ui.theme.StatusProposalBg
import com.example.ui.theme.StatusProposalText

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProjectWorkspaceScreen(
    project: ProjectEntity,
    workAreas: List<WorkAreaEntity>,
    documents: List<DocumentEntity>,
    snapshots: List<DocumentSnapshotEntity>,
    brainItems: List<BrainItemEntity>,
    changeTrail: List<ChangeTrailEntity>,
    journalOps: List<FileOperationJournalEntity>,
    selectedSection: ProjectSectionTab,
    selectedWorkAreaId: String?,
    onSelectSection: (ProjectSectionTab) -> Unit,
    onSelectWorkArea: (String?) -> Unit,
    onBackToProjects: () -> Unit,
    onEditProject: (String, String, ProjectStatus, String, String) -> Unit,
    onAddWorkArea: (String, String) -> Unit,
    onRefreshFolderReadOnly: () -> Unit,
    onAddManualBrainItem: (BrainCategory, String?, String, String, TruthStatus, String) -> Unit,
    onUpdateBrainTruthStatus: (String, TruthStatus, String) -> Unit,
    onResolveChangeAlert: (String, Boolean, String) -> Unit,
    onUpdateDocumentMeta: (String, String?, String, String, DocumentLifecycleCategory) -> Unit,
    onUndoJournalOp: (String) -> Unit,
    onExportHandoverMarkdown: (String?) -> Unit,
    onConnectProjectFolder: (Uri) -> Unit = {}
) {
    BackHandler { onBackToProjects() }
    val projectFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { treeUri: Uri? ->
        if (treeUri != null) {
            onConnectProjectFolder(treeUri)
        }
    }

    var showEditProjectDialog by remember { mutableStateOf(false) }
    var showAddBrainDialog by remember { mutableStateOf(false) }
    var inspectedDoc by remember { mutableStateOf<DocumentEntity?>(null) }
    var selectedBrainCategoryFilter by remember { mutableStateOf<BrainCategory?>(null) }

    val filteredBrainItems = remember(brainItems, selectedWorkAreaId, selectedBrainCategoryFilter) {
        brainItems.filter { item ->
            (selectedWorkAreaId == null || item.workAreaId == selectedWorkAreaId) &&
                (selectedBrainCategoryFilter == null || item.category == selectedBrainCategoryFilter?.name)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Top Project Bar
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    IconButton(
                        onClick = onBackToProjects,
                        modifier = Modifier.testTag("back_to_projects_button")
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to My Projects",
                            tint = CompassRuby
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = project.name,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${project.status} • ${documents.size} Files • ${brainItems.size} Brain Items",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { showEditProjectDialog = true }) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit Project", tint = CompassRuby)
                    }
                    OutlinedButton(
                        onClick = { onExportHandoverMarkdown(selectedWorkAreaId) },
                        modifier = Modifier.testTag("export_handover_button")
                    ) {
                        Icon(Icons.Default.Description, contentDescription = "Export Handover", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Handover .md")
                    }
                }

                // Section Navigation Pills
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ProjectSectionTab.entries.forEach { tab ->
                        FilterChip(
                            selected = selectedSection == tab,
                            onClick = { onSelectSection(tab) },
                            label = { Text(tab.label) },
                            modifier = Modifier.testTag("section_tab_${tab.name}")
                        )
                    }
                }
            }
        }

        // Main Section Content
        BoxWrapper(modifier = Modifier.weight(1f)) {
            when (selectedSection) {
                ProjectSectionTab.OVERVIEW -> {
                    OverviewSectionContent(
                        project = project,
                        documents = documents,
                        brainItems = brainItems,
                        changeTrail = changeTrail,
                        onRefreshFolder = onRefreshFolderReadOnly,
                        onPickPhoneFolder = { projectFolderLauncher.launch(null) },
                        onAddBrainItem = { showAddBrainDialog = true },
                        onJumpToSection = onSelectSection
                    )
                }

                ProjectSectionTab.BRAIN -> {
                    ProjectBrainSectionContent(
                        workAreas = workAreas,
                        brainItems = filteredBrainItems,
                        documents = documents,
                        selectedWorkAreaId = selectedWorkAreaId,
                        selectedCategory = selectedBrainCategoryFilter,
                        onSelectWorkArea = onSelectWorkArea,
                        onSelectCategory = { selectedBrainCategoryFilter = it },
                        onAddBrainClick = { showAddBrainDialog = true },
                        onUpdateTruthStatus = onUpdateBrainTruthStatus,
                        onOpenSourceDocument = { docId ->
                            inspectedDoc = documents.firstOrNull { it.docId == docId }
                        }
                    )
                }

                ProjectSectionTab.WORK_AREAS -> {
                    WorkAreasSectionContent(
                        workAreas = workAreas,
                        documents = documents,
                        brainItems = brainItems,
                        changeTrail = changeTrail,
                        onAddWorkArea = onAddWorkArea,
                        onExportWorkAreaHandover = { areaId -> onExportHandoverMarkdown(areaId) },
                        onUpdateBrainTruthStatus = onUpdateBrainTruthStatus,
                        onResolveChangeAlert = onResolveChangeAlert
                    )
                }

                ProjectSectionTab.FILES -> {
                    FilesInventorySectionContent(
                        project = project,
                        documents = documents,
                        workAreas = workAreas,
                        changeTrail = changeTrail,
                        onRefreshFolder = onRefreshFolderReadOnly,
                        onInspectDocument = { inspectedDoc = it },
                        onMoveDocumentCategory = { doc, newCat ->
                            onUpdateDocumentMeta(doc.docId, doc.workAreaId, doc.aiSourceName, doc.conversationUrl, newCat)
                        }
                    )
                }

                ProjectSectionTab.TASKS -> {
                    TasksAndProgressSectionContent(
                        brainItems = brainItems,
                        onAddTaskClick = { showAddBrainDialog = true },
                        onUpdateTruthStatus = onUpdateBrainTruthStatus
                    )
                }

                ProjectSectionTab.CHANGE_TRAIL -> {
                    ChangeTrailSectionContent(
                        changeTrail = changeTrail,
                        onResolveAlert = onResolveChangeAlert
                    )
                }

                ProjectSectionTab.CLEANUP -> {
                    DuplicatesAndCleanupSectionContent(
                        documents = documents,
                        journalOps = journalOps,
                        onInspectDocument = { inspectedDoc = it },
                        onUpdateDocCategory = { doc, newCat ->
                            onUpdateDocumentMeta(doc.docId, doc.workAreaId, doc.aiSourceName, doc.conversationUrl, newCat)
                        },
                        onUndoJournalOp = onUndoJournalOp
                    )
                }
            }
        }
    }

    if (showEditProjectDialog) {
        CreateOrEditProjectDialog(
            initialProject = project,
            onDismiss = { showEditProjectDialog = false },
            onConfirm = { name, purpose, _, status, resume, links ->
                onEditProject(name, purpose, status, resume, links)
                showEditProjectDialog = false
            }
        )
    }

    if (showAddBrainDialog) {
        AddBrainItemDialog(
            workAreas = workAreas,
            onDismiss = { showAddBrainDialog = false },
            onConfirm = { cat, areaId, content, reason, status, ai ->
                onAddManualBrainItem(cat, areaId, content, reason, status, ai)
                showAddBrainDialog = false
            }
        )
    }

    inspectedDoc?.let { doc ->
        val snap = snapshots.firstOrNull { it.docId == doc.docId }
        DocumentInspectorDialog(
            document = doc,
            snapshot = snap,
            workAreas = workAreas,
            onDismiss = { inspectedDoc = null },
            onSaveMetadata = { areaId, ai, url, cat ->
                onUpdateDocumentMeta(doc.docId, areaId, ai, url, cat)
            }
        )
    }
}

@Composable
private fun BoxWrapper(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(modifier = modifier) { content() }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OverviewSectionContent(
    project: ProjectEntity,
    documents: List<DocumentEntity>,
    brainItems: List<BrainItemEntity>,
    changeTrail: List<ChangeTrailEntity>,
    onRefreshFolder: () -> Unit,
    onPickPhoneFolder: () -> Unit,
    onAddBrainItem: () -> Unit,
    onJumpToSection: (ProjectSectionTab) -> Unit
) {
    val context = LocalContext.current
    val currentDocsCount = documents.count { it.lifecycleCategory == DocumentLifecycleCategory.CURRENT.name }
    val supersededDocsCount = documents.count { it.lifecycleCategory == DocumentLifecycleCategory.SUPERSEDED.name }
    val deletionDocsCount = documents.count { it.lifecycleCategory == DocumentLifecycleCategory.REVIEW_FOR_DELETION.name }
    val pendingSafetyBrakeCount = changeTrail.count { it.reviewStatus == ReviewStatus.PENDING_REVIEW.name }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Purpose & Quick Resume Paste Card for Next AI Chat (Stage 4)
        item {
            var copiedQuickResume by remember { mutableStateOf(false) }
            val quickResumeText = remember(project, brainItems, changeTrail) {
                HandoverAndBackupEngine.generateQuickResumePrompt(project, brainItems, changeTrail)
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("PROJECT PURPOSE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(project.purpose, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("QUICK RESUME BRIEF FOR YOUR NEXT AI CHAT", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                    Spacer(modifier = Modifier.height(6.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = quickResumeText,
                            fontFamily = JetBrainsMonoFamily,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("${project.name} Quick Resume", quickResumeText))
                                copiedQuickResume = true
                            },
                            modifier = Modifier.testTag("copy_quick_resume_button")
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy Resume Brief", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (copiedQuickResume) "Copied for Next AI!" else "Copy Quick Resume")
                        }
                        OutlinedButton(
                            onClick = { onJumpToSection(ProjectSectionTab.WORK_AREAS) }
                        ) {
                            Text("Compare Multiple AIs")
                        }
                    }
                }
            }
        }

        // Automatic Project Compass Folder & Handover Verification Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = "Project Folder",
                            tint = CompassRuby,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = project.linkedFolderDisplayName ?: "Project Compass/${project.name}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Project Compass automatically scans incoming handovers in your folder. When a new handover is verified clean, the older handover is automatically moved into Superseded/. If an approved rule was left out or changed, the Safety Brake holds the move until you check it.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            color = StatusApprovedBg,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.clickable { onJumpToSection(ProjectSectionTab.FILES) }
                        ) {
                            Text(
                                text = "$currentDocsCount in Main Folder",
                                style = MaterialTheme.typography.labelLarge,
                                color = StatusApprovedText,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                        Surface(
                            color = StatusProposalBg,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.clickable { onJumpToSection(ProjectSectionTab.CLEANUP) }
                        ) {
                            Text(
                                text = "$supersededDocsCount in Superseded/",
                                style = MaterialTheme.typography.labelLarge,
                                color = StatusProposalText,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                        Surface(
                            color = StatusProposalBg,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.clickable { onJumpToSection(ProjectSectionTab.CLEANUP) }
                        ) {
                            Text(
                                text = "$deletionDocsCount in Review_For_Deletion/",
                                style = MaterialTheme.typography.labelLarge,
                                color = StatusProposalText,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }

                    if (pendingSafetyBrakeCount > 0) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            color = StatusAlertBg,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onJumpToSection(ProjectSectionTab.CHANGE_TRAIL) }
                        ) {
                            Text(
                                text = "Safety Brake Active: $pendingSafetyBrakeCount handover change(s) or omitted rule(s) need your check before retiring predecessor.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = StatusAlertText,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onRefreshFolder,
                            modifier = Modifier.testTag("refresh_folder_button")
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Scan Folder", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Scan Folder Now")
                        }
                        OutlinedButton(
                            onClick = onPickPhoneFolder,
                            modifier = Modifier.testTag("link_phone_folder_button")
                        ) {
                            Icon(Icons.Default.Folder, contentDescription = "Link Phone Folder", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Link Phone Folder")
                        }
                        OutlinedButton(
                            onClick = { onJumpToSection(ProjectSectionTab.CLEANUP) }
                        ) {
                            Text("View Subfolders & Undo")
                        }
                    }
                }
            }
        }

        // Truth & Evidence Summary Grid
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Project Brain Snapshot", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        OutlinedButton(
                            onClick = onAddBrainItem,
                            modifier = Modifier.testTag("add_brain_item_overview_button")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Add", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Add Note")
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        val approved = brainItems.count { it.truthStatus == TruthStatus.APPROVED.name }
                        val verified = brainItems.count { it.truthStatus == TruthStatus.VERIFIED.name }
                        val proposals = brainItems.count { it.truthStatus == TruthStatus.PROPOSAL.name }
                        val claims = brainItems.count { it.truthStatus == TruthStatus.SOURCE_CLAIM.name }
                        AssistChip(
                            onClick = { onJumpToSection(ProjectSectionTab.BRAIN) },
                            label = { Text("$approved Approved Rules") }
                        )
                        AssistChip(
                            onClick = { onJumpToSection(ProjectSectionTab.TASKS) },
                            label = { Text("$verified Verified Results") }
                        )
                        AssistChip(
                            onClick = { onJumpToSection(ProjectSectionTab.BRAIN) },
                            label = { Text("$proposals Proposals to Review") }
                        )
                        AssistChip(
                            onClick = { onJumpToSection(ProjectSectionTab.BRAIN) },
                            label = { Text("$claims Unverified Claims") }
                        )
                        AssistChip(
                            onClick = { onJumpToSection(ProjectSectionTab.CHANGE_TRAIL) },
                            label = { Text("${changeTrail.size} Change Trail Logs") }
                        )
                    }
                }
            }
        }

        // External Work Tools & Sites Launcher (§4)
        item {
            val links = remember(project.externalLinksJson) {
                project.externalLinksJson.lines().mapNotNull { line ->
                    val parts = line.split("|")
                    if (parts.size == 2 && parts[1].isNotBlank()) parts[0].trim() to parts[1].trim() else null
                }
            }
            if (links.isNotEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("External Work Tools & AI Assistants", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            links.forEach { (title, url) ->
                                AssistChip(
                                    onClick = {
                                        try {
                                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                        } catch (_: Exception) {
                                        }
                                    },
                                    label = { Text(title) },
                                    leadingIcon = {
                                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = title, modifier = Modifier.size(15.dp))
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProjectBrainSectionContent(
    workAreas: List<WorkAreaEntity>,
    brainItems: List<BrainItemEntity>,
    documents: List<DocumentEntity>,
    selectedWorkAreaId: String?,
    selectedCategory: BrainCategory?,
    onSelectWorkArea: (String?) -> Unit,
    onSelectCategory: (BrainCategory?) -> Unit,
    onAddBrainClick: () -> Unit,
    onUpdateTruthStatus: (String, TruthStatus, String) -> Unit,
    onOpenSourceDocument: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("project_brain_list"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Evidence-Backed Project Brain", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(
                                text = "Text found in a document is never automatically true. Review proposals and verify claims below.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Button(
                            onClick = onAddBrainClick,
                            modifier = Modifier.testTag("add_brain_item_button")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Add", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Add")
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text("Filter by Work Area:", style = MaterialTheme.typography.labelSmall)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = selectedWorkAreaId == null,
                            onClick = { onSelectWorkArea(null) },
                            label = { Text("All Areas") }
                        )
                        workAreas.forEach { area ->
                            FilterChip(
                                selected = selectedWorkAreaId == area.workAreaId,
                                onClick = { onSelectWorkArea(area.workAreaId) },
                                label = { Text(area.name) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Filter by Category:", style = MaterialTheme.typography.labelSmall)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = selectedCategory == null,
                            onClick = { onSelectCategory(null) },
                            label = { Text("All Categories") }
                        )
                        BrainCategory.entries.forEach { cat ->
                            FilterChip(
                                selected = selectedCategory == cat,
                                onClick = { onSelectCategory(cat) },
                                label = { Text(cat.label) }
                            )
                        }
                    }
                }
            }
        }

        items(brainItems, key = { it.brainItemId }) { item ->
            val areaName = workAreas.firstOrNull { it.workAreaId == item.workAreaId }?.name ?: "General"
            val catLabel = BrainCategory.entries.firstOrNull { it.name == item.category }?.label ?: item.category

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TruthStatusBadge(statusName = item.truthStatus)
                        Text(
                            text = "$catLabel • $areaName • AI: ${item.aiContributor}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = item.content,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    if (item.reasonOrContext.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = item.reasonOrContext,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Source: ${item.exactSourceFilename}",
                                    fontFamily = JetBrainsMonoFamily,
                                    style = MaterialTheme.typography.labelSmall
                                )
                                if (item.sectionLineReference.isNotBlank()) {
                                    Text(
                                        text = "Ref: ${item.sectionLineReference}",
                                        fontFamily = JetBrainsMonoFamily,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }
                            if (item.sourceDocId != null && documents.any { it.docId == item.sourceDocId }) {
                                TextButton(onClick = { onOpenSourceDocument(item.sourceDocId) }) {
                                    Text("View File")
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (item.truthStatus != TruthStatus.APPROVED.name) {
                            OutlinedButton(
                                onClick = {
                                    onUpdateTruthStatus(item.brainItemId, TruthStatus.APPROVED, "Explicitly approved by user.")
                                },
                                modifier = Modifier.testTag("approve_brain_${item.brainItemId}")
                            ) {
                                Icon(Icons.Default.Check, contentDescription = "Approve", modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Approve")
                            }
                        }
                        if (item.truthStatus != TruthStatus.VERIFIED.name) {
                            OutlinedButton(
                                onClick = {
                                    onUpdateTruthStatus(item.brainItemId, TruthStatus.VERIFIED, "Verified with evidence by user.")
                                },
                                modifier = Modifier.testTag("verify_brain_${item.brainItemId}")
                            ) {
                                Icon(Icons.Default.Verified, contentDescription = "Verify", modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Mark Verified")
                            }
                        }
                        if (item.truthStatus != TruthStatus.UNCERTAIN.name) {
                            TextButton(
                                onClick = {
                                    onUpdateTruthStatus(item.brainItemId, TruthStatus.UNCERTAIN, "Marked uncertain for follow-up.")
                                }
                            ) {
                                Text("Mark Uncertain")
                            }
                        }
                        if (item.truthStatus != TruthStatus.SUPERSEDED.name) {
                            TextButton(
                                onClick = {
                                    onUpdateTruthStatus(item.brainItemId, TruthStatus.SUPERSEDED, "Marked superseded while keeping history.")
                                }
                            ) {
                                Text("Supersede")
                            }
                        }
                    }
                }
            }
        }
    }
}
