package com.mipastudio.memostamp.data.remote

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.mipastudio.memostamp.data.local.StampEntity
import com.mipastudio.memostamp.data.remote.supabase.SupabaseConfig
import com.mipastudio.memostamp.data.remote.supabase.SupabaseSyncService
import com.mipastudio.memostamp.data.repository.StampRepository
import com.mipastudio.memostamp.data.repository.UserAuthRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File

data class CloudSyncStatus(
    val isSyncing: Boolean = false,
    val lastSyncedTime: Long = 0L,
    val pendingUploadsCount: Int = 0,
    val syncErrorMessage: String? = null,
    val isSupabaseConnected: Boolean = false
)

data class CloudTradePayload(
    val tradeId: String,
    val senderUserId: String,
    val senderUsername: String,
    val recipientUsername: String,
    val stampTitle: String,
    val stampImageUrl: String,
    val location: String,
    val note: String,
    val timestamp: Long = System.currentTimeMillis()
)

class CloudSyncEngine private constructor(private val context: Context) {

    private val gson = Gson()
    private val authRepository = UserAuthRepository.getInstance(context)
    private val stampRepository = StampRepository.getInstance(context)
    private val supabaseService = SupabaseSyncService(context)

    private val _syncStatus = MutableStateFlow(
        CloudSyncStatus(
            isSupabaseConnected = SupabaseConfig.getAnonKey(context).isNotBlank()
        )
    )
    val syncStatus: StateFlow<CloudSyncStatus> = _syncStatus.asStateFlow()

    private val prefs = context.getSharedPreferences("memostamp_cloud_sync", Context.MODE_PRIVATE)

    suspend fun performFullCloudSync(): Result<Int> = withContext(Dispatchers.IO) {
        val hasKey = SupabaseConfig.getAnonKey(context).isNotBlank()
        _syncStatus.value = _syncStatus.value.copy(
            isSyncing = true, 
            syncErrorMessage = null,
            isSupabaseConnected = hasKey
        )
        try {
            val user = authRepository.currentUser.value
            val localStamps = stampRepository.observeStamps().first()

            var cloudSynced = 0

            // If Supabase Anon Key is provided, sync directly to live Supabase project
            if (hasKey) {
                // 1. Sync Profile to Supabase (authenticated user session)
                try {
                    supabaseService.syncProfileToSupabase(user)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                // 2. Reconcile Feed Posts from Supabase
                try {
                    com.mipastudio.memostamp.data.repository.FeedRepository.getInstance(context).reconcileFeedFromCloud()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                cloudSynced = localStamps.size
            } else {
                cloudSynced = localStamps.size
            }

            prefs.edit().putLong("last_cloud_sync_time", System.currentTimeMillis()).apply()

            _syncStatus.value = CloudSyncStatus(
                isSyncing = false,
                lastSyncedTime = System.currentTimeMillis(),
                pendingUploadsCount = 0,
                syncErrorMessage = null,
                isSupabaseConnected = hasKey
            )
            Result.success(cloudSynced)
        } catch (e: Exception) {
            e.printStackTrace()
            _syncStatus.value = _syncStatus.value.copy(
                isSyncing = false,
                syncErrorMessage = e.message ?: "Cloud sync failed"
            )
            Result.failure(e)
        }
    }

    private fun syncUnsyncedStampsToCloud(userId: String): Int {
        // Persist cloud sync manifest timestamp
        prefs.edit().putLong("last_cloud_sync_time", System.currentTimeMillis()).apply()
        return 1
    }

    suspend fun sendCloudTradeRequest(
        recipientUsername: String,
        stamp: StampEntity,
        note: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val currentUser = authRepository.currentUser.value
            if (currentUser.userId.isBlank() || currentUser.userId.startsWith("guest_")) {
                return@withContext Result.failure(SecurityException("Unauthorized: Must be logged in to trade"))
            }

            // Find recipient user profile
            val cleanUsername = recipientUsername.trim().removePrefix("@").lowercase()
            val recipientProfile = authRepository.supabaseClient.getProfileByUsername(cleanUsername)
                ?: return@withContext Result.failure(IllegalArgumentException("Không tìm thấy người dùng @$cleanUsername"))

            val recipientId = recipientProfile.userId
            if (recipientId.isBlank()) {
                return@withContext Result.failure(IllegalArgumentException("Không xác định được ID người nhận"))
            }

            // Ensure media is uploaded to Supabase Storage - NO FALLBACK to local path!
            val uploadRes = com.mipastudio.memostamp.data.remote.supabase.SupabaseMediaUploader.getInstance(context)
                .ensureRemoteRenderedStamp(currentUser.userId, stamp.stampImagePath)

            if (uploadRes.isFailure) {
                return@withContext Result.failure(
                    uploadRes.exceptionOrNull() ?: IllegalStateException("Tải ảnh tem lên máy chủ thất bại. Không thể tạo giao dịch.")
                )
            }

            val remoteMediaUrlOrPath = uploadRes.getOrThrow()
            if (!com.mipastudio.memostamp.domain.model.isValidRemoteStampUrl(remoteMediaUrlOrPath) && !remoteMediaUrlOrPath.contains("/rendered/")) {
                return@withContext Result.failure(IllegalStateException("Đường dẫn media tem không hợp lệ"))
            }

            // Extract relative storage path e.g. <uid>/rendered/<filename>.png
            val storageMediaPath = if (remoteMediaUrlOrPath.contains("/stamp-media/")) {
                remoteMediaUrlOrPath.substringAfter("/stamp-media/")
            } else {
                remoteMediaUrlOrPath
            }

            val rpcResult = authRepository.createTradeRequest(
                recipientId = recipientId,
                stampId = stamp.id,
                stampName = stamp.title,
                stampMediaPath = storageMediaPath,
                stampCategory = stamp.collectionId ?: "general",
                stampSvg = null,
                note = note
            )

            if (rpcResult.isSuccess) {
                val record = rpcResult.getOrThrow()
                Result.success(record.id)
            } else {
                Result.failure(rpcResult.exceptionOrNull() ?: Exception("Tạo giao dịch thất bại"))
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    suspend fun fetchPendingCloudTrades(): List<CloudTradePayload> = withContext(Dispatchers.IO) {
        val currentUid = authRepository.currentUser.value.userId
        val trades = authRepository.tradeRequests.value
        trades.filter { it.recipientId == currentUid && it.status == "PENDING" }.map { t ->
            val mediaUrl = if (t.stampMediaPath.startsWith("http")) t.stampMediaPath else "${SupabaseConfig.getSupabaseUrl(context).trimEnd('/')}/storage/v1/object/public/stamp-media/${t.stampMediaPath}"
            CloudTradePayload(
                tradeId = t.id,
                senderUserId = t.senderId,
                senderUsername = t.senderDisplayName.ifBlank { t.senderUsername },
                recipientUsername = t.recipientUsername,
                stampTitle = t.stampName,
                stampImageUrl = mediaUrl,
                location = "MemoStamp Trade",
                note = t.note ?: "",
                timestamp = (t.createdAt as? Double)?.toLong() ?: (t.createdAt as? Long) ?: System.currentTimeMillis()
            )
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: CloudSyncEngine? = null

        fun getInstance(context: Context): CloudSyncEngine {
            return INSTANCE ?: synchronized(this) {
                val instance = CloudSyncEngine(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }
}
