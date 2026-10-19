package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.CompassDatabase
import com.example.data.CompassRepository
import com.example.data.DocumentLifecycleCategory
import com.example.data.ReviewStatus
import com.example.domain.HandoverAndBackupEngine
import com.example.domain.SafeFileMover
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    private lateinit var context: Context
    private lateinit var db: CompassDatabase
    private lateinit var repository: CompassRepository

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, CompassDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = CompassRepository(db.compassDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `automatic handover verification, safety brake, duplicate subfolder move, and 1-tap undo`() = runBlocking {
        val project = repository.importDemoPopulatedProjectReadOnly(context)
        assertEquals("Botanical Field Journal", project.name)

        val demoDir = File(context.filesDir, "demo_filemate_sorted_folder")

        // 1. Verify Claude v1 was automatically moved into Superseded/ when Gemini v1.1 passed verification
        val v1InRoot = File(demoDir, "2026-10-10_Claude_Botanical_Handover_v1.md")
        val v1InSuperseded = File(demoDir, "${SafeFileMover.SUBFOLDER_SUPERSEDED}/2026-10-10_Claude_Botanical_Handover_v1.md")
        assertFalse(v1InRoot.exists())
        assertTrue(v1InSuperseded.exists())

        // 2. Verify Gemini v1.1 was held in the main folder by the Safety Brake because ChatGPT v2 omitted a requirement
        val v1_1InRoot = File(demoDir, "2026-10-12_Gemini_Botanical_Handover_v1_1.md")
        assertTrue(v1_1InRoot.exists())

        // 3. Verify the exact duplicate research file was automatically moved into Review_For_Deletion/
        val dupInDeletion = File(demoDir, "${SafeFileMover.SUBFOLDER_DELETION_REVIEW}/2026-10-15_Gemini_Herbarium_Research_Copy.txt")
        assertTrue(dupInDeletion.exists())

        // 4. Test 1-tap Undo (Pull Back Out): restore the duplicate from Review_For_Deletion/ back to the main folder
        val journalOps = db.compassDao().getAllJournalOperationsSnapshot()
        val dupOp = journalOps.first { it.originalFilename == "2026-10-15_Gemini_Herbarium_Research_Copy.txt" && it.canUndo }
        repository.undoJournalMove(context, dupOp.opId)

        val dupRestoredInRoot = File(demoDir, "2026-10-15_Gemini_Herbarium_Research_Copy.txt")
        assertTrue(dupRestoredInRoot.exists())
        assertFalse(dupInDeletion.exists())

        // 5. Resolve the Safety Brake alert for ChatGPT v2 and verify Gemini v1.1 is then automatically moved into Superseded/
        val pendingAlerts = db.compassDao().getChangeTrailForProject(project.projectId)
            .filter { it.reviewStatus == ReviewStatus.PENDING_REVIEW.name }
        assertTrue(pendingAlerts.isNotEmpty())
        pendingAlerts.forEach { alert ->
            repository.resolveChangeTrailAlert(context, alert.changeId, approveChange = false, userNote = "Keep original rule")
        }

        val v1_1InSuperseded = File(demoDir, "${SafeFileMover.SUBFOLDER_SUPERSEDED}/2026-10-12_Gemini_Botanical_Handover_v1_1.md")
        assertTrue(v1_1InSuperseded.exists())
        val v1_1DocAfter = db.compassDao().getDocumentsForProject(project.projectId)
            .first { it.originalFilename == "2026-10-12_Gemini_Botanical_Handover_v1_1.md" }
        assertEquals(DocumentLifecycleCategory.SUPERSEDED.name, v1_1DocAfter.lifecycleCategory)

        // 6. Verify Markdown Handover export & Backup checksum integrity
        val areas = db.compassDao().getWorkAreasForProject(project.projectId)
        val docs = db.compassDao().getDocumentsForProject(project.projectId)
        val brain = db.compassDao().getBrainItemsForProject(project.projectId)
        val trail = db.compassDao().getChangeTrailForProject(project.projectId)
        val markdown = HandoverAndBackupEngine.generateMarkdownHandover(
            project = project,
            workAreas = areas,
            selectedWorkAreaId = null,
            documents = docs,
            brainItems = brain,
            changeTrail = trail
        )
        assertTrue(markdown.contains("PROJECT COMPASS HANDOVER: BOTANICAL FIELD JOURNAL"))

        // 7. Stage 4: Verify Quick Resume Brief & Multi-AI Comparison Summary
        val quickResume = HandoverAndBackupEngine.generateQuickResumePrompt(project, brain, trail)
        assertTrue(quickResume.contains("QUICK RESUME BRIEF — Botanical Field Journal"))
        assertTrue(quickResume.contains("Non-Negotiable Approved Rules"))

        val aiSummaries = HandoverAndBackupEngine.buildMultiAiContributionsSummary(docs, brain, trail)
        assertTrue(aiSummaries.any { it.aiName.contains("Claude") })
        assertTrue(aiSummaries.any { it.aiName.contains("Gemini") })
        assertTrue(aiSummaries.any { it.aiName.contains("ChatGPT") })

        // 8. Stage 4: Save generated Master Handover directly into the project folder and verify ChatGPT v2 is auto-moved to Superseded/
        val saveMsg = repository.saveHandoverMarkdownToProjectFolder(context, project.projectId, markdown)
        assertTrue(saveMsg.contains("Compass_Master_Handover.md"))
        val v2InSuperseded = File(demoDir, "${SafeFileMover.SUBFOLDER_SUPERSEDED}/2026-10-14_ChatGPT_Botanical_Handover_v2.md")
        assertTrue(v2InSuperseded.exists())

        val backupJson = repository.generateBackupJson(includePhysicalFiles = false)
        val preview = HandoverAndBackupEngine.inspectAndPreviewBackup(backupJson)
        assertTrue(preview.isValidChecksum)
        assertEquals(1, preview.projectsCount)
    }
}
