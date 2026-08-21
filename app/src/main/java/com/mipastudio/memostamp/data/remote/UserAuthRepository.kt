package com.mipastudio.memostamp.data.remote

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
    val userId: String = "user_phat_main",
    val username: String = "phat_memostamp",
    val displayName: String = "Phat Nguyen",
    val email: String = "",
    val avatarUrl: String = "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=300",
    val coverUrl: String = "https://images.unsplash.com/photo-1506744038136-46273834b3fb?w=1200",
    val bio: String = "Sưu tầm ký ức qua từng con tem bưu chính 📮",
    val city: String = "Đà Lạt",
    val isCloudSynced: Boolean = true,
    val totalStampsCount: Int = 0
) {
    fun sanitized(): UserProfile = copy(
        userId = if (userId.isNullOrBlank()) "user_phat_main" else userId,
        username = if (username.isNullOrBlank()) "phat_memostamp" else username,
        displayName = if (displayName.isNullOrBlank()) "Phat Nguyen" else displayName,
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

class UserAuthRepository private constructor(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("memostamp_auth_prefs", Context.MODE_PRIVATE)
    private val friendsPrefs: SharedPreferences = context.getSharedPreferences("memostamp_friends_prefs", Context.MODE_PRIVATE)
    private val requestsPrefs: SharedPreferences = context.getSharedPreferences("memostamp_requests_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val db = MemoStampDatabase.getInstance(context)
    private val userDao: UserDao = db.userDao()
    private val supabaseClient = SupabaseClient.getInstance(context)
    private val coroutineScope = CoroutineScope(Dispatchers.IO)

    private val _isLoggedIn = MutableStateFlow<Boolean>(prefs.getBoolean("is_logged_in", false))
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    private val _currentUser = MutableStateFlow<UserProfile>(loadInitialUser())
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
        _friendIds.value = loadFriendIds(_currentUser.value.userId)
        val initialReqs = loadFriendRequests()
        _friendRequests.value = initialReqs
        notifiedPendingRequestIds.addAll(initialReqs.filter { it.status == "PENDING" }.map { it.id })
        notifiedAcceptedRequestIds.addAll(initialReqs.filter { it.status == "ACCEPTED" }.map { it.id })
        coroutineScope.launch {
            cleanUpOldMockUsersIfNeeded()
            ensurePrimaryUserInDb()
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
            if (currentUid.isNotBlank()) {
                // 1. Ensure current profile is always synced to Supabase
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
                val cloudRequests = supabaseClient.getFriendRequestsForUser(currentUid)
                val validCloudRequests = cloudRequests.filter { it.senderId.isNotBlank() && it.recipientId.isNotBlank() }
                _friendRequests.value = validCloudRequests.sortedByDescending { it.createdAt }
                saveFriendRequests(_friendRequests.value, syncToCloud = false)

                if (validCloudRequests.isNotEmpty()) {
                    // Check for new incoming pending friend requests to notify
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

                    // Check for accepted friend requests to notify sender
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

                // 4. Sync friends list from Supabase
                val cloudFriends = supabaseClient.getFriendsForUser(currentUid)
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

                val allActiveFriends = (cloudFriends + newAcceptedFriendIds).filter { it.isNotBlank() && it != currentUid }.toSet()
                _friendIds.value = allActiveFriends
                friendsPrefs.edit().putStringSet(getFriendsPrefKey(currentUid), allActiveFriends).apply()
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
            val existing = userDao.getUserByUid(userId)
            if (existing == null) {
                userDao.insertUser(
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
            delay(3500) // Poll Supabase every 3.5 seconds for instant real-time sync
        }
    }

    private fun getFriendsPrefKey(userId: String): String = "friends_of_$userId"

    private fun loadFriendIds(userId: String): Set<String> {
        val raw = friendsPrefs.getStringSet(getFriendsPrefKey(userId), null)
        return raw ?: emptySet()
    }

    private fun loadFriendRequests(): List<FriendRequest> {
        val json = requestsPrefs.getString("friend_requests_json", null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<FriendRequest>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun saveFriendRequests(requests: List<FriendRequest>, syncToCloud: Boolean = true) {
        _friendRequests.value = requests
        requestsPrefs.edit().putString("friend_requests_json", gson.toJson(requests)).apply()
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

    /**
     * Gửi lời mời kết bạn đến người dùng khác (Online Supabase + Local)
     */
    fun sendFriendRequest(targetUser: UserProfile): Result<Unit> {
        val current = _currentUser.value
        if (targetUser.userId == current.userId || targetUser.username.equals(current.username, ignoreCase = true)) {
            return Result.failure(IllegalArgumentException("Bạn không thể gửi lời mời kết bạn cho chính mình"))
        }
        if (isFriend(targetUser.userId)) {
            return Result.failure(IllegalArgumentException("Hai bạn đã là bạn bè rồi"))
        }
        if (isRequestPendingTo(targetUser.userId)) {
            return Result.failure(IllegalArgumentException("Đã gửi lời mời kết bạn trước đó, đang chờ đối phương chấp nhận"))
        }

        // Kiểm tra xem đối phương có từng gửi lời mời cho mình chưa -> Nếu có thì chấp nhận luôn
        val incoming = getIncomingRequestFrom(targetUser.userId)
        if (incoming != null) {
            return acceptFriendRequest(incoming.id)
        }

        val newReq = FriendRequest(
            id = "freq_" + UUID.randomUUID().toString().take(8),
            senderId = current.userId,
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

        val updated = _friendRequests.value.filterNot { it.senderId == current.userId && it.recipientId == targetUser.userId } + newReq
        saveFriendRequests(updated)

        // Đồng bộ lên Supabase Cloud
        coroutineScope.launch {
            try {
                supabaseClient.sendFriendRequest(newReq)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        return Result.success(Unit)
    }

    /**
     * Chấp nhận lời mời kết bạn (Online Supabase + Local)
     */
    fun acceptFriendRequest(requestId: String): Result<Unit> {
        val current = _currentUser.value
        val req = _friendRequests.value.find { it.id == requestId }
            ?: return Result.failure(IllegalArgumentException("Không tìm thấy lời mời kết bạn"))

        val targetUserId = if (req.recipientId == current.userId) req.senderId else req.recipientId
        val targetUsername = if (req.recipientId == current.userId) req.senderUsername else req.recipientUsername
        val targetDisplayName = if (req.recipientId == current.userId) req.senderDisplayName else req.recipientDisplayName
        val targetAvatar = if (req.recipientId == current.userId) req.senderAvatar else req.recipientAvatar

        // 1. Thêm vào danh sách bạn bè của người dùng hiện tại
        val myFriends = _friendIds.value.toMutableSet()
        myFriends.add(targetUserId)
        _friendIds.value = myFriends
        friendsPrefs.edit().putStringSet(getFriendsPrefKey(current.userId), myFriends).apply()

        // Cập nhật cho người gửi trong bộ nhớ cục bộ
        val theirFriends = loadFriendIds(targetUserId).toMutableSet()
        theirFriends.add(current.userId)
        friendsPrefs.edit().putStringSet(getFriendsPrefKey(targetUserId), theirFriends).apply()

        // 2. Đảm bảo đối phương có mặt trong hồ sơ người dùng cục bộ & danh sách tài khoản
        coroutineScope.launch {
            ensureUserProfileExists(targetUserId, targetUsername, targetDisplayName, targetAvatar)
        }

        // 3. Đánh dấu request là ACCEPTED
        val updatedRequests = _friendRequests.value.map {
            if (it.id == requestId) it.copy(status = "ACCEPTED") else it
        }
        _friendRequests.value = updatedRequests
        saveFriendRequests(updatedRequests, syncToCloud = false)

        // 4. Đồng bộ tức thì lên Supabase Cloud
        coroutineScope.launch {
            try {
                supabaseClient.updateFriendRequestStatus(requestId, "ACCEPTED")
                supabaseClient.addFriendship(current.userId, targetUserId)
                // Đảm bảo 2 bên nhận được ngay
                syncWithSupabaseOnce()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        return Result.success(Unit)
    }

    /**
     * Từ chối lời mời kết bạn
     */
    fun declineFriendRequest(requestId: String): Result<Unit> {
        val updatedRequests = _friendRequests.value.filterNot { it.id == requestId }
        saveFriendRequests(updatedRequests, syncToCloud = false)

        coroutineScope.launch {
            try {
                supabaseClient.updateFriendRequestStatus(requestId, "DECLINED")
                syncWithSupabaseOnce()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        return Result.success(Unit)
    }

    /**
     * Thu hồi lời mời kết bạn đã gửi
     */
    fun cancelFriendRequest(targetUserId: String): Result<Unit> {
        val currentUid = _currentUser.value.userId
        val targetReq = _friendRequests.value.find { it.senderId == currentUid && it.recipientId == targetUserId }
        val updatedRequests = _friendRequests.value.filterNot {
            it.senderId == currentUid && it.recipientId == targetUserId && it.status == "PENDING"
        }
        saveFriendRequests(updatedRequests, syncToCloud = false)

        if (targetReq != null) {
            coroutineScope.launch {
                try {
                    supabaseClient.updateFriendRequestStatus(targetReq.id, "CANCELLED")
                    syncWithSupabaseOnce()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        return Result.success(Unit)
    }

    /**
     * Hủy kết bạn (Unfriend)
     */
    fun unfriend(targetUserId: String): Result<Unit> {
        val currentUid = _currentUser.value.userId
        val myFriends = _friendIds.value.toMutableSet()
        myFriends.remove(targetUserId)
        _friendIds.value = myFriends
        friendsPrefs.edit().putStringSet(getFriendsPrefKey(currentUid), myFriends).apply()

        // Đồng thời hủy từ phía bên kia
        val theirFriends = loadFriendIds(targetUserId).toMutableSet()
        theirFriends.remove(currentUid)
        friendsPrefs.edit().putStringSet(getFriendsPrefKey(targetUserId), theirFriends).apply()

        val updatedRequests = _friendRequests.value.filterNot {
            (it.senderId == currentUid && it.recipientId == targetUserId) ||
            (it.senderId == targetUserId && it.recipientId == currentUid)
        }
        saveFriendRequests(updatedRequests, syncToCloud = false)

        coroutineScope.launch {
            try {
                supabaseClient.removeFriendship(currentUid, targetUserId)
                syncWithSupabaseOnce()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        return Result.success(Unit)
    }

    suspend fun searchUsers(query: String): List<UserProfile> = withContext(Dispatchers.IO) {
        val cleanQuery = query.trim().lowercase().removePrefix("@")
        if (cleanQuery.isBlank()) return@withContext emptyList()

        // Tìm trên Supabase Cloud trực tiếp
        val cloudResults = try {
            supabaseClient.searchProfiles(cleanQuery)
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
        return prefs.getBoolean("is_logged_in", false)
    }

    private fun loadInitialUser(): UserProfile {
        val json = prefs.getString("user_profile_json", null)
        if (!json.isNullOrBlank()) {
            try {
                val parsed = gson.fromJson(json, UserProfile::class.java)
                if (parsed != null) {
                    return parsed.sanitized()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Primary user profile
        return UserProfile(
            userId = "user_phat_main",
            username = "phat_memostamp",
            displayName = "Phat Nguyen",
            email = "phatdinh265@gmail.com",
            avatarUrl = "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=300",
            coverUrl = "https://images.unsplash.com/photo-1506744038136-46273834b3fb?w=1200",
            bio = "Sưu tầm ký ức qua từng con tem bưu chính 📮",
            city = "Sài Gòn",
            isCloudSynced = true,
            totalStampsCount = 0
        ).sanitized()
    }

    private suspend fun cleanUpOldMockUsersIfNeeded() = withContext(Dispatchers.IO) {
        val mockUids = listOf("user_heritage_vietnam", "user_minh_dalat", "user_linh_seasun", "user_huy_wanderer")
        for (uid in mockUids) {
            userDao.deleteUserByUid(uid)
        }
    }

    private suspend fun ensurePrimaryUserInDb() = withContext(Dispatchers.IO) {
        val user = _currentUser.value
        val existing = userDao.getUserByUid(user.userId)
        if (existing == null) {
            userDao.insertUser(
                UserEntity(
                    uid = user.userId,
                    username = user.username,
                    displayName = user.displayName,
                    email = user.email,
                    passwordHash = "123456",
                    avatarUrl = user.avatarUrl,
                    coverUrl = user.coverUrl,
                    bio = user.bio,
                    city = user.city,
                    totalStamps = user.totalStampsCount
                )
            )
        }
    }

    suspend fun refreshAccountsList() = withContext(Dispatchers.IO) {
        val dbUsers = userDao.getAllUsers()
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

    /**
     * Đăng ký tài khoản với ID duy nhất (Đồng bộ Cloud Supabase + Local)
     */
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

        // Kiểm tra định dạng ID
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

        // 1. Kiểm tra tính DUY NHẤT trên Local DB
        val existingUserLocal = userDao.getUserByUsernameOrEmail(cleanUsername)
        if (existingUserLocal != null) {
            return@withContext Result.failure(IllegalArgumentException("ID @$cleanUsername đã có người sử dụng. Vui lòng chọn ID khác!"))
        }

        // 2. Kiểm tra tính DUY NHẤT trên Supabase Cloud
        try {
            val cloudUser = supabaseClient.getProfileByUsername(cleanUsername)
            if (cloudUser != null) {
                return@withContext Result.failure(IllegalArgumentException("ID @$cleanUsername đã được đăng ký trên hệ thống. Vui lòng chọn ID khác!"))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        if (cleanEmail.isNotBlank()) {
            val existingEmail = userDao.getUserByUsernameOrEmail(cleanEmail)
            if (existingEmail != null) {
                return@withContext Result.failure(IllegalArgumentException("Email $cleanEmail đã được sử dụng."))
            }
        }

        val newUid = "user_" + UUID.randomUUID().toString().take(8)
        val finalAvatar = avatarUrl ?: "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?w=300"
        val finalCover = coverUrl ?: "https://images.unsplash.com/photo-1506744038136-46273834b3fb?w=1200"

        val entity = UserEntity(
            uid = newUid,
            username = cleanUsername,
            displayName = displayName.ifBlank { cleanUsername },
            email = cleanEmail,
            passwordHash = password,
            avatarUrl = finalAvatar,
            coverUrl = finalCover,
            bio = bio,
            city = city,
            totalStamps = 0,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        userDao.insertUser(entity)

        val profile = UserProfile(
            userId = newUid,
            username = cleanUsername,
            displayName = entity.displayName,
            email = cleanEmail,
            avatarUrl = finalAvatar,
            coverUrl = finalCover,
            bio = bio,
            city = city,
            totalStampsCount = 0
        ).sanitized()

        // Lưu profile lên Supabase Cloud
        try {
            supabaseClient.upsertProfile(profile, password)
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
        
        // 1. Kiểm tra Local trước
        var user = userDao.getUserByUsernameOrEmail(cleanIdentifier)
        
        // 2. Nếu không thấy trên Local, kiểm tra trên Supabase Cloud
        if (user == null) {
            try {
                val cloudProfile = supabaseClient.getProfileByUsername(cleanIdentifier)
                if (cloudProfile != null) {
                    val entity = UserEntity(
                        uid = cloudProfile.userId,
                        username = cloudProfile.username,
                        displayName = cloudProfile.displayName,
                        email = cloudProfile.email ?: "",
                        passwordHash = cloudProfile.passwordHash ?: "",
                        avatarUrl = cloudProfile.avatarUrl,
                        coverUrl = cloudProfile.coverUrl,
                        bio = cloudProfile.bio ?: "",
                        city = cloudProfile.city ?: "Đà Lạt",
                        totalStamps = 0,
                        createdAt = (cloudProfile.createdAt as? Double)?.toLong() ?: (cloudProfile.createdAt as? Long) ?: System.currentTimeMillis(),
                        updatedAt = System.currentTimeMillis()
                    )
                    userDao.insertUser(entity)
                    user = entity
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        if (user == null) {
            return@withContext Result.failure(IllegalArgumentException("Không tìm thấy tài khoản \"$identifier\""))
        }

        if (user.passwordHash.isNotBlank() && user.passwordHash != password) {
            return@withContext Result.failure(IllegalArgumentException("Mật khẩu không chính xác"))
        }

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

    suspend fun switchAccount(userId: String): Result<UserProfile> = withContext(Dispatchers.IO) {
        val user = userDao.getUserByUid(userId)
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
        val editor = prefs.edit().putString("user_profile_json", gson.toJson(profile))
        if (markLoggedIn) {
            editor.putBoolean("is_logged_in", true)
            _isLoggedIn.value = true
        }
        editor.apply()
        _currentUser.value = profile
        _friendIds.value = loadFriendIds(profile.userId)
        coroutineScope.launch {
            val entity = userDao.getUserByUid(profile.userId)
            if (entity != null) {
                userDao.insertUser(
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
            try {
                supabaseClient.upsertProfile(profile)
            } catch (e: Exception) {
                e.printStackTrace()
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

    fun logout() {
        prefs.edit().putBoolean("is_logged_in", false).remove("user_profile_json").apply()
        _isLoggedIn.value = false
        val randomId = "guest_" + UUID.randomUUID().toString().take(6)
        val guest = UserProfile(
            userId = randomId,
            username = "guest_$randomId",
            displayName = "Khách du hành",
            email = "",
            avatarUrl = "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?w=300",
            bio = "Chưa đăng nhập tài khoản MemoStamp",
            city = "Việt Nam",
            isCloudSynced = false,
            totalStampsCount = 0
        ).sanitized()
        _currentUser.value = guest
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

