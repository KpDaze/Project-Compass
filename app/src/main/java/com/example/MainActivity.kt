package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.RateReview
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.CompassDatabase
import com.example.data.CompassRepository
import com.example.data.ReviewStatus
import com.example.data.TruthStatus
import com.example.ui.CompassUiState
import com.example.ui.CompassViewModel
import com.example.ui.TopLevelTab
import com.example.ui.components.MarkdownExportDialog
import com.example.ui.screens.ProjectWorkspaceScreen
import com.example.ui.screens.ProjectsHomeScreen
import com.example.ui.screens.SettingsAndBackupsScreen
import com.example.ui.screens.UnassignedInboxScreen
import com.example.ui.screens.UpdatesReviewQueueScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.StatusApprovedBg
import com.example.ui.theme.StatusApprovedText

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val db = CompassDatabase.getDatabase(applicationContext)
        val repository = CompassRepository(db.compassDao())

        val sharedText = if (intent?.action == Intent.ACTION_SEND && intent.type?.startsWith("text/") == true) {
            intent.getStringExtra(Intent.EXTRA_TEXT)
        } else {
            null
        }
        val sharedSubject = intent?.getStringExtra(Intent.EXTRA_SUBJECT) ?: "Shared_Android_Note.md"

        setContent {
            MyApplicationTheme {
                val viewModel: CompassViewModel = viewModel(factory = CompassViewModel.Factory(repository))
                LaunchedEffect(Unit) {
                    viewModel.ensureWorkspaceReady(applicationContext)
                    if (!sharedText.isNullOrBlank()) {
                        viewModel.addInboxItem(sharedSubject, sharedText, "Shared via Android")
                        intent?.action = ""
                    }
                }
                ProjectCompassApp(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun ProjectCompassApp(viewModel: CompassViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val activeProject = state.projects.firstOrNull { it.projectId == state.selectedProjectId }
    val pendingReviewTotal = state.brainItems.count {
        it.truthStatus == TruthStatus.PROPOSAL.name || it.truthStatus == TruthStatus.SOURCE_CLAIM.name
    } + state.changeTrail.count { it.reviewStatus == ReviewStatus.PENDING_REVIEW.name }
    val inboxCount = state.documents.count { it.projectId == null }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            if (activeProject == null) {
                NavigationBar {
                    NavigationBarItem(
                        selected = state.selectedTopTab == TopLevelTab.MY_PROJECTS,
                        onClick = { viewModel.selectTopTab(TopLevelTab.MY_PROJECTS) },
                        icon = { Icon(Icons.Default.Explore, contentDescription = "My Projects") },
                        label = { Text("My Projects") },
                        modifier = Modifier.testTag("nav_my_projects")
                    )
                    NavigationBarItem(
                        selected = state.selectedTopTab == TopLevelTab.UPDATES_REVIEW,
                        onClick = { viewModel.selectTopTab(TopLevelTab.UPDATES_REVIEW) },
                        icon = {
                            BadgedBox(
                                badge = {
                                    if (pendingReviewTotal > 0) {
                                        Badge { Text(pendingReviewTotal.toString()) }
                                    }
                                }
                            ) {
                                Icon(Icons.Default.RateReview, contentDescription = "Review Queue")
                            }
                        },
                        label = { Text("Review Queue") },
                        modifier = Modifier.testTag("nav_updates_review")
                    )
                    NavigationBarItem(
                        selected = state.selectedTopTab == TopLevelTab.UNASSIGNED_INBOX,
                        onClick = { viewModel.selectTopTab(TopLevelTab.UNASSIGNED_INBOX) },
                        icon = {
                            BadgedBox(
                                badge = {
                                    if (inboxCount > 0) {
                                        Badge { Text(inboxCount.toString()) }
                                    }
                                }
                            ) {
                                Icon(Icons.Default.Inbox, contentDescription = "Unassigned Inbox")
                            }
                        },
                        label = { Text("Inbox") },
                        modifier = Modifier.testTag("nav_unassigned_inbox")
                    )
                    NavigationBarItem(
                        selected = state.selectedTopTab == TopLevelTab.SETTINGS_BACKUPS,
                        onClick = { viewModel.selectTopTab(TopLevelTab.SETTINGS_BACKUPS) },
                        icon = { Icon(Icons.Default.Backup, contentDescription = "Backups") },
                        label = { Text("Backups") },
                        modifier = Modifier.testTag("nav_settings_backups")
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            AnimatedVisibility(visible = state.statusBannerMessage != null) {
                state.statusBannerMessage?.let { msg ->
                    Surface(
                        color = StatusApprovedBg,
                        contentColor = StatusApprovedText,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = msg,
                                style = MaterialTheme.typography.bodyMedium,
                                color = StatusApprovedText,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            IconButton(onClick = { viewModel.dismissBanner() }) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Dismiss Banner",
                                    tint = StatusApprovedText
                                )
                            }
                        }
                    }
                }
            }

            Box(modifier = Modifier.weight(1f)) {
                if (activeProject != null) {
                    val projectId = activeProject.projectId
                    ProjectWorkspaceScreen(
                        project = activeProject,
                        workAreas = state.workAreas.filter { it.projectId == projectId },
                        documents = state.documents.filter { it.projectId == projectId },
                        snapshots = state.snapshots,
                        brainItems = state.brainItems.filter { it.projectId == projectId },
                        changeTrail = state.changeTrail.filter { it.projectId == projectId },
                        journalOps = state.journalOps.filter { it.projectId == projectId },
                        selectedSection = state.selectedProjectSection,
                        selectedWorkAreaId = state.selectedWorkAreaFilterId,
                        onSelectSection = { viewModel.selectProjectSection(it) },
                        onSelectWorkArea = { viewModel.selectWorkAreaFilter(it) },
                        onBackToProjects = { viewModel.closeProjectWorkspace() },
                        onEditProject = { name, purpose, status, resume, links ->
                            viewModel.updateProject(projectId, name, purpose, status, resume, links)
                        },
                        onAddWorkArea = { name, desc ->
                            viewModel.addWorkArea(projectId, name, desc)
                        },
                        onRefreshFolderReadOnly = {
                            viewModel.refreshFolderReadOnly(context, projectId)
                        },
                        onAddManualBrainItem = { cat, areaId, content, reason, status, ai ->
                            viewModel.addManualBrainEntry(projectId, areaId, cat, content, reason, status, ai)
                        },
                        onUpdateBrainTruthStatus = { itemId, newStatus, reason ->
                            viewModel.updateBrainTruthStatus(itemId, newStatus, reason)
                        },
                        onResolveChangeAlert = { changeId, approve, note ->
                            viewModel.resolveChangeAlert(context, changeId, approve, note)
                        },
                        onUpdateDocumentMeta = { docId, areaId, ai, url, cat ->
                            viewModel.updateDocumentMetadata(context, docId, areaId, ai, url, cat)
                        },
                        onUndoJournalOp = { opId ->
                            viewModel.undoSubfolderMove(context, opId)
                        },
                        onExportHandoverMarkdown = { areaId ->
                            viewModel.generateHandoverMarkdown(projectId, areaId)
                        },
                        onConnectProjectFolder = { treeUri ->
                            viewModel.connectSafFolder(context, projectId, treeUri)
                        }
                    )
                } else {
                    GlobalTabsContent(state = state, viewModel = viewModel)
                }
            }
        }
    }

    state.exportedMarkdownPreview?.let { md ->
        MarkdownExportDialog(
            title = state.exportedMarkdownTitle,
            markdownContent = md,
            onDismiss = { viewModel.dismissMarkdownPreview() },
            onSaveToFolder = { viewModel.saveExportedHandoverToFolder(context) }
        )
    }
}

@Composable
private fun GlobalTabsContent(
    state: CompassUiState,
    viewModel: CompassViewModel
) {
    val context = LocalContext.current
    when (state.selectedTopTab) {
        TopLevelTab.MY_PROJECTS -> {
            ProjectsHomeScreen(
                projects = state.projects,
                documents = state.documents,
                brainItems = state.brainItems,
                changeTrail = state.changeTrail,
                onOpenProject = { projId -> viewModel.openProject(projId, context) },
                onCreateProject = { name, purpose, type, resume ->
                    viewModel.createNewProject(name, purpose, type, resume, context = context)
                },
                onUpdateProjectStatus = { proj, newStatus ->
                    viewModel.updateProject(
                        proj.projectId,
                        proj.name,
                        proj.purpose,
                        newStatus,
                        proj.resumePointSummary,
                        proj.externalLinksJson
                    )
                },
                onConnectRootFolder = { treeUri ->
                    viewModel.connectRootCompassFolder(context, treeUri)
                },
                onDeleteProject = { proj ->
                    viewModel.deleteProject(proj.projectId, proj.name)
                }
            )
        }

        TopLevelTab.UPDATES_REVIEW -> {
            UpdatesReviewQueueScreen(
                projects = state.projects,
                brainItems = state.brainItems,
                changeTrail = state.changeTrail,
                onUpdateBrainTruth = { itemId, status, reason ->
                    viewModel.updateBrainTruthStatus(itemId, status, reason)
                },
                onResolveChangeAlert = { changeId, approve, note ->
                    viewModel.resolveChangeAlert(context, changeId, approve, note)
                }
            )
        }

        TopLevelTab.UNASSIGNED_INBOX -> {
            UnassignedInboxScreen(
                projects = state.projects,
                inboxDocuments = state.documents.filter { it.projectId == null },
                onAddInboxItem = { title, content, ai ->
                    viewModel.addInboxItem(title, content, ai)
                },
                onApproveAssignToProject = { docId, targetProjId ->
                    viewModel.approveInboxAssignment(docId, targetProjId)
                }
            )
        }

        TopLevelTab.SETTINGS_BACKUPS -> {
            SettingsAndBackupsScreen(
                projectsCount = state.projects.size,
                documentsCount = state.documents.size,
                brainItemsCount = state.brainItems.size,
                changeTrailCount = state.changeTrail.size,
                latestBackupJson = state.latestBackupJson,
                restorePreview = state.restorePreview,
                onCreateBackup = { includeFiles -> viewModel.createBackup(includeFiles) },
                onPreviewRestore = { json -> viewModel.previewRestoreFromBackupJson(json) },
                onConfirmRestore = { viewModel.confirmAndExecuteRestore() },
                onDismissRestorePreview = { viewModel.dismissRestorePreview() }
            )
        }
    }
}
