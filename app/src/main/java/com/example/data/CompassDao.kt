package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CompassDao {
    // Projects
    @Query("SELECT * FROM projects ORDER BY createdAt DESC")
    fun observeAllProjects(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects ORDER BY createdAt DESC")
    suspend fun getAllProjectsSnapshot(): List<ProjectEntity>

    @Query("SELECT * FROM projects WHERE projectId = :projectId LIMIT 1")
    suspend fun getProjectById(projectId: String): ProjectEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProject(project: ProjectEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProjects(projects: List<ProjectEntity>)

    // Work Areas
    @Query("SELECT * FROM work_areas ORDER BY displayOrder ASC")
    fun observeAllWorkAreas(): Flow<List<WorkAreaEntity>>

    @Query("SELECT * FROM work_areas WHERE projectId = :projectId ORDER BY displayOrder ASC")
    suspend fun getWorkAreasForProject(projectId: String): List<WorkAreaEntity>

    @Query("SELECT * FROM work_areas ORDER BY displayOrder ASC")
    suspend fun getAllWorkAreasSnapshot(): List<WorkAreaEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertWorkArea(workArea: WorkAreaEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertWorkAreas(workAreas: List<WorkAreaEntity>)

    // Documents
    @Query("SELECT * FROM documents ORDER BY recordedAt DESC")
    fun observeAllDocuments(): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE projectId = :projectId ORDER BY recordedAt DESC")
    suspend fun getDocumentsForProject(projectId: String): List<DocumentEntity>

    @Query("SELECT * FROM documents ORDER BY recordedAt DESC")
    suspend fun getAllDocumentsSnapshot(): List<DocumentEntity>

    @Query("SELECT * FROM documents WHERE docId = :docId LIMIT 1")
    suspend fun getDocumentById(docId: String): DocumentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDocument(document: DocumentEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDocuments(documents: List<DocumentEntity>)

    // Document Snapshots
    @Query("SELECT * FROM document_snapshots ORDER BY recordedAt DESC")
    fun observeAllSnapshots(): Flow<List<DocumentSnapshotEntity>>

    @Query("SELECT * FROM document_snapshots WHERE docId = :docId ORDER BY versionNumber DESC")
    suspend fun getSnapshotsForDocument(docId: String): List<DocumentSnapshotEntity>

    @Query("SELECT * FROM document_snapshots ORDER BY recordedAt DESC")
    suspend fun getAllSnapshotsSnapshot(): List<DocumentSnapshotEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSnapshot(snapshot: DocumentSnapshotEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSnapshots(snapshots: List<DocumentSnapshotEntity>)

    // Brain Items
    @Query("SELECT * FROM brain_items ORDER BY recordedAt DESC")
    fun observeAllBrainItems(): Flow<List<BrainItemEntity>>

    @Query("SELECT * FROM brain_items WHERE projectId = :projectId ORDER BY recordedAt DESC")
    suspend fun getBrainItemsForProject(projectId: String): List<BrainItemEntity>

    @Query("SELECT * FROM brain_items ORDER BY recordedAt DESC")
    suspend fun getAllBrainItemsSnapshot(): List<BrainItemEntity>

    @Query("SELECT * FROM brain_items WHERE brainItemId = :brainItemId LIMIT 1")
    suspend fun getBrainItemById(brainItemId: String): BrainItemEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBrainItem(item: BrainItemEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBrainItems(items: List<BrainItemEntity>)

    // Evidence Links
    @Query("SELECT * FROM evidence_links")
    fun observeAllEvidenceLinks(): Flow<List<EvidenceLinkEntity>>

    @Query("SELECT * FROM evidence_links")
    suspend fun getAllEvidenceLinksSnapshot(): List<EvidenceLinkEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEvidenceLink(link: EvidenceLinkEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEvidenceLinks(links: List<EvidenceLinkEntity>)

    // Change Trail
    @Query("SELECT * FROM change_trail_entries ORDER BY recordedAt DESC")
    fun observeAllChangeTrail(): Flow<List<ChangeTrailEntity>>

    @Query("SELECT * FROM change_trail_entries WHERE projectId = :projectId ORDER BY recordedAt DESC")
    suspend fun getChangeTrailForProject(projectId: String): List<ChangeTrailEntity>

    @Query("SELECT * FROM change_trail_entries ORDER BY recordedAt DESC")
    suspend fun getAllChangeTrailSnapshot(): List<ChangeTrailEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertChangeTrailEntry(entry: ChangeTrailEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertChangeTrailEntries(entries: List<ChangeTrailEntity>)

    // File Operation Journal
    @Query("SELECT * FROM file_operation_journal ORDER BY timestamp DESC")
    fun observeAllJournalOperations(): Flow<List<FileOperationJournalEntity>>

    @Query("SELECT * FROM file_operation_journal ORDER BY timestamp DESC")
    suspend fun getAllJournalOperationsSnapshot(): List<FileOperationJournalEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertJournalOperation(op: FileOperationJournalEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertJournalOperations(ops: List<FileOperationJournalEntity>)
}
