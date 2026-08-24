package com.mipastudio.memostamp.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "feed_reactions",
    indices = [Index(value = ["postId", "userId"], unique = true)],
    foreignKeys = [
        ForeignKey(
            entity = FeedPostEntity::class,
            parentColumns = ["id"],
            childColumns = ["postId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class FeedReactionEntity(
    @PrimaryKey val id: String, // "$postId:$userId"
    val postId: String,
    val userId: String,
    val userName: String,
    val emoji: String = "❤️",
    val createdAt: Long
)

@Entity(
    tableName = "feed_comments",
    indices = [Index(value = ["postId"])],
    foreignKeys = [
        ForeignKey(
            entity = FeedPostEntity::class,
            parentColumns = ["id"],
            childColumns = ["postId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class FeedCommentEntity(
    @PrimaryKey val id: String,
    val postId: String,
    val authorId: String,
    val authorName: String,
    val authorAvatar: String,
    val content: String,
    val createdAt: Long
)

@Entity(
    tableName = "feed_replies",
    indices = [Index(value = ["postId"])],
    foreignKeys = [
        ForeignKey(
            entity = FeedPostEntity::class,
            parentColumns = ["id"],
            childColumns = ["postId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class FeedReplyEntity(
    @PrimaryKey val id: String,
    val postId: String,
    val authorId: String,
    val authorName: String,
    val authorAvatar: String,
    val replyStampId: String,
    val replyStampUrl: String?,
    val shape: String = "classic",
    val note: String? = null,
    val createdAt: Long
)

@Entity(tableName = "circles")
data class CircleEntity(
    @PrimaryKey val id: String,
    val ownerId: String,
    val name: String,
    val icon: String = "⭕",
    val memberIds: String = "",
    val createdAt: Long
)

@Entity(
    tableName = "feed_seen",
    primaryKeys = ["postId", "userId"]
)
data class FeedSeenEntity(
    val postId: String,
    val userId: String,
    val seenAt: Long
)
