package org.adaway.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

data class RuleTypeCount(val type: RuleType, val count: Int)

@Dao
interface UserRuleDao {
    @Insert
    suspend fun insertAll(rules: List<UserRuleEntity>)

    @Query("DELETE FROM user_rules")
    suspend fun deleteAll()

    @Query("SELECT type, COUNT(*) AS count FROM user_rules GROUP BY type")
    fun observeCountsByType(): Flow<List<RuleTypeCount>>
}
