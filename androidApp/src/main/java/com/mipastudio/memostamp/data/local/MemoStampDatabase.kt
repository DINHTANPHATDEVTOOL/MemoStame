package com.mipastudio.memostamp.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        StampEntity::class,
        StampDraftEntity::class,
        CollectionEntity::class,
        FeedPostEntity::class,
        FeedReactionEntity::class,
        FeedCommentEntity::class,
        FeedReplyEntity::class,
        CircleEntity::class,
        FeedSeenEntity::class,
        UserEntity::class
    ],
    version = 10,
    exportSchema = false
)
abstract class MemoStampDatabase : RoomDatabase() {

    abstract fun stampDao(): StampDao
    abstract fun stampDraftDao(): StampDraftDao
    abstract fun collectionDao(): CollectionDao
    abstract fun feedDao(): FeedDao
    abstract fun circleDao(): CircleDao
    abstract fun userDao(): UserDao

    companion object {
        @Volatile
        private var INSTANCE: MemoStampDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS drafts (
                        id TEXT NOT NULL PRIMARY KEY,
                        originalImagePath TEXT NOT NULL,
                        renderedImagePath TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        title TEXT NOT NULL,
                        note TEXT NOT NULL,
                        memoryDate INTEGER NOT NULL,
                        location TEXT,
                        mood TEXT,
                        collectionId TEXT
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS collections (
                        id TEXT NOT NULL PRIMARY KEY,
                        name TEXT NOT NULL,
                        description TEXT,
                        iconEmoji TEXT,
                        coverStampId TEXT,
                        createdAt INTEGER NOT NULL,
                        sortOrder INTEGER NOT NULL,
                        collectionType TEXT NOT NULL,
                        targetCount INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE stamps ADD COLUMN preset TEXT DEFAULT 'NATURAL'")
                db.execSQL("ALTER TABLE stamps ADD COLUMN templateId TEXT DEFAULT 'classic_post'")
                db.execSQL("ALTER TABLE stamps ADD COLUMN borderStyle TEXT DEFAULT 'perforated'")
                db.execSQL("ALTER TABLE stamps ADD COLUMN designJson TEXT DEFAULT NULL")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS feed_posts (
                        id TEXT NOT NULL PRIMARY KEY,
                        stampId TEXT NOT NULL,
                        stampUrl TEXT NOT NULL,
                        stampTitle TEXT NOT NULL,
                        shape TEXT NOT NULL DEFAULT 'classic',
                        authorId TEXT NOT NULL,
                        authorName TEXT NOT NULL,
                        authorAvatar TEXT NOT NULL,
                        caption TEXT,
                        audienceType TEXT NOT NULL,
                        circleId TEXT,
                        circleName TEXT,
                        createdAt INTEGER NOT NULL,
                        type TEXT NOT NULL DEFAULT 'MEMORY',
                        coAuthorsJson TEXT,
                        location TEXT
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS feed_reactions (
                        id TEXT NOT NULL PRIMARY KEY,
                        postId TEXT NOT NULL,
                        userId TEXT NOT NULL,
                        userName TEXT NOT NULL,
                        emoji TEXT NOT NULL,
                        createdAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS feed_replies (
                        id TEXT NOT NULL PRIMARY KEY,
                        postId TEXT NOT NULL,
                        authorId TEXT NOT NULL,
                        authorName TEXT NOT NULL,
                        authorAvatar TEXT NOT NULL,
                        replyStampId TEXT NOT NULL,
                        replyStampUrl TEXT,
                        shape TEXT NOT NULL DEFAULT 'classic',
                        note TEXT,
                        createdAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS circles (
                        id TEXT NOT NULL PRIMARY KEY,
                        ownerId TEXT NOT NULL,
                        name TEXT NOT NULL,
                        icon TEXT NOT NULL DEFAULT '⭕',
                        memberIds TEXT NOT NULL DEFAULT '',
                        createdAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS feed_seen (
                        postId TEXT NOT NULL PRIMARY KEY,
                        userId TEXT NOT NULL,
                        seenAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. Cleanup duplicate reactions prior to unique index creation
                db.execSQL(
                    """
                    DELETE FROM feed_reactions
                    WHERE rowid NOT IN (
                        SELECT MAX(rowid)
                        FROM feed_reactions
                        GROUP BY postId, userId
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_feed_reactions_postId_userId ON feed_reactions(postId, userId)")

                // 2. Create feed_comments table with CASCADE ForeignKey
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS feed_comments (
                        id TEXT NOT NULL PRIMARY KEY,
                        postId TEXT NOT NULL,
                        authorId TEXT NOT NULL,
                        authorName TEXT NOT NULL,
                        authorAvatar TEXT NOT NULL,
                        content TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        FOREIGN KEY(postId) REFERENCES feed_posts(id) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_feed_comments_postId ON feed_comments(postId)")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS users (
                        uid TEXT NOT NULL PRIMARY KEY,
                        username TEXT NOT NULL,
                        displayName TEXT NOT NULL,
                        avatarUrl TEXT,
                        bio TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try {
                    db.execSQL("ALTER TABLE users ADD COLUMN email TEXT NOT NULL DEFAULT ''")
                    db.execSQL("ALTER TABLE users ADD COLUMN passwordHash TEXT NOT NULL DEFAULT ''")
                    db.execSQL("ALTER TABLE users ADD COLUMN city TEXT NOT NULL DEFAULT 'Đà Lạt'")
                    db.execSQL("ALTER TABLE users ADD COLUMN totalStamps INTEGER NOT NULL DEFAULT 0")
                } catch (e: Exception) {
                    // Handled if columns already existed
                }
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try {
                    db.execSQL("ALTER TABLE users ADD COLUMN coverUrl TEXT DEFAULT NULL")
                } catch (e: Exception) {
                    // Handled if column already existed
                }
            }
        }

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try {
                    db.execSQL("ALTER TABLE drafts ADD COLUMN croppedImagePath TEXT DEFAULT NULL")
                    db.execSQL("ALTER TABLE stamps ADD COLUMN croppedImagePath TEXT DEFAULT NULL")
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        fun getInstance(context: Context): MemoStampDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    MemoStampDatabase::class.java,
                    "memostamp_database"
                )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10)
                .fallbackToDestructiveMigration()
                .build().also { INSTANCE = it }
            }
        }
    }
}
