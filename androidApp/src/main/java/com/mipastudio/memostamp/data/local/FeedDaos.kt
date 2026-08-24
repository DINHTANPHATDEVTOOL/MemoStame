package com.mipastudio.memostamp.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface FeedDao {
    @Query("SELECT * FROM feed_posts ORDER BY createdAt DESC")
    fun observeAllPosts(): Flow<List<FeedPostEntity>>

    @Query("SELECT * FROM feed_posts WHERE audienceType = 'FRIENDS' ORDER BY createdAt DESC")
    fun observeFriendsPosts(): Flow<List<FeedPostEntity>>

    @Query("SELECT * FROM feed_posts WHERE circleId = :circleId ORDER BY createdAt DESC")
    fun observeCirclePosts(circleId: String): Flow<List<FeedPostEntity>>

    @Query("SELECT * FROM feed_posts WHERE id = :postId")
    fun getPostById(postId: String): FeedPostEntity?

    @Query("SELECT * FROM feed_posts WHERE stampId = :stampId")
    fun getPostByStampId(stampId: String): FeedPostEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertPost(post: FeedPostEntity): Long

    @Query("DELETE FROM feed_posts WHERE id = :postId")
    fun deletePostById(postId: String): Int

    @Query("DELETE FROM feed_posts WHERE stampId = :stampId")
    fun deletePostByStampId(stampId: String): Int

    @Query("SELECT COUNT(*) FROM feed_posts")
    fun getPostCount(): Int

    @Query("SELECT * FROM feed_posts ORDER BY createdAt DESC")
    fun getAllPostsList(): List<FeedPostEntity>

    // Reactions (Single reaction per user/post via deterministic PK or Unique Index)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertReaction(reaction: FeedReactionEntity): Long

    @Query("SELECT * FROM feed_reactions ORDER BY createdAt ASC")
    fun observeAllReactions(): Flow<List<FeedReactionEntity>>

    @Query("SELECT * FROM feed_reactions WHERE postId = :postId ORDER BY createdAt ASC")
    fun getReactionsForPost(postId: String): List<FeedReactionEntity>

    @Query("SELECT * FROM feed_reactions")
    fun getAllReactionsList(): List<FeedReactionEntity>

    @Query("DELETE FROM feed_reactions WHERE postId = :postId AND userId = :userId")
    fun deleteReaction(postId: String, userId: String): Int

    // Comments
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertComment(comment: FeedCommentEntity): Long

    @Query("SELECT * FROM feed_comments ORDER BY createdAt ASC")
    fun observeAllComments(): Flow<List<FeedCommentEntity>>

    @Query("SELECT * FROM feed_comments WHERE postId = :postId ORDER BY createdAt ASC")
    fun getCommentsForPost(postId: String): List<FeedCommentEntity>

    @Query("SELECT * FROM feed_comments")
    fun getAllCommentsList(): List<FeedCommentEntity>

    @Query("DELETE FROM feed_comments WHERE id = :commentId")
    fun deleteComment(commentId: String): Int

    // Replies
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertReply(reply: FeedReplyEntity): Long

    @Query("SELECT * FROM feed_replies ORDER BY createdAt ASC")
    fun observeAllReplies(): Flow<List<FeedReplyEntity>>

    @Query("SELECT * FROM feed_replies WHERE postId = :postId ORDER BY createdAt ASC")
    fun getRepliesForPost(postId: String): List<FeedReplyEntity>

    // Seen State
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun markPostSeen(seen: FeedSeenEntity): Long

    @Query("SELECT * FROM feed_seen")
    fun observeSeenPosts(): Flow<List<FeedSeenEntity>>
}

@Dao
interface CircleDao {
    @Query("SELECT * FROM circles ORDER BY createdAt DESC")
    fun observeAllCircles(): Flow<List<CircleEntity>>

    @Query("SELECT * FROM circles WHERE id = :circleId")
    fun getCircleById(circleId: String): CircleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertCircle(circle: CircleEntity): Long

    @Query("DELETE FROM circles WHERE id = :circleId")
    fun deleteCircle(circleId: String): Int

    @Query("SELECT COUNT(*) FROM circles")
    fun getCircleCount(): Int
}
