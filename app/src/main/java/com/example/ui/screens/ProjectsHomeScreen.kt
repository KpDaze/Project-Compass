package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Unarchive
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.BrainItemEntity
import com.example.data.ChangeTrailEntity
import com.example.data.DocumentEntity
import com.example.data.ProjectEntity
import com.example.data.ProjectStatus
import com.example.data.ProjectType
import com.example.data.ReviewStatus
import com.example.data.TruthStatus
import com.example.ui.components.CreateOrEditProjectDialog
import com.example.ui.theme.CompassPink
import com.example.ui.theme.CompassRuby
import com.example.ui.theme.StatusApprovedBg
import com.example.ui.theme.StatusApprovedText
import com.example.ui.theme.StatusProposalBg
import com.example.ui.theme.StatusProposalText

@Composable
fun ProjectCompassLogoEmblem(
    modifier: Modifier = Modifier,
    size: Dp = 84.dp
) {
    Canvas(modifier = modifier.size(size)) {
        val cx = this.size.width / 2f
        val cy = this.size.height / 2f
        val rOuterRing = this.size.minDimension * 0.36f
        val rCardinalTip = this.size.minDimension * 0.48f
        val rCardinalBase = this.size.minDimension * 0.11f
        val rDiagTip = this.size.minDimension * 0.30f
        val rDiagBase = this.size.minDimension * 0.08f
        val rInnerMask = this.size.minDimension * 0.175f
        val rInnerPinkRing = this.size.minDimension * 0.14f

        // 1. Outer Deep Ruby Ring (#9E0B37)
        drawCircle(
            color = CompassRuby,
            radius = rOuterRing,
            center = Offset(cx, cy),
            style = Stroke(width = this.size.minDimension * 0.045f)
        )

        // 2. 4 Diagonal Rose Pink Star Points (#FF4785)
        val diagOffsets = listOf(
            Pair(-0.707f, -0.707f),
            Pair(0.707f, -0.707f),
            Pair(0.707f, 0.707f),
            Pair(-0.707f, 0.707f)
        )
        diagOffsets.forEach { (dx, dy) ->
            val px = -dy
            val py = dx
            val path = Path().apply {
                moveTo(cx + dx * rDiagTip, cy + dy * rDiagTip)
                lineTo(cx + px * rDiagBase, cy + py * rDiagBase)
                lineTo(cx - px * rDiagBase, cy - py * rDiagBase)
                close()
            }
            drawPath(path = path, color = CompassPink)
        }

        // 3. 4 Main Cardinal Deep Ruby Star Points (#9E0B37)
        val cardinalOffsets = listOf(
            Pair(0f, -1f), // North
            Pair(1f, 0f),  // East
            Pair(0f, 1f),  // South
            Pair(-1f, 0f)  // West
        )
        cardinalOffsets.forEach { (dx, dy) ->
            val px = -dy
            val py = dx
            val path = Path().apply {
                moveTo(cx + dx * rCardinalTip, cy + dy * rCardinalTip)
                lineTo(cx + px * rCardinalBase, cy + py * rCardinalBase)
                lineTo(cx - px * rCardinalBase, cy - py * rCardinalBase)
                close()
            }
            drawPath(path = path, color = CompassRuby)
        }

        // 4. Center White Disc Mask + Inner Rose Pink Ring
        drawCircle(
            color = Color.White,
            radius = rInnerMask,
            center = Offset(cx, cy)
        )
        drawCircle(
            color = CompassPink,
            radius = rInnerPinkRing,
            center = Offset(cx, cy),
            style = Stroke(width = this.size.minDimension * 0.028f)
        )

        // 5. Center Upward Rose Pink Navigation Arrow (#FF4785)
        val arrowPath = Path().apply {
            moveTo(cx, cy - rInnerPinkRing * 0.62f)
            lineTo(cx + rInnerPinkRing * 0.48f, cy + rInnerPinkRing * 0.52f)
            lineTo(cx, cy + rInnerPinkRing * 0.22f)
            lineTo(cx - rInnerPinkRing * 0.48f, cy + rInnerPinkRing * 0.52f)
            close()
        }
        drawPath(path = arrowPath, color = CompassPink)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProjectsHomeScreen(
    projects: List<ProjectEntity>,
    documents: List<DocumentEntity>,
    brainItems: List<BrainItemEntity>,
    changeTrail: List<ChangeTrailEntity>,
    onOpenProject: (String) -> Unit,
    onCreateProject: (String, String, ProjectType, String) -> Unit,
    onUpdateProjectStatus: (ProjectEntity, ProjectStatus) -> Unit,
    onImportDemoProject: () -> Unit
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var showArchived by remember { mutableStateOf(false) }

    val visibleProjects = remember(projects, showArchived) {
        if (showArchived) {
            projects.filter { it.status == ProjectStatus.ARCHIVED.name }
        } else {
            projects.filter { it.status != ProjectStatus.ARCHIVED.name }
        }
    }

    val pendingReviewCount = remember(brainItems, changeTrail) {
        brainItems.count { it.truthStatus == TruthStatus.PROPOSAL.name || it.truthStatus == TruthStatus.SOURCE_CLAIM.name } +
            changeTrail.count { it.reviewStatus == ReviewStatus.PENDING_REVIEW.name }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("projects_home_list"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Official Project Compass Brand Header ("Big ideas. Less chaos.")
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    // Subtle tucked-away "+" icon in top-right corner for manual fallback creation
                    IconButton(
                        onClick = { showCreateDialog = true },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .testTag("new_project_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Create folder manually",
                            tint = CompassRuby
                        )
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        ProjectCompassLogoEmblem(size = 80.dp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "P R O J E C T",
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 4.sp
                            ),
                            color = CompassRuby,
                            textAlign = TextAlign.Center
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(vertical = 2.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(32.dp)
                                    .height(1.5.dp)
                                    .background(CompassPink)
                            )
                            Text(
                                text = "  C O M P A S S  ",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 3.sp
                                ),
                                color = CompassPink
                            )
                            Box(
                                modifier = Modifier
                                    .width(32.dp)
                                    .height(1.5.dp)
                                    .background(CompassPink)
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Big ideas. Less chaos.",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = CompassRuby,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }

        // Active / Archived Filters
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !showArchived,
                        onClick = { showArchived = false },
                        label = { Text("Active (${projects.count { it.status != ProjectStatus.ARCHIVED.name }})") }
                    )
                    FilterChip(
                        selected = showArchived,
                        onClick = { showArchived = true },
                        label = { Text("Archived (${projects.count { it.status == ProjectStatus.ARCHIVED.name }})") }
                    )
                }
                if (pendingReviewCount > 0) {
                    Surface(
                        color = StatusProposalBg,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "$pendingReviewCount to review",
                            style = MaterialTheme.typography.labelLarge,
                            color = StatusProposalText,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }
            }
        }

        // Empty State: prompt to load demo or wait for FileMate
        if (visibleProjects.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.size(54.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Folder,
                                    contentDescription = "Project Compass Folder",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = if (showArchived) "No Archived Projects" else "Watching Your Project Compass Folder",
                            style = MaterialTheme.typography.titleLarge,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "As FileMate moves your AI downloads into your Project Compass folder, each project will appear here automatically.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = onImportDemoProject,
                            modifier = Modifier.testTag("import_demo_folder_button")
                        ) {
                            Icon(Icons.Default.Folder, contentDescription = "Sample", modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Preview Sample Project Folder")
                        }
                    }
                }
            }
        } else {
            // Clean, Brand-Coordinated Project Cards
            items(visibleProjects, key = { it.projectId }) { project ->
                val projDocs = documents.filter { it.projectId == project.projectId }
                val projBrain = brainItems.filter { it.projectId == project.projectId }
                val projChanges = changeTrail.filter {
                    it.projectId == project.projectId && it.reviewStatus == ReviewStatus.PENDING_REVIEW.name
                }
                val approvedCount = projBrain.count {
                    it.truthStatus == TruthStatus.APPROVED.name || it.truthStatus == TruthStatus.VERIFIED.name
                }
                val unverifiedCount = projBrain.count {
                    it.truthStatus == TruthStatus.PROPOSAL.name || it.truthStatus == TruthStatus.SOURCE_CLAIM.name
                }

                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenProject(project.projectId) }
                        .testTag("project_card_${project.name}")
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.Folder,
                                            contentDescription = "Folder",
                                            tint = CompassRuby,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = project.name,
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (project.linkedFolderDisplayName != null) {
                                        Text(
                                            text = project.linkedFolderDisplayName,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            Surface(
                                color = StatusApprovedBg,
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = project.status,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = StatusApprovedText,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        if (project.purpose.isNotBlank() && project.purpose != "Project managed with FileMate and Project Compass.") {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = project.purpose,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = "${projDocs.size} files",
                                    style = MaterialTheme.typography.labelLarge,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                            if (approvedCount > 0) {
                                Surface(
                                    color = StatusApprovedBg,
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = "$approvedCount approved",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = StatusApprovedText,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                            if (unverifiedCount + projChanges.size > 0) {
                                Surface(
                                    color = StatusProposalBg,
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = "${unverifiedCount + projChanges.size} to review",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = StatusProposalText,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = {
                                    val targetStatus = if (project.status == ProjectStatus.ARCHIVED.name) {
                                        ProjectStatus.ACTIVE
                                    } else {
                                        ProjectStatus.ARCHIVED
                                    }
                                    onUpdateProjectStatus(project, targetStatus)
                                }
                            ) {
                                Icon(
                                    imageVector = if (project.status == ProjectStatus.ARCHIVED.name) {
                                        Icons.Default.Unarchive
                                    } else {
                                        Icons.Default.Archive
                                    },
                                    contentDescription = "Archive or Restore",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (project.status == ProjectStatus.ARCHIVED.name) "Restore" else "Archive")
                            }

                            Button(
                                onClick = { onOpenProject(project.projectId) },
                                modifier = Modifier.testTag("open_project_button_${project.name}")
                            ) {
                                Text("Open Project")
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = "Open",
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateOrEditProjectDialog(
            initialProject = null,
            onDismiss = { showCreateDialog = false },
            onConfirm = { name, purpose, type, _, resumePoint, _ ->
                onCreateProject(name, purpose, type, resumePoint)
                showCreateDialog = false
            }
        )
    }
}
