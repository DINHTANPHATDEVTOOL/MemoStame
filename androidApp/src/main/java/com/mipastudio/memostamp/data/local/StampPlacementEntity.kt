package com.mipastudio.memostamp.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.mipastudio.memostamp.domain.model.StampPlacement

@Entity(
    tableName = "album_stamp_placements",
    indices = [
        Index(value = ["ownerId", "albumId", "stampId"], unique = true),
        Index(value = ["ownerId", "albumId", "pageIndex"]),
        Index(value = ["ownerId", "albumId"])
    ]
)
data class StampPlacementEntity(
    @PrimaryKey
    val id: String,
    val ownerId: String,
    val albumId: String,
    val pageIndex: Int,
    val stampId: String,
    val x: Double,
    val y: Double,
    val scale: Double = 1.0,
    val rotationDegrees: Double = 0.0,
    val zIndex: Int = 1,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun toDomain(): StampPlacement = StampPlacement(
        id = id,
        ownerId = ownerId,
        albumId = albumId,
        pageIndex = pageIndex,
        stampId = stampId,
        x = x,
        y = y,
        scale = scale,
        rotationDegrees = rotationDegrees,
        zIndex = zIndex,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    companion object {
        fun fromDomain(domain: StampPlacement): StampPlacementEntity = StampPlacementEntity(
            id = domain.id.ifBlank { java.util.UUID.randomUUID().toString() },
            ownerId = domain.ownerId,
            albumId = domain.albumId,
            pageIndex = domain.pageIndex,
            stampId = domain.stampId,
            x = domain.x,
            y = domain.y,
            scale = domain.scale,
            rotationDegrees = domain.rotationDegrees,
            zIndex = domain.zIndex,
            createdAt = if (domain.createdAt > 0L) domain.createdAt else System.currentTimeMillis(),
            updatedAt = if (domain.updatedAt > 0L) domain.updatedAt else System.currentTimeMillis()
        )
    }
}
