package com.mipastudio.memostamp.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface CollectionDao {
    @Query("SELECT * FROM collections ORDER BY sortOrder ASC, createdAt DESC")
    fun observeCollections(): Flow<List<CollectionEntity>>

    @Query("SELECT * FROM collections WHERE (ownerId = :ownerId OR ownerId = '' OR ownerId IS NULL) ORDER BY sortOrder ASC, createdAt DESC")
    fun observeCollectionsByOwner(ownerId: String): Flow<List<CollectionEntity>>

    @Query("SELECT * FROM collections WHERE id = :id LIMIT 1")
    fun getCollectionById(id: String): CollectionEntity?

    @Query("SELECT * FROM collections WHERE id = :id AND (ownerId = :ownerId OR ownerId = '' OR ownerId IS NULL) LIMIT 1")
    fun getCollectionById(id: String, ownerId: String): CollectionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertCollection(collection: CollectionEntity): Long

    @Update
    fun updateCollection(collection: CollectionEntity): Int

    @Query("SELECT COUNT(*) FROM collections")
    fun getCollectionCount(): Int

    @Query("SELECT COUNT(*) FROM collections WHERE (ownerId = :ownerId OR ownerId = '' OR ownerId IS NULL)")
    fun getCollectionCountByOwner(ownerId: String): Int

    @Query("DELETE FROM collections WHERE id = :id")
    fun deleteCollectionById(id: String): Int

    @Query("DELETE FROM collections WHERE id = :id AND (ownerId = :ownerId OR ownerId = '' OR ownerId IS NULL)")
    fun deleteCollectionById(id: String, ownerId: String): Int
}
