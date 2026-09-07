package com.mipastudio.memostamp.data.repository

import android.content.Context
import android.util.Log
import com.mipastudio.memostamp.data.local.AlbumLayoutDao
import com.mipastudio.memostamp.data.local.AlbumPageEntity
import com.mipastudio.memostamp.data.local.MemoStampDatabase
import com.mipastudio.memostamp.data.local.StampPlacementEntity
import com.mipastudio.memostamp.data.remote.supabase.SupabaseClient
import com.mipastudio.memostamp.domain.model.AlbumPage
import com.mipastudio.memostamp.domain.model.StampPlacement
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

data class AlbumLayoutState(
    val albumId: String,
    val pages: List<AlbumPage> = emptyList(),
    val placements: List<StampPlacement> = emptyList()
)

class AlbumLayoutRepository(
    private val context: Context,
    private val albumLayoutDao: AlbumLayoutDao,
    private val supabaseClient: SupabaseClient,
    private val getCurrentUserId: () -> String?
) {
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val sessionGeneration = AtomicLong(0)

    companion object {
        private const val TAG = "AlbumLayoutRepository"

        @Volatile
        private var instance: AlbumLayoutRepository? = null

        fun getInstance(context: Context, getCurrentUserId: () -> String?): AlbumLayoutRepository {
            return instance ?: synchronized(this) {
                instance ?: run {
                    val db = MemoStampDatabase.getInstance(context)
                    val sClient = SupabaseClient.getInstance(context)
                    AlbumLayoutRepository(
                        context.applicationContext,
                        db.albumLayoutDao(),
                        sClient,
                        getCurrentUserId
                    ).also { instance = it }
                }
            }
        }

        fun isVirtualAlbum(albumId: String): Boolean {
            return albumId.isBlank() || albumId.startsWith("loc_") || albumId.startsWith("virtual_")
        }

        fun isValidGeometry(x: Double, y: Double, scale: Double, rotationDegrees: Double, zIndex: Int = 0): Boolean {
            if (x.isNaN() || x.isInfinite() || x < 0.0 || x > 1.0) return false
            if (y.isNaN() || y.isInfinite() || y < 0.0 || y > 1.0) return false
            if (scale.isNaN() || scale.isInfinite() || scale <= 0.05 || scale > 5.0) return false
            if (rotationDegrees.isNaN() || rotationDegrees.isInfinite() || rotationDegrees < -360.0 || rotationDegrees > 360.0) return false
            if (zIndex < -1000 || zIndex > 1000) return false
            return true
        }
    }

    /**
     * Bumps the session generation counter to invalidate inflight async responses
     * across logout and login boundaries (Account-switch race guard).
     */
    fun onSessionChanged() {
        sessionGeneration.incrementAndGet()
        Log.d(TAG, "Session generation bumped to ${sessionGeneration.get()}")
    }

    /**
     * Observes local layout reactively for an album.
     * Initiates a background cloud sync for non-virtual albums if authenticated.
     */
    fun observeLayout(albumId: String): Flow<AlbumLayoutState> {
        val ownerId = getCurrentUserId()?.trim().orEmpty()
        val pagesFlow = albumLayoutDao.observePages(ownerId, albumId)
        val placementsFlow = albumLayoutDao.observePlacements(ownerId, albumId)

        // Trigger background sync if real collection and authenticated
        if (ownerId.isNotBlank() && !isVirtualAlbum(albumId)) {
            repositoryScope.launch {
                try {
                    syncLayoutFromCloud(albumId)
                } catch (e: Exception) {
                    Log.w(TAG, "Background sync for album $albumId skipped or failed: ${e.message}")
                }
            }
        }

        return combine(pagesFlow, placementsFlow) { pageEntities, placementEntities ->
            AlbumLayoutState(
                albumId = albumId,
                pages = pageEntities.map { it.toDomain() },
                placements = placementEntities.map { it.toDomain() }
            )
        }.distinctUntilChanged()
    }

    suspend fun getLocalLayout(albumId: String): AlbumLayoutState = withContext(Dispatchers.IO) {
        val ownerId = getCurrentUserId()?.trim().orEmpty()
        val pages = albumLayoutDao.getPages(ownerId, albumId).map { it.toDomain() }
        val placements = albumLayoutDao.getPlacements(ownerId, albumId).map { it.toDomain() }
        AlbumLayoutState(albumId = albumId, pages = pages, placements = placements)
    }

    /**
     * Authenticated cloud sync with local-first reconciliation and account-switch race guard.
     */
    suspend fun syncLayoutFromCloud(albumId: String): Result<Unit> = withContext(Dispatchers.IO) {
        if (isVirtualAlbum(albumId)) {
            Log.d(TAG, "Skipping cloud sync for virtual album: $albumId")
            return@withContext Result.success(Unit)
        }

        val capturedUid = getCurrentUserId()?.trim()
        if (capturedUid.isNullOrBlank()) {
            return@withContext Result.failure(IllegalStateException("Unauthenticated"))
        }
        val capturedGen = sessionGeneration.get()

        try {
            val remotePages = supabaseClient.getAlbumPages(capturedUid, albumId)
            val remotePlacements = supabaseClient.getStampPlacements(capturedUid, albumId)

            // Account-Switch Race Guard check
            val activeUid = getCurrentUserId()?.trim()
            val activeGen = sessionGeneration.get()
            if (activeUid != capturedUid || activeGen != capturedGen) {
                Log.w(TAG, "Discarded stale layout response for user $capturedUid; active user is $activeUid")
                return@withContext Result.failure(CancellationException("Account switch race guard"))
            }

            // Reconcile into Room
            if (remotePages.isNotEmpty()) {
                val pageEntities = remotePages.map { r ->
                    AlbumPageEntity(
                        id = r.id.ifBlank { UUID.randomUUID().toString() },
                        ownerId = capturedUid,
                        albumId = albumId,
                        pageIndex = r.pageIndex,
                        createdAt = System.currentTimeMillis(),
                        updatedAt = System.currentTimeMillis()
                    )
                }
                albumLayoutDao.upsertPages(pageEntities)
            }

            if (remotePlacements.isNotEmpty()) {
                val placementEntities = remotePlacements.filter { r ->
                    isValidGeometry(r.x, r.y, r.scale, r.rotationDegrees, r.zIndex)
                }.map { r ->
                    StampPlacementEntity(
                        id = r.id.ifBlank { UUID.randomUUID().toString() },
                        ownerId = capturedUid,
                        albumId = albumId,
                        pageIndex = r.pageIndex,
                        stampId = r.stampId,
                        x = r.x,
                        y = r.y,
                        scale = r.scale,
                        rotationDegrees = r.rotationDegrees,
                        zIndex = r.zIndex,
                        createdAt = System.currentTimeMillis(),
                        updatedAt = System.currentTimeMillis()
                    )
                }
                albumLayoutDao.upsertPlacements(placementEntities)
            }

            Result.success(Unit)
        } catch (e: Throwable) {
            Log.w(TAG, "Error syncing layout for album $albumId: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun upsertPage(albumId: String, pageIndex: Int): Result<AlbumPage> = withContext(Dispatchers.IO) {
        if (pageIndex < 0) return@withContext Result.failure(IllegalArgumentException("pageIndex must be >= 0"))
        val ownerId = getCurrentUserId()?.trim().orEmpty()
        val page = AlbumPage(
            id = UUID.randomUUID().toString(),
            ownerId = ownerId,
            albumId = albumId,
            pageIndex = pageIndex,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        albumLayoutDao.upsertPages(listOf(AlbumPageEntity.fromDomain(page)))

        if (ownerId.isNotBlank() && !isVirtualAlbum(albumId)) {
            repositoryScope.launch {
                try {
                    supabaseClient.upsertAlbumPages(listOf(page))
                } catch (e: Exception) {
                    Log.w(TAG, "Cloud upsertPage error: ${e.message}")
                }
            }
        }
        Result.success(page)
    }

    suspend fun upsertPlacement(
        albumId: String,
        pageIndex: Int,
        stampId: String,
        x: Double,
        y: Double,
        scale: Double = 1.0,
        rotationDegrees: Double = 0.0,
        zIndex: Int = 1
    ): Result<StampPlacement> = withContext(Dispatchers.IO) {
        if (pageIndex < 0) return@withContext Result.failure(IllegalArgumentException("pageIndex must be >= 0"))
        if (!isValidGeometry(x, y, scale, rotationDegrees, zIndex)) {
            return@withContext Result.failure(IllegalArgumentException("Invalid geometry parameters"))
        }
        val ownerId = getCurrentUserId()?.trim().orEmpty()

        // Ensure page exists locally
        val existingPages = albumLayoutDao.getPages(ownerId, albumId)
        if (existingPages.none { it.pageIndex == pageIndex }) {
            upsertPage(albumId, pageIndex)
        }

        val placement = StampPlacement(
            id = UUID.randomUUID().toString(),
            ownerId = ownerId,
            albumId = albumId,
            pageIndex = pageIndex,
            stampId = stampId,
            x = x,
            y = y,
            scale = scale,
            rotationDegrees = rotationDegrees,
            zIndex = zIndex,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        albumLayoutDao.upsertPlacements(listOf(StampPlacementEntity.fromDomain(placement)))

        if (ownerId.isNotBlank() && !isVirtualAlbum(albumId)) {
            repositoryScope.launch {
                try {
                    supabaseClient.upsertStampPlacements(listOf(placement))
                } catch (e: Exception) {
                    Log.w(TAG, "Cloud upsertPlacement error: ${e.message}")
                }
            }
        }
        Result.success(placement)
    }

    suspend fun updatePlacementTransform(
        placementId: String,
        albumId: String,
        x: Double,
        y: Double,
        scale: Double,
        rotationDegrees: Double,
        zIndex: Int
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (!isValidGeometry(x, y, scale, rotationDegrees, zIndex)) {
            return@withContext Result.failure(IllegalArgumentException("Invalid geometry parameters"))
        }
        val ownerId = getCurrentUserId()?.trim().orEmpty()
        val allPlacements = albumLayoutDao.getPlacements(ownerId, albumId)
        val existing = allPlacements.find { it.id == placementId }
            ?: return@withContext Result.failure(NoSuchElementException("Placement not found"))

        val updated = existing.copy(
            x = x,
            y = y,
            scale = scale,
            rotationDegrees = rotationDegrees,
            zIndex = zIndex,
            updatedAt = System.currentTimeMillis()
        )
        albumLayoutDao.upsertPlacements(listOf(updated))

        if (ownerId.isNotBlank() && !isVirtualAlbum(albumId)) {
            repositoryScope.launch {
                try {
                    supabaseClient.upsertStampPlacements(listOf(updated.toDomain()))
                } catch (e: Exception) {
                    Log.w(TAG, "Cloud updatePlacementTransform error: ${e.message}")
                }
            }
        }
        Result.success(Unit)
    }

    suspend fun movePlacementToPage(
        placementId: String,
        albumId: String,
        newPageIndex: Int
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (newPageIndex < 0) return@withContext Result.failure(IllegalArgumentException("newPageIndex must be >= 0"))
        val ownerId = getCurrentUserId()?.trim().orEmpty()
        val allPlacements = albumLayoutDao.getPlacements(ownerId, albumId)
        val existing = allPlacements.find { it.id == placementId }
            ?: return@withContext Result.failure(NoSuchElementException("Placement not found"))

        // Ensure target page exists
        val existingPages = albumLayoutDao.getPages(ownerId, albumId)
        if (existingPages.none { it.pageIndex == newPageIndex }) {
            upsertPage(albumId, newPageIndex)
        }

        val updated = existing.copy(pageIndex = newPageIndex, updatedAt = System.currentTimeMillis())
        albumLayoutDao.upsertPlacements(listOf(updated))

        if (ownerId.isNotBlank() && !isVirtualAlbum(albumId)) {
            repositoryScope.launch {
                try {
                    supabaseClient.upsertStampPlacements(listOf(updated.toDomain()))
                } catch (e: Exception) {
                    Log.w(TAG, "Cloud movePlacementToPage error: ${e.message}")
                }
            }
        }
        Result.success(Unit)
    }

    suspend fun deletePlacement(placementId: String, albumId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val ownerId = getCurrentUserId()?.trim().orEmpty()
        albumLayoutDao.deletePlacement(placementId, ownerId)

        if (ownerId.isNotBlank() && !isVirtualAlbum(albumId)) {
            repositoryScope.launch {
                try {
                    supabaseClient.deleteStampPlacement(placementId)
                } catch (e: Exception) {
                    Log.w(TAG, "Cloud deletePlacement error: ${e.message}")
                }
            }
        }
        Result.success(Unit)
    }
}
