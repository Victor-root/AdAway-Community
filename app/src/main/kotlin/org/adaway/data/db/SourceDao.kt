package org.adaway.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SourceDao {
    @Insert
    suspend fun insertAll(sources: List<SourceEntity>)

    /** Fails with a constraint error when another source already has the same url. */
    @Insert
    suspend fun insert(source: SourceEntity): Long

    /** Puts back a deleted row, unless an equivalent one was added meanwhile. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(source: SourceEntity)

    @Update
    suspend fun update(source: SourceEntity)

    @Query("DELETE FROM sources WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM sources")
    suspend fun deleteAll()

    /** Sources in creation order, which is also their priority order for redirections. */
    @Query("SELECT * FROM sources ORDER BY id")
    fun observeAll(): Flow<List<SourceEntity>>

    @Query("SELECT * FROM sources ORDER BY id")
    suspend fun getAll(): List<SourceEntity>

    @Query("SELECT * FROM sources WHERE id = :id")
    suspend fun get(id: Long): SourceEntity?

    @Query("SELECT * FROM sources WHERE url = :url")
    suspend fun findByUrl(url: String): SourceEntity?

    @Query("UPDATE sources SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)

    @Query("UPDATE sources SET updateAvailable = 1 WHERE id = :id")
    suspend fun markUpdateAvailable(id: Long)

    /**
     * Records a successful synchronization, unless the source was edited meanwhile: the list read
     * then belongs to its previous address or format.
     */
    @Query(
        """
        UPDATE sources SET entityTag = :entityTag, lastModifiedHeader = :lastModifiedHeader,
            onlineModifiedAt = :onlineModifiedAt, syncedAt = :syncedAt, ruleCount = :ruleCount,
            syncError = NULL, syncErrorStatus = NULL, updateAvailable = 0
        WHERE id = :id AND url = :url AND format = :format AND redirectsEnabled = :redirectsEnabled
        """,
    )
    suspend fun recordSynchronized(
        id: Long,
        url: String,
        format: SourceFormat,
        redirectsEnabled: Boolean,
        entityTag: String?,
        lastModifiedHeader: String?,
        onlineModifiedAt: Long?,
        syncedAt: Long,
        ruleCount: Int,
    )

    @Query(
        """
        UPDATE sources SET syncedAt = :syncedAt, syncError = NULL, syncErrorStatus = NULL,
            updateAvailable = 0
        WHERE id = :id AND url = :url
        """,
    )
    suspend fun recordNotModified(id: Long, url: String, syncedAt: Long)

    @Query("UPDATE sources SET syncError = :error, syncErrorStatus = :status WHERE id = :id")
    suspend fun recordSyncFailure(id: Long, error: SyncError, status: Int?)
}
