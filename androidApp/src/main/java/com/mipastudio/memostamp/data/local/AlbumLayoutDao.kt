package com.mipastudio.memostamp.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AlbumLayoutDao {

    @Query("SELECT * FROM album_pages WHERE ownerId = :ownerId AND albumId = :albumId ORDER BY pageIndex ASC")
    fun observePages(ownerId: String, albumId: String): Flow<List<AlbumPageEntity>>

    @Query("SELECT * FROM album_pages WHERE ownerId = :ownerId AND albumId = :albumId ORDER BY pageIndex ASC")
    suspend fun getPages(ownerId: String, albumId: String): List<AlbumPageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPages(pages: List<AlbumPageEntity>)

    @Query("DELETE FROM album_pages WHERE ownerId = :ownerId AND albumId = :albumId")
    suspend fun deletePagesForAlbum(ownerId: String, albumId: String)

    @Query("DELETE FROM album_pages WHERE id = :pageId AND ownerId = :ownerId")
    suspend fun deletePage(pageId: String, ownerId: String)

    @Query("SELECT * FROM album_stamp_placements WHERE ownerId = :ownerId AND albumId = :albumId ORDER BY pageIndex ASC, zIndex ASC, id ASC")
    fun observePlacements(ownerId: String, albumId: String): Flow<List<StampPlacementEntity>>

    @Query("SELECT * FROM album_stamp_placements WHERE ownerId = :ownerId AND albumId = :albumId ORDER BY pageIndex ASC, zIndex ASC, id ASC")
    suspend fun getPlacements(ownerId: String, albumId: String): List<StampPlacementEntity>

    @Query("SELECT * FROM album_stamp_placements WHERE ownerId = :ownerId AND albumId = :albumId AND pageIndex = :pageIndex ORDER BY zIndex ASC, id ASC")
    suspend fun getPlacementsForPage(ownerId: String, albumId: String, pageIndex: Int): List<StampPlacementEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPlacements(placements: List<StampPlacementEntity>)

    @Query("DELETE FROM album_stamp_placements WHERE id = :placementId AND ownerId = :ownerId")
    suspend fun deletePlacement(placementId: String, ownerId: String)

    @Query("DELETE FROM album_stamp_placements WHERE ownerId = :ownerId AND albumId = :albumId")
    suspend fun deletePlacementsForAlbum(ownerId: String, albumId: String)

    @Query("DELETE FROM album_pages WHERE ownerId = :ownerId")
    suspend fun deleteAllPagesForOwner(ownerId: String)

    @Query("DELETE FROM album_stamp_placements WHERE ownerId = :ownerId")
    suspend fun deleteAllPlacementsForOwner(ownerId: String)
}
