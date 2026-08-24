package com.mipastudio.memostamp.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface CollectionDao {
    @Query("SELECT * FROM collections ORDER BY sortOrder ASC, createdAt DESC")
    fun observeCollections(): Flow<List<CollectionEntity>>

    @Query("SELECT * FROM collections WHERE id = :id LIMIT 1")
    fun getCollectionById(id: String): CollectionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertCollection(collection: CollectionEntity): Long

    @Update
    fun updateCollection(collection: CollectionEntity): Int

    @Query("SELECT COUNT(*) FROM collections")
    fun getCollectionCount(): Int

    @Query("DELETE FROM collections WHERE id = :id")
    fun deleteCollectionById(id: String): Int
}
