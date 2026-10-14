package org.adaway.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

data class SourceCounts(val total: Int, val enabled: Int)

@Dao
interface SourceDao {
    @Insert
    suspend fun insertAll(sources: List<SourceEntity>)

    @Query("DELETE FROM sources")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) AS total, COALESCE(SUM(enabled), 0) AS enabled FROM sources")
    fun observeCounts(): Flow<SourceCounts>
}
