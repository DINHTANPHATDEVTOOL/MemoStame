package com.mipastudio.memostamp.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.mipastudio.memostamp.core.notification.InAppBanner
import com.mipastudio.memostamp.core.notification.InAppNotificationManager
import com.mipastudio.memostamp.core.notification.MemoStampNotificationManager
import com.mipastudio.memostamp.data.local.MemoStampDatabase
import com.mipastudio.memostamp.data.local.UserDao
import com.mipastudio.memostamp.data.local.UserEntity
import com.mipastudio.memostamp.data.remote.supabase.AndroidAuthSession
import com.mipastudio.memostamp.data.remote.supabase.AndroidAuthSessionStore
import com.mipastudio.memostamp.data.remote.supabase.SupabaseAuthService
import com.mipastudio.memostamp.data.remote.supabase.SupabaseClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

data class UserProfile(
    val userId: String = "guest_visitor",
    val username: String = "guest_visitor",
    val displayName: String = "Khách du hành",
    val email: String = "",
    val avatarUrl: String = "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=300",
    val coverUrl: String = "https://images.unsplash.com/photo-1506744038136-46273834b3fb?w=1200",
    val bio: String = "Sưu tầm ký ức qua từng con tem bưu chính 📮",
    val city: String = "Đà Lạt",
    val isCloudSynced: Boolean = true,
    val totalStampsCount: Int = 0
) {
    fun sanitized(): UserProfile = copy(
        userId = if (userId.isNullOrBlank()) "guest_visitor" else userId,
        username = if (username.isNullOrBlank()) "guest_visitor" else username,
        displayName = if (displayName.isNullOrBlank()) "Khách du hành" else displayName,
        email = email ?: "",
        avatarUrl = if (avatarUrl.isNullOrBlank()) "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=300" else avatarUrl,
        coverUrl = if (coverUrl.isNullOrBlank()) "https://images.unsplash.com/photo-1506744038136-46273834b3fb?w=1200" else coverUrl,
        bio = bio ?: "Sưu tầm ký ức qua từng con tem bưu chính 📮",
        city = if (city.isNullOrBlank()) "Đà Lạt" else city
    )
}

data class FriendRequest(
    val id: String,
    val senderId: String,
    val senderUsername: String,
    val senderDisplayName: String,
    val senderAvatar: String,
    val recipientId: String,
    val recipientUsername: String,
    val recipientDisplayName: String,
    val recipientAvatar: String,
    val status: String = "PENDING", // PENDING, ACCEPTED, DECLINED
    val createdAt: Long = System.currentTimeMillis()
)

class UserAuthRepository internal constructor(
    private val context: Context,
    val supabaseClient: SupabaseClient = SupabaseClient.getInstance(context),
    val sessionStore: AndroidAuthSessionStore = AndroidAuthSessionStore(context),
    val supabaseAuthService: SupabaseAuthService = SupabaseAuthService.getInstance()
) {

    private val prefs: SharedPreferences? = try { context.getSharedPreferences("memostamp_auth_prefs", Context.MODE_PRIVATE) } catch (_: Throwable) { null }
    private val friendsPrefs: SharedPreferences? = try { context.getSharedPreferences("memostamp_friends_prefs", Context.MODE_PRIVATE) } catch (_: Throwable) { null }
    private val requestsPrefs: SharedPreferences? = try { context.getSharedPreferences("memostamp_requests_prefs", Context.MODE_PRIVATE) } catch (_: Throwable) { null }
    private val gson = Gson()
    private val db: MemoStampDatabase? = try { MemoStampDatabase.getInstance(context) } catch (_: Throwable) { null }
    private val userDao: UserDao? = try { db?.userDao() } catch (_: Throwable) { null }
    private val coroutineScope = CoroutineScope(Dispatchers.IO)

    private val _isSessionPersistent = MutableStateFlow<Boolean>(sessionStore.sessionPersistenceAvailable)
    val isSessionPersistent: StateFlow<Boolean> = _isSessionPersistent.asStateFlow()

    private val _isLoggedIn = MutableStateFlow<Boolean>(false)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    private val _authUserId = MutableStateFlow<String?>(null)
    val authUserId: StateFlow<String?> = _authUserId.asStateFlow()

    private val _accessToken = MutableStateFlow<String?>(null)
    val accessToken: StateFlow<String?> = _accessToken.asStateFlow()

    private val _refreshToken = MutableStateFlow<String?>(null)
    val refreshToken: StateFlow<String?> = _refreshToken.asStateFlow()

    private val _currentUser = MutableStateFlow<UserProfile>(createGuestUser())
    val currentUser: StateFlow<UserProfile> = _currentUser.asStateFlow()

    private val _allAccounts = MutableStateFlow<List<UserProfile>>(emptyList())
    val allAccounts: StateFlow<List<UserProfile>> = _allAccounts.asStateFlow()

    private val _friendIds = MutableStateFlow<Set<String>>(emptySet())
    val friendIds: StateFlow<Set<String>> = _friendIds.asStateFlow()

    private val _friendRequests = MutableStateFlow<List<FriendRequest>>(emptyList())
    val friendRequests: StateFlow<List<FriendRequest>> = _friendRequests.asStateFlow()

    private val notifiedPendingRequestIds = mutableSetOf<String>()
    private val notifiedAcceptedRequestIds = mutableSetOf<String>()

    init {
        _isSessionPersistent.value = sessionStore.sessionPersistenceAvailable
        val session = sessionStore.load()
        if (session != null && !session.isExpired()) {
            _authUserId.value = session.userId
            _accessToken.value = session.accessToken
            _refreshToken.value = session.refreshToken
            supabaseClient.userAccessToken = session.accessToken
            _isLoggedIn.value = true
            _currentUser.value = loadInitialUser(session.userId)
        } else if (session != null && session.refreshToken.isNotBlank()) {
            coroutineScope.launch {
                val refreshRes = supabaseAuthService.refreshSession(session.refreshToken)
                if (refreshRes.isSuccess) {
                    val refreshedSession = refreshRes.getOrThrow()
                    val persisted = sessionStore.save(refreshedSession)
                    _isSessionPersistent.value = persisted
                    _authUserId.value = refreshedSession.userId
                    _accessToken.value = refreshedSession.accessToken
                    _refreshToken.value = refreshedSession.refreshToken
                    supabaseClient.userAccessToken = refreshedSession.accessToken
                    _isLoggedIn.value = true
                    _currentUser.value = loadInitialUser(refreshedSession.userId)
                } else {
                    sessionStore.clear()
                    _authUserId.value = null
                    _accessToken.value = null
                    _refreshToken.value = null
                    supabaseClient.userAccessToken = null
                    _isLoggedIn.value = false
                    _currentUser.value = createGuestUser()
                }
            }
        } else {
            sessionStore.clear()
            _authUserId.value = null
            _accessToken.value = null
            _refreshToken.value = null
            supabaseClient.userAccessToken = null
            _isLoggedIn.value = false
            _currentUser.value = createGuestUser()
        }

        _friendIds.value = loadFriendIds(_currentUser.value.userId)
        val initialReqs = loadFriendRequests(_currentUser.value.userId)
        _friendRequests.value = initialReqs
        notifiedPendingRequestIds.addAll(initialReqs.filter { it.status == "PENDING" }.map { it.id })
        notifiedAcceptedRequestIds.addAll(initialReqs.filter { it.status == "ACCEPTED" }.map { it.id })
        coroutineScope.launch {
            cleanUpOldMockUsersIfNeeded()
            refreshAccountsList()
            syncWithSupabaseLoop()
        }
    }

    fun triggerSync() {
        coroutineScope.launch {
            syncWithSupabaseOnce()
        }
    }

    suspend fun syncWithSupabaseOnce() = withContext(Dispatchers.IO) {
        try {
            val user = _currentUser.value
            val currentUid = user.userId
            if (currentUid.isNotBlank() && _isLoggedIn.value && !currentUid.startsWith("guest_")) {
                // 1. Ensure current profile is synced to Supabase
                supabaseClient.upsertProfile(user)

                // 2. Sync all public accounts from Supabase for discovery
                val cloudProfiles = supabaseClient.getAllProfiles()
                if (cloudProfiles.isNotEmpty()) {
                    for (p in cloudProfiles) {
                        ensureUserProfileExists(p.userId, p.username, p.displayName, p.avatarUrl)
                    }
                    _allAccounts.value = cloudProfiles.map { it.sanitized() }
                }

                // 3. Sync friend requests from Supabase
                val resRequests = supabaseClient.getFriendRequestsForUser(currentUid)
                var validCloudRequests: List<FriendRequest> = emptyList()
                if (resRequests.isSuccess) {
                    val cloudRequests = resRequests.getOrNull() ?: emptyList()
                    validCloudRequests = cloudRequests.filter { it.senderId.isNotBlank() && it.recipientId.isNotBlank() }
                    val sortedRequests = validCloudRequests.sortedByDescending { it.createdAt }
                    _friendRequests.value = sortedRequests
                    saveFriendRequests(currentUid, sortedRequests, syncToCloud = false)

                    if (validCloudRequests.isNotEmpty()) {
                        val newIncomingRequests = validCloudRequests.filter { req ->
                            req.recipientId == currentUid &&
                            req.status.equals("PENDING", ignoreCase = true) &&
                            !notifiedPendingRequestIds.contains(req.id)
                        }
                        newIncomingRequests.forEach { req ->
                            notifiedPendingRequestIds.add(req.id)
                            MemoStampNotificationManager.sendFriendRequestNotification(
                                context = context,
                                senderName = req.senderDisplayName.ifBlank { req.senderUsername },
                                senderId = req.senderId
                            )
                            InAppNotificationManager.show(
                                InAppBanner(
                                    id = "req_${req.id}",
                                    title = "🤝 Lời mời kết bạn mới",
                                    message = "${req.senderDisplayName.ifBlank { req.senderUsername }} muốn kết nối bạn bè với bạn!",
                                    avatarUrl = req.senderAvatar,
                                    iconEmoji = "🤝",
                                    targetRoute = "friends",
                                    senderName = req.senderDisplayName
                                )
                            )
                        }

                        val newlyAcceptedRequests = validCloudRequests.filter { req ->
                            req.senderId == currentUid &&
                            req.status.equals("ACCEPTED", ignoreCase = true) &&
                            !notifiedAcceptedRequestIds.contains(req.id)
                        }
                        newlyAcceptedRequests.forEach { req ->
                            notifiedAcceptedRequestIds.add(req.id)
                            MemoStampNotificationManager.sendFriendAcceptedNotification(
                                context = context,
                                friendName = req.recipientDisplayName.ifBlank { req.recipientUsername },
                                friendId = req.recipientId
                            )
                            InAppNotificationManager.show(
                                InAppBanner(
                                    id = "acc_${req.id}",
                                    title = "🎉 Đã kết nối bạn bè!",
                                    message = "${req.recipientDisplayName.ifBlank { req.recipientUsername }} đã chấp nhận lời mời kết bạn của bạn!",
                                    avatarUrl = req.recipientAvatar,
                                    iconEmoji = "🎉",
                                    targetRoute = "chat/${req.recipientId}",
                                    senderName = req.recipientDisplayName
                                )
                            )
                        }
                    }
                } else {
                    validCloudRequests = _friendRequests.value.filter { it.senderId.isNotBlank() && it.recipientId.isNotBlank() }
                }

                // 4. Sync friends list from Supabase
                val resFriends = supabaseClient.getFriendsForUser(currentUid)
                if (resFriends.isSuccess) {
                    val cloudFriends = resFriends.getOrNull() ?: emptySet()
                    val newAcceptedFriendIds = mutableSetOf<String>()
                    validCloudRequests.filter { it.status.equals("ACCEPTED", ignoreCase = true) }.forEach { req ->
                        if (req.senderId == currentUid && req.recipientId.isNotBlank()) {
                            newAcceptedFriendIds.add(req.recipientId)
                            ensureUserProfileExists(req.recipientId, req.recipientUsername, req.recipientDisplayName, req.recipientAvatar)
                        } else if (req.recipientId == currentUid && req.senderId.isNotBlank()) {
                            newAcceptedFriendIds.add(req.senderId)
                            ensureUserProfileExists(req.senderId, req.senderUsername, req.senderDisplayName, req.senderAvatar)
                        }
                    }

                    val resolvedFriends = (cloudFriends + newAcceptedFriendIds).filter { it.isNotBlank() && it != currentUid }.toSet()
                    _friendIds.value = resolvedFriends
                    friendsPrefs?.edit()?.putStringSet(getFriendsPrefKey(currentUid), resolvedFriends)?.apply()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun ensureUserProfileExists(
        userId: String,
        username: String,
        displayName: String,
        avatarUrl: String?
    ) = withContext(Dispatchers.IO) {
        if (userId.isBlank()) return@withContext
        try {
            val existing = userDao?.getUserByUid(userId)
            if (existing == null) {
                userDao?.insertUser(
                    UserEntity(
                        uid = userId,
                        username = username.ifBlank { "user_${userId.take(6)}" },
                        displayName = displayName.ifBlank { "Người dùng MemoStamp" },
                        avatarUrl = avatarUrl ?: "https://i.pravatar.cc/150?u=$userId",
                        email = "",
                        coverUrl = "https://images.unsplash.com/photo-1506744038136-46273834b3fb?w=1200",
                        bio = "Người sưu tầm tem",
                        city = "",
                        totalStamps = 0
                    )
                )
            }
            val currentAccounts = _allAccounts.value.toMutableList()
            if (currentAccounts.none { it.userId == userId }) {
                currentAccounts.add(
                    UserProfile(
                        userId = userId,
                        username = username.ifBlank { "user_${userId.take(6)}" },
                        displayName = displayName.ifBlank { "Người dùng MemoStamp" },
                        avatarUrl = avatarUrl ?: "https://i.pravatar.cc/150?u=$userId",
                        coverUrl = "https://images.unsplash.com/photo-1506744038136-46273834b3fb?w=1200",
                        bio = "Người sưu tầm tem",
                        city = "",
                        isCloudSynced = true
                    )
                )
                _allAccounts.value = currentAccounts
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private suspend fun syncWithSupabaseLoop() {
        while (coroutineScope.isActive) {
            syncWithSupabaseOnce()
            delay(3500)
        }
    }

    private fun getFriendsPrefKey(userId: String): String = "friends_of_$userId"

    private fun loadFriendIds(userId: String): Set<String> {
        val raw = friendsPrefs?.getStringSet(getFriendsPrefKey(userId), null)
        return raw ?: emptySet()
    }

    private fun getRequestsPrefKey(userId: String): String = "friend_requests_of_$userId"

    private fun loadFriendRequests(userId: String): List<FriendRequest> {
        if (userId.isBlank() || userId.startsWith("guest_")) return emptyList()

        val scopedKey = getRequestsPrefKey(userId)
        var json = requestsPrefs?.getString(scopedKey, null)

        if (json == null) {
            val oldGlobalJson = requestsPrefs?.getString("friend_requests_json", null)
            if (!oldGlobalJson.isNullOrBlank()) {
                try {
                    val type = object : TypeToken<List<FriendRequest>>() {}.type
                    val oldList: List<FriendRequest> = gson.fromJson(oldGlobalJson, type) ?: emptyList()
                    val ownedList = oldList.filter { it.senderId == userId || it.recipientId == userId }
                    if (ownedList.isNotEmpty()) {
                        json = gson.toJson(ownedList)
                        requestsPrefs?.edit()?.putString(scopedKey, json)?.apply()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                requestsPrefs?.edit()?.remove("friend_requests_json")?.apply()
            }
        }

        if (json.isNullOrBlank()) return emptyList()

        return try {
            val type = object : TypeToken<List<FriendRequest>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun saveFriendRequests(userId: String, requests: List<FriendRequest>, syncToCloud: Boolean = true) {
        if (userId.isBlank() || userId.startsWith("guest_")) {
            _friendRequests.value = emptyList()
            return
        }
        _friendRequests.value = requests
        requestsPrefs?.edit()?.putString(getRequestsPrefKey(userId), gson.toJson(requests))?.apply()
    }

    fun isFriend(userId: String): Boolean {
        return _friendIds.value.contains(userId)
    }

    fun getPendingReceivedRequests(): List<FriendRequest> {
        val currentUid = _currentUser.value.userId
        return _friendRequests.value.filter { it.recipientId == currentUid && it.status == "PENDING" }
    }

    fun getPendingSentRequests(): List<FriendRequest> {
        val currentUid = _currentUser.value.userId
        return _friendRequests.value.filter { it.senderId == currentUid && it.status == "PENDING" }
    }

    fun isRequestPendingTo(targetUserId: String): Boolean {
        val currentUid = _currentUser.value.userId
        return _friendRequests.value.any { it.senderId == currentUid && it.recipientId == targetUserId && it.status == "PENDING" }
    }

    fun getIncomingRequestFrom(senderUserId: String): FriendRequest? {
        val currentUid = _currentUser.value.userId
        return _friendRequests.value.find { it.senderId == senderUserId && it.recipientId == currentUid && it.status == "PENDING" }
    }

    suspend fun sendFriendRequest(targetUser: UserProfile): Result<Unit> = withContext(Dispatchers.IO) {
        val authUid = _authUserId.value
        val current = _currentUser.value
        if (authUid.isNullOrBlank() || !_isLoggedIn.value || current.userId != authUid || authUid.startsWith("guest_")) {
            return@withContext Result.failure(SecurityException("Unauthorized: Guest cannot send friend requests"))
        }

        if (targetUser.userId == authUid || targetUser.username.equals(current.username, ignoreCase = true)) {
            return@withContext Result.failure(IllegalArgumentException("Bạn không thể gửi lời mời kết bạn cho chính mình"))
        }
        if (isFriend(targetUser.userId)) {
            return@withContext Result.failure(IllegalArgumentException("Hai bạn đã là bạn bè rồi"))
        }
        if (isRequestPendingTo(targetUser.userId)) {
            return@withContext Result.failure(IllegalArgumentException("Đã gửi lời mời kết bạn trước đó, đang chờ đối phương chấp nhận"))
        }

        val incoming = getIncomingRequestFrom(targetUser.userId)
        if (incoming != null) {
            return@withContext acceptFriendRequest(incoming.id)
        }

        val newReq = FriendRequest(
            id = UUID.randomUUID().toString(),
            senderId = authUid,
            senderUsername = current.username,
            senderDisplayName = current.displayName,
            senderAvatar = current.avatarUrl,
            recipientId = targetUser.userId,
            recipientUsername = targetUser.username,
            recipientDisplayName = targetUser.displayName,
            recipientAvatar = targetUser.avatarUrl,
            status = "PENDING",
            createdAt = System.currentTimeMillis()
        )

        val previousReqs = _friendRequests.value
        val updated = previousReqs.filterNot { it.senderId == authUid && it.recipientId == targetUser.userId } + newReq
        saveFriendRequests(authUid, updated)

        val res = supabaseClient.sendFriendRequest(newReq)
        if (res.isFailure) {
            saveFriendRequests(authUid, previousReqs)
            return@withContext Result.failure(res.exceptionOrNull() ?: Exception("Gửi lời mời kết bạn thất bại"))
        }

        Result.success(Unit)
    }

    suspend fun acceptFriendRequest(requestId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val authUid = _authUserId.value
        val current = _currentUser.value
        if (authUid.isNullOrBlank() || !_isLoggedIn.value || current.userId != authUid || authUid.startsWith("guest_")) {
            return@withContext Result.failure(SecurityException("Unauthorized: Must be logged in to accept friend requests"))
        }

        val req = _friendRequests.value.find { it.id == requestId }
            ?: return@withContext Result.failure(IllegalArgumentException("Không tìm thấy lời mời kết bạn"))

        if (requestId.startsWith("freq_")) {
            val updatedRequests = _friendRequests.value.filterNot { it.id == requestId }
            saveFriendRequests(authUid, updatedRequests, syncToCloud = false)
            return@withContext Result.failure(SecurityException("Unauthorized: Legacy request ID not supported"))
        }

        if (req.status != "PENDING") {
            return@withContext Result.failure(IllegalArgumentException("Lời mời kết bạn đã được xử lý"))
        }

        if (req.recipientId != authUid || req.senderId == authUid) {
            return@withContext Result.failure(SecurityException("Unauthorized: Cannot accept this friend request"))
        }

        val targetUserId = req.senderId
        val targetUsername = req.senderUsername
        val targetDisplayName = req.senderDisplayName
        val targetAvatar = req.senderAvatar

        val previousFriends = _friendIds.value
        val previousReqs = _friendRequests.value

        val updatedFriends = previousFriends.toMutableSet().apply { add(targetUserId) }
        _friendIds.value = updatedFriends
        friendsPrefs?.edit()?.putStringSet(getFriendsPrefKey(authUid), updatedFriends)?.apply()

        val updatedRequests = previousReqs.map {
            if (it.id == requestId) it.copy(status = "ACCEPTED") else it
        }
        saveFriendRequests(authUid, updatedRequests, syncToCloud = false)

        val resRpc = supabaseClient.acceptFriendRequestRpc(requestId)
        if (resRpc.isFailure) {
            _friendIds.value = previousFriends
            friendsPrefs?.edit()?.putStringSet(getFriendsPrefKey(authUid), previousFriends)?.apply()
            saveFriendRequests(authUid, previousReqs, syncToCloud = false)
            return@withContext Result.failure(resRpc.exceptionOrNull() ?: Exception("Chấp nhận lời mời kết bạn thất bại"))
        }

        ensureUserProfileExists(targetUserId, targetUsername, targetDisplayName, targetAvatar)
        syncWithSupabaseOnce()
        Result.success(Unit)
    }

    suspend fun declineFriendRequest(requestId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val authUid = _authUserId.value
        val current = _currentUser.value
        if (authUid.isNullOrBlank() || !_isLoggedIn.value || current.userId != authUid || authUid.startsWith("guest_")) {
            return@withContext Result.failure(SecurityException("Unauthorized: Must be logged in to decline friend requests"))
        }

        val req = _friendRequests.value.find { it.id == requestId }
            ?: return@withContext Result.failure(IllegalArgumentException("Không tìm thấy lời mời kết bạn"))

        if (requestId.startsWith("freq_")) {
            val updatedRequests = _friendRequests.value.filterNot { it.id == requestId }
            saveFriendRequests(authUid, updatedRequests, syncToCloud = false)
            return@withContext Result.failure(SecurityException("Unauthorized: Legacy request ID not supported"))
        }

        if (req.status != "PENDING" || req.recipientId != authUid || req.senderId == authUid) {
            return@withContext Result.failure(SecurityException("Unauthorized: Cannot decline this friend request"))
        }

        val previousReqs = _friendRequests.value
        val updatedRequests = previousReqs.filterNot { it.id == requestId }
        saveFriendRequests(authUid, updatedRequests, syncToCloud = false)

        val resRpc = supabaseClient.declineFriendRequestRpc(requestId)
        if (resRpc.isFailure) {
            saveFriendRequests(authUid, previousReqs, syncToCloud = false)
            return@withContext Result.failure(resRpc.exceptionOrNull() ?: Exception("Từ chối lời mời kết bạn thất bại"))
        }

        syncWithSupabaseOnce()
        Result.success(Unit)
    }

    suspend fun cancelOutgoingFriendRequest(requestId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val authUid = _authUserId.value
        val current = _currentUser.value
        if (authUid.isNullOrBlank() || !_isLoggedIn.value || current.userId != authUid || authUid.startsWith("guest_")) {
            return@withContext Result.failure(SecurityException("Unauthorized: Must be logged in to cancel friend requests"))
        }

        val req = _friendRequests.value.find { it.id == requestId }
            ?: return@withContext Result.failure(IllegalArgumentException("Không tìm thấy lời mời kết bạn"))

        if (requestId.startsWith("freq_")) {
            val updatedRequests = _friendRequests.value.filterNot { it.id == requestId }
            saveFriendRequests(authUid, updatedRequests, syncToCloud = false)
            return@withContext Result.failure(SecurityException("Unauthorized: Legacy request ID not supported"))
        }

        if (req.status != "PENDING" || req.senderId != authUid) {
            return@withContext Result.failure(SecurityException("Unauthorized: Cannot cancel this outgoing friend request"))
        }

        val previousReqs = _friendRequests.value
        val updatedRequests = previousReqs.filterNot { it.id == requestId }
        saveFriendRequests(authUid, updatedRequests, syncToCloud = false)

        val resRpc = supabaseClient.cancelFriendRequestRpc(requestId)
        if (resRpc.isFailure) {
            saveFriendRequests(authUid, previousReqs, syncToCloud = false)
            return@withContext Result.failure(resRpc.exceptionOrNull() ?: Exception("Thu hồi lời mời kết bạn thất bại"))
        }

        syncWithSupabaseOnce()
        Result.success(Unit)
    }

    suspend fun cancelFriendRequest(targetUserId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val authUid = _authUserId.value ?: return@withContext Result.failure(SecurityException("Not logged in"))
        val req = _friendRequests.value.find { it.senderId == authUid && it.recipientId == targetUserId && it.status == "PENDING" }
            ?: return@withContext Result.failure(IllegalArgumentException("No pending request to $targetUserId"))
        cancelOutgoingFriendRequest(req.id)
    }

    suspend fun unfriend(targetUserId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val authUid = _authUserId.value
        val current = _currentUser.value
        if (authUid.isNullOrBlank() || !_isLoggedIn.value || current.userId != authUid || authUid.startsWith("guest_")) {
            return@withContext Result.failure(SecurityException("Unauthorized: Must be logged in to unfriend"))
        }

        val previousFriends = _friendIds.value
        val updatedFriends = previousFriends.toMutableSet().apply { remove(targetUserId) }
        _friendIds.value = updatedFriends
        friendsPrefs?.edit()?.putStringSet(getFriendsPrefKey(authUid), updatedFriends)?.apply()

        val resRpc = supabaseClient.unfriendUserRpc(targetUserId)
        if (resRpc.isFailure) {
            _friendIds.value = previousFriends
            friendsPrefs?.edit()?.putStringSet(getFriendsPrefKey(authUid), previousFriends)?.apply()
            return@withContext Result.failure(resRpc.exceptionOrNull() ?: Exception("Hủy kết bạn thất bại"))
        }

        syncWithSupabaseOnce()
        Result.success(Unit)
    }

    suspend fun searchUsers(query: String): List<UserProfile> = withContext(Dispatchers.IO) {
        val cleanQuery = query.trim().lowercase().removePrefix("@")
        if (cleanQuery.isBlank()) return@withContext emptyList()

        val cloudResults = try {
            val profiles = supabaseClient.getAllProfiles()
            profiles.filter {
                it.username.lowercase().contains(cleanQuery) ||
                it.displayName.lowercase().contains(cleanQuery) ||
                it.userId.lowercase().contains(cleanQuery)
            }
        } catch (e: Exception) {
            emptyList()
        }

        val allLocal = _allAccounts.value
        val localFiltered = allLocal.filter { user ->
            user.username.lowercase().contains(cleanQuery) ||
            user.displayName.lowercase().contains(cleanQuery) ||
            user.userId.lowercase().contains(cleanQuery)
        }

        val merged = (cloudResults + localFiltered).distinctBy { it.userId }
        merged
    }

    fun isUserLoggedIn(): Boolean {
        return _isLoggedIn.value && !_currentUser.value.userId.startsWith("guest_")
    }

    private fun loadInitialUser(authUid: String? = null): UserProfile {
        val targetUid = authUid ?: _authUserId.value
        if (targetUid.isNullOrBlank() || !_isLoggedIn.value) {
            return createGuestUser()
        }
        val json = prefs?.getString("user_profile_json", null)
        if (!json.isNullOrBlank()) {
            try {
                val parsed = gson.fromJson(json, UserProfile::class.java)
                if (parsed != null && parsed.userId == targetUid) {
                    return parsed.sanitized()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return UserProfile(
            userId = targetUid,
            username = "user_${targetUid.take(6)}",
            displayName = "Người dùng MemoStamp",
            email = "",
            avatarUrl = "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=300",
            coverUrl = "https://images.unsplash.com/photo-1506744038136-46273834b3fb?w=1200",
            bio = "Sưu tầm ký ức qua từng con tem bưu chính 📮",
            city = "Sài Gòn",
            isCloudSynced = true,
            totalStampsCount = 0
        ).sanitized()
    }

    fun createGuestUser(): UserProfile {
        return UserProfile(
            userId = "guest_visitor",
            username = "guest_visitor",
            displayName = "Khách du hành",
            email = "",
            avatarUrl = "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=300",
            coverUrl = "https://images.unsplash.com/photo-1506744038136-46273834b3fb?w=1200",
            bio = "Chưa đăng nhập tài khoản MemoStamp",
            city = "Việt Nam",
            isCloudSynced = false,
            totalStampsCount = 0
        ).sanitized()
    }

    private suspend fun cleanUpOldMockUsersIfNeeded() = withContext(Dispatchers.IO) {
        val mockUids = listOf("user_heritage_vietnam", "user_minh_dalat", "user_linh_seasun", "user_huy_wanderer")
        for (uid in mockUids) {
            userDao?.deleteUserByUid(uid)
        }
    }

    suspend fun refreshAccountsList() = withContext(Dispatchers.IO) {
        val dbUsers = userDao?.getAllUsers() ?: emptyList()
        val list = dbUsers.map { entity ->
            UserProfile(
                userId = entity.uid,
                username = entity.username,
                displayName = entity.displayName,
                email = entity.email,
                avatarUrl = entity.avatarUrl ?: "https://i.pravatar.cc/150?u=${entity.uid}",
                coverUrl = entity.coverUrl ?: "https://images.unsplash.com/photo-1506744038136-46273834b3fb?w=1200",
                bio = entity.bio ?: "Người yêu dấu tem & bưu thiếp 📮",
                city = entity.city ?: "Đà Lạt",
                totalStampsCount = entity.totalStamps
            ).sanitized()
        }
        _allAccounts.value = list
    }

    suspend fun register(
        displayName: String,
        username: String,
        email: String,
        password: String,
        city: String = "Đà Lạt",
        bio: String = "Người yêu dấu tem bưu chính 📮",
        avatarUrl: String? = null,
        coverUrl: String? = null
    ): Result<UserProfile> = withContext(Dispatchers.IO) {
        val cleanUsername = username.trim().lowercase().removePrefix("@")
        val cleanEmail = email.trim().lowercase()

        if (cleanUsername.length < 3 || cleanUsername.length > 24) {
            return@withContext Result.failure(IllegalArgumentException("ID người dùng phải từ 3 đến 24 ký tự"))
        }

        val isValidIdPattern = cleanUsername.all { it.isLetterOrDigit() || it == '_' }
        if (!isValidIdPattern) {
            return@withContext Result.failure(IllegalArgumentException("ID người dùng chỉ được chứa chữ cái không dấu, chữ số và dấu gạch dưới (_)"))
        }

        if (password.length < 4) {
            return@withContext Result.failure(IllegalArgumentException("Mật khẩu phải từ 4 ký tự"))
        }

        if (cleanEmail.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Vui lòng nhập địa chỉ email hợp lệ"))
        }

        try {
            val cloudUser = supabaseClient.getProfileByUsername(cleanUsername)
            if (cloudUser != null) {
                return@withContext Result.failure(IllegalArgumentException("ID @$cleanUsername đã được đăng ký trên hệ thống. Vui lòng chọn ID khác!"))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val authResult = supabaseAuthService.signUp(cleanEmail, password)
        if (authResult.isFailure) {
            val errMsg = authResult.exceptionOrNull()?.message ?: "Đăng ký không thành công"
            return@withContext Result.failure(IllegalArgumentException(errMsg))
        }

        val session = authResult.getOrThrow()
        val realUid = session.userId

        val persisted = sessionStore.save(session)
        _isSessionPersistent.value = persisted
        _authUserId.value = realUid
        _accessToken.value = session.accessToken
        _refreshToken.value = session.refreshToken
        supabaseClient.userAccessToken = session.accessToken
        _isLoggedIn.value = true

        val finalAvatar = avatarUrl ?: "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?w=300"
        val finalCover = coverUrl ?: "https://images.unsplash.com/photo-1506744038136-46273834b3fb?w=1200"

        val entity = UserEntity(
            uid = realUid,
            username = cleanUsername,
            displayName = displayName.ifBlank { cleanUsername },
            email = cleanEmail,
            passwordHash = "",
            avatarUrl = finalAvatar,
            coverUrl = finalCover,
            bio = bio,
            city = city,
            totalStamps = 0,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        userDao?.insertUser(entity)

        val profile = UserProfile(
            userId = realUid,
            username = cleanUsername,
            displayName = entity.displayName,
            email = cleanEmail,
            avatarUrl = finalAvatar,
            coverUrl = finalCover,
            bio = bio,
            city = city,
            totalStampsCount = 0
        ).sanitized()

        try {
            supabaseClient.upsertProfile(profile)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        saveUserProfile(profile)
        refreshAccountsList()
        Result.success(profile)
    }

    suspend fun login(
        identifier: String,
        password: String
    ): Result<UserProfile> = withContext(Dispatchers.IO) {
        val cleanIdentifier = identifier.trim().lowercase().removePrefix("@")
        if (cleanIdentifier.isBlank() || password.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Vui lòng nhập tên đăng nhập/email và mật khẩu"))
        }

        val emailToUse: String = if (cleanIdentifier.contains("@")) {
            cleanIdentifier
        } else {
            val localUser = userDao?.getUserByUsernameOrEmail(cleanIdentifier)
            if (localUser != null && localUser.email.isNotBlank()) {
                localUser.email
            } else {
                val cloudProfile = supabaseClient.getProfileByUsername(cleanIdentifier)
                if (cloudProfile != null && !cloudProfile.email.isNullOrBlank()) {
                    cloudProfile.email
                } else {
                    "${cleanIdentifier}@memostamp.app"
                }
            }
        }

        val authResult = supabaseAuthService.signIn(emailToUse, password)
        if (authResult.isFailure) {
            val errMsg = authResult.exceptionOrNull()?.message ?: "Tên đăng nhập hoặc mật khẩu không chính xác"
            return@withContext Result.failure(IllegalArgumentException(errMsg))
        }

        val session = authResult.getOrThrow()
        val realUid = session.userId

        val persisted = sessionStore.save(session)
        _isSessionPersistent.value = persisted
        _authUserId.value = realUid
        _accessToken.value = session.accessToken
        _refreshToken.value = session.refreshToken
        supabaseClient.userAccessToken = session.accessToken
        _isLoggedIn.value = true

        var cloudProfileRecord = supabaseClient.getProfileById(realUid)
        if (cloudProfileRecord == null && !cleanIdentifier.contains("@")) {
            cloudProfileRecord = supabaseClient.getProfileByUsername(cleanIdentifier)
        }

        val localUser = userDao?.getUserByUid(realUid)
        val profile = UserProfile(
            userId = realUid,
            username = cloudProfileRecord?.username ?: localUser?.username ?: cleanIdentifier,
            displayName = cloudProfileRecord?.displayName ?: localUser?.displayName ?: cleanIdentifier,
            email = session.email.ifBlank { localUser?.email ?: emailToUse },
            avatarUrl = cloudProfileRecord?.avatarUrl ?: localUser?.avatarUrl ?: "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=300",
            coverUrl = cloudProfileRecord?.coverUrl ?: localUser?.coverUrl ?: "https://images.unsplash.com/photo-1506744038136-46273834b3fb?w=1200",
            bio = cloudProfileRecord?.bio ?: localUser?.bio ?: "Sưu tầm ký ức qua từng con tem bưu chính 📮",
            city = cloudProfileRecord?.city ?: localUser?.city ?: "Đà Lạt",
            totalStampsCount = localUser?.totalStamps ?: 0
        ).sanitized()

        val entity = UserEntity(
            uid = realUid,
            username = profile.username,
            displayName = profile.displayName,
            email = profile.email,
            passwordHash = "",
            avatarUrl = profile.avatarUrl,
            coverUrl = profile.coverUrl,
            bio = profile.bio,
            city = profile.city,
            totalStamps = profile.totalStampsCount,
            createdAt = localUser?.createdAt ?: System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        userDao?.insertUser(entity)

        saveUserProfile(profile)
        Result.success(profile)
    }

    suspend fun switchAccount(userId: String): Result<UserProfile> = withContext(Dispatchers.IO) {
        val user = userDao?.getUserByUid(userId)
            ?: return@withContext Result.failure(IllegalArgumentException("Không tìm thấy tài khoản"))

        val profile = UserProfile(
            userId = user.uid,
            username = user.username,
            displayName = user.displayName,
            email = user.email,
            avatarUrl = user.avatarUrl ?: "https://i.pravatar.cc/150?u=${user.uid}",
            coverUrl = user.coverUrl ?: "https://images.unsplash.com/photo-1506744038136-46273834b3fb?w=1200",
            bio = user.bio ?: "Người yêu dấu tem & bưu thiếp 📮",
            city = user.city ?: "Đà Lạt",
            totalStampsCount = user.totalStamps
        ).sanitized()
        saveUserProfile(profile)
        Result.success(profile)
    }

    fun saveUserProfile(profile: UserProfile, markLoggedIn: Boolean = true) {
        val editor = prefs?.edit()?.putString("user_profile_json", gson.toJson(profile))
        if (markLoggedIn) {
            editor?.putBoolean("is_logged_in", true)
            _isLoggedIn.value = true
        }
        editor?.apply()
        _currentUser.value = profile
        _friendIds.value = loadFriendIds(profile.userId)
        _friendRequests.value = loadFriendRequests(profile.userId)
        coroutineScope.launch {
            val entity = userDao?.getUserByUid(profile.userId)
            if (entity != null) {
                userDao?.insertUser(
                    entity.copy(
                        displayName = profile.displayName,
                        avatarUrl = profile.avatarUrl,
                        coverUrl = profile.coverUrl,
                        bio = profile.bio,
                        city = profile.city,
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }
            refreshAccountsList()
            if (profile.isCloudSynced && _accessToken.value != null && !profile.userId.startsWith("guest_")) {
                try {
                    supabaseClient.upsertProfile(profile)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    suspend fun updateProfile(
        displayName: String,
        bio: String,
        avatarUrl: String?,
        city: String,
        coverUrl: String? = null
    ) = withContext(Dispatchers.IO) {
        val current = _currentUser.value
        val updated = current.copy(
            displayName = displayName,
            bio = bio,
            avatarUrl = avatarUrl ?: current.avatarUrl,
            coverUrl = coverUrl ?: current.coverUrl,
            city = city
        ).sanitized()
        saveUserProfile(updated, markLoggedIn = true)
    }

    suspend fun updateCoverPhoto(newCoverUrl: String) = withContext(Dispatchers.IO) {
        val current = _currentUser.value
        val updated = current.copy(coverUrl = newCoverUrl).sanitized()
        saveUserProfile(updated, markLoggedIn = true)
    }

    suspend fun updateAvatarPhoto(newAvatarUrl: String) = withContext(Dispatchers.IO) {
        val current = _currentUser.value
        val updated = current.copy(avatarUrl = newAvatarUrl).sanitized()
        saveUserProfile(updated, markLoggedIn = true)
    }

    fun saveMediaUriToLocal(uri: Uri, prefix: String = "profile_cover"): String? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val dir = File(context.filesDir, "user_media").apply { mkdirs() }
            val destFile = File(dir, "${prefix}_${System.currentTimeMillis()}.jpg")
            FileOutputStream(destFile).use { out ->
                inputStream.use { input ->
                    input.copyTo(out)
                }
            }
            destFile.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun updateDisplayName(newName: String) {
        val updated = _currentUser.value.copy(displayName = newName)
        saveUserProfile(updated, markLoggedIn = true)
    }

    suspend fun updatePassword(
        currentPassword: String,
        newPassword: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val currentProfile = _currentUser.value
        val sessionEmail = currentProfile.email.ifBlank {
            sessionStore.load()?.email ?: ""
        }
        val identifier = if (sessionEmail.isNotBlank()) sessionEmail else currentProfile.username
        if (identifier.isBlank()) {
            return@withContext Result.failure(IllegalStateException("Chưa xác định được tài khoản người dùng"))
        }

        val authResult = supabaseAuthService.signIn(identifier, currentPassword)
        if (authResult.isFailure) {
            val errMsg = authResult.exceptionOrNull()?.message ?: "Mật khẩu hiện tại không chính xác"
            return@withContext Result.failure(IllegalArgumentException(errMsg))
        }

        val newSession = authResult.getOrThrow()
        sessionStore.save(newSession)
        _accessToken.value = newSession.accessToken
        _refreshToken.value = newSession.refreshToken
        supabaseClient.userAccessToken = newSession.accessToken

        val updateResult = supabaseAuthService.updateUserPassword(newSession.accessToken, newPassword)
        if (updateResult.isFailure) {
            val errMsg = updateResult.exceptionOrNull()?.message ?: "Cập nhật mật khẩu thất bại"
            return@withContext Result.failure(IllegalStateException(errMsg))
        }

        Result.success(Unit)
    }

    fun logout() {
        val currentToken = _accessToken.value
        if (!currentToken.isNullOrBlank()) {
            coroutineScope.launch {
                supabaseAuthService.signOut(currentToken)
            }
        }

        sessionStore.clear()
        prefs?.edit()?.putBoolean("is_logged_in", false)?.remove("user_profile_json")?.apply()

        _isLoggedIn.value = false
        _authUserId.value = null
        _accessToken.value = null
        _refreshToken.value = null
        supabaseClient.userAccessToken = null

        _friendIds.value = emptySet()
        _friendRequests.value = emptyList()

        _currentUser.value = createGuestUser()
    }

    internal fun setTestAuthState(
        isLoggedIn: Boolean,
        authUser: UserProfile?,
        friends: Set<String> = emptySet(),
        requests: List<FriendRequest> = emptyList()
    ) {
        _isLoggedIn.value = isLoggedIn
        _authUserId.value = authUser?.userId
        _currentUser.value = authUser ?: createGuestUser()
        _friendIds.value = friends
        _friendRequests.value = requests
        if (authUser != null && !authUser.userId.startsWith("guest_")) {
            _accessToken.value = "test_jwt_${authUser.userId}"
            supabaseClient.userAccessToken = "test_jwt_${authUser.userId}"
        } else {
            _accessToken.value = null
            supabaseClient.userAccessToken = null
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: UserAuthRepository? = null

        fun getInstance(context: Context): UserAuthRepository {
            return INSTANCE ?: synchronized(this) {
                val instance = UserAuthRepository(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }
}
