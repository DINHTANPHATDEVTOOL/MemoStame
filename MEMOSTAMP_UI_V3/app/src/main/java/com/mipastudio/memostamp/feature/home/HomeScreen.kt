package com.mipastudio.memostamp.feature.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.mipastudio.memostamp.R
import com.mipastudio.memostamp.core.model.Stamp
import com.mipastudio.memostamp.core.model.StampType
import com.mipastudio.memostamp.core.repository.SampleDataRepository
import com.mipastudio.memostamp.core.theme.*
import com.mipastudio.memostamp.core.ui.EnvelopeModal
import com.mipastudio.memostamp.core.ui.StampCard
import com.mipastudio.memostamp.data.repository.StampRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onCreateStampClick: () -> Unit,
    onStampClick: (Stamp) -> Unit,
    onCollectionClick: (String) -> Unit,
    onProfileClick: () -> Unit = {},
    onInboxClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val currentUser = SampleDataRepository.currentUser
    val repository = remember(context) { StampRepository.getInstance(context) }
    val roomEntities by repository.observeStamps().collectAsState(initial = emptyList())
    val roomCollections by repository.observeCollections().collectAsState(initial = emptyList())
    var showEnvelopeModal by remember { mutableStateOf(false) }

    val dateFormat = remember { SimpleDateFormat("dd.MM.yy", Locale.getDefault()) }
    val firstName = remember(currentUser.displayName) {
        currentUser.displayName.trim().substringBefore(" ").ifBlank { "you" }
    }

    val stamps = remember(roomEntities) {
        roomEntities.map { entity ->
            Stamp(
                id = entity.id,
                stampNumber = "#STAMP-${entity.id.take(8).uppercase()}",
                title = entity.title,
                imageUrl = entity.stampImagePath,
                creatorId = currentUser.id,
                creatorName = currentUser.displayName,
                ownerId = currentUser.id,
                ownerName = currentUser.displayName,
                createdDate = dateFormat.format(Date(entity.createdAt)),
                memoryDate = dateFormat.format(Date(entity.memoryDate)),
                location = entity.location ?: "",
                caption = entity.note,
                type = StampType.PERSONAL
            )
        }
    }

    LaunchedEffect(Unit) { repository.ensureDefaultCollections() }

    if (showEnvelopeModal) {
        val sampleOrRealStamp = stamps.firstOrNull() ?: Stamp(
            id = "gift_stamp_1",
            stampNumber = "#STAMP-GIFT-001",
            title = "Welcome Gift Memory",
            imageUrl = "https://images.unsplash.com/photo-1506744038136-46273834b3fb?w=600",
            creatorId = "user_2",
            creatorName = "Minh Nguyen",
            ownerId = currentUser.id,
            ownerName = currentUser.displayName,
            createdDate = "Today",
            memoryDate = "13.08.26",
            location = "Da Lat, Vietnam",
            caption = "Welcome to MemoStamp. Your first memory is waiting."
        )
        EnvelopeModal(
            senderName = "Minh Nguyen",
            stamp = sampleOrRealStamp,
            onDismiss = { showEnvelopeModal = false },
            onCollectStamp = { }
        )
    }

    Scaffold(
        containerColor = WarmPaperBg,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(id = R.drawable.app_logo),
                            contentDescription = "MemoStamp",
                            modifier = Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                        Spacer(modifier = Modifier.width(9.dp))
                        Text(
                            text = "MemoStamp",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            onInboxClick()
                            showEnvelopeModal = true
                        }
                    ) {
                        Icon(
                            Icons.Outlined.MailOutline,
                            contentDescription = "Inbox",
                            tint = PrimaryText
                        )
                    }
                    AsyncImage(
                        model = currentUser.avatarUrl,
                        contentDescription = "Profile",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .padding(end = 16.dp)
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(SurfaceSoft)
                            .clickable(onClick = onProfileClick)
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = WarmPaperBg)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 28.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                Text(
                    text = "Keep today.",
                    style = MaterialTheme.typography.displayMedium,
                    color = PrimaryText
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = "Good afternoon, $firstName",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SecondaryText
                )
            }

            Spacer(modifier = Modifier.height(22.dp))

            if (stamps.isEmpty()) {
                Surface(
                    onClick = onCreateStampClick,
                    color = SurfaceDark,
                    contentColor = Color.White,
                    shape = RoundedCornerShape(26.dp),
                    modifier = Modifier
                        .padding(horizontal = 20.dp)
                        .fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Outlined.PhotoCamera, contentDescription = null, tint = Color.White)
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Press your first memory", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                "Open the camera and turn a moment into a stamp.",
                                color = Color.White.copy(alpha = 0.68f),
                                fontSize = 12.sp,
                                lineHeight = 17.sp
                            )
                        }
                        Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = Color.White)
                    }
                }
            } else {
                SectionHeader(title = "On this day")
                Surface(
                    onClick = { onStampClick(stamps.first()) },
                    shape = RoundedCornerShape(24.dp),
                    color = SurfaceWhite,
                    modifier = Modifier
                        .padding(horizontal = 20.dp)
                        .fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .width(116.dp)
                                .height(145.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            StampCard(
                                stamp = stamps.first(),
                                modifier = Modifier.fillMaxSize(),
                                onClick = { onStampClick(stamps.first()) }
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "A memory found you again",
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp,
                                lineHeight = 22.sp
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                stamps.first().title,
                                color = SecondaryText,
                                fontSize = 13.sp,
                                maxLines = 2
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                "Open memory",
                                color = AccentRed,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(26.dp))
            SectionHeader(
                title = "Recent memories",
                action = if (stamps.isNotEmpty()) "See all" else null
            )

            if (stamps.isNotEmpty()) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(stamps.take(12), key = { it.id }) { stamp ->
                        StampCard(
                            stamp = stamp,
                            modifier = Modifier.width(164.dp),
                            onClick = { onStampClick(stamp) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))
            SectionHeader(title = "Collections")

            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                roomCollections.take(5).forEachIndexed { index, col ->
                    val count = roomEntities.count { it.collectionId == col.id }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { onCollectionClick(col.id) }
                            .padding(vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(13.dp))
                                .background(
                                    when (index % 3) {
                                        0 -> AccentRedSoft
                                        1 -> AccentBlueSoft
                                        else -> SurfaceSoft
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(col.iconEmoji ?: "•", fontSize = 18.sp)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(col.name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text("$count memories", color = SecondaryText, fontSize = 12.sp)
                        }
                        Icon(
                            Icons.Outlined.ChevronRight,
                            contentDescription = null,
                            tint = TertiaryText,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(26.dp))
            SectionHeader(title = "From friends")
            Surface(
                onClick = {
                    onInboxClick()
                    showEnvelopeModal = true
                },
                color = SurfaceWhite,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(15.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(AccentRedSoft),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Outlined.MailOutline, contentDescription = null, tint = AccentRed)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Minh sent you a memory", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text("Tap to open the envelope", color = SecondaryText, fontSize = 12.sp)
                    }
                    Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = TertiaryText)
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    action: String? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = PrimaryText)
        if (action != null) {
            Text(action, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = AccentRed)
        }
    }
}
