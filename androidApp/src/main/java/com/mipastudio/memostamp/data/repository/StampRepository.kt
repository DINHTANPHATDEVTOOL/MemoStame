package com.mipastudio.memostamp.data.repository

import android.content.Context
import com.mipastudio.memostamp.data.local.MemoStampDatabase
import com.mipastudio.memostamp.data.local.StampDao
import com.mipastudio.memostamp.data.local.StampDraftDao
import com.mipastudio.memostamp.data.local.StampDraftEntity
import com.mipastudio.memostamp.data.local.StampEntity
import com.mipastudio.memostamp.domain.model.StampDraft
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

import androidx.room.withTransaction
import com.mipastudio.memostamp.data.local.CollectionDao
import com.mipastudio.memostamp.data.local.CollectionEntity
import com.mipastudio.memostamp.data.remote.CloudSyncEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

import kotlinx.coroutines.flow.combine

class StampRepository private constructor(
    private val context: Context,
    private val database: MemoStampDatabase,
    private val stampDao: StampDao,
    private val stampDraftDao: StampDraftDao,
    private val collectionDao: CollectionDao
) {
    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    private val authRepo by lazy { UserAuthRepository.getInstance(context) }

    private fun triggerCloudAutoSync() {
        coroutineScope.launch {
            try {
                CloudSyncEngine.getInstance(context).performFullCloudSync()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    suspend fun saveDraft(draft: StampDraft): String = withContext(Dispatchers.IO) {
        val id = UUID.randomUUID().toString()
        val currentUserId = authRepo.currentUser.value.userId
        val entity = StampDraftEntity(
            id = id,
            ownerId = currentUserId,
            originalImagePath = draft.originalImagePath,
            croppedImagePath = draft.croppedImagePath,
            renderedImagePath = draft.renderedImagePath,
            createdAt = System.currentTimeMillis(),
            title = draft.title,
            note = draft.note,
            memoryDate = draft.memoryDate,
            location = draft.location,
            mood = draft.mood,
            collectionId = draft.collectionId,
            filterId = draft.filterId,
            filterIntensity = draft.filterIntensity,
            filterSpecJson = draft.filterSpecJson
        )
        stampDraftDao.insertDraft(entity)
        id
    }

    suspend fun getDraft(id: String): StampDraft? = withContext(Dispatchers.IO) {
        val currentUserId = authRepo.currentUser.value.userId
        val entity = stampDraftDao.getDraftById(id, currentUserId)
            ?: stampDraftDao.getDraftById(id)?.takeIf { it.ownerId == currentUserId || it.ownerId.isBlank() }
            ?: return@withContext null
        StampDraft(
            id = entity.id,
            originalImagePath = entity.originalImagePath,
            croppedImagePath = entity.croppedImagePath,
            renderedImagePath = entity.renderedImagePath,
            title = entity.title,
            location = entity.location ?: "",
            memoryDate = entity.memoryDate,
            mood = entity.mood ?: "✨",
            note = entity.note,
            collectionId = entity.collectionId,
            filterId = entity.filterId,
            filterIntensity = entity.filterIntensity,
            filterSpecJson = entity.filterSpecJson
        )
    }

    suspend fun getNewestDraft(): Pair<String, StampDraft>? = withContext(Dispatchers.IO) {
        cleanupExpiredDrafts()
        val currentUserId = authRepo.currentUser.value.userId
        val entity = stampDraftDao.getNewestDraft(currentUserId)
            ?: stampDraftDao.getNewestDraft()?.takeIf { it.ownerId == currentUserId || it.ownerId.isBlank() }
            ?: return@withContext null
        val draft = StampDraft(
            id = entity.id,
            originalImagePath = entity.originalImagePath,
            croppedImagePath = entity.croppedImagePath,
            renderedImagePath = entity.renderedImagePath,
            title = entity.title,
            location = entity.location ?: "",
            memoryDate = entity.memoryDate,
            mood = entity.mood ?: "✨",
            note = entity.note,
            collectionId = entity.collectionId,
            filterId = entity.filterId,
            filterIntensity = entity.filterIntensity,
            filterSpecJson = entity.filterSpecJson
        )
        Pair(entity.id, draft)
    }

    suspend fun cleanupExpiredDrafts() = withContext(Dispatchers.IO) {
        val cutoff = System.currentTimeMillis() - 24 * 60 * 60 * 1000L
        val currentUserId = authRepo.currentUser.value.userId
        val expired = stampDraftDao.getExpiredDrafts(cutoff, currentUserId)
        for (entity in expired) {
            try {
                val orig = File(entity.originalImagePath)
                if (orig.exists()) orig.delete()
                val stamp = File(entity.renderedImagePath)
                if (stamp.exists()) stamp.delete()
                entity.croppedImagePath?.let { path ->
                    val cropped = File(path)
                    if (cropped.exists()) cropped.delete()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        stampDraftDao.deleteExpiredDrafts(cutoff, currentUserId)
    }

    suspend fun removeDraft(id: String) = withContext(Dispatchers.IO) {
        val currentUserId = authRepo.currentUser.value.userId
        val entity = stampDraftDao.getDraftById(id, currentUserId)
            ?: stampDraftDao.getDraftById(id)?.takeIf { it.ownerId == currentUserId || it.ownerId.isBlank() }
        if (entity != null) {
            stampDraftDao.deleteDraftById(id, currentUserId)
            try {
                val origFile = File(entity.originalImagePath)
                if (origFile.exists()) origFile.delete()
                val stampFile = File(entity.renderedImagePath)
                if (stampFile.exists()) stampFile.delete()
                entity.croppedImagePath?.let { path ->
                    val croppedFile = File(path)
                    if (croppedFile.exists()) croppedFile.delete()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun observeStamps(): Flow<List<StampEntity>> = combine(stampDao.observeStamps(), authRepo.currentUser) { stamps, user ->
        stamps.filter { it.ownerId == user.userId || (it.ownerId.isBlank() && user.userId == "user_phat_main") }
    }

    suspend fun getStampById(id: String): Result<StampEntity?> = withContext(Dispatchers.IO) {
        try {
            val currentUserId = authRepo.currentUser.value.userId
            val entity = stampDao.getStampById(id, currentUserId)
                ?: stampDao.getStampById(id)?.takeIf { it.ownerId == currentUserId || it.ownerId.isBlank() }
            if (entity != null && entity.ownerId.isNotBlank() && entity.ownerId != currentUserId) {
                return@withContext Result.failure(SecurityException("Unauthorized access to stamp"))
            }
            Result.success(entity)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    suspend fun saveStamp(draft: StampDraft, draftId: String? = null): Result<StampEntity> = withContext(Dispatchers.IO) {
        try {
            val currentUserId = authRepo.currentUser.value.userId
            val entity = StampEntity(
                id = UUID.randomUUID().toString(),
                ownerId = currentUserId,
                originalImagePath = draft.originalImagePath,
                croppedImagePath = draft.croppedImagePath,
                stampImagePath = draft.renderedImagePath,
                title = draft.title,
                note = draft.note,
                createdAt = System.currentTimeMillis(),
                memoryDate = draft.memoryDate,
                location = draft.location,
                mood = draft.mood,
                collectionId = draft.collectionId,
                favorite = false,
                filterId = draft.filterId,
                filterIntensity = draft.filterIntensity,
                filterSpecJson = draft.filterSpecJson
            )
            database.withTransaction {
                val insertedId = stampDao.insert(entity)
                if (insertedId <= -1) {
                    throw IllegalStateException("Database insert failed")
                }
                if (!draftId.isNullOrBlank()) {
                    stampDraftDao.deleteDraftById(draftId, currentUserId)
                }
            }
            triggerCloudAutoSync()
            Result.success(entity)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    suspend fun updateStamp(stamp: StampEntity): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val currentUserId = authRepo.currentUser.value.userId
            if (stamp.ownerId.isNotBlank() && stamp.ownerId != currentUserId) {
                return@withContext Result.failure(SecurityException("Unauthorized access to stamp"))
            }
            val updatedRows = stampDao.update(stamp)
            if (updatedRows > 0) {
                triggerCloudAutoSync()
                Result.success(Unit)
            } else {
                Result.failure(IllegalStateException("Database update failed"))
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    suspend fun deleteStamp(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val currentUserId = authRepo.currentUser.value.userId
            val entity = stampDao.getStampById(id, currentUserId)
                ?: stampDao.getStampById(id)?.takeIf { it.ownerId == currentUserId || it.ownerId.isBlank() }
            if (entity == null || (entity.ownerId.isNotBlank() && entity.ownerId != currentUserId)) {
                return@withContext Result.failure(SecurityException("Unauthorized or stamp not found"))
            }
            val deletedRows = stampDao.deleteById(id, currentUserId)
            if (deletedRows > 0) {
                try {
                    val origFile = File(entity.originalImagePath)
                    if (origFile.exists()) origFile.delete()
                    val stampFile = File(entity.stampImagePath)
                    if (stampFile.exists()) stampFile.delete()
                    entity.croppedImagePath?.let { path ->
                        val croppedFile = File(path)
                        if (croppedFile.exists()) croppedFile.delete()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                triggerCloudAutoSync()
                Result.success(Unit)
            } else {
                Result.failure(IllegalStateException("No stamp found to delete with id: $id"))
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    suspend fun ensureDefaultCollections() = withContext(Dispatchers.IO) {
        val currentUserId = authRepo.currentUser.value.userId
        if (collectionDao.getCollectionCountByOwner(currentUserId) == 0) {
            val defaults = listOf(
                CollectionEntity("col_${currentUserId}_travel_default", currentUserId, "Travel & Places", "Destinations, journeys and outdoor adventures", "✈️", null, System.currentTimeMillis(), 0, "SPECIAL", 12),
                CollectionEntity("col_${currentUserId}_coffee_default", currentUserId, "Coffee & Food", "Cafes, meals and culinary experiences", "☕", null, System.currentTimeMillis(), 1, "NORMAL", 10),
                CollectionEntity("col_${currentUserId}_daily_default", currentUserId, "Daily Life", "Everyday moments and small joys", "🌿", null, System.currentTimeMillis(), 2, "NORMAL", 15),
                CollectionEntity("col_${currentUserId}_special_default", currentUserId, "Special Moments", "Anniversaries, celebrations and milestones", "🎉", null, System.currentTimeMillis(), 3, "SERIES", 8)
            )
            for (col in defaults) {
                collectionDao.insertCollection(col)
            }
        }
    }

    fun observeCollections(): Flow<List<CollectionEntity>> = combine(collectionDao.observeCollections(), authRepo.currentUser) { collections, user ->
        collections.filter { it.ownerId == user.userId || (it.ownerId.isBlank() && user.userId == "user_phat_main") }
    }

    suspend fun createCollection(
        name: String,
        description: String? = null,
        iconEmoji: String? = "📁",
        type: String = "NORMAL",
        targetCount: Int = 12
    ): String = withContext(Dispatchers.IO) {
        val id = UUID.randomUUID().toString()
        val currentUserId = authRepo.currentUser.value.userId
        val entity = CollectionEntity(
            id = id,
            ownerId = currentUserId,
            name = name,
            description = description,
            iconEmoji = iconEmoji,
            createdAt = System.currentTimeMillis(),
            collectionType = type,
            targetCount = targetCount
        )
        collectionDao.insertCollection(entity)
        id
    }

    suspend fun updateStampCollection(stampId: String, collectionId: String?): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val currentUserId = authRepo.currentUser.value.userId
            val stamp = stampDao.getStampById(stampId, currentUserId)
                ?: stampDao.getStampById(stampId)?.takeIf { it.ownerId == currentUserId || it.ownerId.isBlank() }
                ?: return@withContext Result.failure(IllegalArgumentException("Stamp not found"))
            if (stamp.ownerId.isNotBlank() && stamp.ownerId != currentUserId) {
                return@withContext Result.failure(SecurityException("Unauthorized access to stamp"))
            }
            val updated = stamp.copy(collectionId = collectionId)
            stampDao.update(updated)
            triggerCloudAutoSync()
            Result.success(Unit)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    suspend fun toggleCollectionPrivacy(collectionId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val currentUserId = authRepo.currentUser.value.userId
            val col = collectionDao.getCollectionById(collectionId, currentUserId)
                ?: collectionDao.getCollectionById(collectionId)?.takeIf { it.ownerId == currentUserId || it.ownerId.isBlank() }
                ?: return@withContext Result.failure(IllegalArgumentException("Collection not found"))
            if (col.ownerId.isNotBlank() && col.ownerId != currentUserId) {
                return@withContext Result.failure(SecurityException("Unauthorized access to collection"))
            }
            val newPrivacy = if (col.privacy == "ONLY_ME") "FRIENDS" else "ONLY_ME"
            val updated = col.copy(privacy = newPrivacy)
            collectionDao.updateCollection(updated)
            Result.success(Unit)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: StampRepository? = null

        fun getInstance(context: Context): StampRepository {
            return INSTANCE ?: synchronized(this) {
                val appContext = context.applicationContext
                val db = MemoStampDatabase.getInstance(appContext)
                val instance = StampRepository(appContext, db, db.stampDao(), db.stampDraftDao(), db.collectionDao())
                INSTANCE = instance
                instance
            }
        }
    }
}
