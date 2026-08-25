package com.mipastudio.memostamp.feature.collection

import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.FlightTakeoff
import androidx.compose.material.icons.outlined.LocalCafe
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.rememberAsyncImagePainter
import com.mipastudio.memostamp.ui.theme.*
import com.mipastudio.memostamp.data.local.StampEntity
import com.mipastudio.memostamp.data.repository.StampRepository
import kotlinx.coroutines.launch
import kotlin.math.ceil

// Color system for book covers
val AccentGold = Color(0xFFD1A559)
val VintageLeatherRed = Color(0xFF9E3E2F)
val ClassicCoffeeBrown = Color(0xFF6D4C41)
val ThangLongEarth = Color(0xFF8D6E63)
val DarkWoodBg = Color(0xFF2C2421)
val BorderColor = Color(0xFFE8E2D9)
val CreamCardColor = Color(0xFFF4EBDD)

data class AlbumStampData(
    val id: String,
    val name: String,
    val imageUrl: String
)

data class AlbumData(
    val id: String,
    val title: String,
    val desc: String,
    val progress: String,
    val icon: ImageVector,
    val coverColor: Color,
    val stamps: List<AlbumStampData>
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
    val cloudStamps by stampRepo.observeStamps().collectAsState(initial = emptyList())

    val albumsList: List<AlbumData> = remember(cloudStamps) {
        if (cloudStamps.isEmpty()) {
            emptyList()
        } else {
            val grouped: Map<String, List<StampEntity>> = cloudStamps.groupBy { stamp ->
                if (stamp.location.isNullOrEmpty()) "Kỷ niệm chung" else stamp.location!!
            }
            val coverColors = listOf(VintageLeatherRed, ClassicCoffeeBrown, ThangLongEarth)
            val icons = listOf(Icons.Outlined.FlightTakeoff, Icons.Outlined.LocalCafe, Icons.Outlined.AccountBalance)

            grouped.entries.mapIndexed { index, entry ->
                AlbumData(
                    id = "album_$index",
                    title = entry.key,
                    desc = "Bộ sưu tập tem kỷ niệm tại ${entry.key}",
                    progress = "${entry.value.size}/${entry.value.size}",
                    icon = icons[index % icons.size],
                    coverColor = coverColors[index % coverColors.size],
                    stamps = entry.value.map { stamp -> AlbumStampData(stamp.id, stamp.title, stamp.stampImagePath) }
                )
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
                            "STAMP ALBUMS",
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryText
                        )
                        Text(
                            "Curated Memory Collections • Chạm để mở sách",
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
                        text = "Chưa có bộ sưu tập tem nào",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryText
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Hãy chụp và lưu tem từ máy ảnh để tự động tạo bộ sưu tập theo địa điểm!",
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

    selectedAlbum?.let { album ->
        StampBookViewerModal(
            album = album,
            onDismiss = { selectedAlbum = null }
        )
    }
}

// ==========================================
// 📕 BÌA CUỐN SÁCH NGOÀI DANH SÁCH (COVER PREVIEW)
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

        // Họa tiết dập nổi mạ vàng (Gold Foil Border)
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
                    item.icon,
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
                    letterSpacing = 1.2.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = item.desc,
                    textAlign = TextAlign.Center,
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 11.sp
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
                        text = "${item.progress} collected",
                        color = AccentGold,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

// ==========================================
// 📖 MÀN HÌNH MỞ SÁCH LẬT TỪNG TRANG TEM (STAMP BOOK VIEWER)
// ==========================================
@Composable
fun StampBookViewerModal(
    album: AlbumData,
    onDismiss: () -> Unit
) {
    val totalPages = maxOf(1, ceil(album.stamps.size / 4f).toInt()) + 1
    val pagerState = rememberPagerState(pageCount = { totalPages })
    val scope = rememberCoroutineScope()

    Dialog(
        onDismissRequest = { onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = DarkWoodBg
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
            ) {
                // Transparent Top Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = album.title,
                        color = AccentGold,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    IconButton(onClick = { onDismiss() }) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = AccentGold)
                    }
                }

                // PageView Book Content
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize()
                    ) { pageIndex ->
                        BookPageContent(pageIndex = pageIndex, album = album)
                    }
                }

                // Bottom Navigation Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            if (pagerState.currentPage > 0) {
                                scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                            }
                        },
                        enabled = pagerState.currentPage > 0
                    ) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "Previous",
                            tint = if (pagerState.currentPage > 0) AccentGold else AccentGold.copy(alpha = 0.3f)
                        )
                    }
                    Text(
                        text = "Trang ${pagerState.currentPage + 1} / $totalPages",
                        color = AccentGold,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    IconButton(
                        onClick = {
                            if (pagerState.currentPage < totalPages - 1) {
                                scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                            }
                        },
                        enabled = pagerState.currentPage < totalPages - 1
                    ) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowForward,
                            contentDescription = "Next",
                            tint = if (pagerState.currentPage < totalPages - 1) AccentGold else AccentGold.copy(alpha = 0.3f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun BookPageContent(
    pageIndex: Int,
    album: AlbumData
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .shadow(16.dp, RoundedCornerShape(12.dp))
            .background(WarmPaperBg, RoundedCornerShape(12.dp))
            .border(1.5.dp, BorderColor, RoundedCornerShape(12.dp))
    ) {
        // Spine Fold Gradient on Left
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(18.dp)
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(Color.Black.copy(alpha = 0.18f), Color.Transparent)
                    )
                )
        )

        if (pageIndex == 0) {
            // Page 1: Intro Page
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 24.dp, top = 20.dp, end = 20.dp, bottom = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Spacer(modifier = Modifier.weight(1f))
                Icon(album.icon, contentDescription = null, tint = AccentRed, modifier = Modifier.size(48.dp))
                Spacer(modifier = Modifier.height(12.dp))
                Text(album.title, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = PrimaryText)
                Spacer(modifier = Modifier.height(6.dp))
                Text(album.desc, color = SecondaryText, fontStyle = FontStyle.Italic, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = BorderColor, modifier = Modifier.padding(horizontal = 24.dp))
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "“Từng con tem lưu giữ một mảnh ký ức nguyên vẹn theo dòng thời gian.”",
                    textAlign = TextAlign.Center,
                    color = PrimaryText,
                    fontSize = 12.sp,
                    fontStyle = FontStyle.Italic,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                Spacer(modifier = Modifier.weight(1f))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Vuốt sang phải để mở tem", color = SecondaryText, fontSize = 11.sp)
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = null, tint = SecondaryText, modifier = Modifier.size(12.dp))
                }
            }
        } else {
            // Page 2+: 2x2 Grid
            val startIndex = (pageIndex - 1) * 4
            val pageStamps = album.stamps.drop(startIndex).take(4)

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(start = 28.dp, top = 20.dp, end = 20.dp, bottom = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items((0..3).toList()) { slotIndex ->
                    if (slotIndex < pageStamps.size) {
                        val stamp = pageStamps[slotIndex]
                        StampSlotItem(stamp = stamp)
                    } else {
                        EmptySlotItem()
                    }
                }
            }
        }
    }
}

@Composable
fun StampSlotItem(stamp: AlbumStampData) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.82f)
            .shadow(4.dp, RoundedCornerShape(6.dp))
            .background(Color.White, RoundedCornerShape(6.dp))
            .border(1.dp, BorderColor, RoundedCornerShape(6.dp))
            .padding(6.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                painter = rememberAsyncImagePainter(stamp.imageUrl),
                contentDescription = stamp.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(4.dp))
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                stamp.name,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = PrimaryText,
                maxLines = 1
            )
        }
    }
}

@Composable
fun EmptySlotItem() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.82f)
            .background(CreamCardColor.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            .border(1.dp, BorderColor, RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Outlined.Lock, contentDescription = "Locked", tint = BorderColor, modifier = Modifier.size(24.dp))
    }
}
