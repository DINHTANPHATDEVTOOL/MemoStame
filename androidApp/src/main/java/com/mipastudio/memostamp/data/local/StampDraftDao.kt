package com.mipastudio.memostamp.data.local

import androidx.room.*

@Dao
interface StampDraftDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertDraft(draft: StampDraftEntity): Long

    @Query("SELECT * FROM drafts WHERE id = :id")
    fun getDraftById(id: String): StampDraftEntity?

    @Query("SELECT * FROM drafts ORDER BY createdAt DESC LIMIT 1")
    fun getNewestDraft(): StampDraftEntity?

    @Query("SELECT * FROM drafts WHERE createdAt < :cutoffTimestamp")
    fun getExpiredDrafts(cutoffTimestamp: Long): List<StampDraftEntity>

    @Query("DELETE FROM drafts WHERE createdAt < :cutoffTimestamp")
    fun deleteExpiredDrafts(cutoffTimestamp: Long): Int

    @Query("DELETE FROM drafts WHERE id = :id")
    fun deleteDraftById(id: String): Int

    @Query("DELETE FROM drafts")
    fun deleteAllDrafts(): Int
}
