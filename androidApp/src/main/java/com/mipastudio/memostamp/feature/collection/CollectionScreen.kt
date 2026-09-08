package com.mipastudio.memostamp.feature.collection

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mipastudio.memostamp.R
import com.mipastudio.memostamp.data.repository.AlbumLayoutRepository
import com.mipastudio.memostamp.data.repository.StampRepository
import com.mipastudio.memostamp.data.repository.UserAuthRepository
import com.mipastudio.memostamp.feature.collection.book.AlbumStampData
import com.mipastudio.memostamp.feature.collection.book.StampBook3DRenderer
import com.mipastudio.memostamp.ui.icon.MemoStampIcons
import com.mipastudio.memostamp.ui.theme.*

// Color system for book covers
val VintageLeatherRed = Color(0xFF9E3E2F)
val ClassicCoffeeBrown = Color(0xFF6D4C41)
val ThangLongEarth = Color(0xFF8D6E63)
val PineForestGreen = Color(0xFF385750)
val DarkWoodBg = Color(0xFF2C2421)
val BorderColor = Color(0xFFE8E2D9)
val CreamCardColor = Color(0xFFF4EBDD)

data class AlbumData(
    val id: String,
    val ownerId: String,
    val title: String,
    val desc: String,
    val progress: String,
    val iconKey: String?,
    val coverColor: Color,
    val stamps: List<AlbumStampData>,
    val privacy: String = "FRIENDS",
    val targetCount: Int = 12
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionScreen(
    initialCollectionId: String? = null,
    onStampClick: (String) -> Unit = {}
) {
    var selectedAlbum by remember { mutableStateOf<AlbumData?>(null) }
    val context = LocalContext.current
    val stampRepo = remember(context) { StampRepository.getInstance(context) }
    val authRepo = remember(context) { UserAuthRepository.getInstance(context) }
    val currentUser by authRepo.currentUser.collectAsState()

    val cloudStamps by stampRepo.observeStamps().collectAsState(initial = emptyList())
    val persistedCollections by stampRepo.observeCollections().collectAsState(initial = emptyList())

    // Ensure defaults exist for current user
    LaunchedEffect(currentUser.userId) {
        if (currentUser.userId.isNotBlank()) {
            stampRepo.ensureDefaultCollections()
        }
    }

    val defaultLocationName = stringResource(R.string.book_legacy_default_location)
    val legacyDescTemplate = stringResource(R.string.book_legacy_desc_format)
    val coverColors = listOf(VintageLeatherRed, ClassicCoffeeBrown, ThangLongEarth, PineForestGreen)

    val albumsList: List<AlbumData> = remember(persistedCollections, cloudStamps, defaultLocationName, legacyDescTemplate) {
        if (persistedCollections.isNotEmpty()) {
            // Priority 1: Real Persisted Collections with Stable Canonical IDs
            persistedCollections.mapIndexed { index, col ->
                val matchingStamps = cloudStamps.filter { it.collectionId == col.id }
                val target = maxOf(col.targetCount, matchingStamps.size)
                AlbumData(
                    id = col.id,
                    ownerId = col.ownerId,
                    title = col.name,
                    desc = col.description ?: col.name,
                    progress = "${matchingStamps.size}/$target",
                    iconKey = col.resolvedIconKey(),
                    coverColor = coverColors[index % coverColors.size],
                    stamps = matchingStamps.map { AlbumStampData(it.id, it.title, it.stampImagePath) },
                    privacy = col.privacy,
                    targetCount = col.targetCount
                )
            }
        } else if (cloudStamps.isNotEmpty()) {
            // Fallback: Group by location ONLY when 0 persisted collections exist
            // Fallback IDs are deterministic across launches (no unstable album_0, album_1)
            val grouped = cloudStamps.groupBy { stamp ->
                if (stamp.location.isNullOrBlank()) defaultLocationName else stamp.location!!
            }
            val sortedKeys = grouped.keys.sorted()
            sortedKeys.mapIndexed { index, key ->
                val stampsForLocation = grouped[key] ?: emptyList()
                val locHash = (key.hashCode().toLong() and 0xFFFFFFFFL).toString(16)
                AlbumData(
                    id = "loc_$locHash",
                    ownerId = currentUser.userId,
                    title = key,
                    desc = String.format(legacyDescTemplate, key),
                    progress = "${stampsForLocation.size}/${stampsForLocation.size}",
                    iconKey = "travel",
                    coverColor = coverColors[index % coverColors.size],
                    stamps = stampsForLocation.map { AlbumStampData(it.id, it.title, it.stampImagePath) },
                    privacy = "FRIENDS",
                    targetCount = stampsForLocation.size
                )
            }
        } else {
            emptyList()
        }
    }

    LaunchedEffect(initialCollectionId, albumsList) {
        if (initialCollectionId != null && selectedAlbum == null) {
            val found = albumsList.find { it.id == initialCollectionId }
            if (found != null) {
                selectedAlbum = found
            }
        }
    }

    val pagerState = rememberPagerState(pageCount = { albumsList.size })

    Scaffold(
        containerColor = WarmPaperBg,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = stringResource(R.string.book_shelf_title),
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryText
                        )
                        Text(
                            text = stringResource(R.string.book_shelf_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = SecondaryText
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = WarmPaperBg)
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center
        ) {
            if (albumsList.isEmpty()) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Text(
                        text = stringResource(R.string.book_shelf_empty_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryText
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.book_shelf_empty_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = SecondaryText,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                HorizontalPager(
                    state = pagerState,
                    contentPadding = PaddingValues(horizontal = 42.dp),
                    pageSpacing = 18.dp,
                    modifier = Modifier.fillMaxHeight(0.85f)
                ) { page ->
                    val album = albumsList[page]
                    BookCoverPreview(
                        item = album,
                        onClick = { selectedAlbum = album }
                    )
                }
            }
        }
    }

    // 📖 Production 3D Stamp Book Renderer (replaces old flat viewer)
    selectedAlbum?.let { album ->
        val curator = currentUser.displayName.takeIf { it.isNotBlank() }
            ?: currentUser.username.takeIf { it.isNotBlank() }
            ?: "Collector"

        val albumLayoutRepo = remember(context) {
            AlbumLayoutRepository.getInstance(context) { authRepo.authUserId.value ?: currentUser.userId }
        }
        val layoutState by albumLayoutRepo.observeLayout(album.id).collectAsState(initial = null)
        val placements = layoutState?.placements ?: emptyList()

        StampBook3DRenderer(
            albumId = album.id,
            albumTitle = album.title,
            albumDescription = album.desc,
            curatorName = curator,
            coverColor = album.coverColor,
            iconKey = album.iconKey,
            stamps = album.stamps,
            placements = placements,
            availableVaultStamps = cloudStamps,
            albumLayoutRepo = albumLayoutRepo,
            onStampClick = onStampClick,
            onDismiss = { selectedAlbum = null }
        )
    }
}

// ==========================================
// 📕 BÌA CUỐN SÁCH NGOÀI GIÁ SÁCH (COVER PREVIEW)
// ==========================================
@Composable
fun BookCoverPreview(
    item: AlbumData,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .shadow(16.dp, RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp, topEnd = 16.dp, bottomEnd = 16.dp))
            .background(
                color = item.coverColor,
                shape = RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp, topEnd = 16.dp, bottomEnd = 16.dp)
            )
            .clickable { onClick() }
    ) {
        // Gáy sách (Book Spine Shadow)
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(24.dp)
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.4f),
                            Color.White.copy(alpha = 0.1f),
                            Color.Black.copy(alpha = 0.2f)
                        )
                    )
                )
        )

        // Họa tiết dập nổi viền mạ vàng (Gold Foil Border)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 32.dp, top = 20.dp, end = 20.dp, bottom = 20.dp)
                .border(1.5.dp, AccentGold.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                Icon(
                    imageVector = MemoStampIcons.resolve(item.iconKey),
                    contentDescription = null,
                    tint = AccentGold,
                    modifier = Modifier.size(52.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = item.title,
                    textAlign = TextAlign.Center,
                    color = AccentGold,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = item.desc,
                    textAlign = TextAlign.Center,
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 11.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.Black.copy(alpha = 0.35f))
                        .border(0.8.dp, AccentGold, RoundedCornerShape(20.dp))
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = stringResource(R.string.book_shelf_collected_format, item.progress),
                        color = AccentGold,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
