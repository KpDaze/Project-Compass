package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOff
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import com.example.data.DocumentEntity
import com.example.data.DocumentLifecycleCategory
import com.example.data.DocumentSnapshotEntity
import com.example.data.FolderPermissionState
import com.example.data.ProjectEntity
import com.example.data.ProjectStatus
import com.example.data.ProjectType
import com.example.data.TruthStatus
import com.example.data.WorkAreaEntity
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.StatusAlertBg
import com.example.ui.theme.StatusAlertText
import com.example.ui.theme.StatusApprovedBg
import com.example.ui.theme.StatusApprovedText
import com.example.ui.theme.StatusClaimBg
import com.example.ui.theme.StatusClaimText
import com.example.ui.theme.StatusProposalBg
import com.example.ui.theme.StatusProposalText
import com.example.ui.theme.StatusSupersededBg
import com.example.ui.theme.StatusSupersededText
import com.example.ui.theme.StatusVerifiedBg
import com.example.ui.theme.StatusVerifiedText

@Composable
fun TruthStatusBadge(statusName: String, modifier: Modifier = Modifier) {
    val status = remember(statusName) {
        TruthStatus.entries.firstOrNull { it.name == statusName } ?: TruthStatus.SOURCE_CLAIM
    }
    val (bg, fg) = when (status) {
        TruthStatus.VERIFIED -> StatusVerifiedBg to StatusVerifiedText
        TruthStatus.APPROVED -> StatusApprovedBg to StatusApprovedText
        TruthStatus.PROPOSAL -> StatusProposalBg to StatusProposalText
        TruthStatus.SOURCE_CLAIM -> StatusClaimBg to StatusClaimText
        TruthStatus.UNCERTAIN -> StatusAlertBg to StatusAlertText
        TruthStatus.SUPERSEDED -> StatusSupersededBg to StatusSupersededText
    }
    Surface(
        color = bg,
        contentColor = fg,
        shape = RoundedCornerShape(6.dp),
        modifier = modifier
    ) {
        Text(
            text = status.label,
            style = MaterialTheme.typography.labelLarge,
            color = fg,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun FolderHealthBadge(permissionState: String, folderName: String?) {
    val state = remember(permissionState) {
        FolderPermissionState.entries.firstOrNull { it.name == permissionState } ?: FolderPermissionState.UNLINKED
    }
    val (bg, fg, icon) = when (state) {
        FolderPermissionState.CONNECTED -> Triple(StatusVerifiedBg, StatusVerifiedText, Icons.Default.Folder)
        FolderPermissionState.PERMISSION_NEEDED -> Triple(StatusAlertBg, StatusAlertText, Icons.Default.Warning)
        FolderPermissionState.UNLINKED -> Triple(StatusClaimBg, StatusClaimText, Icons.Default.FolderOff)
    }
    Surface(
        color = bg,
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = state.label,
                tint = fg,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = if (state == FolderPermissionState.CONNECTED && !folderName.isNullOrBlank()) {
                    "Folder: $folderName"
                } else if (state == FolderPermissionState.CONNECTED) {
                    "Folder Synced"
                } else {
                    state.label
                },
                style = MaterialTheme.typography.labelLarge,
                color = fg
            )
        }
    }
}

@Composable
fun CreateOrEditProjectDialog(
    initialProject: ProjectEntity? = null,
    onDismiss: () -> Unit,
    onConfirm: (name: String, purpose: String, type: ProjectType, status: ProjectStatus, resumePoint: String, links: String) -> Unit
) {
    var name by remember { mutableStateOf(initialProject?.name ?: "") }
    var purpose by remember { mutableStateOf(initialProject?.purpose ?: "") }
    var selectedStatus by remember {
        mutableStateOf(
            ProjectStatus.entries.firstOrNull { it.name == initialProject?.status } ?: ProjectStatus.ACTIVE
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (initialProject == null) "Create Project Folder" else "Edit Project Details")
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Project Name") },
                    placeholder = { Text("e.g. Garden Tracker, Novel Idea") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("project_name_input")
                )
                OutlinedTextField(
                    value = purpose,
                    onValueChange = { purpose = it },
                    label = { Text("Description / What is it for? (Optional)") },
                    minLines = 2,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("project_purpose_input")
                )
                if (initialProject != null) {
                    Text("Status", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(ProjectStatus.ACTIVE, ProjectStatus.PAUSED, ProjectStatus.COMPLETED, ProjectStatus.ARCHIVED).forEach { st ->
                            FilterChip(
                                selected = selectedStatus == st,
                                onClick = { selectedStatus = st },
                                label = { Text(st.label) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        onConfirm(
                            name.trim(),
                            purpose.trim().ifEmpty { "Project managed with FileMate and Project Compass." },
                            ProjectType.SOFTWARE,
                            selectedStatus,
                            "Check recent files and tasks.",
                            ""
                        )
                    }
                },
                enabled = name.isNotBlank(),
                modifier = Modifier.testTag("confirm_save_project_button")
            ) {
                Text(if (initialProject == null) "Create" else "Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddBrainItemDialog(
    workAreas: List<WorkAreaEntity>,
    onDismiss: () -> Unit,
    onConfirm: (category: BrainCategory, workAreaId: String?, content: String, reason: String, status: TruthStatus, aiSource: String) -> Unit
) {
    var category by remember { mutableStateOf(BrainCategory.REQUIREMENT) }
    var selectedWorkAreaId by remember { mutableStateOf(workAreas.firstOrNull()?.workAreaId) }
    var content by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf("") }
    var truthStatus by remember { mutableStateOf(TruthStatus.APPROVED) }
    var aiSource by remember { mutableStateOf("User-Authored") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Project Item") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Type of Item", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    BrainCategory.entries.forEach { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = { category = cat },
                            label = { Text(cat.label) }
                        )
                    }
                }
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("What is the note, requirement, or decision?") },
                    minLines = 3,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("brain_content_input")
                )
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Reason or context (Optional)") },
                    minLines = 1,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Status", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        TruthStatus.APPROVED,
                        TruthStatus.PROPOSAL,
                        TruthStatus.VERIFIED,
                        TruthStatus.SOURCE_CLAIM
                    ).forEach { st ->
                        FilterChip(
                            selected = truthStatus == st,
                            onClick = { truthStatus = st },
                            label = { Text(st.label) }
                        )
                    }
                }
                if (workAreas.isNotEmpty()) {
                    Text("Work Area (Optional)", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        workAreas.forEach { area ->
                            FilterChip(
                                selected = selectedWorkAreaId == area.workAreaId,
                                onClick = { selectedWorkAreaId = area.workAreaId },
                                label = { Text(area.name) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (content.isNotBlank()) {
                        onConfirm(category, selectedWorkAreaId, content, reason, truthStatus, aiSource)
                    }
                },
                enabled = content.isNotBlank(),
                modifier = Modifier.testTag("confirm_add_brain_button")
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DocumentInspectorDialog(
    document: DocumentEntity,
    snapshot: DocumentSnapshotEntity?,
    workAreas: List<WorkAreaEntity>,
    onDismiss: () -> Unit,
    onSaveMetadata: (workAreaId: String?, aiSource: String, url: String, lifecycle: DocumentLifecycleCategory) -> Unit
) {
    var selectedWorkAreaId by remember { mutableStateOf(document.workAreaId) }
    var aiSource by remember { mutableStateOf(document.aiSourceName) }
    var convUrl by remember { mutableStateOf(document.conversationUrl) }
    var selectedLifecycle by remember {
        mutableStateOf(
            DocumentLifecycleCategory.entries.firstOrNull { it.name == document.lifecycleCategory }
                ?: DocumentLifecycleCategory.CURRENT
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(document.originalFilename) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = "Format: ${document.formatLabel}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "Path: ${document.currentRelativePath}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        if (document.aiSourceName.isNotBlank() && document.aiSourceName != "Not Stated") {
                            Text(
                                text = "Source AI: ${document.aiSourceName}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }

                Text("Lifecycle Status", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DocumentLifecycleCategory.entries.forEach { cat ->
                        FilterChip(
                            selected = selectedLifecycle == cat,
                            onClick = { selectedLifecycle = cat },
                            label = { Text(cat.label) }
                        )
                    }
                }

                if (workAreas.isNotEmpty()) {
                    Text("Work Area", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        workAreas.forEach { area ->
                            FilterChip(
                                selected = selectedWorkAreaId == area.workAreaId,
                                onClick = { selectedWorkAreaId = area.workAreaId },
                                label = { Text(area.name) }
                            )
                        }
                    }
                }

                Text(
                    text = "Document Contents",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 100.dp, max = 220.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                        .padding(10.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    val txt = snapshot?.extractedPlainText.orEmpty().ifBlank { "No text content found." }
                    Text(
                        text = txt,
                        fontFamily = JetBrainsMonoFamily,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSaveMetadata(selectedWorkAreaId, aiSource, convUrl, selectedLifecycle)
                    onDismiss()
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MarkdownExportDialog(
    title: String,
    markdownContent: String,
    onDismiss: () -> Unit,
    onSaveToFolder: (() -> Unit)? = null
) {
    val context = LocalContext.current
    var copiedToast by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Clean Markdown handover generated from your Project Brain. You can save it directly into your Project Compass folder (retiring the older handover to Superseded/) or copy/share it.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                        .padding(10.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = markdownContent,
                        fontFamily = JetBrainsMonoFamily,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                if (copiedToast) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Copied",
                            tint = StatusVerifiedText,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Copied to clipboard!", color = StatusVerifiedText)
                    }
                }
            }
        },
        confirmButton = {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (onSaveToFolder != null) {
                    Button(
                        onClick = onSaveToFolder,
                        modifier = Modifier.testTag("save_handover_to_folder_button")
                    ) {
                        Icon(Icons.Default.Folder, contentDescription = "Save to Folder", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Save to Folder")
                    }
                }
                OutlinedButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText(title, markdownContent))
                        copiedToast = true
                    },
                    modifier = Modifier.testTag("copy_handover_markdown_button")
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Copy")
                }
                OutlinedButton(
                    onClick = {
                        val sendIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/markdown"
                            putExtra(Intent.EXTRA_SUBJECT, title)
                            putExtra(Intent.EXTRA_TEXT, markdownContent)
                        }
                        context.startActivity(Intent.createChooser(sendIntent, "Share Handover Markdown"))
                    },
                    modifier = Modifier.testTag("share_handover_markdown_button")
                ) {
                    Icon(Icons.Default.Share, contentDescription = "Share", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Share")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}
