package com.mipastudio.memostamp.repository

import com.mipastudio.memostamp.getCurrentEpochMillis
import com.mipastudio.memostamp.domain.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private fun currentTimeMillis(): Long = getCurrentEpochMillis()

class SharedMemoStampRepository {

    private val _currentUser = MutableStateFlow(
        UserProfile(
            uid = "user_me",
            username = "phat_memostamp",
            displayName = "Phat Nguyen",
            avatarUrl = "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=300",
            bio = "Sưu tầm ký ức qua từng con tem bưu chính 📮",
            stampsCreatedCount = 0,
            stampsCollectedCount = 0,
            placesVisitedCount = 0
        )
    )
    val currentUser: StateFlow<UserProfile> = _currentUser.asStateFlow()

    fun setCurrentUser(profile: UserProfile) {
        _currentUser.value = profile
    }

    fun resetUserScopedState() {
        _stamps.value = emptyList()
        _collections.value = listOf(
            CollectionItem("col_travel", "Travel & Places", "Destinations & journeys", "plane", "SPECIAL", 12, 0),
            CollectionItem("col_coffee", "Coffee & Food", "Cafes and meals", "coffee", "NORMAL", 10, 0),
            CollectionItem("col_daily", "Daily Life", "Everyday moments", "leaf", "NORMAL", 15, 0),
            CollectionItem("col_special", "Special Moments", "Anniversaries & milestones", "star", "SERIES", 8, 0)
        )
        _friends.value = emptyList()
        _friendRequests.value = emptyList()
        _tradeRequests.value = emptyList()
        _feedPosts.value = emptyList()
        _circles.value = emptyList()
        _badges.value = listOf(
            PassportBadge("Explorer", "Visited 5+ countries & cities", "plane", false),
            PassportBadge("Coffee Lover", "Created 10+ coffee memory stamps", "coffee", false),
            PassportBadge("Master Crafter", "Customized 15+ die-cut stamps", "palette", false),
            PassportBadge("Trade King", "Completed 5+ stamp exchanges", "crown", false)
        )
    }

    private val _circles = MutableStateFlow<List<Circle>>(emptyList())
    val circles: StateFlow<List<Circle>> = _circles.asStateFlow()

    private val _badges = MutableStateFlow<List<PassportBadge>>(
        listOf(
            PassportBadge("Explorer", "Visited 5+ countries & cities", "plane", false),
            PassportBadge("Coffee Lover", "Created 10+ coffee memory stamps", "coffee", false),
            PassportBadge("Master Crafter", "Customized 15+ die-cut stamps", "palette", false),
            PassportBadge("Trade King", "Completed 5+ stamp exchanges", "crown", false)
        )
    )
    val badges: StateFlow<List<PassportBadge>> = _badges.asStateFlow()

    private val _feedPosts = MutableStateFlow<List<FeedPost>>(emptyList())
    val feedPosts: StateFlow<List<FeedPost>> = _feedPosts.asStateFlow()

    private val _stamps = MutableStateFlow<List<StampItem>>(emptyList())
    val stamps: StateFlow<List<StampItem>> = _stamps.asStateFlow()

    private val _collections = MutableStateFlow<List<CollectionItem>>(
        listOf(
            CollectionItem("col_travel", "Travel & Places", "Destinations & journeys", "plane", "SPECIAL", 12, 0),
            CollectionItem("col_coffee", "Coffee & Food", "Cafes and meals", "coffee", "NORMAL", 10, 0),
            CollectionItem("col_daily", "Daily Life", "Everyday moments", "leaf", "NORMAL", 15, 0),
            CollectionItem("col_special", "Special Moments", "Anniversaries & milestones", "star", "SERIES", 8, 0)
        )
    )
    val collections: StateFlow<List<CollectionItem>> = _collections.asStateFlow()

    private val _friends = MutableStateFlow<List<FriendItem>>(emptyList())
    val friends: StateFlow<List<FriendItem>> = _friends.asStateFlow()

    private val _friendRequests = MutableStateFlow<List<FriendRequestItem>>(emptyList())
    val friendRequests: StateFlow<List<FriendRequestItem>> = _friendRequests.asStateFlow()

    private val _tradeRequests = MutableStateFlow<List<TradeRequest>>(emptyList())
    val tradeRequests: StateFlow<List<TradeRequest>> = _tradeRequests.asStateFlow()

    fun loadDemoFixtures() {
        _circles.value = listOf(
            Circle("c_1", "user_me", "Best Friends", "heart", listOf("user_huy", "user_linh"), currentTimeMillis()),
            Circle("c_2", "user_me", "Da Lat Trip", "tree", listOf("user_huy", "user_phat"), currentTimeMillis()),
            Circle("c_3", "user_me", "Class 22DTHB3", "academic", listOf("user_linh", "user_phat"), currentTimeMillis())
        )
        _feedPosts.value = listOf(
            FeedPost(
                id = "post_1",
                stampId = "stamp_1",
                stampUrl = "https://images.unsplash.com/photo-1506744038136-46273834b3fb?w=600",
                stampTitle = "Đà Lạt chiều mưa",
                shape = "heart",
                authorId = "user_me",
                authorName = "Minh Nguyen",
                authorAvatar = "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?w=150",
                caption = "Một chiều chẳng có kế hoạch. Cà phê góc phố Đà Lạt.",
                audienceType = AudienceType.FRIENDS,
                createdAt = currentTimeMillis() - 120000,
                type = FeedPostType.MEMORY,
                location = "Đà Lạt, Lâm Đồng",
                reactionCount = 5,
                commentCount = 2,
                replyCount = 1,
                isLikedByMe = true,
                reactions = listOf(
                    FeedReaction("r1", "post_1", "user_huy", "Huy Tran", "heart", currentTimeMillis() - 60000),
                    FeedReaction("r2", "post_1", "user_linh", "Linh Pham", "heart", currentTimeMillis() - 50000)
                ),
                comments = listOf(
                    FeedComment("c1", "post_1", "user_huy", "Huy Tran", "https://images.unsplash.com/photo-1570295999919-56ceb5ecca61?w=150", "Cảnh này chill quá bạn ơi!", currentTimeMillis() - 40000),
                    FeedComment("c2", "post_1", "user_linh", "Linh Pham", "https://images.unsplash.com/photo-1494790108377-be9c29b29330?w=150", "Góc này quán nào vậy ạ?", currentTimeMillis() - 20000)
                ),
                replies = listOf(
                    FeedReply("rep1", "post_1", "user_huy", "Huy Tran", "https://images.unsplash.com/photo-1570295999919-56ceb5ecca61?w=150", "stamp_reply_1", "https://images.unsplash.com/photo-1495474472287-4d71bcdd2085?w=600", "classic", "Góp 1 chiếc tem cà phê nè!", currentTimeMillis() - 30000)
                )
            ),
            FeedPost(
                id = "post_2",
                stampId = "stamp_2",
                stampUrl = "https://images.unsplash.com/photo-1495474472287-4d71bcdd2085?w=600",
                stampTitle = "Coffee after class",
                shape = "classic",
                authorId = "user_huy",
                authorName = "Huy Tran",
                authorAvatar = "https://images.unsplash.com/photo-1570295999919-56ceb5ecca61?w=150",
                caption = "Coffee after class, nạp lại năng lượng chạy deadline.",
                audienceType = AudienceType.FRIENDS,
                createdAt = currentTimeMillis() - 1080000,
                type = FeedPostType.MEMORY,
                location = "Saigon Central",
                reactionCount = 12,
                commentCount = 4,
                replyCount = 0,
                isLikedByMe = false
            ),
            FeedPost(
                id = "post_3",
                stampId = "stamp_3",
                stampUrl = "https://images.unsplash.com/photo-1507525428034-b723cf961d3e?w=600",
                stampTitle = "Sunset at Beach",
                shape = "oval",
                authorId = "user_linh",
                authorName = "Linh Pham",
                authorAvatar = "https://images.unsplash.com/photo-1494790108377-be9c29b29330?w=150",
                caption = "Cuối tuần bình yên bên bờ biển.",
                audienceType = AudienceType.FRIENDS,
                createdAt = currentTimeMillis() - 3600000,
                type = FeedPostType.MEMORY,
                location = "Vũng Tàu",
                reactionCount = 8,
                commentCount = 1,
                replyCount = 0,
                isLikedByMe = true
            )
        )
        _friends.value = listOf(
            FriendItem("user_huy", "Huy Tran", "huy_tran", "https://images.unsplash.com/photo-1570295999919-56ceb5ecca61?w=150", true, 5),
            FriendItem("user_linh", "Linh Pham", "linh_pham", "https://images.unsplash.com/photo-1494790108377-be9c29b29330?w=150", true, 3),
            FriendItem("user_phat", "Phat Le", "phat_le", "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=150", false, 1)
        )
        _friendRequests.value = listOf(
            FriendRequestItem("freq_1", "Minh Thu", "minh_thu", "https://images.unsplash.com/photo-1544005313-94ddf0286df2?w=150", "PENDING", currentTimeMillis() - 7200000)
        )
        _tradeRequests.value = listOf(
            TradeRequest(
                id = "trade_1",
                senderName = "Huy Tran",
                senderAvatar = "https://images.unsplash.com/photo-1570295999919-56ceb5ecca61?w=150",
                stampTitle = "Vintage Hanoi Train Street",
                stampUrl = "https://images.unsplash.com/photo-1528127269322-539801943592?w=600",
                status = "PENDING",
                createdAt = currentTimeMillis() - 3600000
            )
        )
    }

    fun toggleLike(postId: String) {
        val current = _feedPosts.value
        _feedPosts.value = current.map { post ->
            if (post.id == postId) {
                val newLiked = !post.isLikedByMe
                val newCount = if (newLiked) post.reactionCount + 1 else post.reactionCount - 1
                post.copy(isLikedByMe = newLiked, reactionCount = newCount)
            } else post
        }
    }

    fun addComment(postId: String, content: String) {
        val me = _currentUser.value
        val comment = FeedComment(
            id = "c_${currentTimeMillis()}",
            postId = postId,
            authorId = me.uid,
            authorName = me.displayName,
            authorAvatar = me.avatarUrl ?: "",
            content = content,
            createdAt = currentTimeMillis()
        )
        _feedPosts.value = _feedPosts.value.map { post ->
            if (post.id == postId) {
                post.copy(
                    comments = post.comments + comment,
                    commentCount = post.commentCount + 1
                )
            } else post
        }
    }

    fun deleteComment(postId: String, commentId: String) {
        _feedPosts.value = _feedPosts.value.map { post ->
            if (post.id == postId) {
                val updatedComments = post.comments.filter { it.id != commentId }
                post.copy(
                    comments = updatedComments,
                    commentCount = updatedComments.size
                )
            } else post
        }
    }

    fun addStampReply(postId: String, stampTitle: String, note: String, shape: String, imageUrl: String) {
        val me = _currentUser.value
        val reply = FeedReply(
            id = "rep_${currentTimeMillis()}",
            postId = postId,
            authorId = me.uid,
            authorName = me.displayName,
            authorAvatar = me.avatarUrl ?: "",
            replyStampId = "stamp_${currentTimeMillis()}",
            replyStampUrl = imageUrl,
            shape = shape,
            note = note,
            createdAt = currentTimeMillis()
        )
        _feedPosts.value = _feedPosts.value.map { post ->
            if (post.id == postId) {
                post.copy(
                    replies = post.replies + reply,
                    replyCount = post.replyCount + 1
                )
            } else post
        }
    }

    fun addStamp(
        title: String,
        note: String,
        location: String?,
        imageUrl: String,
        originalImageUrl: String? = null,
        shape: String = "classic",
        collectionId: String? = null,
        audience: AudienceType = AudienceType.FRIENDS,
        mood: String = "😊 Happy",
        memoryDate: Long = currentTimeMillis()
    ): StampItem {
        val me = _currentUser.value
        val stampId = "stamp_${currentTimeMillis()}"
        val stamp = StampItem(
            id = stampId,
            originalImagePath = originalImageUrl ?: imageUrl,
            stampImagePath = imageUrl,
            title = title,
            note = note,
            createdAt = currentTimeMillis(),
            memoryDate = memoryDate,
            location = location,
            mood = mood,
            collectionId = collectionId,
            favorite = false,
            shape = shape
        )
        _stamps.value = listOf(stamp) + _stamps.value

        // Update user stats
        _currentUser.value = me.copy(stampsCreatedCount = me.stampsCreatedCount + 1)

        return stamp
    }

    fun restoreStamps(stamps: List<StampItem>) {
        _stamps.value = stamps
    }

    fun restoreCollections(collections: List<CollectionItem>) {
        _collections.value = collections
    }

    fun publishStampToFeed(stampId: String, audience: AudienceType = AudienceType.FRIENDS, caption: String? = null): FeedPost? {
        val me = _currentUser.value
        val stamp = _stamps.value.find { it.id == stampId } ?: return null
        val post = FeedPost(
            id = "post_${currentTimeMillis()}",
            stampId = stampId,
            stampUrl = stamp.stampImagePath,
            stampTitle = stamp.title,
            shape = stamp.shape,
            authorId = me.uid,
            authorName = me.displayName,
            authorAvatar = me.avatarUrl ?: "",
            caption = caption ?: stamp.note.ifBlank { stamp.title },
            audienceType = audience,
            createdAt = currentTimeMillis(),
            location = stamp.location
        )
        _feedPosts.value = listOf(post) + _feedPosts.value
        return post
    }

    fun updateStamp(updatedStamp: StampItem): Boolean {
        var found = false
        _stamps.value = _stamps.value.map { s ->
            if (s.id == updatedStamp.id) {
                found = true
                updatedStamp
            } else s
        }
        return found
    }

    fun sendTradeRequest(friendId: String, stampId: String): Boolean {
        val me = _currentUser.value
        val friend = _friends.value.find { it.id == friendId } ?: return false
        val stamp = _stamps.value.find { it.id == stampId } ?: return false
        val trade = TradeRequest(
            id = "trade_${currentTimeMillis()}",
            senderName = me.displayName,
            senderAvatar = me.avatarUrl ?: "",
            stampTitle = stamp.title,
            stampUrl = stamp.stampImagePath,
            status = "PENDING",
            createdAt = currentTimeMillis(),
            senderId = me.uid,
            recipientId = friend.id,
            recipientName = friend.displayName,
            stampId = stamp.id
        )
        _tradeRequests.value = listOf(trade) + _tradeRequests.value
        return true
    }

    fun acceptTrade(tradeId: String): Boolean {
        val trade = _tradeRequests.value.find { it.id == tradeId } ?: return false
        val me = _currentUser.value
        if (trade.recipientId != me.uid || trade.senderId == me.uid) {
            return false
        }
        _tradeRequests.value = _tradeRequests.value.filter { it.id != tradeId }
        return true
    }

    fun rejectTrade(tradeId: String): Boolean {
        val trade = _tradeRequests.value.find { it.id == tradeId } ?: return false
        val me = _currentUser.value
        if (trade.recipientId != me.uid || trade.senderId == me.uid) {
            return false
        }
        _tradeRequests.value = _tradeRequests.value.filter { it.id != tradeId }
        return true
    }

    fun cancelOutgoingTrade(tradeId: String): Boolean {
        val trade = _tradeRequests.value.find { it.id == tradeId } ?: return false
        val me = _currentUser.value
        if (trade.senderId != me.uid) {
            return false
        }
        _tradeRequests.value = _tradeRequests.value.filter { it.id != tradeId }
        return true
    }

    fun restoreFriends(friends: List<FriendItem>) {
        _friends.value = friends
    }

    fun restoreFriendRequests(requests: List<FriendRequestItem>) {
        _friendRequests.value = requests.map { r ->
            r.copy(
                senderId = if (r.senderId.isBlank()) "user_${r.senderUsername.ifBlank { "legacy_sender" }}" else r.senderId
            )
        }
    }

    fun restoreTradeRequests(trades: List<TradeRequest>) {
        _tradeRequests.value = trades.map { t ->
            t.copy(
                senderId = if (t.senderId.isBlank()) "user_legacy_sender" else t.senderId
            )
        }
    }

    fun updateProfile(displayName: String, bio: String, avatarUrl: String? = null) {
        _currentUser.value = _currentUser.value.copy(
            displayName = displayName,
            bio = bio,
            avatarUrl = avatarUrl ?: _currentUser.value.avatarUrl
        )
    }

    fun sendFriendRequest(usernameOrCode: String): FriendRequestResult {
        val trimmed = usernameOrCode.trim().lowercase().replace("#", "")
        if (trimmed.length < 3) {
            return FriendRequestResult(false, "Mã/Tên người dùng phải từ 3 ký tự trở lên!")
        }
        val me = _currentUser.value
        if (trimmed == me.username.lowercase()) {
            return FriendRequestResult(false, "Không thể tự kết bạn với chính mình!")
        }
        val existingFriend = _friends.value.find { it.username.lowercase() == trimmed }
        if (existingFriend != null) {
            return FriendRequestResult(false, "${existingFriend.displayName} đã có trong danh sách bạn bè!")
        }
        val existingRequest = _friendRequests.value.find { it.senderUsername.lowercase() == trimmed || it.recipientUsername.lowercase() == trimmed }
        if (existingRequest != null) {
            return FriendRequestResult(false, "Đã gửi hoặc đã nhận lời mời kết bạn từ người này!")
        }

        val request = FriendRequestItem(
            id = "freq_${currentTimeMillis()}",
            senderName = me.displayName,
            senderUsername = me.username,
            senderAvatar = me.avatarUrl ?: "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=150",
            status = "PENDING",
            createdAt = currentTimeMillis(),
            senderId = me.uid,
            recipientId = "user_" + trimmed,
            recipientUsername = trimmed
        )
        _friendRequests.value = listOf(request) + _friendRequests.value
        return FriendRequestResult(true, "Đã gửi lời mời kết bạn tới @$trimmed! 📩")
    }

    fun acceptFriendRequest(requestId: String): Boolean {
        val req = _friendRequests.value.find { it.id == requestId } ?: return false
        val me = _currentUser.value
        if (req.recipientId != me.uid || req.senderId == me.uid) {
            return false
        }
        val newFriend = FriendItem(
            id = if (req.senderId.isNotBlank()) req.senderId else "user_${currentTimeMillis()}",
            displayName = req.senderName,
            username = req.senderUsername,
            avatarUrl = req.senderAvatar,
            isOnline = true,
            tradeCount = 0
        )
        _friends.value = listOf(newFriend) + _friends.value.filter { it.id != newFriend.id }
        _friendRequests.value = _friendRequests.value.filter { it.id != requestId }
        return true
    }

    fun rejectFriendRequest(requestId: String): Boolean {
        val req = _friendRequests.value.find { it.id == requestId } ?: return false
        val me = _currentUser.value
        if (req.recipientId != me.uid || req.senderId == me.uid) {
            return false
        }
        _friendRequests.value = _friendRequests.value.filter { it.id != requestId }
        return true
    }

    fun cancelOutgoingFriendRequest(requestId: String): Boolean {
        val req = _friendRequests.value.find { it.id == requestId } ?: return false
        val me = _currentUser.value
        if (req.senderId != me.uid) {
            return false
        }
        _friendRequests.value = _friendRequests.value.filter { it.id != requestId }
        return true
    }

    fun addFriend(displayName: String, username: String): FriendItem {
        val newFriend = FriendItem(
            id = "user_${currentTimeMillis()}",
            displayName = displayName,
            username = username,
            avatarUrl = "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=150",
            isOnline = true,
            tradeCount = 0
        )
        _friends.value = listOf(newFriend) + _friends.value
        return newFriend
    }

    fun removeFriend(friendId: String) {
        _friends.value = _friends.value.filter { it.id != friendId }
    }

    fun createCollection(name: String, description: String, iconEmoji: String, privacy: String = "FRIENDS"): CollectionItem {
        val col = CollectionItem(
            id = "col_${currentTimeMillis()}",
            name = name,
            description = description,
            iconEmoji = iconEmoji,
            collectionType = "NORMAL",
            targetCount = 12,
            stampsCount = 0,
            privacy = privacy
        )
        _collections.value = _collections.value + col
        return col
    }

    fun toggleCollectionPrivacy(collectionId: String) {
        _collections.value = _collections.value.map { col ->
            if (col.id == collectionId) {
                val newPrivacy = if (col.privacy == "ONLY_ME") "FRIENDS" else "ONLY_ME"
                col.copy(privacy = newPrivacy)
            } else {
                col
            }
        }
    }

    fun updateCollection(collectionId: String, name: String, description: String, iconEmoji: String): Boolean {
        var updated = false
        _collections.value = _collections.value.map { col ->
            if (col.id == collectionId) {
                updated = true
                col.copy(name = name, description = description, iconEmoji = iconEmoji)
            } else col
        }
        return updated
    }

    fun deleteCollection(collectionId: String): Boolean {
        val target = _collections.value.find { it.id == collectionId } ?: return false
        if (target.collectionType == "SPECIAL" || target.collectionType == "DEFAULT" || target.id.contains("default")) {
            return false // Protection: Default collections cannot be deleted!
        }
        val initialSize = _collections.value.size
        _collections.value = _collections.value.filter { it.id != collectionId }
        val removed = _collections.value.size < initialSize
        if (removed) {
            _stamps.value = _stamps.value.map { stamp ->
                if (stamp.collectionId == collectionId) stamp.copy(collectionId = null) else stamp
            }
        }
        return removed
    }

    fun toggleFavorite(stampId: String): Boolean {
        var newFav = false
        _stamps.value = _stamps.value.map { stamp ->
            if (stamp.id == stampId) {
                newFav = !stamp.favorite
                stamp.copy(favorite = newFav)
            } else stamp
        }
        return newFav
    }

    fun updateStampCollection(stampId: String, collectionId: String?): Boolean {
        var updated = false
        _stamps.value = _stamps.value.map { stamp ->
            if (stamp.id == stampId) {
                updated = true
                stamp.copy(collectionId = collectionId)
            } else stamp
        }
        return updated
    }

    fun deleteStamp(stampId: String): Boolean {
        val initialSize = _stamps.value.size
        _stamps.value = _stamps.value.filter { it.id != stampId }
        val deleted = _stamps.value.size < initialSize
        if (deleted) {
            _feedPosts.value = _feedPosts.value.filter { it.stampId != stampId }
        }
        return deleted
    }
}

