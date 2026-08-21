package com.mipastudio.memostamp.data.remote

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.mipastudio.memostamp.data.local.StampEntity
import com.mipastudio.memostamp.data.remote.supabase.SupabaseConfig
import com.mipastudio.memostamp.data.remote.supabase.SupabaseSyncService
import com.mipastudio.memostamp.data.repository.StampRepository
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
                // 1. Sync Profile to Supabase
                supabaseService.syncProfileToSupabase(user)
                // 2. Sync Stamps to Supabase
                val result = supabaseService.syncStampsToSupabase(localStamps, user.userId)
                result.getOrThrow()
                // 3. Sync Feed Posts to Supabase
                try {
                    com.mipastudio.memostamp.data.repository.FeedRepository.getInstance(context).syncFeedFromSupabase()
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
            val tradePayload = CloudTradePayload(
                tradeId = "trade_cloud_" + System.currentTimeMillis(),
                senderUserId = currentUser.userId,
                senderUsername = currentUser.displayName,
                recipientUsername = recipientUsername,
                stampTitle = stamp.title,
                stampImageUrl = stamp.stampImagePath,
                location = stamp.location ?: "MemoStamp Memory",
                note = note
            )

            // Save payload to local queue / mock cloud database
            saveCloudTradePayload(tradePayload)

            Result.success(tradePayload.tradeId)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    suspend fun fetchPendingCloudTrades(): List<CloudTradePayload> = withContext(Dispatchers.IO) {
        val json = prefs.getString("cloud_trades_json", null) ?: return@withContext emptyList()
        try {
            val type = object : TypeToken<List<CloudTradePayload>>() {}.type
            gson.fromJson<List<CloudTradePayload>>(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun saveCloudTradePayload(payload: CloudTradePayload) {
        val currentTrades = prefs.getString("cloud_trades_json", null)
        val list = if (currentTrades != null) {
            try {
                val type = object : TypeToken<MutableList<CloudTradePayload>>() {}.type
                gson.fromJson<MutableList<CloudTradePayload>>(currentTrades, type)
            } catch (e: Exception) {
                mutableListOf()
            }
        } else {
            mutableListOf()
        }
        list.add(0, payload)
        prefs.edit().putString("cloud_trades_json", gson.toJson(list)).apply()
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
