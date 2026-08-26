package com.mipastudio.memostamp.data.local

import androidx.room.*

@Dao
interface StampDraftDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertDraft(draft: StampDraftEntity): Long

    @Query("SELECT * FROM drafts WHERE id = :id AND ownerId = :ownerId")
    fun getDraftById(id: String, ownerId: String): StampDraftEntity?

    @Query("SELECT * FROM drafts WHERE ownerId = :ownerId ORDER BY createdAt DESC LIMIT 1")
    fun getNewestDraft(ownerId: String): StampDraftEntity?

    @Query("SELECT * FROM drafts WHERE createdAt < :cutoffTimestamp AND ownerId = :ownerId")
    fun getExpiredDrafts(cutoffTimestamp: Long, ownerId: String): List<StampDraftEntity>

    @Query("DELETE FROM drafts WHERE createdAt < :cutoffTimestamp AND ownerId = :ownerId")
    fun deleteExpiredDrafts(cutoffTimestamp: Long, ownerId: String): Int

    @Query("DELETE FROM drafts WHERE id = :id AND ownerId = :ownerId")
    fun deleteDraftById(id: String, ownerId: String): Int

    @Query("DELETE FROM drafts WHERE ownerId = :ownerId")
    fun deleteAllDraftsByOwner(ownerId: String): Int
}
