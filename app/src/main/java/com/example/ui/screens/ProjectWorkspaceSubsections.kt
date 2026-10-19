package com.example.ui.screens

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
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.BrainCategory
import com.example.data.BrainItemEntity
import com.example.data.ChangeTrailEntity
import com.example.data.DocumentEntity
import com.example.data.DocumentLifecycleCategory
import com.example.data.FileOperationJournalEntity
import com.example.data.ProjectEntity
import com.example.data.ReviewStatus
import com.example.data.TruthStatus
import com.example.data.WorkAreaEntity
import com.example.ui.components.TruthStatusBadge
import com.example.ui.theme.CompassRuby
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.StatusAlertBg
import com.example.ui.theme.StatusAlertText
import com.example.ui.theme.StatusApprovedBg
import com.example.ui.theme.StatusApprovedText
import com.example.ui.theme.StatusProposalBg
import com.example.ui.theme.StatusProposalText
import com.example.ui.theme.StatusSupersededBg
import com.example.ui.theme.StatusSupersededText
import com.example.ui.theme.StatusVerifiedBg
import com.example.ui.theme.StatusVerifiedText

import com.example.domain.HandoverAndBackupEngine

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WorkAreasSectionContent(
    workAreas: List<WorkAreaEntity>,
    documents: List<DocumentEntity>,
    brainItems: List<BrainItemEntity>,
    changeTrail: List<ChangeTrailEntity>,
    onAddWorkArea: (String, String) -> Unit,
    onExportWorkAreaHandover: (String) -> Unit,
    onUpdateBrainTruthStatus: (String, TruthStatus, String) -> Unit,
    onResolveChangeAlert: (String, Boolean, String) -> Unit
) {
    var showAddAreaDialog by remember { mutableStateOf(false) }
    var newAreaName by remember { mutableStateOf("") }
    var newAreaDesc by remember { mutableStateOf("") }

    val aiSummaries = remember(documents, brainItems, changeTrail) {
        HandoverAndBackupEngine.buildMultiAiContributionsSummary(documents, brainItems, changeTrail)
    }
    val pendingCrossAiConflicts = remember(changeTrail) {
        changeTrail.filter { it.reviewStatus == ReviewStatus.PENDING_REVIEW.name }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("work_areas_and_ais_list"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Stage 4: Multi-AI Side-by-Side Comparison Header
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Multi-AI Side-by-Side Comparison (${aiSummaries.size} Sources)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = CompassRuby
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Compare what each AI assistant contributed across your handovers, see where a newer AI left out an earlier AI's approved rule, and approve the version you want to keep.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Cross-AI Omissions or Disagreements (if any)
        if (pendingCrossAiConflicts.isNotEmpty()) {
            item {
                Text(
                    text = "Cross-AI Omissions & Differences to Resolve (${pendingCrossAiConflicts.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = StatusAlertText
                )
            }
            items(pendingCrossAiConflicts, key = { "ai_diff_${it.changeId}" }) { diff ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = StatusAlertBg),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "${diff.title} (Source AI: ${diff.aiContributor})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = StatusAlertText
                        )
                        Text(
                            text = "Earlier Approved Rule: ${diff.previousWording}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = StatusAlertText
                        )
                        Text(
                            text = "New AI State: ${diff.newWording}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = StatusAlertText
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    onResolveChangeAlert(diff.changeId, false, "Kept earlier approved rule during Multi-AI comparison.")
                                }
                            ) {
                                Text("Keep Earlier Rule")
                            }
                            OutlinedButton(
                                onClick = {
                                    onResolveChangeAlert(diff.changeId, true, "Accepted new AI wording during Multi-AI comparison.")
                                }
                            ) {
                                Text("Accept New AI Change")
                            }
                        }
                    }
                }
            }
        }

        // Side-by-Side AI Contributor Cards
        items(aiSummaries, key = { "ai_${it.aiName}" }) { summary ->
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("ai_summary_card_${summary.aiName}")
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = summary.aiName,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = CompassRuby
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Surface(color = StatusApprovedBg, shape = RoundedCornerShape(6.dp)) {
                                Text(
                                    text = "${summary.approvedCount} Approved",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = StatusApprovedText,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                            if (summary.unverifiedOrProposalCount > 0) {
                                Surface(color = StatusProposalBg, shape = RoundedCornerShape(6.dp)) {
                                    Text(
                                        text = "${summary.unverifiedOrProposalCount} Unverified",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = StatusProposalText,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }

                    if (summary.filenames.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Files: ${summary.filenames.joinToString(", ")}",
                            fontFamily = JetBrainsMonoFamily,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    summary.keyItems.forEach { item ->
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TruthStatusBadge(statusName = item.truthStatus)
                                    Text(item.category, style = MaterialTheme.typography.labelSmall)
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(item.content, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                if (item.truthStatus == TruthStatus.PROPOSAL.name || item.truthStatus == TruthStatus.SOURCE_CLAIM.name) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedButton(
                                            onClick = {
                                                onUpdateBrainTruthStatus(item.brainItemId, TruthStatus.APPROVED, "Approved from Multi-AI Comparison.")
                                            }
                                        ) {
                                            Text("Approve Rule")
                                        }
                                        OutlinedButton(
                                            onClick = {
                                                onUpdateBrainTruthStatus(item.brainItemId, TruthStatus.VERIFIED, "Verified from Multi-AI Comparison.")
                                            }
                                        ) {
                                            Text("Mark Verified")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Work Areas Header
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
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Project Work Areas", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(
                                text = "Export a focused handover for Design, Development, Testing, Research, or Documentation.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Button(onClick = { showAddAreaDialog = true }) {
                            Icon(Icons.Default.Add, contentDescription = "Add Work Area", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("New Area")
                        }
                    }
                }
            }
        }

        items(workAreas, key = { it.workAreaId }) { area ->
            val areaDocs = documents.filter { it.workAreaId == area.workAreaId }
            val areaBrain = brainItems.filter { it.workAreaId == area.workAreaId }
            val aiContributors = (areaDocs.map { it.aiSourceName } + areaBrain.map { it.aiContributor })
                .filter { it.isNotBlank() && it != "Not Stated" }
                .distinct()

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
                        Column(modifier = Modifier.weight(1f)) {
                            Text(area.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(area.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        OutlinedButton(onClick = { onExportWorkAreaHandover(area.workAreaId) }) {
                            Icon(Icons.Default.Description, contentDescription = "Export", modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Export .md")
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "${areaDocs.size} Files • ${areaBrain.size} Brain Items • AIs: ${aiContributors.joinToString(", ").ifEmpty { "None recorded yet" }}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }

    if (showAddAreaDialog) {
        AlertDialog(
            onDismissRequest = { showAddAreaDialog = false },
            title = { Text("Add Work Area") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = newAreaName,
                        onValueChange = { newAreaName = it },
                        label = { Text("Work Area Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newAreaDesc,
                        onValueChange = { newAreaDesc = it },
                        label = { Text("Description") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newAreaName.isNotBlank()) {
                            onAddWorkArea(newAreaName, newAreaDesc)
                            newAreaName = ""
                            newAreaDesc = ""
                            showAddAreaDialog = false
                        }
                    }
                ) {
                    Text("Add Area")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddAreaDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FilesInventorySectionContent(
    project: ProjectEntity,
    documents: List<DocumentEntity>,
    workAreas: List<WorkAreaEntity>,
    changeTrail: List<ChangeTrailEntity>,
    onRefreshFolder: () -> Unit,
    onInspectDocument: (DocumentEntity) -> Unit,
    onMoveDocumentCategory: (DocumentEntity, DocumentLifecycleCategory) -> Unit
) {
    var selectedSubfolderFilter by remember { mutableStateOf<DocumentLifecycleCategory?>(null) }

    val filteredDocs = remember(documents, selectedSubfolderFilter) {
        if (selectedSubfolderFilter == null) {
            documents
        } else {
            documents.filter { it.lifecycleCategory == selectedSubfolderFilter?.name }
        }
    }

    val currentCount = documents.count { it.lifecycleCategory == DocumentLifecycleCategory.CURRENT.name }
    val supersededCount = documents.count { it.lifecycleCategory == DocumentLifecycleCategory.SUPERSEDED.name }
    val deletionCount = documents.count { it.lifecycleCategory == DocumentLifecycleCategory.REVIEW_FOR_DELETION.name }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("files_inventory_list"),
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
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Project Compass Folder & Auto-Sorted Subfolders",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Compass scans every new handover to verify nothing important was omitted or changed before automatically moving older handovers into Superseded/ and duplicates into Review_For_Deletion/.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = selectedSubfolderFilter == null,
                            onClick = { selectedSubfolderFilter = null },
                            label = { Text("All (${documents.size})") }
                        )
                        FilterChip(
                            selected = selectedSubfolderFilter == DocumentLifecycleCategory.CURRENT,
                            onClick = { selectedSubfolderFilter = DocumentLifecycleCategory.CURRENT },
                            label = { Text("Current Folder ($currentCount)") }
                        )
                        FilterChip(
                            selected = selectedSubfolderFilter == DocumentLifecycleCategory.SUPERSEDED,
                            onClick = { selectedSubfolderFilter = DocumentLifecycleCategory.SUPERSEDED },
                            label = { Text("Superseded/ ($supersededCount)") }
                        )
                        FilterChip(
                            selected = selectedSubfolderFilter == DocumentLifecycleCategory.REVIEW_FOR_DELETION,
                            onClick = { selectedSubfolderFilter = DocumentLifecycleCategory.REVIEW_FOR_DELETION },
                            label = { Text("Review_For_Deletion/ ($deletionCount)") }
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(onClick = onRefreshFolder) {
                        Icon(Icons.Default.Refresh, contentDescription = "Scan", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Scan Folder for New Handovers")
                    }
                }
            }
        }

        items(filteredDocs, key = { it.docId }) { doc ->
            val areaName = workAreas.firstOrNull { it.workAreaId == doc.workAreaId }?.name ?: "General"
            val predDoc = documents.firstOrNull { it.docId == doc.predecessorDocId }
            val pendingSafetyAlerts = changeTrail.count {
                it.sourceDocId == doc.docId && it.reviewStatus == ReviewStatus.PENDING_REVIEW.name
            }

            val (badgeBg, badgeFg, folderLabel) = when (doc.lifecycleCategory) {
                DocumentLifecycleCategory.CURRENT.name -> Triple(StatusApprovedBg, StatusApprovedText, "Current Folder")
                DocumentLifecycleCategory.SUPERSEDED.name -> Triple(StatusSupersededBg, StatusSupersededText, "Superseded/ Subfolder")
                DocumentLifecycleCategory.REVIEW_FOR_DELETION.name -> Triple(StatusProposalBg, StatusProposalText, "Review_For_Deletion/")
                else -> Triple(StatusAlertBg, StatusAlertText, doc.lifecycleCategory)
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onInspectDocument(doc) }
                    .testTag("document_card_${doc.originalFilename}")
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Folder,
                                contentDescription = "File",
                                tint = CompassRuby,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = doc.originalFilename,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Surface(
                            color = badgeBg,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = folderLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = badgeFg,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "${doc.formatLabel} • Area: $areaName • AI: ${doc.aiSourceName}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Location: ${doc.currentRelativePath} • Fingerprint: ${doc.sha256Hash.take(12)}…",
                        fontFamily = JetBrainsMonoFamily,
                        style = MaterialTheme.typography.labelSmall
                    )

                    if (predDoc != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        if (pendingSafetyAlerts > 0) {
                            Surface(
                                color = StatusAlertBg,
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "Safety Brake Held Move: $pendingSafetyAlerts omitted/changed requirement(s) found vs '${predDoc.originalFilename}'. Check Review Queue.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = StatusAlertText,
                                    modifier = Modifier.padding(8.dp)
                                )
                            }
                        } else {
                            Surface(
                                color = StatusVerifiedBg,
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "Verified Successor to '${predDoc.originalFilename}' (Predecessor in ${predDoc.currentRelativePath})",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = StatusVerifiedText,
                                    modifier = Modifier.padding(8.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (doc.lifecycleCategory != DocumentLifecycleCategory.CURRENT.name) {
                            Button(
                                onClick = { onMoveDocumentCategory(doc, DocumentLifecycleCategory.CURRENT) }
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Move Out", modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Move Out to Main Folder")
                            }
                        }
                        if (doc.lifecycleCategory != DocumentLifecycleCategory.SUPERSEDED.name) {
                            OutlinedButton(
                                onClick = { onMoveDocumentCategory(doc, DocumentLifecycleCategory.SUPERSEDED) }
                            ) {
                                Text("Move to Superseded/")
                            }
                        }
                        if (doc.lifecycleCategory != DocumentLifecycleCategory.REVIEW_FOR_DELETION.name) {
                            OutlinedButton(
                                onClick = { onMoveDocumentCategory(doc, DocumentLifecycleCategory.REVIEW_FOR_DELETION) }
                            ) {
                                Text("Move to Review_For_Deletion/")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TasksAndProgressSectionContent(
    brainItems: List<BrainItemEntity>,
    onAddTaskClick: () -> Unit,
    onUpdateTruthStatus: (String, TruthStatus, String) -> Unit
) {
    val tasksAndWork = remember(brainItems) {
        brainItems.filter {
            it.category == BrainCategory.TASK.name ||
                it.category == BrainCategory.NEXT_STEP.name ||
                it.category == BrainCategory.COMPLETED_WORK.name ||
                it.category == BrainCategory.PROBLEM_ATTEMPT.name
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
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
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Tasks, Problems & Verified Progress", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            text = "Separates AI claims of completion ('Unverified Source Claim') from work you have actually verified ('Verified Result').",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Button(onClick = onAddTaskClick) {
                        Icon(Icons.Default.Add, contentDescription = "Add", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add Task")
                    }
                }
            }
        }

        items(tasksAndWork, key = { it.brainItemId }) { item ->
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
                            text = "${item.category} • ${item.exactSourceFilename}",
                            fontFamily = JetBrainsMonoFamily,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(item.content, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (item.truthStatus != TruthStatus.VERIFIED.name) {
                            OutlinedButton(
                                onClick = {
                                    onUpdateTruthStatus(item.brainItemId, TruthStatus.VERIFIED, "Verified completed by user.")
                                }
                            ) {
                                Icon(Icons.Default.Verified, contentDescription = "Verify", modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Confirm & Mark Verified")
                            }
                        }
                        if (item.truthStatus == TruthStatus.SOURCE_CLAIM.name) {
                            OutlinedButton(
                                onClick = {
                                    onUpdateTruthStatus(item.brainItemId, TruthStatus.APPROVED, "Approved as active task.")
                                }
                            ) {
                                Text("Approve Task")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ChangeTrailSectionContent(
    changeTrail: List<ChangeTrailEntity>,
    onResolveAlert: (String, Boolean, String) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("change_trail_list"),
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
                    Text("Handover Scan & Change Trail", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        text = "Every new handover is scanned against your approved Project Brain. If an older rule was omitted or changed, Compass holds the automatic move until you review it here.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        items(changeTrail, key = { it.changeId }) { entry ->
            val isPending = entry.reviewStatus == ReviewStatus.PENDING_REVIEW.name
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
                        Surface(
                            color = if (isPending) StatusAlertBg else StatusVerifiedBg,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "${entry.changeType} • ${entry.reviewStatus}",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isPending) StatusAlertText else StatusVerifiedText,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                        Text(
                            text = "AI: ${entry.aiContributor}",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(entry.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("PREVIOUS APPROVED WORDING:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            Text(entry.previousWording, style = MaterialTheme.typography.bodyMedium)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("NEW HANDOVER SCAN RESULT:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                            Text(entry.newWording, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Source: ${entry.exactSourceFilename} (${entry.sectionLineRef})",
                        fontFamily = JetBrainsMonoFamily,
                        style = MaterialTheme.typography.labelSmall
                    )
                    Text(
                        text = "Scan Note: ${entry.documentedReason}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (isPending) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    onResolveAlert(entry.changeId, false, "Kept original Approved requirement.")
                                }
                            ) {
                                Icon(Icons.Default.Check, contentDescription = "Keep Original", modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Keep Original Rule & Retire Old Handover")
                            }
                            OutlinedButton(
                                onClick = {
                                    onResolveAlert(entry.changeId, true, "Approved change in Change Trail.")
                                }
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Accept Change", modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Accept Change")
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
fun DuplicatesAndCleanupSectionContent(
    documents: List<DocumentEntity>,
    journalOps: List<FileOperationJournalEntity>,
    onInspectDocument: (DocumentEntity) -> Unit,
    onUpdateDocCategory: (DocumentEntity, DocumentLifecycleCategory) -> Unit,
    onUndoJournalOp: (String) -> Unit
) {
    val supersededDocs = remember(documents) {
        documents.filter { it.lifecycleCategory == DocumentLifecycleCategory.SUPERSEDED.name }
    }
    val deletionReviewDocs = remember(documents) {
        documents.filter { it.lifecycleCategory == DocumentLifecycleCategory.REVIEW_FOR_DELETION.name }
    }
    val activeDocs = remember(documents) {
        documents.filter { it.lifecycleCategory == DocumentLifecycleCategory.CURRENT.name }
    }
    val undoableOps = remember(journalOps) {
        journalOps.filter { it.canUndo && it.state == "COMPLETED" }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("cleanup_and_undo_list"),
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
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Automatic Subfolder Sorting & 1-Tap Undo",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "• Clean Handover Succession: When a newer handover passes verification with no omitted rules, the older handover is automatically moved into Superseded/.\n• Safety Brake: If a new handover leaves out or changes an approved rule, Compass holds the move and alerts you first.\n• Easy Recovery: Pull any file back out to your main project folder in one tap below.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (undoableOps.isNotEmpty()) {
            item {
                Text(
                    text = "Recent Automatic & Manual Moves (${undoableOps.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = CompassRuby
                )
            }
            items(undoableOps, key = { "op_${it.opId}" }) { op ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = StatusApprovedBg),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = op.originalFilename,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = StatusApprovedText
                            )
                            Text(
                                text = "Moved from '${op.sourceRelativePath}' -> '${op.targetRelativePath}'",
                                style = MaterialTheme.typography.bodyMedium,
                                color = StatusApprovedText
                            )
                            Text(
                                text = "SHA-256 Verified: ${op.expectedSha256.take(16)}…",
                                fontFamily = JetBrainsMonoFamily,
                                style = MaterialTheme.typography.labelSmall,
                                color = StatusApprovedText
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = { onUndoJournalOp(op.opId) },
                            modifier = Modifier.testTag("undo_move_${op.originalFilename}")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo Move", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Pull Out")
                        }
                    }
                }
            }
        }

        item {
            Text(
                text = "Superseded/ Subfolder (${supersededDocs.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        if (supersededDocs.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "No files in Superseded/ right now.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }
        } else {
            items(supersededDocs, key = { "sup_${it.docId}" }) { doc ->
                SubfolderDocumentCard(
                    doc = doc,
                    subfolderBadge = "In Superseded/",
                    onInspect = { onInspectDocument(doc) },
                    onRestoreToCurrent = { onUpdateDocCategory(doc, DocumentLifecycleCategory.CURRENT) },
                    onMoveOther = { onUpdateDocCategory(doc, DocumentLifecycleCategory.REVIEW_FOR_DELETION) },
                    moveOtherLabel = "Move to Review_For_Deletion/"
                )
            }
        }

        item {
            Text(
                text = "Review_For_Deletion/ Subfolder (${deletionReviewDocs.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        if (deletionReviewDocs.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "No duplicate or flagged files in Review_For_Deletion/.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }
        } else {
            items(deletionReviewDocs, key = { "del_${it.docId}" }) { doc ->
                SubfolderDocumentCard(
                    doc = doc,
                    subfolderBadge = "In Review_For_Deletion/",
                    onInspect = { onInspectDocument(doc) },
                    onRestoreToCurrent = { onUpdateDocCategory(doc, DocumentLifecycleCategory.CURRENT) },
                    onMoveOther = { onUpdateDocCategory(doc, DocumentLifecycleCategory.SUPERSEDED) },
                    moveOtherLabel = "Move to Superseded/"
                )
            }
        }

        item {
            Text(
                text = "Active Main Folder Files (${activeDocs.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        items(activeDocs, key = { "act_${it.docId}" }) { doc ->
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onInspectDocument(doc) }
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(doc.originalFilename, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Path: ${doc.currentRelativePath}", fontFamily = JetBrainsMonoFamily, style = MaterialTheme.typography.labelSmall)
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { onUpdateDocCategory(doc, DocumentLifecycleCategory.SUPERSEDED) }) {
                            Text("Move to Superseded/")
                        }
                        OutlinedButton(onClick = { onUpdateDocCategory(doc, DocumentLifecycleCategory.REVIEW_FOR_DELETION) }) {
                            Text("Move to Review_For_Deletion/")
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SubfolderDocumentCard(
    doc: DocumentEntity,
    subfolderBadge: String,
    onInspect: () -> Unit,
    onRestoreToCurrent: () -> Unit,
    onMoveOther: () -> Unit,
    moveOtherLabel: String
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onInspect() }
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = doc.originalFilename,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Surface(
                    color = StatusProposalBg,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = subfolderBadge,
                        style = MaterialTheme.typography.labelSmall,
                        color = StatusProposalText,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Current Path: ${doc.currentRelativePath}",
                fontFamily = JetBrainsMonoFamily,
                style = MaterialTheme.typography.labelSmall
            )
            if (doc.previousRelativePathsJson.isNotBlank()) {
                Text(
                    text = "Previous Path: ${doc.previousRelativePathsJson}",
                    fontFamily = JetBrainsMonoFamily,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onRestoreToCurrent) {
                    Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Restore", modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Pull Back to Main Folder")
                }
                OutlinedButton(onClick = onMoveOther) {
                    Text(moveOtherLabel)
                }
            }
        }
    }
}
