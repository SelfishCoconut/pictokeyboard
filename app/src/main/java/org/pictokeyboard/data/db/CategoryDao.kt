package org.pictokeyboard.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {

    @Query("SELECT * FROM categories ORDER BY position ASC")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE boardId = :boardId ORDER BY position ASC")
    fun observeByBoard(boardId: String): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories ORDER BY position ASC")
    suspend fun getAll(): List<CategoryEntity>

    @Query("SELECT * FROM categories WHERE boardId = :boardId ORDER BY position ASC")
    suspend fun getByBoard(boardId: String): List<CategoryEntity>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun getById(id: String): CategoryEntity?

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun count(): Int

    /**
     * Positions are per board, so the next one has to be computed within a
     * board — a global MAX would leave gaps and, once a second board exists,
     * make every new category sort after every category on every other board.
     */
    @Query("SELECT COALESCE(MAX(position), -1) FROM categories WHERE boardId = :boardId")
    suspend fun maxPosition(boardId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(category: CategoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(categories: List<CategoryEntity>)

    @Update
    suspend fun update(category: CategoryEntity)

    /**
     * Updates rows in place (by primary key). Used for reordering — unlike an
     * upsert with REPLACE, this never deletes the row, so the pictos that
     * reference it via ON DELETE CASCADE are left untouched.
     */
    @Update
    suspend fun updateAll(categories: List<CategoryEntity>)

    @Delete
    suspend fun delete(category: CategoryEntity)

    @Query("DELETE FROM categories")
    suspend fun clear()
}
