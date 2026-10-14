package org.adaway.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface UserRuleDao {
    @Insert
    suspend fun insertAll(rules: List<UserRuleEntity>)

    @Insert
    suspend fun insert(rule: UserRuleEntity): Long

    /** Puts back a deleted row, unless an equivalent one was added meanwhile. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(rule: UserRuleEntity)

    @Update
    suspend fun update(rule: UserRuleEntity)

    @Query("DELETE FROM user_rules WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM user_rules")
    suspend fun deleteAll()

    /** Rules of [type] whose host matches the SQL LIKE [pattern] (escaped with `\`). */
    @Query("SELECT * FROM user_rules WHERE type = :type AND host LIKE :pattern ESCAPE '\\' ORDER BY host")
    fun observe(type: RuleType, pattern: String): Flow<List<UserRuleEntity>>

    @Query("SELECT * FROM user_rules ORDER BY host")
    suspend fun getAll(): List<UserRuleEntity>

    @Query("SELECT * FROM user_rules WHERE enabled = 1")
    suspend fun getEnabled(): List<UserRuleEntity>

    @Query("SELECT * FROM user_rules WHERE id = :id")
    suspend fun findById(id: Long): UserRuleEntity?

    @Query("SELECT * FROM user_rules WHERE host = :host")
    suspend fun findByHost(host: String): UserRuleEntity?

    @Query("UPDATE user_rules SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)
}
