package org.pictokeyboard.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** One row of [PictoDao.observeCountsByBoard]. */
data class BoardPictoCount(val boardId: String, val pictoCount: Int)

/** One row of [PictoDao.observeCountsByCategory]. */
data class CategoryPictoCount(val categoryId: String, val pictoCount: Int)

@Dao
interface PictoDao {

    @Query("SELECT * FROM pictos WHERE categoryId = :categoryId ORDER BY position ASC")
    fun observeByCategory(categoryId: String): Flow<List<PictoEntity>>

    @Query("SELECT * FROM pictos ORDER BY position ASC")
    suspend fun getAll(): List<PictoEntity>

    @Query("SELECT COUNT(*) FROM pictos")
    fun observeCount(): Flow<Int>

    /**
     * How many pictograms each board holds, for the counts on its card.
     *
     * A LEFT JOIN from categories so a board whose categories are all empty
     * still reports 0 rather than being absent from the result — the boards
     * list has to render a card for it either way.
     */
    @Query(
        "SELECT c.boardId AS boardId, COUNT(p.id) AS pictoCount " +
            "FROM categories c LEFT JOIN pictos p ON p.categoryId = c.id " +
            "GROUP BY c.boardId",
    )
    fun observeCountsByBoard(): Flow<List<BoardPictoCount>>

    /**
     * How many pictograms each category holds, for the count on its row of the
     * board's Categories list.
     *
     * A LEFT JOIN from categories for the same reason as [observeCountsByBoard]:
     * an empty category is exactly the one a caregiver is looking for, so it has
     * to report 0 rather than drop out of the result.
     */
    @Query(
        "SELECT c.id AS categoryId, COUNT(p.id) AS pictoCount " +
            "FROM categories c LEFT JOIN pictos p ON p.categoryId = c.id " +
            "GROUP BY c.id",
    )
    fun observeCountsByCategory(): Flow<List<CategoryPictoCount>>

    /**
     * Pictos of several categories at once, for the miniatures on the boards
     * list: one query for every card rather than one subscription per card.
     */
    @Query("SELECT * FROM pictos WHERE categoryId IN (:categoryIds) ORDER BY position ASC")
    fun observeByCategories(categoryIds: List<String>): Flow<List<PictoEntity>>

    @Query("SELECT * FROM pictos WHERE categoryId = :categoryId ORDER BY position ASC")
    suspend fun getByCategory(categoryId: String): List<PictoEntity>

    @Query("SELECT COALESCE(MAX(position), -1) FROM pictos WHERE categoryId = :categoryId")
    suspend fun maxPosition(categoryId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(picto: PictoEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(pictos: List<PictoEntity>)

    @Update
    suspend fun update(picto: PictoEntity)

    /** Updates rows in place (by primary key); used for reordering. */
    @Update
    suspend fun updateAll(pictos: List<PictoEntity>)

    @Delete
    suspend fun delete(picto: PictoEntity)

    @Query("DELETE FROM pictos")
    suspend fun clear()
}
