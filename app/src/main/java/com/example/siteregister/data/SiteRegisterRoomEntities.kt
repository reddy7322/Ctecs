package com.example.siteregister.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Room offline-first cache & sync queue tables for the CTECS Digital Site Register.
 * Preserves every record offline with idempotent UUIDs, original timestamps, and pending-sync
 * tracking while syncing cleanly against Supabase PostgreSQL when online.
 */
@Entity(
    tableName = "sr_records",
    indices = [
        Index(value = ["recordType", "projectId"]),
        Index(value = ["recordType", "parentId"]),
        Index(value = ["syncState"])
    ]
)
data class SiteRegisterLocalRecordEntity(
    @PrimaryKey val id: String,
    val recordType: String, // PROJECT, PROJECT_CODE, MEMBERSHIP, REG_REQUEST, REGISTER, REVISION, SEGMENT, BC_ENTRY, GLASS_GRID, TACKCOAT_TEST, QUALITY_TEST, OBSERVATION, SIGNATURE, SHARING_CODE, EDIT_REQUEST, EDIT_PERMISSION, AUDIT_LOG
    val projectId: String,
    val parentId: String = "",
    val revisionNumber: Int = 1,
    val status: String = "",
    val payloadJson: String,
    val syncState: String = "PENDING_SYNC", // SYNCED, PENDING_SYNC, CONFLICT, FAILED
    val createdAtMs: Long = System.currentTimeMillis(),
    val updatedAtMs: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "sr_sync_queue",
    indices = [Index(value = ["status", "createdAtMs"])]
)
data class SiteRegisterSyncQueueEntity(
    @PrimaryKey val operationId: String,
    val tableName: String,
    val recordId: String,
    val registerId: String = "",
    val revisionNumber: Int = 1,
    val operationType: String, // UPSERT, RPC, UPLOAD_STORAGE
    val rpcName: String = "",
    val payloadJson: String,
    val status: String = "PENDING", // PENDING, IN_PROGRESS, FAILED, BLOCKED_LOCKED
    val errorMessage: String = "",
    val retryCount: Int = 0,
    val createdAtMs: Long = System.currentTimeMillis(),
    val lastAttemptAtMs: Long = 0L
)

@Dao
interface SiteRegisterDao {
    @Query("SELECT * FROM sr_records WHERE recordType = :type ORDER BY updatedAtMs DESC")
    fun observeByType(type: String): Flow<List<SiteRegisterLocalRecordEntity>>

    @Query("SELECT * FROM sr_records WHERE recordType = :type AND projectId = :projectId ORDER BY updatedAtMs DESC")
    fun observeByTypeAndProject(type: String, projectId: String): Flow<List<SiteRegisterLocalRecordEntity>>

    @Query("SELECT * FROM sr_records WHERE recordType = :type AND parentId = :parentId ORDER BY createdAtMs ASC")
    fun observeByTypeAndParent(type: String, parentId: String): Flow<List<SiteRegisterLocalRecordEntity>>

    @Query("SELECT * FROM sr_records WHERE recordType = :type")
    suspend fun getAllByType(type: String): List<SiteRegisterLocalRecordEntity>

    @Query("SELECT * FROM sr_records WHERE recordType = :type AND projectId = :projectId")
    suspend fun getByTypeAndProject(type: String, projectId: String): List<SiteRegisterLocalRecordEntity>

    @Query("SELECT * FROM sr_records WHERE recordType = :type AND parentId = :parentId ORDER BY createdAtMs ASC")
    suspend fun getByTypeAndParent(type: String, parentId: String): List<SiteRegisterLocalRecordEntity>

    @Query("SELECT * FROM sr_records WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): SiteRegisterLocalRecordEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRecord(record: SiteRegisterLocalRecordEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRecords(records: List<SiteRegisterLocalRecordEntity>)

    @Query("DELETE FROM sr_records WHERE id = :id")
    suspend fun deleteRecordById(id: String)

    @Query("UPDATE sr_records SET syncState = :syncState, updatedAtMs = :updatedAtMs WHERE id = :id")
    suspend fun updateSyncState(id: String, syncState: String, updatedAtMs: Long = System.currentTimeMillis())

    @Query("SELECT COUNT(*) FROM sr_sync_queue WHERE status IN ('PENDING', 'FAILED')")
    fun observePendingSyncCount(): Flow<Int>

    @Query("SELECT * FROM sr_sync_queue WHERE status IN ('PENDING', 'FAILED') ORDER BY createdAtMs ASC")
    suspend fun getPendingSyncOperations(): List<SiteRegisterSyncQueueEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun enqueueSync(op: SiteRegisterSyncQueueEntity)

    @Query("DELETE FROM sr_sync_queue WHERE operationId = :operationId")
    suspend fun markSyncCompleted(operationId: String)

    @Query("UPDATE sr_sync_queue SET status = :status, errorMessage = :errorMessage, retryCount = retryCount + 1, lastAttemptAtMs = :attemptAtMs WHERE operationId = :operationId")
    suspend fun markSyncFailed(operationId: String, status: String, errorMessage: String, attemptAtMs: Long = System.currentTimeMillis())
}
