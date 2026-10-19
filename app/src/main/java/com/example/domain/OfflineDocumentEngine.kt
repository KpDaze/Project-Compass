package com.example.domain

import com.example.data.BrainCategory
import com.example.data.BrainItemEntity
import com.example.data.ChangeTrailEntity
import com.example.data.ChangeType
import com.example.data.DocumentEntity
import com.example.data.EvidenceLinkEntity
import com.example.data.ProjectEntity
import com.example.data.ReviewStatus
import com.example.data.TruthStatus
import com.example.data.WorkAreaEntity
import java.io.ByteArrayInputStream
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipInputStream

data class ExtractedDocumentResult(
    val plainText: String,
    val lineCount: Int,
    val detectedAiSource: String,
    val detectedConversationUrl: String,
    val isHandoverCandidate: Boolean,
    val proposedBrainItems: List<BrainItemEntity>,
    val evidenceLinks: List<EvidenceLinkEntity>,
    val changeTrailAlerts: List<ChangeTrailEntity>
)

object OfflineDocumentEngine {

    fun computeSha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(bytes)
        return hash.joinToString("") { "%02x".format(it) }
    }

    fun detectFormatLabel(filename: String): String {
        val lower = filename.lowercase()
        return when {
            lower.endsWith(".md") || lower.endsWith(".markdown") -> "Markdown (.md)"
            lower.endsWith(".txt") -> "Plain Text (.txt)"
            lower.endsWith(".html") || lower.endsWith(".htm") -> "Web HTML (.html)"
            lower.endsWith(".docx") -> "Word Document (.docx)"
            lower.endsWith(".json") -> "Structured JSON (.json)"
            lower.endsWith(".csv") -> "Spreadsheet CSV (.csv)"
            lower.endsWith(".pdf") -> "PDF Document (.pdf)"
            lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".webp") -> "Image Asset"
            else -> "Project File"
        }
    }

    fun canExtractTextOffline(filename: String): Boolean {
        val lower = filename.lowercase()
        return lower.endsWith(".md") ||
            lower.endsWith(".markdown") ||
            lower.endsWith(".txt") ||
            lower.endsWith(".html") ||
            lower.endsWith(".htm") ||
            lower.endsWith(".docx") ||
            lower.endsWith(".json") ||
            lower.endsWith(".csv")
    }

    fun extractTextOffline(filename: String, bytes: ByteArray): String {
        val lower = filename.lowercase()
        return when {
            lower.endsWith(".docx") -> extractDocxXmlText(bytes)
            lower.endsWith(".html") || lower.endsWith(".htm") -> extractHtmlText(bytes.toString(Charsets.UTF_8))
            canExtractTextOffline(filename) -> bytes.toString(Charsets.UTF_8)
            else -> ""
        }
    }

    private fun extractDocxXmlText(bytes: ByteArray): String {
        return try {
            val zip = ZipInputStream(ByteArrayInputStream(bytes))
            var entry = zip.nextEntry
            var documentXml = ""
            while (entry != null) {
                if (entry.name == "word/document.xml") {
                    documentXml = zip.readBytes().toString(Charsets.UTF_8)
                    break
                }
                entry = zip.nextEntry
            }
            if (documentXml.isEmpty()) return ""
            // Split paragraphs on </w:p> and extract all <w:t ...>text</w:t> runs
            val paragraphs = documentXml.split("</w:p>")
            val textRegex = Regex("<w:t(?:\\s[^>]*)?>([^<]*)</w:t>")
            paragraphs.mapNotNull { para ->
                val line = textRegex.findAll(para).joinToString("") { it.groupValues[1] }.trim()
                line.ifEmpty { null }
            }.joinToString("\n")
        } catch (_: Exception) {
            ""
        }
    }

    private fun extractHtmlText(rawHtml: String): String {
        val withoutScripts = rawHtml
            .replace(Regex("(?is)<script.*?>.*?</script>"), "")
            .replace(Regex("(?is)<style.*?>.*?</style>"), "")
        val withBreaks = withoutScripts
            .replace(Regex("(?i)<h1[^>]*>"), "\n# ")
            .replace(Regex("(?i)<h2[^>]*>"), "\n## ")
            .replace(Regex("(?i)<h3[^>]*>"), "\n### ")
            .replace(Regex("(?i)<li[^>]*>"), "\n- ")
            .replace(Regex("(?i)</(p|div|h1|h2|h3|h4|li|tr)>"), "\n")
            .replace(Regex("(?i)<br\\s*/?>"), "\n")
        val stripped = withBreaks.replace(Regex("<[^>]+>"), "")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
        return stripped.lines().map { it.trimEnd() }.filterIndexed { idx, line ->
            line.isNotBlank() || idx == 0
        }.joinToString("\n")
    }

    /**
     * Parses structured handover/project text without inventing AI attribution or promoting
     * claims to verified facts. Also checks against existing approved Project Brain items
     * to detect omissions or wording updates.
     */
    fun analyzeDocumentContent(
        projectId: String,
        docId: String,
        snapshotId: String,
        filename: String,
        plainText: String,
        workAreas: List<WorkAreaEntity>,
        existingApprovedBrain: List<BrainItemEntity>,
        predecessorDocId: String? = null
    ): ExtractedDocumentResult {
        val lines = plainText.lines()
        var detectedAi = "Not Stated"
        var detectedUrl = ""
        var currentSectionHeading = "Document Header"
        var currentCategory = BrainCategory.REQUIREMENT

        // Only record AI attribution if explicitly stated in the document or FileMate filename prefix
        val lowerName = filename.lowercase()
        when {
            lowerName.contains("claude") -> detectedAi = "Claude"
            lowerName.contains("chatgpt") || lowerName.contains("gpt") -> detectedAi = "ChatGPT"
            lowerName.contains("gemini") -> detectedAi = "Gemini"
            lowerName.contains("perplexity") -> detectedAi = "Perplexity"
        }

        val proposedItems = mutableListOf<BrainItemEntity>()
        val evidenceLinks = mutableListOf<EvidenceLinkEntity>()
        val changeTrailAlerts = mutableListOf<ChangeTrailEntity>()

        val headingRegex = Regex("^(#{1,4})\\s+(.+)$")
        val bulletRegex = Regex("^(\\s*[-*•]|\\s*\\d+[.)])\\s+(.+)$")
        val aiHeaderRegex = Regex("(?i)^(?:source\\s*ai|ai\\s*source|assistant|model|author)\\s*:\\s*(.+)$")
        val urlHeaderRegex = Regex("(?i)^(?:conversation|chat\\s*link|source\\s*url|url)\\s*:\\s*(https?://\\S+)$")

        lines.forEachIndexed { index, rawLine ->
            val lineNum = index + 1
            val trimmed = rawLine.trim()
            if (trimmed.isEmpty()) return@forEachIndexed

            aiHeaderRegex.find(trimmed)?.let { match ->
                val value = match.groupValues[1].trim()
                if (value.isNotEmpty()) detectedAi = value
                return@forEachIndexed
            }
            urlHeaderRegex.find(trimmed)?.let { match ->
                detectedUrl = match.groupValues[1].trim()
                return@forEachIndexed
            }

            val headingMatch = headingRegex.find(trimmed)
            if (headingMatch != null) {
                currentSectionHeading = headingMatch.groupValues[2].trim()
                val hLower = currentSectionHeading.lowercase()
                currentCategory = when {
                    hLower.contains("purpose") || hLower.contains("vision") || hLower.contains("overview") -> BrainCategory.PURPOSE
                    hLower.contains("requirement") || hLower.contains("rule") || hLower.contains("scope") -> BrainCategory.REQUIREMENT
                    hLower.contains("decision") || hLower.contains("architecture") || hLower.contains("choice") -> BrainCategory.DECISION
                    hLower.contains("completed") || hLower.contains("verified") || hLower.contains("done") || hLower.contains("progress") -> BrainCategory.COMPLETED_WORK
                    hLower.contains("task") || hLower.contains("todo") || hLower.contains("outstanding") -> BrainCategory.TASK
                    hLower.contains("problem") || hLower.contains("issue") || hLower.contains("risk") || hLower.contains("attempt") -> BrainCategory.PROBLEM_ATTEMPT
                    hLower.contains("resource") || hLower.contains("link") || hLower.contains("reference") -> BrainCategory.RESOURCE
                    hLower.contains("next step") || hLower.contains("resume") -> BrainCategory.NEXT_STEP
                    else -> BrainCategory.REQUIREMENT
                }
                return@forEachIndexed
            }

            val bulletMatch = bulletRegex.find(trimmed)
            val statementText = bulletMatch?.groupValues?.get(2)?.trim()
                ?: if (currentCategory == BrainCategory.PURPOSE && trimmed.length > 25 && !trimmed.startsWith("#")) trimmed else null

            if (!statementText.isNullOrBlank() && statementText.length >= 8) {
                val cleanText = statementText
                    .removePrefix("[ ]").removePrefix("[x]").removePrefix("[X]")
                    .replace("**", "")
                    .replace(Regex("^\\[[A-Z_\\s—-]+]\\s*"), "")
                    .trim()

                // Critical Handover Rule §6 & §10:
                // Text found in a document is NEVER automatically Approved or Verified.
                // Claims of completion remain SOURCE_CLAIM; proposed requirements/decisions enter as PROPOSAL.
                val initialTruthStatus = when (currentCategory) {
                    BrainCategory.COMPLETED_WORK -> TruthStatus.SOURCE_CLAIM
                    BrainCategory.REQUIREMENT, BrainCategory.DECISION, BrainCategory.PURPOSE -> TruthStatus.PROPOSAL
                    else -> TruthStatus.SOURCE_CLAIM
                }

                val matchedWorkAreaId = inferWorkAreaId(cleanText, currentSectionHeading, workAreas)
                val itemId = UUID.randomUUID().toString()
                val lineRef = "$currentSectionHeading (Line $lineNum)"

                val brainItem = BrainItemEntity(
                    brainItemId = itemId,
                    projectId = projectId,
                    workAreaId = matchedWorkAreaId,
                    category = currentCategory.name,
                    title = cleanText.take(64) + if (cleanText.length > 64) "…" else "",
                    content = cleanText,
                    reasonOrContext = "Extracted from $filename — $lineRef",
                    truthStatus = initialTruthStatus.name,
                    aiContributor = detectedAi,
                    sourceDocId = docId,
                    exactSourceFilename = filename,
                    sectionLineReference = lineRef,
                    exactQuote = trimmed
                )
                proposedItems.add(brainItem)

                evidenceLinks.add(
                    EvidenceLinkEntity(
                        evidenceId = UUID.randomUUID().toString(),
                        brainItemId = itemId,
                        docId = docId,
                        snapshotId = snapshotId,
                        exactFilenameAtTime = filename,
                        sectionHeading = currentSectionHeading,
                        lineRange = "Line $lineNum",
                        exactQuote = trimmed
                    )
                )
            }
        }

        val isHandover = lowerName.contains("handover") ||
            lowerName.contains("brief") ||
            lowerName.contains("spec") ||
            proposedItems.size >= 3

        // Compare against existing Approved/Verified Project Brain items for Omissions & Contradictions (§10 & §11)
        if (isHandover && existingApprovedBrain.isNotEmpty()) {
            val approvedReqs = existingApprovedBrain.filter {
                (it.category == BrainCategory.REQUIREMENT.name || it.category == BrainCategory.DECISION.name) &&
                    (it.truthStatus == TruthStatus.APPROVED.name || it.truthStatus == TruthStatus.VERIFIED.name)
            }
            for (approved in approvedReqs) {
                val approvedKeywords = extractKeywords(approved.content)
                if (approvedKeywords.isEmpty()) continue

                val bestMatch = proposedItems.maxByOrNull { candidate ->
                    keywordOverlapRatio(approvedKeywords, extractKeywords(candidate.content))
                }
                val bestScore = bestMatch?.let {
                    keywordOverlapRatio(approvedKeywords, extractKeywords(it.content))
                } ?: 0.0

                if (bestScore < 0.30) {
                    // Omission detected! Omission is NOT cancellation (§10)
                    changeTrailAlerts.add(
                        ChangeTrailEntity(
                            changeId = UUID.randomUUID().toString(),
                            projectId = projectId,
                            workAreaId = approved.workAreaId,
                            brainItemId = approved.brainItemId,
                            changeType = ChangeType.OMITTED_IN_HANDOVER.name,
                            title = "Approved Requirement Omitted in $filename",
                            previousWording = approved.content,
                            newWording = "[Omitted in $filename — Retained as Approved unless you retire it]",
                            documentedReason = "Omission in a newer handover does not cancel prior approval.",
                            sourceDocId = docId,
                            predecessorDocId = predecessorDocId ?: approved.sourceDocId,
                            exactSourceFilename = filename,
                            sectionLineRef = "Compared against ${approved.exactSourceFilename} (${approved.sectionLineReference})",
                            aiContributor = detectedAi,
                            reviewStatus = ReviewStatus.PENDING_REVIEW.name
                        )
                    )
                } else if (bestMatch != null && bestScore in 0.35..0.92 &&
                    !bestMatch.content.equals(approved.content, ignoreCase = true)
                ) {
                    // Wording changed or potential contradiction!
                    val isReversal = hasNegationFlip(approved.content, bestMatch.content)
                    changeTrailAlerts.add(
                        ChangeTrailEntity(
                            changeId = UUID.randomUUID().toString(),
                            projectId = projectId,
                            workAreaId = approved.workAreaId,
                            brainItemId = approved.brainItemId,
                            changeType = if (isReversal) ChangeType.CONTRADICTION_DETECTED.name else ChangeType.WORDING_CHANGED.name,
                            title = if (isReversal) "Potential Conflict with Approved Rule" else "Updated Wording Proposed",
                            previousWording = approved.content,
                            newWording = bestMatch.content,
                            documentedReason = "Detected difference between approved Brain record (${approved.exactSourceFilename}) and new handover ($filename).",
                            sourceDocId = docId,
                            predecessorDocId = predecessorDocId ?: approved.sourceDocId,
                            exactSourceFilename = filename,
                            sectionLineRef = bestMatch.sectionLineReference,
                            aiContributor = detectedAi,
                            reviewStatus = ReviewStatus.PENDING_REVIEW.name
                        )
                    )
                }
            }
        }

        return ExtractedDocumentResult(
            plainText = plainText,
            lineCount = lines.size,
            detectedAiSource = detectedAi,
            detectedConversationUrl = detectedUrl,
            isHandoverCandidate = isHandover,
            proposedBrainItems = proposedItems,
            evidenceLinks = evidenceLinks,
            changeTrailAlerts = changeTrailAlerts
        )
    }

    /**
     * Suggests an existing project for an Unassigned Inbox item based on context keywords (§8).
     * Approximate matching is always presented as a suggestion, never certainty.
     */
    fun suggestProjectForContent(
        filename: String,
        content: String,
        projects: List<ProjectEntity>
    ): Pair<ProjectEntity?, String> {
        if (projects.isEmpty()) return null to "No existing projects yet"
        val combinedTokens = extractKeywords("$filename $content")
        var bestProject: ProjectEntity? = null
        var bestScore = 0.0

        for (project in projects.filter { it.status != "ARCHIVED" }) {
            val projectTokens = extractKeywords("${project.name} ${project.purpose} ${project.resumePointSummary}")
            val overlap = keywordOverlapRatio(projectTokens, combinedTokens)
            if (overlap > bestScore) {
                bestScore = overlap
                bestProject = project
            }
        }
        return if (bestProject != null && bestScore >= 0.12) {
            bestProject to "Suggested match (${(bestScore * 100).toInt()}% keyword overlap with '${bestProject.name}' — requires your approval)"
        } else {
            null to "No strong project match found — kept safely in Unassigned Inbox"
        }
    }

    fun computeLineDiffSummary(oldText: String, newText: String): Pair<String, String> {
        val oldLines = oldText.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val newLines = newText.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val removed = oldLines.filterNot { it in newLines }.take(4).joinToString("\n• ", prefix = "• ")
        val added = newLines.filterNot { it in oldLines }.take(4).joinToString("\n• ", prefix = "• ")
        return (if (removed == "• ") "No lines removed" else removed) to
            (if (added == "• ") "No new lines added" else added)
    }

    private fun inferWorkAreaId(
        statement: String,
        sectionHeading: String,
        workAreas: List<WorkAreaEntity>
    ): String? {
        if (workAreas.isEmpty()) return null
        val text = "$sectionHeading $statement".lowercase()
        val matchName = when {
            text.contains("ui") || text.contains("screen") || text.contains("design") || text.contains("layout") -> "Design"
            text.contains("test") || text.contains("verify") || text.contains("robolectric") || text.contains("bug") -> "Testing"
            text.contains("research") || text.contains("investigate") || text.contains("compare") -> "Research"
            text.contains("handover") || text.contains("doc") || text.contains("export") || text.contains("guide") -> "Documentation"
            else -> "Development"
        }
        return workAreas.firstOrNull { it.name.equals(matchName, ignoreCase = true) }?.workAreaId
            ?: workAreas.firstOrNull()?.workAreaId
    }

    private val stopWords = setOf(
        "the", "and", "for", "with", "that", "this", "from", "into", "must", "should",
        "will", "have", "has", "not", "are", "was", "were", "can", "all", "any"
    )

    private fun extractKeywords(text: String): Set<String> {
        return text.lowercase()
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .split(Regex("\\s+"))
            .filter { it.length >= 4 && it !in stopWords }
            .toSet()
    }

    private fun keywordOverlapRatio(target: Set<String>, candidate: Set<String>): Double {
        if (target.isEmpty() || candidate.isEmpty()) return 0.0
        val intersection = target.intersect(candidate).size
        return intersection.toDouble() / target.size.toDouble()
    }

    private fun hasNegationFlip(oldText: String, newText: String): Boolean {
        val negations = listOf("not", "never", "no ", "without", "prohibited", "forbidden")
        val oldHasNeg = negations.any { oldText.lowercase().contains(it) }
        val newHasNeg = negations.any { newText.lowercase().contains(it) }
        return oldHasNeg != newHasNeg
    }
}
