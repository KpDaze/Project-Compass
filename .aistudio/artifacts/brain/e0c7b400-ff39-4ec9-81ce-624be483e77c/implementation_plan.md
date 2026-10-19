# Project Compass — Technical Design & Implementation Proposal

Project Compass is an offline-first, native Android project headquarters and evidence-backed **Project Brain** that turns folders of AI handovers, notes, and project documents into verified, traceable project knowledge—without ever silently overwriting approvals, renaming sorted files, or requiring cloud accounts or paid AI services.

---

## User Review & Critical Decisions

> [!IMPORTANT]
> **Confirmed Choices & Plain-English Clarification on Document Types (Section 13 of Handover Request)**
> Please review the confirmed choices below and let us know if you would like any adjustments before we begin coding **Stage 1 (Foundation)**.

- **Confirmed Decision 1 — First Coding Milestone**: **Build Stage 1 (Foundation) first for review and acceptance.** We will design the complete 4-stage database and architecture now so nothing has to be thrown away later, but we will only build and verify Stage 1 first, then pause for your review.
- **Confirmed Decision 4 — No External or Paid Services Without Your Prior Approval**: **Stages 1–3 do not need any external API or cloud service**—everything from folder scanning and SHA-256 fingerprinting to document comparison, Project Brain tracking, and backups works best directly on the phone. When we reach **Stage 4 (Advanced Assistance)**, if an optional AI model or external API would genuinely improve complex summaries or subtle conflict detection where offline keyword matching falls short, we will first explain what it does, whether it requires an account or billing, its usage limits, and the free offline alternative so you can decide whether to approve it.
- **Confirmed Decision 2 — Physical Subfolders for Retired Files**: **Move into `Superseded` and `Review_For_Deletion` subfolders inside the project folder (once approved).** In Stage 1, folder scanning is strictly **100% read-only** (zero moves). When we reach Stage 3, approving a handover replacement or cleanup action will safely move the retired file into `_Superseded/` or `_Review_For_Deletion/` inside that project's folder while keeping its original filename intact.
- **Confirmed Decision 3 — Backup & Restore Packaging**: **Two-mode backup.**
  1. *Default Knowledge & Trail Backup (Fast, Small)*: Saves all projects, the Project Brain, Change Trail, document history, and saved text snapshots for comparison—without copying heavy project files.
  2. *Optional Full Archive Bundle*: Packages the Knowledge Backup **plus** actual copies of your project documents into a single portable archive when you explicitly choose to include physical files.
- **Confirmed Decision 5 — Broad Offline File Format Support**:
  1. **Direct Offline Text Reading & Comparison**: Built-in local readers for Markdown (`.md`), Plain Text (`.txt`), Web/HTML (`.html`), Word Documents (`.docx`), and structured files (`.json`, `.csv`).
  2. **Universal File Cataloging**: Every other file type in your folder (PDFs, images, archives, etc.) is cataloged with a permanent ID and digital fingerprint and opens in your phone's viewer when tapped.

---

## 1. Overview & Core Concept

- **What It Does**: Project Compass acts as the central "control room" for your software, creative, and personal projects. Working right where **FileMate** leaves off (after FileMate sorts downloaded AI files into project folders), Compass reads your linked project folders, tracks every file with a permanent ID, builds a structured **Project Brain** (purpose, approved requirements, decisions, verified progress, tasks, problems, and next steps), compares new AI handovers against what you actually approved in the past, and writes portable Markdown handover briefings for any AI or work area.
- **Target Audience / Persona**: Non-programmers and multi-AI creators who collaborate with multiple AI assistants (Claude, ChatGPT, Gemini, etc.) across long-running projects and need clear, plain-English protection against AI amnesia, silent requirement drops, and unverified claims ("I fixed and tested it").
- **Key Value**: **Truth & Traceability.** Every statement in Project Compass clearly shows whether it is merely an *Unverified Source Claim* found in a document, a *Proposal*, an *Approved Decision*, a *Verified Result*, *Uncertain*, or *Superseded*—backed by the exact document name and section where it came from.

### Genuine Contradictions & Technical Risks Identified in the Handover

> [!WARNING]
> **Audit of Contradictions, Edge Cases & How Our Design Resolves Them**

1. **Comparing New Documents Against Predecessors Without Unnecessary Copies (Sections 9, 10, 13, and 20)**
   - *Confirmed Workflow*: Documents in Project Compass are treated as immutable records—you do not overwrite existing handover files. Instead, when a new document or handover arrives in the folder, both the old file and the new file exist side-by-side. Compass compares the **new document** directly against the **older predecessor document** (and your approved Project Brain) to show what is old, what is new, and what has changed, and then moves the older file into `_Superseded/` once you approve. Compass also keeps lightweight text snapshots in its database as a safety net if an external app ever modifies a file directly, without making any unnecessary file copies.
2. **Android Folder Permissions vs. Identity Tracking Across Moves (Sections 3, 9, and 16)**
   - *The Tension*: Android's Storage Access Framework (`DocumentFile`) does not give files a permanent hardware serial number. If a file is moved or edited outside Compass while Compass is closed, Android treats the new path as a brand-new URI.
   - *Our Resolution*: Compass assigns every document a permanent **Compass Document ID (`doc_uuid`)** and combines **SHA-256 content fingerprinting**, filename matching, size, and saved text-similarity snapshots to automatically recognize when a file was moved, edited in place, or duplicated—without losing its historical links in the Change Trail.
3. **FileMate Co-Existence & Same-Filename Safety Net (Section 2 & Section 13)**
   - *Confirmed Resolution*: Compass never renames any file sorted by FileMate and never touches folders outside your explicitly linked project folders. When moving retired files into `_Superseded/` or `_Review_For_Deletion/`, if an older file with the exact same filename ever already exists in that subfolder, Compass will never overwrite or rename it—instead, it can place the file inside a dated subfolder (e.g., `_Superseded/2026-10-17/filename.md`) or ask you how to proceed so the original filename stays 100% untouched.

---

## 2. User Experience & Visual Design

### Visual Identity & Theme ("Editorial Field-Book & Precision Control Room")
- **Aesthetic Direction**: Warm, high-contrast **Editorial Field-Book** design. Instead of cryptic developer jargon or sterile grey boxes, Project Compass feels like an executive research binder combined with a clear mission-control dashboard.
- **Color Palette & Mood**:
  - **Surface / Background**: Warm Alabaster (`#FAF8F5`) in Light Mode and Deep Slate Obsidian (`#12161F`) in Dark Mode to reduce eye strain when reading long handovers and comparisons.
  - **Primary Accent**: Compass Navy (`#1E3A5F` Light / `#7BA7E1` Dark) for primary navigation, headers, and action buttons.
  - **Evidence & Truth Status Badges (Semantic Color Coding)**:
    - *Verified Result*: Emerald Green (`#15803D`) — proven and tested.
    - *Approved Decision / Requirement*: Royal Indigo (`#3730A3`) — agreed by you.
    - *Proposal / To Review*: Amber Gold (`#B45309`) — waiting for your decision.
    - *Unverified Source Claim*: Slate Blue (`#475569`) — stated by an AI document, not yet proven.
    - *Contradiction / Omission Alert*: Crimson Alert (`#B91C1C`) — conflicts with or drops an earlier approved item.
    - *Superseded / Retired*: Muted Stone (`#78716C`) — historical record replaced by a newer approved version.
- **Typography & Hierarchy**:
  - **Display & Section Headers**: **Plus Jakarta Sans** (bundled locally) for crisp, approachable headings and project titles.
  - **Body & Handover Reading**: **Inter** or **Public Sans** for comfortable reading of requirements, decisions, and plain-English explanations.
  - **Fingerprints, Filenames & Exact Line References**: **JetBrains Mono** so exact filenames, hashes, and document quotes stand out clearly as source evidence.

### (11) Proposed Phone Screens & Simple Navigation
To avoid overwhelming a phone screen with 11 separate tabs at once, Compass uses a **2-tier navigation layout**:

```
┌──────────────────────────────────────────────────────────────────────────┐
│  TIER 1: GLOBAL HOME (Bottom Navigation Bar — 4 Top-Level Destinations)  │
├─────────────────┬────────────────────┬─────────────────┬─────────────────┤
│ 1. My Projects  │ 2. Updates Review  │ 3. Unassigned   │ 4. Settings &   │
│  (Headquarters) │  (Alerts & Diffs)  │     Inbox       │    Backups      │
└────────┬────────┴────────────────────┴─────────────────┴─────────────────┘
         │ Tap any Project Card
         ▼
┌──────────────────────────────────────────────────────────────────────────┐
│  TIER 2: PROJECT WORKSPACE (Top Bar + Scrollable Section Pills)          │
├──────────────────────────────────────────────────────────────────────────┤
│  [Overview]  [Project Brain]  [Work Areas & AIs]  [Files & Inventory]    │
│  [Tasks & Next Steps]  [Change Trail]  [Cleanup & Duplicates]            │
├──────────────────────────────────────────────────────────────────────────┤
│  Persistent Bottom Action Bar:                                           │
│  [+ Add Note / Paste / File]   [Scan Folder]   [Export Handover (.md)]   │
└──────────────────────────────────────────────────────────────────────────┘
```

1. **My Projects (Global Home)**: Shows active and archived projects, folder connection health badge (*Connected*, *Permission Needed*, or *No Folder Linked*), urgent review count, and a one-tap **"Resume Where You Left Off"** card on each project.
2. **Project Overview (Inside a Project)**: Plain-English project purpose, current resume point, top 3 next steps, active problems, quick links to launch external tools/websites (e.g., Claude, ChatGPT, GitHub, Google Docs), and folder sync status.
3. **Project Brain**: Organized filterable cards for *Requirements*, *Decisions & Reasons*, *Completed & Verified Work*, *Problems & Attempts*, and *Resources*. Every card displays its **Truth Badge** (*Claim*, *Proposal*, *Approved*, *Verified*, *Uncertain*, *Superseded*) and a tappable **Source Evidence Chip** (opening the exact filename and paragraph where it came from).
4. **Work Areas & Multiple AIs**: Filters project knowledge and handovers by logical area (*Design*, *Development*, *Testing*, *Research*, *Documentation*, or custom areas). Tracks which AI assistant (ChatGPT, Claude, Gemini, Perplexity, etc.) proposed what, conversation URLs, and competing proposals side-by-side without forcing physical subfolders.
5. **Files & Read-Only Inventory**: Shows all discovered files in the linked folder, their stable Compass ID, SHA-256 fingerprint badge, lifecycle state (*Current*, *Superseded*, *Review for Deletion*, *Uncertain*), version chain, and whether the file has been edited in place since last scan.
6. **Updates to Review (Significant Alerts)**: The approval gatekeeper. Separates *Critical Contradictions & Omitted Requirements* from *New Proposals* and *Ordinary Progress*. Lets you Approve, Reject, Mark Uncertain, or Correct any extracted item with one tap.
7. **Change Trail**: A chronological audit log showing *Old Wording vs. New Wording*, exact source filenames/lines, date recorded vs. document date, documented reason, AI contributor, and predecessor handover links.
8. **Tasks & Next Steps**: Actionable checklist linked to Work Areas and source documents, distinguishing "AI claimed this is done" from "User verified this is done."
9. **Cleanup & Duplicate Review**: Shows exact duplicate files (identical hash), repeated knowledge, and retirement candidates, with safe actions (*Keep Both*, *Mark Superseded*, *Move to Subfolder*, *Merge Knowledge*) and an **Undo** history drawer.
10. **Unassigned Inbox**: Holds pasted text, shared notes from Android's Share menu, or manually picked files that haven't been matched to a project yet, showing a suggested project with confidence reasons.
11. **Settings & Backups**: Manage folder permissions, create/verify Knowledge Backups or Full Archive Bundles, preview restores before applying, and configure default work areas.

---

## 3. Key Product Decisions & Trade-Offs

- **Decision 1 — Storing Compressed Text Snapshots for In-Place Edit Detection**
  - *Chosen Approach*: Store extracted plain-text snapshots inside SQLite (`document_snapshots`) for text/Markdown/DOCX files while leaving heavy binary files on disk.
  - *Why*: Satisfies both Section 9 (must compare versions when a file is edited in place) and Section 20 (avoid unnecessary file copies), using only a few kilobytes per handover.
  - *Alternatives Considered*: Making full hidden backup copies of every file in the project folder (rejected: violates Section 20 "minimal copies" and clutters the user's folder).
- **Decision 2 — Two-Stage Approval Gate for Handover Succession & Physical Subfolder Moves**
  - *Chosen Approach*: Approving a new handover as `CURRENT` updates the logical status and offers a clearly visible checkbox/confirmation to physically move the retired predecessor into `_Superseded/`.
  - *Why*: Directly honors Section 13 ("Approval of current status does not automatically approve physical movement unless included in that approval or an existing approved rule") and your preference to organize retired files into `_Superseded` and `_Review_For_Deletion` subfolders.
  - *Alternatives Considered*: Silent background file moves on folder scan (rejected: strictly forbidden by Sections 7, 13, and 16).

---

## 4. Technical Architecture & Data Strategy *(Complete 13-Part Reference)*

### Requirements-to-Stage Mapping Checklist

| Handover Section & Requirement | Stage 1 (Foundation) | Stage 2 (Intelligence) | Stage 3 (Organisation) | Stage 4 (Advanced AI) |
| :--- | :---: | :---: | :---: | :---: |
| **§1, §4, §19:** Project CRUD, Archive/Restore, Resume Point, External App Links, Phone UI | **Full** | — | — | — |
| **§2, §3, §7:** SAF Folder Link/Disconnect/Reconnect, 100% Read-Only Inventory, SHA-256 Hashes | **Full** | — | — | — |
| **§5:** Logical Work Areas & Multi-AI Attribution Records (Manual + Basic Tagging) | **Core** | — | **Richer** | — |
| **§6:** Project Brain Structure (Purpose, Reqs, Decisions, Verified Work, Tasks, Problems, Statuses) | **Full** | — | — | — |
| **§9:** Stable Internal Document IDs (`doc_uuid`), Location History, Hash Fingerprints | **Full** | **In-Place Edit Diffs** | **Move Tracking** | — |
| **§17, §18:** Markdown Handover Exports (Project/Work Area) & Metadata/File Backup & Restore | **Full** | — | — | — |
| **§7, §10, §11:** Local Text Extraction, Predecessor Handover Comparison, Omission & Conflict Alerts | Basic Import | **Full** | — | **Semantic** |
| **§12, §15:** Full Evidence-Backed Change Trail (Old vs. New Wording, Line Refs) & Manual Refresh | Schema + Manual | **Full Auto-Diff** | — | — |
| **§8:** Unassigned Inbox, Android Share Receiver, Pasted Text, Project Matching Suggestions | Manual Entry | — | **Full Auto-Match** | — |
| **§13, §14, §16:** `_Superseded` & `_Review_For_Deletion` Subfolder Moves, Journaled Undo, Cleanup | Logical Flags | Exact Dupes | **Full Safe Moves** | — |
| **§21, §22:** Optional On-Device AI Summaries & Semantic Conflict Detection | — | — | — | **Full** |

---

### (1) Native Architecture & (2) Local Data Model

Project Compass uses a 100% offline **Clean Architecture (MVVM)** built with **Kotlin**, **Jetpack Compose (Material 3)**, **Android Storage Access Framework (`DocumentFile`)**, and a private local **Room / SQLite Database**. It has zero dependency on FileMate's internal database and makes zero network requests.

```
┌────────────────────────────────────────────────────────────────────────────┐
│                   JETPACK COMPOSE PHONE UI (Material 3)                    │
│  My Projects │ Project Workspace │ Brain & Trail │ Review Gate │ Backups   │
└──────────────────────────────────────┬─────────────────────────────────────┘
                                       │ StateFlow (Reactive UI State)
┌──────────────────────────────────────▼─────────────────────────────────────┐
│                     DOMAIN & OFFLINE INTELLIGENCE LAYER                    │
│  ┌──────────────────────┐ ┌──────────────────────┐ ┌────────────────────┐  │
│  │ Read-Only Inventory  │ │ Deterministic Diff & │ │ Safe File Move     │  │
│  │ & Hash Engine        │ │ Handover Comparator  │ │ Journal & Undo     │  │
│  └──────────────────────┘ └──────────────────────┘ └────────────────────┘  │
│  ┌──────────────────────┐ ┌──────────────────────┐ ┌────────────────────┐  │
│  │ Markdown / Text /    │ │ Evidence & Truth     │ │ Markdown Handover  │  │
│  │ DOCX Local Extractor │ │ Gatekeeper           │ │ & Backup Generator │  │
│  └──────────────────────┘ └──────────────────────┘ └────────────────────┘  │
└───────────────────┬────────────────────────────────────┬───────────────────┘
                    │                                    │
┌───────────────────▼──────────────────┐ ┌───────────────▼───────────────────┐
│   LOCAL ROOM / SQLITE DATABASE       │ │ ANDROID STORAGE ACCESS FRAMEWORK  │
│ • Projects & Folder Permissions      │ │ • User-Granted Project Tree URIs  │
│ • Documents (doc_uuid, hashes, path) │ │ • Read-Only Inventory Scanner     │
│ • DocumentSnapshots (for edit diffs) │ │ • Approved Subfolder Mover:       │
│ • BrainItems (Reqs, Decisions, etc.) │ │   /_Superseded/                   │
│ • EvidenceSources (Doc + Line/Sect)  │ │   /_Review_For_Deletion/          │
│ • ChangeTrailEntries (Old vs New)    │ │ • Android Share Intent Receiver   │
│ • FileOperationJournal (Safe Undo)   │ └───────────────────────────────────┘
└──────────────────────────────────────┘
```

#### Core Local Database Tables (Room Entities)
1. **`projects`**: `projectId` (UUID), `name`, `purpose`, `status` (Active, Paused, Completed, Archived), `resumePointSummary`, `linkedFolderUri` (nullable), `permissionState` (Connected, Revoked, Unlinked), `externalLinksJson` (links to AI chats/tools), `createdAt`, `lastScannedAt`.
2. **`work_areas`**: `workAreaId`, `projectId`, `name` (Design, Development, Testing, Research, Documentation, or Custom), `description`, `displayOrder`.
3. **`documents`**: `docId` (Permanent Compass UUID — never changes even if moved), `projectId` (nullable if in Unassigned Inbox), `originalFilename`, `currentRelativePath`, `currentSafUri`, `previousRelativePathsJson`, `sha256Hash`, `fileSizeBytes`, `lastModifiedTimestamp`, `lifecycleCategory` (`CURRENT`, `SUPERSEDED`, `REVIEW_FOR_DELETION`, `UNCERTAIN`), `isHandoverReport`, `predecessorDocId` (nullable), `aiSourceName` (e.g., Claude, ChatGPT, Gemini, Unknown), `conversationUrl`, `workAreaId`, `missingOnDisk` (boolean).
4. **`document_snapshots`**: `snapshotId`, `docId`, `versionNumber`, `sha256Hash`, `recordedAt`, `extractedPlainText`, `parsedSectionsJson`. *(Enables exact comparison when a file is edited in place or replaced by a successor handover).*
5. **`brain_items`**: `brainItemId` (UUID), `projectId`, `workAreaId`, `category` (`PURPOSE`, `REQUIREMENT`, `DECISION`, `COMPLETED_WORK`, `TASK`, `PROBLEM_ATTEMPT`, `RESOURCE`, `NEXT_STEP`), `title`, `content`, `reasonOrContext`, `truthStatus` (`SOURCE_CLAIM`, `PROPOSAL`, `APPROVED`, `VERIFIED`, `UNCERTAIN`, `SUPERSEDED`), `aiContributor`, `supersededByItemId` (nullable), `recordedAt`, `eventDateText` (nullable).
6. **`evidence_links`**: `evidenceId`, `brainItemId`, `docId`, `snapshotId`, `exactFilenameAtTime`, `sectionHeading`, `lineRange`, `exactQuote`.
7. **`change_trail_entries`**: `changeId`, `projectId`, `workAreaId`, `brainItemId`, `changeType` (`ADDED`, `WORDING_CHANGED`, `STATUS_CHANGED`, `OMITTED_IN_HANDOVER`, `CONTRADICTION_DETECTED`, `DECISION_REVERSED`, `FILE_RETIRES_PREDECESSOR`), `previousWording`, `newWording`, `documentedReason`, `sourceDocId`, `predecessorDocId`, `sectionLineRef`, `aiContributor`, `reviewStatus` (`PENDING_REVIEW`, `APPROVED`, `REJECTED`), `recordedAt`, `documentedEventDate`.
8. **`file_operation_journal`**: `opId`, `projectId`, `docId`, `operationType` (`MOVE_TO_SUPERSEDED`, `MOVE_TO_DELETION_REVIEW`, `RESTORE_TO_CURRENT`), `sourceUri`, `sourceRelativePath`, `targetUri`, `targetRelativePath`, `expectedSha256`, `state` (`INTENT_LOGGED`, `COPIED_VERIFIED`, `SOURCE_REMOVED`, `COMPLETED`, `ROLLED_BACK`, `FAILED`), `canUndo`, `timestamp`.

---

### (3) Folder Access & First-Time Import
- **Scoped Folder Access**: Uses Android's `Intent.ACTION_OPEN_DOCUMENT_TREE` combined with `takePersistableUriPermission`. Compass only sees the specific project folder you select—never your whole phone.
- **Disconnect / Reconnect / Lapsed Permissions**: On app launch or project open, Compass checks `DocumentFile.canRead()`. If Android permission was revoked or the folder was moved, Compass switches the project badge to **"Folder Disconnected — Knowledge Preserved"**. All your Project Brain records, Change Trail, and past snapshots remain 100% readable offline, and a single **"Reconnect Folder"** button guides you to re-select the folder and reconciles existing files by their SHA-256 hash.
- **100% Read-Only First Import**:
  1. Recursively walks the connected folder without writing, moving, or renaming anything.
  2. Computes SHA-256 fingerprints, file sizes, and last-modified timestamps.
  3. Detects exact duplicates (same SHA-256 hash in multiple files), likely version sequences (e.g., `Handover_v1.md`, `Handover_v2.md`), and structured Markdown/Text handovers.
  4. Extracts candidate Purpose, Requirements, Decisions, Tasks, and Problems as **Proposals (`PROPOSAL` / `SOURCE_CLAIM`)** inside the **Updates to Review** queue—never marking them as Approved or Verified until you review and approve them.

---

### (4) Document Identity & Edited-in-Place Handling
- **Permanent Identity (`doc_uuid`)**: Every file gets an internal UUID the moment Compass first sees it. All Brain items and Change Trail records point to `doc_uuid` + `snapshotId` (while also storing the human-readable filename).
- **Four Deterministic Detection Rules on Refresh**:
  1. **Unchanged (`Same Path + Same SHA-256`)**: Skipped immediately—zero reprocessing overhead.
  2. **Edited in Place (`Same Path + Different SHA-256`)**: Compass keeps the previous `DocumentSnapshot`, saves a new `DocumentSnapshot`, runs a line-by-line diff between the two versions, and logs an **Edited-in-Place Alert** in *Updates to Review* so you can see exactly what lines changed inside the file.
  3. **Moved or Renamed Externally (`Missing at Old Path + New Path with Identical SHA-256`)**: Compass recognizes the fingerprint, updates `currentRelativePath` on the existing `doc_uuid`, records the old path in `previousRelativePathsJson`, and keeps all historical links unbroken.
  4. **Exact Duplicate (`Existing Path Still Present + Second File with Identical SHA-256`)**: Flagged in **Cleanup & Duplicates** without deleting either file.

---

### (5) Handover Comparisons & Significant Alerts
- **How Comparison Works**: When a new handover arrives, Compass compares its extracted sections against **two baselines**:
  1. Its **Predecessor Handover** (either selected by you or suggested by version sequence/work area).
  2. The **Accumulated Approved Project Brain** (every requirement and decision currently marked `APPROVED` or `VERIFIED`).
- **Protecting Against AI Amnesia ("Omission is Not Cancellation")**:
  - If your Approved Project Brain has 12 approved requirements, and a new AI handover only lists 9 of them, Compass flags the 3 missing items as **"Significant Omission Alerts"** (*"Requirement #4 was approved on 12 Oct in `handover_v2.md` (Lines 42–48) but is missing from `handover_v3.md`. Keep as Approved?"*).
  - If a new handover states *"Completed and tested login flow"*, Compass classifies it as an **Unsupported Source Claim (`SOURCE_CLAIM`)** until you either attach verification evidence or explicitly tap **"Mark Verified"**.
- **Filtering Out Cosmetic Noise**: Whitespace, bullet-style changes (`-` vs `*`), and capitalization differences are ignored in the Significant Alerts feed so you only review meaningful changes to requirements, decisions, tasks, problems, and status claims.

---

### (6) Project Brain & Change Trail
- **Strict Separation of Truth States**: Every record in the Project Brain has a mandatory state machine:
  `SOURCE_CLAIM` ➔ `PROPOSAL` ➔ `APPROVED` ➔ `VERIFIED` (plus `UNCERTAIN` and `SUPERSEDED`).
- **No Silent Overwrites**: Even when you approve a change to an existing requirement or decision, the previous wording is never erased—it is linked via `supersededByItemId` and written into the **Change Trail** with:
  - Previous wording and new wording side-by-side.
  - Exact filename and section/line reference for both old and new states.
  - Date recorded by Compass *and* any date mentioned inside the document.
  - Documented reason for the change, Work Area, and AI contributor.

---

### (7) Multiple AIs & Logical Work Areas
- **Logical Work Areas Without Forced Subfolders**: Every project comes pre-configured with *Design*, *Development*, *Testing*, *Research*, and *Documentation* (plus custom work areas you add). Files can live anywhere in the project folder and be tagged to one or more Work Areas.
- **Honest AI Attribution**: Each document, proposal, and Change Trail entry records:
  - Known AI source (e.g., *ChatGPT*, *Claude*, *Gemini*, *User-Authored*, or *Unknown — Not Stated*). Compass **never guesses or invents** which AI wrote a file if the file or user hasn't stated it.
  - Original filename, optional conversation link, task context, and competing proposals (e.g., if Claude and Gemini propose two different database designs for the *Development* work area, both are preserved side-by-side until you approve one and mark the other *Superseded*).

---

### (8) Safe Current / Superseded / Deletion Review & Journaled Undo
- **Subfolder Policy (Per Your Confirmed Choice)**:
  - Active files stay in their current project folder location (`CURRENT`).
  - When a new handover is approved as `CURRENT` and you approve retiring its predecessor, Compass moves the predecessor into `_Superseded/` inside the project folder (`SUPERSEDED`).
  - When you mark a duplicate or obsolete file for deletion review in Cleanup, Compass moves it into `_Review_For_Deletion/` inside the project folder (`REVIEW_FOR_DELETION`).
  - **Original filenames are never changed.** If `_Superseded/handover.md` already exists, Compass stops, alerts you of the name collision, and asks before taking action rather than silently renaming or overwriting.
- **6-Step Safe File Move Protocol & Undo**:
  1. Re-verify SAF folder write permission and confirm the source file's SHA-256 hash has not changed since the move was proposed.
  2. Check available storage space and verify no file with the same name exists at the destination subfolder.
  3. Write an `INTENT_LOGGED` entry in `file_operation_journal`.
  4. Stream-copy the bytes to the destination subfolder (`_Superseded/` or `_Review_For_Deletion/`) and compute the destination file's SHA-256 hash.
  5. Only after the destination SHA-256 hash **100% matches** the source SHA-256 hash, remove the source file entry and update the document's `currentRelativePath` and `currentSafUri` in SQLite (preserving all `doc_uuid` references).
  6. Enable one-tap **Undo Move**, which safely reverses the journaled move back to the original relative path using the same verification checks.

---

### (9) Backup & Restore
- **Two Explicit Modes (Per Your Confirmed Choice)**:
  1. **Knowledge & Trail Backup (`.compassbackup.zip` — Default)**: Contains the full SQLite/JSON export of all projects, Work Areas, Project Brain items, Change Trail entries, document identities, review history, and compressed text snapshots, plus a `manifest.json` with SHA-256 checksums for every table.
  2. **Full Archive Bundle (`.compassfull.zip` — Optional)**: Includes everything in the Knowledge Backup **plus** an explicit copy of the physical files from your selected projects. The UI clearly shows the estimated size and states whether physical documents are included before exporting.
- **Safe Restore Preview & Folder Reconciliation**:
  - Restoring never blindly wipes your current data. Compass first validates the backup's SHA-256 checksums and shows a **Restore Impact Preview** (how many projects, brain items, and change records will be added or updated).
  - After restore, Compass guides you to reconnect your project folders and automatically matches physical files on disk back to their restored `doc_uuid` records using SHA-256 hashes and relative paths. If any physical file is missing on disk, its historical knowledge and Change Trail are still preserved with a clear *"File Not Currently on Disk"* badge.

---

### (10) Offline Intelligence Approach & Honest Reliability Boundaries

| Capability | Technical Approach (100% Offline) | Reliability Level | How Compass Presents & Verifies It |
| :--- | :--- | :--- | :--- |
| **File Identity, Exact Duplicates & In-Place Edits** | SHA-256 cryptographic hashing, byte size, path tracking, and Myers line-by-line diff algorithm on saved snapshots. | **100% Deterministic** | Presented as verified facts (exact line numbers, exact byte hashes). |
| **Markdown & Structured Handover Parsing** | Deterministic AST/heading parser recognizing `# Purpose`, `## Requirements`, `## Decisions`, `## Completed`, `## Tasks`, `## Problems`, checkboxes (`- [ ]`, `- [x]`), and numbered items. | **High (Deterministic Structure)** | Extracted items always enter as `SOURCE_CLAIM` / `PROPOSAL` with exact line numbers and quotes; never auto-approved. |
| **Predecessor Matching & Omission Detection** | Compares normalized headings, requirement IDs, and Myers token overlap + Jaccard keyword similarity between Approved Brain items and new handover sections. | **Approximate / Assisted** | Flagged as *"Likely Omission"* or *"Candidate Match"* in Updates to Review with side-by-side quotes; user confirms or dismisses. |
| **Project Suggestion for Unassigned Inbox** | TF-IDF keyword overlap against existing project names, work area terms, and recent filenames. | **Approximate Suggestion** | Shown as *"Suggested Project (Confidence: Medium)"*; always requires user approval before assignment or new-project creation. |
| **Stage 4 Semantic Conflict & Summary Assistance** | Free local offline rule & similarity engine by default; optional on-device AI or user-approved API if needed for deeper semantic understanding (only after explaining purpose, limits, billing, and offline alternative for your explicit approval). | **Advisory Only (Explicit Uncertainty)** | Clearly labeled *"Assisted Suggestion — Verify Source"*; never alters Project Brain without user approval. |

---

### (12) Staged Implementation & Acceptance Test Plan

Per your confirmation, **upon approval of this plan we will implement Stage 1 (Foundation)** and verify it with automated local JVM/Robolectric tests and real Android runtime checks before asking for your review to proceed to Stage 2.

#### Stage 1 Scope (First Milestone to Code Upon Approval)
1. **Complete Room Database Schema**: All core tables (`projects`, `work_areas`, `documents`, `document_snapshots`, `brain_items`, `evidence_links`, `change_trail_entries`, `file_operation_journal`) created upfront so future stages require zero destructive migrations.
2. **Project Headquarters & Management**: Create, edit, rename, archive, and restore projects (with or without linked folders); set project purpose, status, resume point, and external app/website quick-launch links.
3. **Scoped Folder Permissions & Read-Only Inventory**: Connect, disconnect, and reconnect project folders via Android SAF (`DocumentFile`); scan populated folders in **strictly 100% read-only mode**; compute SHA-256 hashes; assign permanent `doc_uuid` records; save initial text snapshots for `.md`, `.txt`, `.json`, `.html`, `.csv`, and `.docx` files; detect exact hash duplicates and candidate handovers.
4. **Initial Project Brain & Manual/Extracted Proposals**: Parse Markdown/Text handovers into candidate Purpose, Requirements, Decisions, Verified Work, Tasks, Problems, and Resources; display Truth Badges (`SOURCE_CLAIM`, `PROPOSAL`, `APPROVED`, `VERIFIED`, `UNCERTAIN`, `SUPERSEDED`) with source filename and line evidence; allow creating, approving, editing, and recording Change Trail entries.
5. **Logical Work Areas & Multi-AI Attribution**: Assign documents and Brain items to Work Areas (*Design*, *Development*, *Testing*, *Research*, *Documentation*) and record AI contributors (*Claude*, *ChatGPT*, *Gemini*, etc.) and conversation links.
6. **Markdown Handover Generator & Two-Mode Backup/Restore**: Export clean, evidence-referenced Markdown handovers for an entire project or specific Work Area; create and restore verified Knowledge Backups (`.compassbackup.zip`) and Optional Full Archive Bundles (`.compassfull.zip`) with Restore Impact Preview.
7. **Stage 1 Automated Verification Tests**:
   - Read-only folder inventory test verifying zero source files are modified, renamed, or moved.
   - Permanent `doc_uuid` and SHA-256 fingerprint generation test.
   - Truth-state gatekeeper test proving extracted statements default to `SOURCE_CLAIM` / `PROPOSAL` and never silently overwrite `APPROVED` or `VERIFIED` items.
   - Backup integrity checksum and Restore Preview round-trip test.
   - Markdown handover export test verifying unsupported claims are clearly separated from verified progress.

#### Subsequent Stages (Built After Your Stage 1 Review)
- **Stage 2 (Document Intelligence)**: Automated in-place edit diff viewer, automated predecessor handover comparison against historical approved Project Brain, Omission & Contradiction alert engine, and one-tap Change Trail approval workflows.
- **Stage 3 (Organisation)**: Approved internal moves into `_Superseded/` and `_Review_For_Deletion/` subfolders with 6-step journaled verification and Undo, full Cleanup & Duplicate resolution workflows, Android Share target receiver, and Unassigned Inbox project matching.
- **Stage 4 (Advanced Assistance)**: Deeper semantic contradiction detection, cross-handover summaries, and contextual suggestions—using free local methods first, and proposing any optional AI/external service only with a full plain-English breakdown of purpose, limits, and any cost/billing for your approval first.
