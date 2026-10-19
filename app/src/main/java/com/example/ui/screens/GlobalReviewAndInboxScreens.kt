package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import com.example.data.BrainItemEntity
import com.example.data.ChangeTrailEntity
import com.example.data.DocumentEntity
import com.example.data.ProjectEntity
import com.example.data.ReviewStatus
import com.example.data.TruthStatus
import com.example.domain.BackupRestorePreview
import com.example.ui.components.TruthStatusBadge
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.StatusAlertBg
import com.example.ui.theme.StatusAlertText
import com.example.ui.theme.StatusApprovedBg
import com.example.ui.theme.StatusApprovedText
import com.example.ui.theme.StatusVerifiedBg
import com.example.ui.theme.StatusVerifiedText

@Composable
fun UpdatesReviewQueueScreen(
    projects: List<ProjectEntity>,
    brainItems: List<BrainItemEntity>,
    changeTrail: List<ChangeTrailEntity>,
    onUpdateBrainTruth: (String, TruthStatus, String) -> Unit,
    onResolveChangeAlert: (String, Boolean, String) -> Unit
) {
    val pendingChanges = remember(changeTrail) {
        changeTrail.filter { it.reviewStatus == ReviewStatus.PENDING_REVIEW.name }
    }
    val pendingBrain = remember(brainItems) {
        brainItems.filter {
            it.truthStatus == TruthStatus.PROPOSAL.name || it.truthStatus == TruthStatus.SOURCE_CLAIM.name
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("updates_review_list"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "Review Queue",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Whenever a new AI handover updates a rule or omits a previous requirement, it appears here for your approval before entering the project.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }

        if (pendingChanges.isNotEmpty()) {
            item {
                Text(
                    text = "Omissions & Wording Changes (${pendingChanges.size})",
                    style = MaterialTheme.typography.titleMedium,
                    color = StatusAlertText
                )
            }
            items(pendingChanges, key = { "alert_${it.changeId}" }) { alert ->
                val projName = projects.firstOrNull { it.projectId == alert.projectId }?.name ?: "Project"
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Surface(
                            color = StatusAlertBg,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "$projName • ${alert.changeType}",
                                style = MaterialTheme.typography.labelSmall,
                                color = StatusAlertText,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(alert.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("Previous approved rule: ${alert.previousWording}", style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("New handover state: ${alert.newWording}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Found in: ${alert.exactSourceFilename} • Source AI: ${alert.aiContributor}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    onResolveChangeAlert(alert.changeId, false, "Retained prior approved requirement.")
                                }
                            ) {
                                Text("Keep Original Rule")
                            }
                            OutlinedButton(
                                onClick = {
                                    onResolveChangeAlert(alert.changeId, true, "Accepted new handover change.")
                                }
                            ) {
                                Text("Accept Change")
                            }
                        }
                    }
                }
            }
        }

        item {
            Text(
                text = "New Handover Items to Review (${pendingBrain.size})",
                style = MaterialTheme.typography.titleMedium
            )
        }

        if (pendingBrain.isEmpty() && pendingChanges.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("All Caught Up!", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "No pending changes or unverified claims waiting for review.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        items(pendingBrain, key = { "rev_${it.brainItemId}" }) { item ->
            val projName = projects.firstOrNull { it.projectId == item.projectId }?.name ?: "Project"
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TruthStatusBadge(statusName = item.truthStatus)
                        Text("$projName • ${item.category}", style = MaterialTheme.typography.labelSmall)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(item.content, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "From: ${item.exactSourceFilename} • Author: ${item.aiContributor}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                onUpdateBrainTruth(item.brainItemId, TruthStatus.APPROVED, "Approved in Review Queue.")
                            }
                        ) {
                            Icon(Icons.Default.Check, contentDescription = "Approve", modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Approve")
                        }
                        OutlinedButton(
                            onClick = {
                                onUpdateBrainTruth(item.brainItemId, TruthStatus.VERIFIED, "Verified in Review Queue.")
                            }
                        ) {
                            Icon(Icons.Default.Verified, contentDescription = "Verify", modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Mark Verified")
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun UnassignedInboxScreen(
    projects: List<ProjectEntity>,
    inboxDocuments: List<DocumentEntity>,
    onAddInboxItem: (String, String, String) -> Unit,
    onApproveAssignToProject: (String, String) -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var titleInput by remember { mutableStateOf("") }
    var contentInput by remember { mutableStateOf("") }
    var aiSourceInput by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("unassigned_inbox_list"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Unassigned Inbox", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text(
                                text = "Loose notes, pasted AI snippets, or files dropped into your Project Compass folder that haven't been matched to a project yet.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Button(
                            onClick = { showAddDialog = true },
                            modifier = Modifier.testTag("add_inbox_item_button")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Paste Note", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Paste Note")
                        }
                    }
                }
            }
        }

        if (inboxDocuments.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Inbox is Empty", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Any loose files or notes will land here safely until you pick which project folder they belong to.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(inboxDocuments, key = { it.docId }) { doc ->
                val suggestedProject = projects.firstOrNull { it.projectId == doc.suggestedProjectId }
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(doc.originalFilename, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        if (doc.aiSourceName != "Not Stated") {
                            Text(
                                text = "Source: ${doc.aiSourceName}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        if (suggestedProject != null) {
                            Surface(
                                color = StatusApprovedBg,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "Suggested Project: ${suggestedProject.name}",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = StatusApprovedText,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = { onApproveAssignToProject(doc.docId, suggestedProject.projectId) }
                            ) {
                                Text("Move to '${suggestedProject.name}'")
                            }
                        }
                        if (projects.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Or select another project:", style = MaterialTheme.typography.labelSmall)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                projects.forEach { proj ->
                                    OutlinedButton(
                                        onClick = { onApproveAssignToProject(doc.docId, proj.projectId) }
                                    ) {
                                        Text(proj.name)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Paste Note or Handover") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = titleInput,
                        onValueChange = { titleInput = it },
                        label = { Text("Note Title (e.g. Claude_Notes.md)") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("inbox_title_input")
                    )
                    OutlinedTextField(
                        value = aiSourceInput,
                        onValueChange = { aiSourceInput = it },
                        label = { Text("Author / AI Source (Optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = contentInput,
                        onValueChange = { contentInput = it },
                        label = { Text("Paste text here") },
                        minLines = 4,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("inbox_content_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (contentInput.isNotBlank()) {
                            onAddInboxItem(
                                titleInput.ifBlank { "Pasted_Note.md" },
                                contentInput,
                                aiSourceInput
                            )
                            titleInput = ""
                            contentInput = ""
                            aiSourceInput = ""
                            showAddDialog = false
                        }
                    },
                    modifier = Modifier.testTag("confirm_inbox_save_button")
                ) {
                    Text("Save to Inbox")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun SettingsAndBackupsScreen(
    projectsCount: Int,
    documentsCount: Int,
    brainItemsCount: Int,
    changeTrailCount: Int,
    latestBackupJson: String?,
    restorePreview: BackupRestorePreview?,
    onCreateBackup: (includePhysicalFiles: Boolean) -> Unit,
    onPreviewRestore: (String) -> Unit,
    onConfirmRestore: () -> Unit,
    onDismissRestorePreview: () -> Unit
) {
    val context = LocalContext.current
    var includePhysicalFiles by remember { mutableStateOf(false) }
    var restoreInputJson by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("settings_backups_list"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text("Project Backups", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Everything in Project Compass is stored safely and offline on your device ($projectsCount projects, $brainItemsCount items recorded).",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Text("Backup Type:", style = MaterialTheme.typography.labelLarge)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = !includePhysicalFiles,
                            onClick = { includePhysicalFiles = false },
                            label = { Text("Project Brain Only (Default)") }
                        )
                        FilterChip(
                            selected = includePhysicalFiles,
                            onClick = { includePhysicalFiles = true },
                            label = { Text("Include File Copies") }
                        )
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Button(
                        onClick = { onCreateBackup(includePhysicalFiles) },
                        modifier = Modifier.testTag("generate_backup_button")
                    ) {
                        Icon(Icons.Default.Backup, contentDescription = "Backup", modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Create Backup")
                    }
                }
            }
        }

        if (!latestBackupJson.isNullOrBlank()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Backup Ready", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 80.dp, max = 160.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                                .padding(10.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            Text(
                                text = latestBackupJson,
                                fontFamily = JetBrainsMonoFamily,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("Project Compass Backup", latestBackupJson))
                                }
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Copy Backup")
                            }
                            Button(
                                onClick = { onPreviewRestore(latestBackupJson) },
                                modifier = Modifier.testTag("test_restore_latest_backup_button")
                            ) {
                                Icon(Icons.Default.Restore, contentDescription = "Preview Restore", modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Verify & Preview")
                            }
                        }
                    }
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Restore from Backup", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = restoreInputJson,
                        onValueChange = { restoreInputJson = it },
                        label = { Text("Paste Backup JSON") },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = { onPreviewRestore(restoreInputJson) },
                        enabled = restoreInputJson.isNotBlank()
                    ) {
                        Icon(Icons.Default.Restore, contentDescription = "Preview", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Preview Restore")
                    }
                }
            }
        }
    }

    restorePreview?.let { preview ->
        AlertDialog(
            onDismissRequest = onDismissRestorePreview,
            title = { Text("Restore Preview") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        color = if (preview.isValidChecksum) StatusVerifiedBg else StatusAlertBg,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = preview.summaryMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (preview.isValidChecksum) StatusVerifiedText else StatusAlertText,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                    Text("• Projects: ${preview.projectsCount}")
                    Text("• Items: ${preview.brainItemsCount}")
                    Text("• Files: ${preview.documentsCount}")
                }
            },
            confirmButton = {
                Button(
                    onClick = onConfirmRestore,
                    enabled = preview.isValidChecksum,
                    modifier = Modifier.testTag("confirm_restore_button")
                ) {
                    Text("Confirm Restore")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissRestorePreview) { Text("Cancel") }
            }
        )
    }
}
