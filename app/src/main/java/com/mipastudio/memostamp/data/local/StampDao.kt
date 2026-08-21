package com.mipastudio.memostamp.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface StampDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(stamp: StampEntity): Long

    @Update
    fun update(stamp: StampEntity): Int

    @Delete
    fun delete(stamp: StampEntity): Int

    @Query("DELETE FROM stamps WHERE id = :id")
    fun deleteById(id: String): Int

    @Query("SELECT * FROM stamps ORDER BY memoryDate DESC")
    fun observeStamps(): Flow<List<StampEntity>>

    @Query("SELECT * FROM stamps ORDER BY memoryDate DESC")
    fun getAllStampsList(): List<StampEntity>

    @Query("SELECT * FROM stamps WHERE id = :id")
    fun getStampById(id: String): StampEntity?
}
