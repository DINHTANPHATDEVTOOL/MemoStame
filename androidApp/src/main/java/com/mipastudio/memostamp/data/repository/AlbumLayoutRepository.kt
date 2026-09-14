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
        val targetPage = existingPages.find { it.pageIndex == pageIndex }?.toDomain()
            ?: upsertPage(albumId, pageIndex).getOrNull()
        val assignedPageId = targetPage?.id.orEmpty()

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
            pageId = assignedPageId,
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
        newPageIndex: Int,
        targetPageId: String? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (newPageIndex < 0) return@withContext Result.failure(IllegalArgumentException("newPageIndex must be >= 0"))
        val ownerId = getCurrentUserId()?.trim().orEmpty()
        val allPlacements = albumLayoutDao.getPlacements(ownerId, albumId)
        val existing = allPlacements.find { it.id == placementId }
            ?: return@withContext Result.failure(NoSuchElementException("Placement not found"))

        // Ensure target page exists
        val existingPages = albumLayoutDao.getPages(ownerId, albumId)
        val targetPage = if (!targetPageId.isNullOrBlank()) {
            existingPages.find { it.id == targetPageId }?.toDomain()
        } else {
            existingPages.find { it.pageIndex == newPageIndex }?.toDomain()
        } ?: upsertPage(albumId, newPageIndex).getOrNull()
        val newPageId = targetPage?.id ?: targetPageId ?: existing.pageId
        val finalPageIndex = targetPage?.pageIndex ?: newPageIndex

        val updated = existing.copy(
            pageIndex = finalPageIndex,
            pageId = newPageId,
            updatedAt = System.currentTimeMillis()
        )
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

    // ==========================================
    // CANONICAL PAGE LIFECYCLE MANAGEMENT (TASK #80)
    // ==========================================

    suspend fun appendPage(albumId: String): Result<AlbumPage> = withContext(Dispatchers.IO) {
        if (isVirtualAlbum(albumId)) {
            return@withContext Result.failure(IllegalStateException("Virtual albums are read-only"))
        }
        val ownerId = getCurrentUserId()?.trim().orEmpty()
        if (ownerId.isBlank()) {
            return@withContext Result.failure(IllegalStateException("Unauthenticated"))
        }

        val currentPages = albumLayoutDao.getPages(ownerId, albumId)
        if (currentPages.size >= 50) {
            return@withContext Result.failure(IllegalStateException("Maximum pages limit reached (50)"))
        }

        val nextIndex = (currentPages.maxOfOrNull { it.pageIndex } ?: -1) + 1
        val newPage = AlbumPage(
            id = UUID.randomUUID().toString(),
            ownerId = ownerId,
            albumId = albumId,
            pageIndex = nextIndex,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        albumLayoutDao.upsertPages(listOf(AlbumPageEntity.fromDomain(newPage)))

        repositoryScope.launch {
            try {
                supabaseClient.appendAlbumPage(albumId)
            } catch (e: Exception) {
                Log.w(TAG, "Cloud appendPage error: ${e.message}")
            }
        }

        Result.success(newPage)
    }

    suspend fun removePage(pageId: String, albumId: String): Result<Unit> = withContext(Dispatchers.IO) {
        if (isVirtualAlbum(albumId)) {
            return@withContext Result.failure(IllegalStateException("Virtual albums are read-only"))
        }
        val ownerId = getCurrentUserId()?.trim().orEmpty()
        if (ownerId.isBlank()) {
            return@withContext Result.failure(IllegalStateException("Unauthenticated"))
        }

        val currentPages = albumLayoutDao.getPages(ownerId, albumId).sortedBy { it.pageIndex }
        val targetPage = currentPages.find { it.id == pageId }
            ?: return@withContext Result.failure(NoSuchElementException("Page not found"))

        if (currentPages.size <= 1) {
            return@withContext Result.failure(IllegalStateException("Cannot remove the only page of an album"))
        }

        // Safety Invariant: cannot remove non-empty page
        val allPlacements = albumLayoutDao.getPlacements(ownerId, albumId)
        val hasPlacements = allPlacements.any { it.pageId == pageId || it.pageIndex == targetPage.pageIndex }
        if (hasPlacements) {
            return@withContext Result.failure(IllegalStateException("Page contains stamp placements and cannot be removed"))
        }

        // Delete page locally
        albumLayoutDao.deletePage(pageId, ownerId)

        // Reindex remaining pages so pageIndex is contiguous (0, 1, 2...)
        val remainingPages = currentPages.filter { it.id != pageId }
        val updatedPages = remainingPages.mapIndexed { idx, p ->
            p.copy(pageIndex = idx, updatedAt = System.currentTimeMillis())
        }
        albumLayoutDao.upsertPages(updatedPages)

        // Also shift placements whose page was reindexed
        val updatedPlacements = allPlacements.mapNotNull { pl ->
            val matchingNewPage = updatedPages.find { it.id == pl.pageId }
            if (matchingNewPage != null && matchingNewPage.pageIndex != pl.pageIndex) {
                pl.copy(pageIndex = matchingNewPage.pageIndex, updatedAt = System.currentTimeMillis())
            } else null
        }
        if (updatedPlacements.isNotEmpty()) {
            albumLayoutDao.upsertPlacements(updatedPlacements)
        }

        repositoryScope.launch {
            try {
                supabaseClient.removeAlbumPage(albumId, pageId)
            } catch (e: Exception) {
                Log.w(TAG, "Cloud removePage error: ${e.message}")
            }
        }

        Result.success(Unit)
    }

    suspend fun reorderPages(albumId: String, pageIds: List<String>): Result<Unit> = withContext(Dispatchers.IO) {
        if (isVirtualAlbum(albumId)) {
            return@withContext Result.failure(IllegalStateException("Virtual albums are read-only"))
        }
        val ownerId = getCurrentUserId()?.trim().orEmpty()
        if (ownerId.isBlank()) {
            return@withContext Result.failure(IllegalStateException("Unauthenticated"))
        }

        val currentPages = albumLayoutDao.getPages(ownerId, albumId)
        if (currentPages.size != pageIds.size) {
            return@withContext Result.failure(IllegalArgumentException("Page IDs count mismatch"))
        }

        val allPlacements = albumLayoutDao.getPlacements(ownerId, albumId)

        // Create mapping from pageId to new index
        val pageMap = currentPages.associateBy { it.id }
        val updatedPages = pageIds.mapIndexedNotNull { newIndex, id ->
            pageMap[id]?.copy(pageIndex = newIndex, updatedAt = System.currentTimeMillis())
        }

        if (updatedPages.size != pageIds.size) {
            return@withContext Result.failure(IllegalArgumentException("Unknown page ID in reorder list"))
        }

        // Optimistically update Room
        albumLayoutDao.upsertPages(updatedPages)

        // Update placements to follow their page's new pageIndex
        val pageIndexByPageId = updatedPages.associate { it.id to it.pageIndex }
        val updatedPlacements = allPlacements.map { pl ->
            val newIdx = pageIndexByPageId[pl.pageId]
            if (newIdx != null && newIdx != pl.pageIndex) {
                pl.copy(pageIndex = newIdx, updatedAt = System.currentTimeMillis())
            } else pl
        }
        albumLayoutDao.upsertPlacements(updatedPlacements)

        repositoryScope.launch {
            try {
                supabaseClient.reorderAlbumPages(albumId, pageIds)
            } catch (e: Exception) {
                Log.w(TAG, "Cloud reorderAlbumPages error: ${e.message}")
            }
        }

        Result.success(Unit)
    }

    suspend fun ensurePageStructure(
        albumId: String,
        currentPlacements: List<StampPlacement> = emptyList()
    ): Result<List<AlbumPage>> = withContext(Dispatchers.IO) {
        if (isVirtualAlbum(albumId)) {
            return@withContext Result.success(emptyList())
        }
        val ownerId = getCurrentUserId()?.trim().orEmpty()
        if (ownerId.isBlank()) {
            return@withContext Result.failure(IllegalStateException("Unauthenticated"))
        }

        val existingPages = albumLayoutDao.getPages(ownerId, albumId)
        if (existingPages.isNotEmpty()) {
            return@withContext Result.success(existingPages.map { it.toDomain() })
        }

        val placements = if (currentPlacements.isNotEmpty()) {
            currentPlacements
        } else {
            albumLayoutDao.getPlacements(ownerId, albumId).map { it.toDomain() }
        }

        // Bootstrap: at least page 0, and up to max placement pageIndex
        val maxPageNeeded = maxOf(0, placements.maxOfOrNull { it.pageIndex } ?: 0)
        val created = (0..maxPageNeeded).map { pIdx ->
            AlbumPage(
                id = UUID.randomUUID().toString(),
                ownerId = ownerId,
                albumId = albumId,
                pageIndex = pIdx,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
        }
        albumLayoutDao.upsertPages(created.map { AlbumPageEntity.fromDomain(it) })

        // Backfill placements with pageId if needed
        val pageIdByIndex = created.associate { it.pageIndex to it.id }
        val placementsWithPageId = placements.map { pl ->
            val assignedId = pageIdByIndex[pl.pageIndex].orEmpty()
            pl.copy(pageId = assignedId)
        }
        if (placementsWithPageId.isNotEmpty()) {
            albumLayoutDao.upsertPlacements(placementsWithPageId.map { StampPlacementEntity.fromDomain(it) })
        }

        repositoryScope.launch {
            try {
                supabaseClient.upsertAlbumPages(created)
                if (placementsWithPageId.isNotEmpty()) {
                    supabaseClient.upsertStampPlacements(placementsWithPageId)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Cloud ensurePageStructure error: ${e.message}")
            }
        }

        Result.success(created)
    }
}
