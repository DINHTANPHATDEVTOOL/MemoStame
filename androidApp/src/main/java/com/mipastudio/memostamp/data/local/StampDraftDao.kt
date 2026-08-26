package com.mipastudio.memostamp.data.local

import androidx.room.*

@Dao
interface StampDraftDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertDraft(draft: StampDraftEntity): Long

    @Query("SELECT * FROM drafts WHERE id = :id")
    fun getDraftById(id: String): StampDraftEntity?

    @Query("SELECT * FROM drafts WHERE id = :id AND (ownerId = :ownerId OR ownerId = '' OR ownerId IS NULL)")
    fun getDraftById(id: String, ownerId: String): StampDraftEntity?

    @Query("SELECT * FROM drafts ORDER BY createdAt DESC LIMIT 1")
    fun getNewestDraft(): StampDraftEntity?

    @Query("SELECT * FROM drafts WHERE (ownerId = :ownerId OR ownerId = '' OR ownerId IS NULL) ORDER BY createdAt DESC LIMIT 1")
    fun getNewestDraft(ownerId: String): StampDraftEntity?

    @Query("SELECT * FROM drafts WHERE createdAt < :cutoffTimestamp")
    fun getExpiredDrafts(cutoffTimestamp: Long): List<StampDraftEntity>

    @Query("SELECT * FROM drafts WHERE createdAt < :cutoffTimestamp AND (ownerId = :ownerId OR ownerId = '' OR ownerId IS NULL)")
    fun getExpiredDrafts(cutoffTimestamp: Long, ownerId: String): List<StampDraftEntity>

    @Query("DELETE FROM drafts WHERE createdAt < :cutoffTimestamp")
    fun deleteExpiredDrafts(cutoffTimestamp: Long): Int

    @Query("DELETE FROM drafts WHERE createdAt < :cutoffTimestamp AND (ownerId = :ownerId OR ownerId = '' OR ownerId IS NULL)")
    fun deleteExpiredDrafts(cutoffTimestamp: Long, ownerId: String): Int

    @Query("DELETE FROM drafts WHERE id = :id")
    fun deleteDraftById(id: String): Int

    @Query("DELETE FROM drafts WHERE id = :id AND (ownerId = :ownerId OR ownerId = '' OR ownerId IS NULL)")
    fun deleteDraftById(id: String, ownerId: String): Int

    @Query("DELETE FROM drafts")
    fun deleteAllDrafts(): Int
}
