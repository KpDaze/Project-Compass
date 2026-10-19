package com.example

import com.example.data.BrainCategory
import com.example.data.BrainItemEntity
import com.example.data.ChangeType
import com.example.data.TruthStatus
import com.example.data.WorkAreaEntity
import com.example.domain.OfflineDocumentEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ExampleUnitTest {

    @Test
    fun `sha256 fingerprint is deterministic and exact`() {
        val sample = "Project Compass Stage 1 Foundation".toByteArray(Charsets.UTF_8)
        val h1 = OfflineDocumentEngine.computeSha256(sample)
        val h2 = OfflineDocumentEngine.computeSha256(sample)
        assertEquals(64, h1.length)
        assertEquals(h1, h2)
    }

    @Test
    fun `extracted statements never auto-promote to APPROVED or VERIFIED and detect omissions`() {
        val areas = listOf(
            WorkAreaEntity("w1", "p1", "Development", "Dev", 0)
        )
        val existingApproved = listOf(
            BrainItemEntity(
                brainItemId = "b_approved_1",
                projectId = "p1",
                workAreaId = "w1",
                category = BrainCategory.REQUIREMENT.name,
                title = "Must keep original filenames untouched",
                content = "Must keep original filenames untouched after FileMate sorting.",
                truthStatus = TruthStatus.APPROVED.name,
                exactSourceFilename = "handover_v1.md",
                sectionLineReference = "Line 10"
            )
        )

        val handoverV2 = """
            Source AI: Claude
            ## Requirements
            - Support offline SQLite storage with Room database.
            ## Completed Work
            - Implemented and tested the entire cloud sync pipeline.
        """.trimIndent()

        val result = OfflineDocumentEngine.analyzeDocumentContent(
            projectId = "p1",
            docId = "d2",
            snapshotId = "s2",
            filename = "handover_v2.md",
            plainText = handoverV2,
            workAreas = areas,
            existingApprovedBrain = existingApproved
        )

        assertEquals("Claude", result.detectedAiSource)
        // Rule §6 & §10: Claims of completed/tested work remain SOURCE_CLAIM, never VERIFIED
        assertTrue(result.proposedBrainItems.none { it.truthStatus == TruthStatus.VERIFIED.name })
        assertTrue(result.proposedBrainItems.none { it.truthStatus == TruthStatus.APPROVED.name })

        // Rule §10: Omission in newer handover triggers OMITTED_IN_HANDOVER alert, not cancellation
        assertTrue(result.changeTrailAlerts.any { it.changeType == ChangeType.OMITTED_IN_HANDOVER.name })
    }

    @Test
    fun `offline docx and html readers extract clean text`() {
        val html = "<h1>Purpose</h1><p>Offline Project Brain &amp; Trail</p>"
        val extractedHtml = OfflineDocumentEngine.extractTextOffline("spec.html", html.toByteArray())
        assertTrue(extractedHtml.contains("# Purpose"))
        assertTrue(extractedHtml.contains("Offline Project Brain & Trail"))

        // Build a minimal valid .docx zip in memory
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            zos.putNextEntry(ZipEntry("word/document.xml"))
            val xml = "<w:document><w:body><w:p><w:r><w:t>Hello from Word Handover</w:t></w:r></w:p></w:body></w:document>"
            zos.write(xml.toByteArray(Charsets.UTF_8))
            zos.closeEntry()
        }
        val docxText = OfflineDocumentEngine.extractTextOffline("brief.docx", baos.toByteArray())
        assertEquals("Hello from Word Handover", docxText)
        assertFalse(OfflineDocumentEngine.canExtractTextOffline("diagram.png"))
    }
}
