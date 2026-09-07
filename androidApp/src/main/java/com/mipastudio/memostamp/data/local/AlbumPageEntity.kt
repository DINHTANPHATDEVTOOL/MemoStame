package com.mipastudio.memostamp.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.mipastudio.memostamp.domain.model.AlbumPage

@Entity(
    tableName = "album_pages",
    indices = [
        Index(value = ["ownerId", "albumId", "pageIndex"], unique = true),
        Index(value = ["ownerId", "albumId"])
    ]
)
data class AlbumPageEntity(
    @PrimaryKey
    val id: String,
    val ownerId: String,
    val albumId: String,
    val pageIndex: Int,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun toDomain(): AlbumPage = AlbumPage(
        id = id,
        ownerId = ownerId,
        albumId = albumId,
        pageIndex = pageIndex,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    companion object {
        fun fromDomain(domain: AlbumPage): AlbumPageEntity = AlbumPageEntity(
            id = domain.id.ifBlank { java.util.UUID.randomUUID().toString() },
            ownerId = domain.ownerId,
            albumId = domain.albumId,
            pageIndex = domain.pageIndex,
            createdAt = if (domain.createdAt > 0L) domain.createdAt else System.currentTimeMillis(),
            updatedAt = if (domain.updatedAt > 0L) domain.updatedAt else System.currentTimeMillis()
        )
    }
}
