package org.pictokeyboard.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface UsageDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(usage: UsageEntity)

    @Query(
        "UPDATE usage SET count = count + 1, lastUsedAt = :now, label = :label, spokenText = :spokenText, language = :language WHERE id = :id",
    )
    suspend fun increment(id: String, label: String, spokenText: String, language: String, now: Long): Int

    /** Records one use, creating the row if needed (UPSERT split for old SQLite). */
    @Transaction
    suspend fun record(usage: UsageEntity, now: Long) {
        insertIfAbsent(usage.copy(count = 0, lastUsedAt = now))
        increment(usage.id, usage.label, usage.spokenText, usage.language, now)
    }

    @Query("SELECT * FROM usage WHERE count > 0 ORDER BY count DESC, lastUsedAt DESC LIMIT :limit")
    suspend fun topUsed(limit: Int): List<UsageEntity>

    @Query("SELECT COUNT(*) FROM usage WHERE count > 0")
    suspend fun usedCount(): Int
}
