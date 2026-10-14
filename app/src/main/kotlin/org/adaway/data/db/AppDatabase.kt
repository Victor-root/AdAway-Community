package org.adaway.data.db

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [SourceEntity::class, UserRuleEntity::class],
    version = 2,
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun sourceDao(): SourceDao

    abstract fun userRuleDao(): UserRuleDao

    companion object {
        const val NAME = "adaway.db"
    }
}
