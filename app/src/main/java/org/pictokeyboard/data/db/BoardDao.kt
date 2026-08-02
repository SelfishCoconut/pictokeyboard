package org.pictokeyboard.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface BoardDao {

    @Query("SELECT * FROM boards ORDER BY position ASC")
    fun observeAll(): Flow<List<BoardEntity>>

    /**
     * The boards the keyboard offers as tabs.
     *
     * Scoped in SQL rather than filtered in the collector because the keyboard
     * decides whether to draw the strip at all from this list's size, and a
     * half-built board the caregiver has hidden must not count towards it.
     */
    @Query("SELECT * FROM boards WHERE showInKeyboard = 1 ORDER BY position ASC")
    fun observeVisible(): Flow<List<BoardEntity>>

    /**
     * The board currently in use.
     *
     * `LIMIT 1` is a safety net, not an expectation: [setActive] is the only
     * writer of the flag and clears the others in the same transaction. If two
     * rows were ever active the keyboard should still show one board rather
     * than crash mid-conversation.
     */
    @Query("SELECT * FROM boards WHERE active = 1 ORDER BY position ASC LIMIT 1")
    fun observeActive(): Flow<BoardEntity?>

    @Query("SELECT * FROM boards WHERE active = 1 ORDER BY position ASC LIMIT 1")
    suspend fun getActive(): BoardEntity?

    @Query("SELECT * FROM boards WHERE id = :id")
    suspend fun getById(id: String): BoardEntity?

    @Query("SELECT COUNT(*) FROM boards")
    suspend fun count(): Int

    @Query("SELECT COALESCE(MAX(position), -1) FROM boards")
    suspend fun maxPosition(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(board: BoardEntity)

    @Update
    suspend fun update(board: BoardEntity)

    /**
     * Updates rows in place, for reordering. Never an upsert with REPLACE: that
     * deletes and re-inserts each row, and every category on the board would go
     * with it through `ON DELETE CASCADE`, taking its pictos too.
     */
    @Update
    suspend fun updateAll(boards: List<BoardEntity>)

    @Delete
    suspend fun delete(board: BoardEntity)

    /**
     * Makes [id] the board in use.
     *
     * One statement rather than a clear-then-set pair, so there is no instant
     * at which no board is active — which for the keyboard would mean an empty
     * grid appearing mid-conversation.
     */
    @Query("UPDATE boards SET active = CASE WHEN id = :id THEN 1 ELSE 0 END")
    suspend fun setActive(id: String)
}
