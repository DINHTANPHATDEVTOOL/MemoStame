package com.mipastudio.memostamp.data.repository

import android.content.Context
import com.mipastudio.memostamp.core.processor.MemoImageProcessor
import com.mipastudio.memostamp.data.local.*
import com.mipastudio.memostamp.data.repository.UserAuthRepository
import com.mipastudio.memostamp.data.repository.UserProfile
import com.mipastudio.memostamp.data.remote.supabase.SupabaseClient
import com.mipastudio.memostamp.domain.model.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

class FeedRepository private constructor(
    private val context: Context,
    private val database: MemoStampDatabase,
    private val feedDao: FeedDao,
    private val circleDao: CircleDao,
    private val stampDao: StampDao
) {

    private val authRepo by lazy { UserAuthRepository.getInstance(context) }
    private val supabaseClient by lazy { SupabaseClient.getInstance(context) }
    private val coroutineScope = CoroutineScope(Dispatchers.IO)

    init {
        coroutineScope.launch {
            syncFeedLoop()
        }
    }

    private suspend fun syncFeedLoop() {
        while (coroutineScope.isActive) {
            try {
                syncFeedFromSupabase()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            delay(5000) // Poll Supabase every 5 seconds for fast feed updates
        }
    }

    suspend fun syncFeedFromSupabase() = withContext(Dispatchers.IO) {
        // 1. DOWNLOAD cloud posts FIRST so user gets new posts immediately
        try {
            val cloudPosts = supabaseClient.getFeedPosts()
            if (cloudPosts.isEmpty() && feedDao.getPostCount() == 0) {
                ensureDefaultFeedData()
            } else {
                for (p in cloudPosts) {
                    val pId = p.id ?: continue
                    val entity = FeedPostEntity(
                        id = pId,
                        stampId = p.stampId.orEmpty(),
                        stampUrl = p.stampUrl.orEmpty().ifBlank { "https://images.unsplash.com/photo-1506744038136-46273834b3fb?w=600" },
                        stampTitle = p.stampTitle.orEmpty().ifBlank { "Tem kỷ niệm" },
                        shape = p.shape.orEmpty().ifBlank { "classic" },
                        authorId = p.authorId.orEmpty().ifBlank { "unknown_author" },
                        authorName = p.authorName.orEmpty().ifBlank { "Người dùng" },
                        authorAvatar = p.authorAvatar ?: "https://i.pravatar.cc/150?u=${p.authorId}",
                        caption = p.caption.orEmpty(),
                        audienceType = p.audienceType.orEmpty().ifBlank { "EVERYONE" },
                        circleId = p.circleId,
                        circleName = p.circleName,
                        createdAt = p.createdAt ?: System.currentTimeMillis(),
                        type = p.type.orEmpty().ifBlank { "MEMORY" },
                        location = p.location
                    )
                    feedDao.insertPost(entity)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        try {
            val cloudReactions = supabaseClient.getFeedReactions()
            for (r in cloudReactions) {
                val rId = r.id ?: continue
                val postId = r.postId ?: continue
                val userId = r.userId ?: continue
                val entity = FeedReactionEntity(
                    id = if (rId.isNotBlank()) rId else "$postId:$userId",
                    postId = postId,
                    userId = userId,
                    userName = r.userName.orEmpty().ifBlank { "Bạn bè" },
                    emoji = r.emoji.orEmpty().ifBlank { "❤️" },
                    createdAt = r.createdAt ?: System.currentTimeMillis()
                )
                feedDao.insertReaction(entity)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        try {
            val cloudComments = supabaseClient.getFeedComments()
            for (c in cloudComments) {
                val cId = c.id ?: continue
                val postId = c.postId ?: continue
                val entity = FeedCommentEntity(
                    id = cId,
                    postId = postId,
                    authorId = c.authorId.orEmpty().ifBlank { "unknown" },
                    authorName = c.authorName.orEmpty().ifBlank { "Bạn bè" },
                    authorAvatar = c.authorAvatar ?: "https://i.pravatar.cc/150?u=${c.authorId}",
                    content = c.content.orEmpty(),
                    createdAt = c.createdAt ?: System.currentTimeMillis()
                )
                feedDao.insertComment(entity)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 2. UPLOAD local posts to Supabase SECOND
        try {
            val localPosts = feedDao.getAllPostsList()
            for (p in localPosts) {
                val cloudUrl = if (p.stampUrl.startsWith("/") || p.stampUrl.startsWith("file://")) {
                    MemoImageProcessor.encodeImageToCloudBase64(p.stampUrl) ?: p.stampUrl
                } else p.stampUrl

                val domainPost = FeedPost(
                    id = p.id,
                    stampId = if (p.stampId.isNotBlank()) p.stampId else "stamp_${p.id}",
                    stampUrl = if (cloudUrl.isNotBlank()) cloudUrl else "https://images.unsplash.com/photo-1506744038136-46273834b3fb?w=600",
                    stampTitle = if (p.stampTitle.isNotBlank()) p.stampTitle else "Tem kỷ niệm",
                    shape = p.shape,
                    authorId = p.authorId,
                    authorName = p.authorName,
                    authorAvatar = p.authorAvatar,
                    caption = p.caption,
                    audienceType = AudienceType.fromString(p.audienceType),
                    circleId = p.circleId,
                    circleName = p.circleName,
                    createdAt = p.createdAt,
                    type = try { FeedPostType.valueOf(p.type) } catch (_: Exception) { FeedPostType.MEMORY },
                    location = p.location
                )
                supabaseClient.createFeedPost(domainPost)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        try {
            val localReactions = feedDao.getAllReactionsList()
            for (r in localReactions) {
                val record = com.mipastudio.memostamp.data.remote.supabase.SupabaseFeedReactionRecord(
                    id = if (r.id.isNotBlank()) r.id else "${r.postId}:${r.userId}",
                    postId = r.postId,
                    userId = r.userId,
                    userName = r.userName,
                    emoji = if (r.emoji.isNotBlank()) r.emoji else "❤️",
                    createdAt = r.createdAt
                )
                supabaseClient.addFeedReaction(record)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        try {
            val localComments = feedDao.getAllCommentsList()
            for (c in localComments) {
                val record = com.mipastudio.memostamp.data.remote.supabase.SupabaseFeedCommentRecord(
                    id = if (c.id.isNotBlank()) c.id else UUID.randomUUID().toString(),
                    postId = c.postId,
                    authorId = c.authorId,
                    authorName = c.authorName,
                    authorAvatar = c.authorAvatar,
                    content = c.content,
                    createdAt = c.createdAt
                )
                supabaseClient.addFeedComment(record)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun getCurrentUser(): UserProfile {
        return authRepo.currentUser.value
    }

    suspend fun ensureDefaultFeedData() = withContext(Dispatchers.IO) {
        if (feedDao.getPostCount() == 0) {
            val now = System.currentTimeMillis()
            val samplePosts = listOf(
                FeedPostEntity(
                    id = "feed_post_1",
                    stampId = "stamp_dalat_01",
                    stampUrl = "https://images.unsplash.com/photo-1506744038136-46273834b3fb?w=600",
                    stampTitle = "Đà Lạt chiều mưa",
                    shape = "heart",
                    authorId = "user_minh_dalat",
                    authorName = "Minh Nguyễn",
                    authorAvatar = "https://images.unsplash.com/photo-1539571696357-5a69c17a67c6?w=300",
                    caption = "Một chiều chẳng có kế hoạch. Cà phê góc phố Đà Lạt ☕",
                    audienceType = AudienceType.FRIENDS.name,
                    circleId = null,
                    createdAt = now - 2 * 60 * 1000L,
                    type = FeedPostType.MEMORY.name,
                    location = "Đà Lạt, Lâm Đồng"
                ),
                FeedPostEntity(
                    id = "feed_post_2",
                    stampId = "stamp_coffee_02",
                    stampUrl = "https://images.unsplash.com/photo-1495474472287-4d71bcdd2085?w=600",
                    stampTitle = "Coffee after class",
                    shape = "classic",
                    authorId = "user_nam_hanoi",
                    authorName = "Hoàng Nam",
                    authorAvatar = "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=300",
                    caption = "Coffee sau giờ học ☕ nạp lại năng lượng.",
                    audienceType = AudienceType.FRIENDS.name,
                    circleId = null,
                    createdAt = now - 18 * 60 * 1000L,
                    type = FeedPostType.MEMORY.name,
                    location = "Phố cổ Hà Nội"
                ),
                FeedPostEntity(
                    id = "feed_post_3",
                    stampId = "stamp_sunset_03",
                    stampUrl = "https://images.unsplash.com/photo-1507525428034-b723cf961d3e?w=600",
                    stampTitle = "Sunset at Beach",
                    shape = "oval",
                    authorId = "user_linh_seasun",
                    authorName = "Linh Trần",
                    authorAvatar = "https://images.unsplash.com/photo-1517841905240-472988babdf9?w=300",
                    caption = "Cuối tuần bình yên bên bờ biển Nha Trang 🌊",
                    audienceType = AudienceType.FRIENDS.name,
                    circleId = null,
                    createdAt = now - 60 * 60 * 1000L,
                    type = FeedPostType.MEMORY.name,
                    location = "Nha Trang, Khánh Hòa"
                )
            )

            for (p in samplePosts) {
                feedDao.insertPost(p)
                try {
                    val domainPost = FeedPost(
                        id = p.id,
                        stampId = p.stampId,
                        stampUrl = p.stampUrl,
                        stampTitle = p.stampTitle,
                        shape = p.shape,
                        authorId = p.authorId,
                        authorName = p.authorName,
                        authorAvatar = p.authorAvatar,
                        caption = p.caption,
                        audienceType = AudienceType.fromString(p.audienceType),
                        circleId = p.circleId,
                        circleName = p.circleName,
                        createdAt = p.createdAt,
                        type = FeedPostType.valueOf(p.type),
                        location = p.location,
                        reactionCount = 1,
                        commentCount = 1,
                        replyCount = 0
                    )
                    supabaseClient.createFeedPost(domainPost)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            // Sample reactions
            val r1 = FeedReactionEntity("feed_post_1:user_linh_seasun", "feed_post_1", "user_linh_seasun", "Linh Trần", "❤️", now - 1 * 60 * 1000L)
            val r2 = FeedReactionEntity("feed_post_2:user_minh_dalat", "feed_post_2", "user_minh_dalat", "Minh Nguyễn", "❤️", now - 10 * 60 * 1000L)
            feedDao.insertReaction(r1)
            feedDao.insertReaction(r2)
            try {
                supabaseClient.addFeedReaction(com.mipastudio.memostamp.data.remote.supabase.SupabaseFeedReactionRecord(r1.id, r1.postId, r1.userId, r1.userName, r1.emoji, r1.createdAt))
                supabaseClient.addFeedReaction(com.mipastudio.memostamp.data.remote.supabase.SupabaseFeedReactionRecord(r2.id, r2.postId, r2.userId, r2.userName, r2.emoji, r2.createdAt))
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // Sample comments
            val c1 = FeedCommentEntity(UUID.randomUUID().toString(), "feed_post_1", "user_linh_seasun", "Linh Trần", "https://images.unsplash.com/photo-1517841905240-472988babdf9?w=300", "Cảnh sương mù đẹp quá Minh ơi! 🌸", now - 1 * 60 * 1000L)
            val c2 = FeedCommentEntity(UUID.randomUUID().toString(), "feed_post_1", "user_nam_hanoi", "Hoàng Nam", "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=300", "Nhìn chill ghê ☕", now - 30 * 1000L)
            feedDao.insertComment(c1)
            feedDao.insertComment(c2)
            try {
                supabaseClient.addFeedComment(com.mipastudio.memostamp.data.remote.supabase.SupabaseFeedCommentRecord(c1.id, c1.postId, c1.authorId, c1.authorName, c1.authorAvatar, c1.content, c1.createdAt))
                supabaseClient.addFeedComment(com.mipastudio.memostamp.data.remote.supabase.SupabaseFeedCommentRecord(c2.id, c2.postId, c2.authorId, c2.authorName, c2.authorAvatar, c2.content, c2.createdAt))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        val user = getCurrentUser()
        if (circleDao.getCircleCountByOwner(user.userId) == 0) {
            val defaults = listOf(
                CircleEntity(
                    id = "circle_${user.userId}_best_friends",
                    ownerId = user.userId,
                    name = "Bạn Thân (Close Friends)",
                    icon = "💖",
                    memberIds = "",
                    createdAt = System.currentTimeMillis()
                ),
                CircleEntity(
                    id = "circle_${user.userId}_travel_buddies",
                    ownerId = user.userId,
                    name = "Hội Phượt & Du Lịch",
                    icon = "✈️",
                    memberIds = "",
                    createdAt = System.currentTimeMillis()
                )
            )
            for (c in defaults) {
                circleDao.insertCircle(c)
            }
        }
    }

    private fun canCurrentUserViewPost(
        post: FeedPostEntity,
        currentUser: UserProfile,
        friendIds: Set<String>,
        circles: List<CircleEntity>
    ): Boolean {
        val audience = AudienceType.fromString(post.audienceType)
        return when (audience) {
            AudienceType.ONLY_ME -> post.authorId == currentUser.userId
            AudienceType.FRIENDS -> post.authorId == currentUser.userId || friendIds.contains(post.authorId)
            AudienceType.SPECIFIC_FRIENDS -> {
                if (post.authorId == currentUser.userId) {
                    true
                } else if (post.circleId.isNullOrBlank()) {
                    false
                } else {
                    val circle = circles.find { it.id == post.circleId }
                    circle != null &&
                    circle.ownerId == post.authorId &&
                    circle.memberIds.split(",").map { it.trim() }.contains(currentUser.userId)
                }
            }
        }
    }

    private suspend fun isAuthorizedToViewPost(postId: String): Boolean {
        val entity = feedDao.getPostById(postId) ?: return false
        val currentUser = getCurrentUser()
        val friendIds = authRepo.friendIds.value
        val circles = circleDao.observeAllCircles().first()
        return canCurrentUserViewPost(entity, currentUser, friendIds, circles)
    }

    fun observeFriendsFeed(): Flow<List<FeedPost>> {
        return combine(
            feedDao.observeAllPosts(),
            feedDao.observeAllReactions(),
            feedDao.observeAllComments(),
            feedDao.observeAllReplies(),
            feedDao.observeSeenPosts(),
            authRepo.friendIds,
            circleDao.observeAllCircles(),
            authRepo.currentUser
        ) { flowArray ->
            @Suppress("UNCHECKED_CAST")
            val posts = flowArray[0] as List<FeedPostEntity>
            @Suppress("UNCHECKED_CAST")
            val reactions = flowArray[1] as List<FeedReactionEntity>
            @Suppress("UNCHECKED_CAST")
            val comments = flowArray[2] as List<FeedCommentEntity>
            @Suppress("UNCHECKED_CAST")
            val replies = flowArray[3] as List<FeedReplyEntity>
            @Suppress("UNCHECKED_CAST")
            val seenList = flowArray[4] as List<FeedSeenEntity>
            @Suppress("UNCHECKED_CAST")
            val friendIds = flowArray[5] as Set<String>
            @Suppress("UNCHECKED_CAST")
            val circles = flowArray[6] as List<CircleEntity>
            val currentUser = flowArray[7] as UserProfile

            val reactionMap = reactions.groupBy { it.postId }
            val commentMap = comments.groupBy { it.postId }
            val replyMap = replies.groupBy { it.postId }
            val seenMap = seenList.filter { it.userId == currentUser.userId }.associateBy { it.postId }

            val filteredEntities = posts.filter { entity ->
                canCurrentUserViewPost(entity, currentUser, friendIds, circles)
            }

            filteredEntities.map { entity ->
                val postReactions = reactionMap[entity.id].orEmpty().map {
                    FeedReaction(it.id, it.postId, it.userId, it.userName, it.emoji, it.createdAt)
                }
                val postComments = commentMap[entity.id].orEmpty().map {
                    FeedComment(it.id, it.postId, it.authorId, it.authorName, it.authorAvatar, it.content, it.createdAt)
                }
                val postReplies = replyMap[entity.id].orEmpty().map {
                    FeedReply(it.id, it.postId, it.authorId, it.authorName, it.authorAvatar, it.replyStampId, it.replyStampUrl, it.shape, it.note, it.createdAt)
                }
                val isLiked = postReactions.any { it.userId == currentUser.userId }

                FeedPost(
                    id = entity.id,
                    stampId = entity.stampId,
                    stampUrl = entity.stampUrl,
                    stampTitle = entity.stampTitle,
                    shape = entity.shape,
                    authorId = entity.authorId,
                    authorName = entity.authorName,
                    authorAvatar = entity.authorAvatar,
                    caption = entity.caption,
                    audienceType = AudienceType.fromString(entity.audienceType),
                    circleId = entity.circleId,
                    circleName = entity.circleName,
                    createdAt = entity.createdAt,
                    type = FeedPostType.valueOf(entity.type),
                    location = entity.location,
                    reactionCount = postReactions.size,
                    commentCount = postComments.size,
                    replyCount = postReplies.size,
                    reactions = postReactions,
                    comments = postComments,
                    replies = postReplies,
                    isLikedByMe = isLiked,
                    isSeen = seenMap.containsKey(entity.id)
                )
            }
        }.flowOn(Dispatchers.IO)
    }

    suspend fun getPostById(postId: String): FeedPost? = withContext(Dispatchers.IO) {
        val entity = feedDao.getPostById(postId) ?: return@withContext null
        val currentUser = getCurrentUser()
        val friendIds = authRepo.friendIds.value
        val circles = circleDao.observeAllCircles().first()

        if (!canCurrentUserViewPost(entity, currentUser, friendIds, circles)) {
            return@withContext null
        }
        val reactions = feedDao.getReactionsForPost(entity.id).map {
            FeedReaction(it.id, it.postId, it.userId, it.userName, it.emoji, it.createdAt)
        }
        val comments = feedDao.getCommentsForPost(entity.id).map {
            FeedComment(it.id, it.postId, it.authorId, it.authorName, it.authorAvatar, it.content, it.createdAt)
        }
        val replies = feedDao.getRepliesForPost(entity.id).map {
            FeedReply(it.id, it.postId, it.authorId, it.authorName, it.authorAvatar, it.replyStampId, it.replyStampUrl, it.shape, it.note, it.createdAt)
        }
        FeedPost(
            id = entity.id,
            stampId = entity.stampId,
            stampUrl = entity.stampUrl,
            stampTitle = entity.stampTitle,
            shape = entity.shape,
            authorId = entity.authorId,
            authorName = entity.authorName,
            authorAvatar = entity.authorAvatar,
            caption = entity.caption,
            audienceType = AudienceType.fromString(entity.audienceType),
            circleId = entity.circleId,
            circleName = entity.circleName,
            createdAt = entity.createdAt,
            type = FeedPostType.valueOf(entity.type),
            location = entity.location,
            reactionCount = reactions.size,
            commentCount = comments.size,
            replyCount = replies.size,
            reactions = reactions,
            comments = comments,
            replies = replies,
            isLikedByMe = reactions.any { it.userId == currentUser.userId }
        )
    }

    suspend fun createPostFromStamp(
        stampEntity: StampEntity,
        audienceType: AudienceType,
        circleId: String? = null,
        circleName: String? = null,
        replyToPostId: String? = null
    ): String = withContext(Dispatchers.IO) {
        val currentUser = getCurrentUser()

        val shape = when {
            stampEntity.templateId?.contains("heart", ignoreCase = true) == true -> "heart"
            stampEntity.templateId?.contains("oval", ignoreCase = true) == true -> "oval"
            else -> "classic"
        }

        if (replyToPostId != null) {
            if (!isAuthorizedToViewPost(replyToPostId)) {
                throw SecurityException("Not authorized to reply to this post")
            }
            val replyId = UUID.randomUUID().toString()
            val reply = FeedReplyEntity(
                id = replyId,
                postId = replyToPostId,
                authorId = currentUser.userId,
                authorName = currentUser.displayName,
                authorAvatar = currentUser.avatarUrl,
                replyStampId = stampEntity.id,
                replyStampUrl = stampEntity.stampImagePath,
                shape = shape,
                note = stampEntity.note,
                createdAt = System.currentTimeMillis()
            )
            feedDao.insertReply(reply)
            return@withContext replyId
        }

        val postId = UUID.randomUUID().toString()
        val cloudStampUrl = MemoImageProcessor.encodeImageToCloudBase64(stampEntity.stampImagePath) ?: stampEntity.stampImagePath

        val postEntity = FeedPostEntity(
            id = postId,
            stampId = stampEntity.id,
            stampUrl = cloudStampUrl,
            stampTitle = stampEntity.title,
            shape = shape,
            authorId = currentUser.userId,
            authorName = currentUser.displayName,
            authorAvatar = currentUser.avatarUrl,
            caption = stampEntity.note.ifBlank { stampEntity.title },
            audienceType = audienceType.name,
            circleId = circleId,
            circleName = circleName,
            createdAt = System.currentTimeMillis(),
            type = FeedPostType.MEMORY.name,
            location = stampEntity.location
        )
        feedDao.insertPost(postEntity)

        coroutineScope.launch {
            try {
                val domainPost = FeedPost(
                    id = postEntity.id,
                    stampId = postEntity.stampId,
                    stampUrl = postEntity.stampUrl,
                    stampTitle = postEntity.stampTitle,
                    shape = postEntity.shape,
                    authorId = postEntity.authorId,
                    authorName = postEntity.authorName,
                    authorAvatar = postEntity.authorAvatar,
                    caption = postEntity.caption,
                    audienceType = AudienceType.fromString(postEntity.audienceType),
                    circleId = postEntity.circleId,
                    circleName = postEntity.circleName,
                    createdAt = postEntity.createdAt,
                    type = FeedPostType.valueOf(postEntity.type),
                    location = postEntity.location,
                    reactionCount = 0,
                    commentCount = 0,
                    replyCount = 0
                )
                val res = supabaseClient.createFeedPost(domainPost)
                if (res.isSuccess) {
                    syncFeedFromSupabase()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        postId
    }

    suspend fun toggleLike(postId: String) = withContext(Dispatchers.IO) {
        if (!isAuthorizedToViewPost(postId)) return@withContext
        val currentUser = getCurrentUser()
        val reactionId = "$postId:${currentUser.userId}"
        val existingReactions = feedDao.getReactionsForPost(postId)
        val hasLiked = existingReactions.any { it.userId == currentUser.userId }
        if (hasLiked) {
            feedDao.deleteReaction(postId, currentUser.userId)
            coroutineScope.launch {
                try {
                    supabaseClient.deleteFeedReaction(postId, currentUser.userId)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        } else {
            val entity = FeedReactionEntity(
                id = reactionId,
                postId = postId,
                userId = currentUser.userId,
                userName = currentUser.displayName,
                emoji = "❤️",
                createdAt = System.currentTimeMillis()
            )
            feedDao.insertReaction(entity)
            coroutineScope.launch {
                try {
                    val record = com.mipastudio.memostamp.data.remote.supabase.SupabaseFeedReactionRecord(
                        id = reactionId,
                        postId = postId,
                        userId = currentUser.userId,
                        userName = currentUser.displayName,
                        emoji = "❤️",
                        createdAt = System.currentTimeMillis()
                    )
                    supabaseClient.addFeedReaction(record)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    suspend fun addComment(postId: String, content: String): String = withContext(Dispatchers.IO) {
        if (!isAuthorizedToViewPost(postId)) return@withContext ""
        val currentUser = getCurrentUser()
        val commentId = UUID.randomUUID().toString()
        val comment = FeedCommentEntity(
            id = commentId,
            postId = postId,
            authorId = currentUser.userId,
            authorName = currentUser.displayName,
            authorAvatar = currentUser.avatarUrl,
            content = content,
            createdAt = System.currentTimeMillis()
        )
        feedDao.insertComment(comment)
        coroutineScope.launch {
            try {
                val record = com.mipastudio.memostamp.data.remote.supabase.SupabaseFeedCommentRecord(
                    id = commentId,
                    postId = postId,
                    authorId = currentUser.userId,
                    authorName = currentUser.displayName,
                    authorAvatar = currentUser.avatarUrl,
                    content = content,
                    createdAt = comment.createdAt
                )
                supabaseClient.addFeedComment(record)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        commentId
    }

    suspend fun like(postId: String) = withContext(Dispatchers.IO) {
        if (!isAuthorizedToViewPost(postId)) return@withContext
        val currentUser = getCurrentUser()
        val reactionId = "$postId:${currentUser.userId}"
        val existingReactions = feedDao.getReactionsForPost(postId)
        val hasLiked = existingReactions.any { it.userId == currentUser.userId }
        if (!hasLiked) {
            feedDao.insertReaction(
                FeedReactionEntity(
                    id = reactionId,
                    postId = postId,
                    userId = currentUser.userId,
                    userName = currentUser.displayName,
                    emoji = "❤️",
                    createdAt = System.currentTimeMillis()
                )
            )
        }
    }

    fun observeCircleFeed(circleId: String): Flow<List<FeedPost>> {
        return combine(
            feedDao.observeCirclePosts(circleId),
            feedDao.observeAllReactions(),
            feedDao.observeAllComments(),
            feedDao.observeAllReplies(),
            feedDao.observeSeenPosts(),
            circleDao.observeAllCircles(),
            authRepo.currentUser
        ) { values ->
            @Suppress("UNCHECKED_CAST")
            val posts = values[0] as List<FeedPostEntity>
            @Suppress("UNCHECKED_CAST")
            val reactions = values[1] as List<FeedReactionEntity>
            @Suppress("UNCHECKED_CAST")
            val comments = values[2] as List<FeedCommentEntity>
            @Suppress("UNCHECKED_CAST")
            val replies = values[3] as List<FeedReplyEntity>
            @Suppress("UNCHECKED_CAST")
            val seenList = values[4] as List<FeedSeenEntity>
            @Suppress("UNCHECKED_CAST")
            val circles = values[5] as List<CircleEntity>
            val currentUser = values[6] as UserProfile

            val circle = circles.find { it.id == circleId }
            if (circle == null) return@combine emptyList()
            val isOwner = circle.ownerId == currentUser.userId
            val isMember = circle.memberIds.split(",").map { it.trim() }.contains(currentUser.userId)
            if (!isOwner && !isMember) return@combine emptyList()

            val reactionMap = reactions.groupBy { it.postId }
            val commentMap = comments.groupBy { it.postId }
            val replyMap = replies.groupBy { it.postId }
            val seenMap = seenList.filter { it.userId == currentUser.userId }.associateBy { it.postId }

            val friendIds = authRepo.friendIds.value
            val filteredPosts = posts.filter { entity ->
                entity.authorId == circle.ownerId &&
                AudienceType.fromString(entity.audienceType) == AudienceType.SPECIFIC_FRIENDS &&
                canCurrentUserViewPost(entity, currentUser, friendIds, circles)
            }

            filteredPosts.map { entity ->
                val postReactions = reactionMap[entity.id].orEmpty().map {
                    FeedReaction(it.id, it.postId, it.userId, it.userName, it.emoji, it.createdAt)
                }
                val postComments = commentMap[entity.id].orEmpty().map {
                    FeedComment(it.id, it.postId, it.authorId, it.authorName, it.authorAvatar, it.content, it.createdAt)
                }
                val postReplies = replyMap[entity.id].orEmpty().map {
                    FeedReply(it.id, it.postId, it.authorId, it.authorName, it.authorAvatar, it.replyStampId, it.replyStampUrl, it.shape, it.note, it.createdAt)
                }
                val isLiked = postReactions.any { it.userId == currentUser.userId }

                FeedPost(
                    id = entity.id,
                    stampId = entity.stampId,
                    stampUrl = entity.stampUrl,
                    stampTitle = entity.stampTitle,
                    shape = entity.shape,
                    authorId = entity.authorId,
                    authorName = entity.authorName,
                    authorAvatar = entity.authorAvatar,
                    caption = entity.caption,
                    audienceType = AudienceType.valueOf(entity.audienceType),
                    circleId = entity.circleId,
                    circleName = entity.circleName,
                    createdAt = entity.createdAt,
                    type = FeedPostType.valueOf(entity.type),
                    location = entity.location,
                    reactionCount = postReactions.size,
                    commentCount = postComments.size,
                    replyCount = postReplies.size,
                    reactions = postReactions,
                    comments = postComments,
                    replies = postReplies,
                    isLikedByMe = isLiked,
                    isSeen = seenMap.containsKey(entity.id)
                )
            }
        }.flowOn(Dispatchers.IO)
    }

    suspend fun markPostSeen(postId: String, userId: String) = withContext(Dispatchers.IO) {
        feedDao.markPostSeen(
            FeedSeenEntity(postId = postId, userId = userId, seenAt = System.currentTimeMillis())
        )
    }

    fun observeCircles(): Flow<List<Circle>> = combine(
        circleDao.observeAllCircles(),
        authRepo.currentUser
    ) { list, currentUser ->
        list.filter { entity ->
            entity.ownerId == currentUser.userId ||
            entity.memberIds.split(",").map { it.trim() }.contains(currentUser.userId)
        }.map { entity ->
            Circle(
                id = entity.id,
                ownerId = entity.ownerId,
                name = entity.name,
                icon = entity.icon,
                memberIds = entity.memberIds.split(",").filter { it.isNotBlank() },
                createdAt = entity.createdAt
            )
        }
    }.flowOn(Dispatchers.IO)

    suspend fun createCircle(name: String, icon: String = "⭕", memberIds: List<String>): Circle = withContext(Dispatchers.IO) {
        val id = UUID.randomUUID().toString()
        val user = getCurrentUser()
        val entity = CircleEntity(
            id = id,
            ownerId = user.userId,
            name = name,
            icon = icon,
            memberIds = memberIds.joinToString(","),
            createdAt = System.currentTimeMillis()
        )
        circleDao.insertCircle(entity)
        Circle(id, entity.ownerId, name, icon, memberIds, entity.createdAt)
    }

    suspend fun deleteComment(commentId: String) = withContext(Dispatchers.IO) {
        val currentUser = getCurrentUser()
        val comment = feedDao.getAllCommentsList().find { it.id == commentId } ?: return@withContext
        val post = feedDao.getPostById(comment.postId)
        val isCommentAuthor = comment.authorId == currentUser.userId
        val isPostAuthor = post != null && post.authorId == currentUser.userId
        if (isCommentAuthor || isPostAuthor) {
            feedDao.deleteComment(commentId)
            val res = supabaseClient.deleteFeedComment(commentId)
            if (res.isFailure) {
                System.err.println("Cloud deleteFeedComment failed for $commentId: ${res.exceptionOrNull()?.message}")
            }
        }
    }

    suspend fun removePostFromFeed(postId: String) = withContext(Dispatchers.IO) {
        val currentUser = getCurrentUser()
        val post = feedDao.getPostById(postId) ?: return@withContext
        if (post.authorId == currentUser.userId) {
            feedDao.deletePostById(postId)
            val res = supabaseClient.deleteFeedPost(postId)
            if (res.isFailure) {
                System.err.println("Cloud deleteFeedPost failed for $postId: ${res.exceptionOrNull()?.message}")
            }
        }
    }

    suspend fun deleteMemory(stampId: String) = withContext(Dispatchers.IO) {
        val currentUser = getCurrentUser()
        val stamp = stampDao.getStampById(stampId, currentUser.userId)
            ?: return@withContext

        val post = feedDao.getPostByStampId(stampId)
        if (post != null && post.authorId != currentUser.userId) {
            return@withContext
        }
        if (post != null) {
            feedDao.deletePostByStampId(stampId)
            val res = supabaseClient.deleteFeedPost(post.id)
            if (res.isFailure) {
                System.err.println("Cloud deleteFeedPost failed for ${post.id}: ${res.exceptionOrNull()?.message}")
            }
        }
        stampDao.deleteById(stampId, currentUser.userId)
    }

    companion object {
        @Volatile
        private var INSTANCE: FeedRepository? = null

        fun getInstance(context: Context): FeedRepository {
            return INSTANCE ?: synchronized(this) {
                val db = MemoStampDatabase.getInstance(context)
                val instance = FeedRepository(context.applicationContext, db, db.feedDao(), db.circleDao(), db.stampDao())
                INSTANCE = instance
                instance
            }
        }
    }
}
