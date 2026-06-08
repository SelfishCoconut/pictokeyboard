package org.pictokeyboard.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PictoDao {

    @Query("SELECT * FROM pictos WHERE categoryId = :categoryId ORDER BY position ASC")
    fun observeByCategory(categoryId: String): Flow<List<PictoEntity>>

    @Query("SELECT * FROM pictos ORDER BY position ASC")
    suspend fun getAll(): List<PictoEntity>

    @Query("SELECT COUNT(*) FROM pictos")
    fun observeCount(): Flow<Int>

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
