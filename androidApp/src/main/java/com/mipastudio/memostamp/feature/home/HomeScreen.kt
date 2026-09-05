package com.mipastudio.memostamp.feature.home

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.mipastudio.memostamp.R
import com.mipastudio.memostamp.domain.model.Stamp
import com.mipastudio.memostamp.domain.model.StampType
import com.mipastudio.memostamp.core.processor.MemoImageProcessor
import com.mipastudio.memostamp.ui.theme.*
import com.mipastudio.memostamp.ui.components.ThemeSelectorModalSheet
import com.mipastudio.memostamp.ui.components.EnvelopeModal
import com.mipastudio.memostamp.ui.components.UserProfileDialog
import com.mipastudio.memostamp.data.local.StampEntity
import com.mipastudio.memostamp.data.repository.UserAuthRepository
import com.mipastudio.memostamp.data.repository.UserProfile
import com.mipastudio.memostamp.data.repository.ChatRepository
import com.mipastudio.memostamp.data.repository.FeedRepository
import com.mipastudio.memostamp.data.repository.StampRepository
import com.mipastudio.memostamp.domain.model.AudienceType
import com.mipastudio.memostamp.domain.model.FeedComment
import com.mipastudio.memostamp.domain.model.FeedPost
import com.mipastudio.memostamp.domain.model.StampDraft
import kotlinx.coroutines.launch
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
    val coroutineScope = rememberCoroutineScope()
    val authRepo = remember(context) { UserAuthRepository.getInstance(context) }
    val stampRepo = remember(context) { StampRepository.getInstance(context) }
    val feedRepo = remember(context) { FeedRepository.getInstance(context) }
    val chatRepo = remember(context) { ChatRepository.getInstance(context) }

    val currentUser by authRepo.currentUser.collectAsState()
    val friendIds by authRepo.friendIds.collectAsState()
    val allChatMessages by chatRepo.messages.collectAsState()

    val feedPostsFlow = remember(feedRepo) { feedRepo.observeFriendsFeed() }
    val feedPosts by feedPostsFlow.collectAsState(initial = emptyList())

    var selectedFilter by remember { mutableIntStateOf(0) } // 0: Tất cả, 1: Bạn bè, 2: Của tôi
    var showThemeSelector by remember { mutableStateOf(false) }
    var showEnvelopeModal by remember { mutableStateOf(false) }
    var showQuickPostModal by remember { mutableStateOf(false) }
    var quickPostCaption by remember { mutableStateOf("") }
    var quickPostAudience by remember { mutableStateOf(AudienceType.FRIENDS) }
    var selectedStampEntity by remember { mutableStateOf<StampEntity?>(null) }
    val myStamps by stampRepo.observeStamps().collectAsState(initial = emptyList())

    var profilePreviewUser by remember { mutableStateOf<UserProfile?>(null) }
    var expandedPostComments by remember { mutableStateOf<Set<String>>(emptySet()) }
    var commentInputs by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

    // Number of received stamp cards for inbox badge
    val unreadInboxCount = remember(allChatMessages, currentUser) {
        allChatMessages.count { it.recipientId == currentUser.userId && !it.stampImageUrl.isNullOrBlank() }
    }

    LaunchedEffect(Unit) {
        feedRepo.syncFeedFromSupabase()
        stampRepo.ensureDefaultCollections()
    }

    val displayPosts = remember(feedPosts, selectedFilter, currentUser, friendIds) {
        when (selectedFilter) {
            0 -> feedPosts.filter { post ->
                when (post.audienceType) {
                    AudienceType.FRIENDS -> post.authorId == currentUser.userId || friendIds.contains(post.authorId)
                    AudienceType.SPECIFIC_FRIENDS -> post.authorId == currentUser.userId || post.targetFriendIds.contains(currentUser.userId)
                    AudienceType.ONLY_ME -> post.authorId == currentUser.userId
                }
            }
            1 -> feedPosts.filter { post -> 
                post.audienceType == AudienceType.SPECIFIC_FRIENDS && (post.authorId == currentUser.userId || post.targetFriendIds.contains(currentUser.userId)) 
            }
            2 -> feedPosts.filter { post -> post.audienceType == AudienceType.ONLY_ME && post.authorId == currentUser.userId }
            3 -> feedPosts.filter { post -> post.authorId == currentUser.userId }
            else -> feedPosts
        }
    }

    if (showQuickPostModal) {
        AlertDialog(
            onDismissRequest = { showQuickPostModal = false },
            confirmButton = {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            try {
                                val stampToUse = selectedStampEntity ?: myStamps.firstOrNull()
                                if (stampToUse != null) {
                                    val updatedStamp = stampToUse.copy(
                                        note = quickPostCaption.ifBlank { stampToUse.note }
                                    )
                                    feedRepo.createPostFromStamp(
                                        stampEntity = updatedStamp,
                                        audienceType = quickPostAudience
                                    )
                                    Toast.makeText(context, "Đã đăng bài viết mới lên Bảng tin! 📮", Toast.LENGTH_SHORT).show()
                                } else {
                                    val newDraft = StampDraft(
                                        originalImagePath = currentUser.avatarUrl.ifBlank { "https://images.unsplash.com/photo-1506744038136-46273834b3fb?w=600" },
                                        renderedImagePath = currentUser.avatarUrl.ifBlank { "https://images.unsplash.com/photo-1506744038136-46273834b3fb?w=600" },
                                        title = "Khoảnh khắc kỷ niệm",
                                        location = "Việt Nam",
                                        memoryDate = System.currentTimeMillis(),
                                        note = quickPostCaption.ifBlank { "Mới đăng khoảnh khắc hôm nay ✨" }
                                    )
                                    val res = stampRepo.saveStamp(newDraft)
                                    if (res.isSuccess) {
                                        feedRepo.createPostFromStamp(
                                            stampEntity = res.getOrThrow(),
                                            audienceType = quickPostAudience
                                        )
                                        Toast.makeText(context, "Đã đăng bài viết mới lên Bảng tin! 📮", Toast.LENGTH_SHORT).show()
                                    } else {
                                        throw res.exceptionOrNull() ?: Exception("Lưu tem thất bại")
                                    }
                                }
                                showQuickPostModal = false
                                quickPostCaption = ""
                                selectedStampEntity = null
                                feedRepo.reconcileFeedFromCloud()
                            } catch (e: Exception) {
                                Toast.makeText(context, "Đăng bài thất bại: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentRed),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text("Đăng bài 🚀", fontWeight = FontWeight.Bold, color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showQuickPostModal = false }) {
                    Text("Hủy", color = SecondaryText)
                }
            },
            title = {
                Text("Tạo bài viết mới 📮", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = PrimaryText)
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AsyncImage(
                            model = currentUser.avatarUrl.ifBlank { "https://i.pravatar.cc/150?u=${currentUser.userId}" },
                            contentDescription = null,
                            modifier = Modifier.size(38.dp).clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(currentUser.displayName, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = PrimaryText)
                            Spacer(modifier = Modifier.height(2.dp))
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = WarmPaperBg,
                                modifier = Modifier.clickable {
                                    quickPostAudience = when (quickPostAudience) {
                                        AudienceType.FRIENDS -> AudienceType.SPECIFIC_FRIENDS
                                        AudienceType.SPECIFIC_FRIENDS -> AudienceType.ONLY_ME
                                        AudienceType.ONLY_ME -> AudienceType.FRIENDS
                                    }
                                }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${quickPostAudience.icon} ${quickPostAudience.label}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = PrimaryText
                                    )
                                    Icon(Icons.Outlined.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = quickPostCaption,
                        onValueChange = { quickPostCaption = it },
                        placeholder = { Text("Chia sẻ suy nghĩ, con tem hay kỷ niệm của bạn...", fontSize = 13.sp, color = SecondaryText) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(90.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = StampBorderDefault,
                            focusedBorderColor = AccentRed
                        )
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text("Chọn con tem đính kèm:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = PrimaryText)
                    Spacer(modifier = Modifier.height(6.dp))

                    if (myStamps.isEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = WarmPaperBg,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showQuickPostModal = false
                                    onCreateStampClick()
                                }
                                .padding(12.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                                Icon(Icons.Outlined.AddAPhoto, contentDescription = null, tint = AccentRed, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Chưa có tem, bấm để chụp tem mới! 📸", fontSize = 12.sp, color = AccentRed, fontWeight = FontWeight.Bold)
                            }
                        }
                    } else {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(myStamps) { stamp ->
                                val isSelected = selectedStampEntity?.id == stamp.id
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isSelected) AccentRed.copy(alpha = 0.15f) else WarmPaperBg,
                                    border = androidx.compose.foundation.BorderStroke(
                                        if (isSelected) 2.dp else 1.dp,
                                        if (isSelected) AccentRed else StampBorderDefault
                                    ),
                                    modifier = Modifier
                                        .size(70.dp)
                                        .clickable {
                                            selectedStampEntity = if (isSelected) null else stamp
                                        }
                                ) {
                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(4.dp)) {
                                        AsyncImage(
                                            model = MemoImageProcessor.resolveImageModel(stamp.stampImagePath),
                                            contentDescription = stamp.title,
                                            contentScale = ContentScale.Fit,
                                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp))
                                        )
                                        if (isSelected) {
                                            Box(
                                                modifier = Modifier
                                                    .align(Alignment.TopEnd)
                                                    .size(18.dp)
                                                    .background(AccentRed, CircleShape),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(Icons.Outlined.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            containerColor = SurfaceWhite,
            shape = RoundedCornerShape(24.dp)
        )
    }

    if (showThemeSelector) {
        ThemeSelectorModalSheet(
            onDismiss = { showThemeSelector = false }
        )
    }

    if (showEnvelopeModal) {
        val firstPost = feedPosts.firstOrNull()
        val activeStamp = Stamp(
            id = firstPost?.id ?: "gift_stamp_1",
            stampNumber = "#STAMP-${firstPost?.id?.takeLast(4) ?: "001"}",
            title = firstPost?.stampTitle ?: "Memory Stamp",
            imageUrl = firstPost?.stampUrl ?: "",
            creatorId = firstPost?.authorId ?: currentUser.userId,
            creatorName = firstPost?.authorName ?: currentUser.displayName,
            ownerId = currentUser.userId,
            ownerName = currentUser.displayName,
            createdDate = "Today",
            memoryDate = "2026.08.25",
            location = firstPost?.location ?: "Local Memory",
            caption = firstPost?.caption ?: ""
        )
        EnvelopeModal(
            senderName = firstPost?.authorName ?: currentUser.displayName,
            stamp = activeStamp,
            onDismiss = { showEnvelopeModal = false },
            onCollectStamp = { }
        )
    }

    // User profile dialog on avatar click
    profilePreviewUser?.let { targetUser ->
        val isFriend = friendIds.contains(targetUser.userId)
        UserProfileDialog(
            user = targetUser,
            isFriend = isFriend,
            onDismiss = { profilePreviewUser = null },
            onOpenChat = { onInboxClick() },
            onSendTrade = { onInboxClick() },
            onAddFriend = {
                coroutineScope.launch {
                    val res = authRepo.sendFriendRequest(targetUser)
                    res.fold(
                        onSuccess = { Toast.makeText(context, "Đã gửi lời mời kết bạn! 🤝", Toast.LENGTH_SHORT).show() },
                        onFailure = { err -> Toast.makeText(context, err.message ?: "Gửi lời mời thất bại", Toast.LENGTH_SHORT).show() }
                    )
                }
            },
            onUnfriend = {
                coroutineScope.launch {
                    val res = authRepo.unfriend(targetUser.userId)
                    res.fold(
                        onSuccess = { Toast.makeText(context, "Đã hủy kết bạn", Toast.LENGTH_SHORT).show() },
                        onFailure = { err -> Toast.makeText(context, err.message ?: "Hủy kết bạn thất bại", Toast.LENGTH_SHORT).show() }
                    )
                }
            }
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(id = R.drawable.app_logo),
                            contentDescription = "MemoStamp",
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "MemoStamp",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = "Bảng tin kỷ niệm 📮",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { showThemeSelector = true }) {
                        Icon(
                            Icons.Outlined.Palette,
                            contentDescription = "Chọn giao diện",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    IconButton(onClick = {
                        coroutineScope.launch {
                            feedRepo.syncFeedFromSupabase()
                            Toast.makeText(context, "Đã làm mới Bảng tin! 🔄", Toast.LENGTH_SHORT).show()
                        }
                    }) {
                        Icon(
                            Icons.Outlined.Refresh,
                            contentDescription = "Làm mới Bảng tin",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }

                    // Inbox button with notification badge counter
                    Box(modifier = Modifier.padding(end = 4.dp)) {
                        IconButton(onClick = onInboxClick) {
                            Icon(
                                Icons.Outlined.MailOutline,
                                contentDescription = "Hộp thư",
                                tint = PrimaryText
                            )
                        }
                        if (unreadInboxCount > 0) {
                            Badge(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(top = 6.dp, end = 6.dp),
                                containerColor = AccentRed,
                                contentColor = Color.White
                            ) {
                                Text(
                                    text = if (unreadInboxCount > 9) "9+" else unreadInboxCount.toString(),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Profile avatar button
                    AsyncImage(
                        model = currentUser.avatarUrl.ifBlank { "https://i.pravatar.cc/150?u=${currentUser.userId}" },
                        contentDescription = "Profile",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .padding(end = 16.dp)
                            .size(36.dp)
                            .clip(CircleShape)
                            .border(1.5.dp, AccentRedSoft, CircleShape)
                            .background(SurfaceSoft)
                            .clickable(onClick = onProfileClick)
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 100.dp)
        ) {
            // 0. Hero Banner: Today's Memory Card (Design System)
            item {
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = StampCreamBg,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .clickable(onClick = onCreateStampClick)
                ) {
                    Row(
                        modifier = Modifier.padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(62.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(AccentRed),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.LocalActivity,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Today’s memory",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = PrimaryText
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = "Capture something worth keeping.",
                                fontSize = 12.sp,
                                color = SecondaryText
                            )
                        }
                        Icon(
                            imageVector = Icons.Outlined.ChevronRight,
                            contentDescription = null,
                            tint = PrimaryText,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // 1. Facebook-style "Bạn đang nghĩ gì?" Post Creation Box
            item {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = SurfaceWhite,
                    shadowElevation = 1.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            AsyncImage(
                                model = currentUser.avatarUrl.ifBlank { "https://i.pravatar.cc/150?u=${currentUser.userId}" },
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .clickable(onClick = onProfileClick)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Surface(
                                shape = RoundedCornerShape(24.dp),
                                color = WarmPaperBg,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { showQuickPostModal = true }
                            ) {
                                Text(
                                    text = "Chia sẻ khoảnh khắc tem kỷ niệm hôm nay... 📮",
                                    fontSize = 13.sp,
                                    color = SecondaryText,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 11.dp),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 12.dp),
                            color = WarmPaperBg
                        )

                        // Quick buttons: Chụp tem mới | Đăng bài từ Kho | Công khai
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable(onClick = onCreateStampClick)
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Outlined.PhotoCamera,
                                    contentDescription = null,
                                    tint = AccentRed,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Chụp tem mới", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = PrimaryText)
                            }

                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { showQuickPostModal = true }
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Outlined.CollectionsBookmark,
                                    contentDescription = null,
                                    tint = AccentBlue,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Kho tem", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = PrimaryText)
                            }

                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { showQuickPostModal = true }
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Outlined.Public,
                                    contentDescription = null,
                                    tint = SuccessGreen,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Đăng ngay", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = PrimaryText)
                            }
                        }
                    }
                }
            }

            // 2. Filter tabs: Tất cả bài viết | Bạn bè | Của tôi
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val filterOptions = listOf(
                        "Tất cả bạn bè" to Icons.Outlined.People,
                        "Bạn bè chọn lọc" to Icons.Outlined.CheckCircle,
                        "Chỉ mình tôi" to Icons.Outlined.Lock,
                        "Bài viết của tôi" to Icons.Outlined.Person
                    )
                    items(filterOptions.size) { idx ->
                        val isSelected = selectedFilter == idx
                        val item = filterOptions[idx]
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = if (isSelected) AccentRed else SurfaceWhite,
                            shadowElevation = if (isSelected) 2.dp else 0.dp,
                            modifier = Modifier.clickable { selectedFilter = idx }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = item.second,
                                    contentDescription = null,
                                    tint = if (isSelected) Color.White else PrimaryText,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = item.first,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) Color.White else PrimaryText
                                )
                            }
                        }
                    }
                }
            }

            // 3. Feed Posts Timeline
            if (displayPosts.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("📮", fontSize = 48.sp)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Chưa có bài viết nào trên bảng tin",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = PrimaryText
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Hãy là người đầu tiên chụp và chia sẻ con tem kỷ niệm hôm nay!",
                                fontSize = 12.sp,
                                color = SecondaryText
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = onCreateStampClick,
                                colors = ButtonDefaults.buttonColors(containerColor = AccentRed),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Icon(Icons.Outlined.PhotoCamera, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Tạo tem đầu tiên", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            } else {
                items(displayPosts, key = { it.id }) { post ->
                    val isLiked = post.isLikedByMe
                    val isExpanded = expandedPostComments.contains(post.id)
                    val currentCommentText = commentInputs[post.id].orEmpty()

                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = SurfaceWhite,
                        shadowElevation = 1.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            // --- POST HEADER ---
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                AsyncImage(
                                    model = post.authorAvatar.ifBlank { "https://i.pravatar.cc/150?u=${post.authorId}" },
                                    contentDescription = post.authorName,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(CircleShape)
                                        .clickable {
                                            profilePreviewUser = UserProfile(
                                                userId = post.authorId,
                                                username = post.authorName.lowercase().replace(" ", "_"),
                                                displayName = post.authorName,
                                                avatarUrl = post.authorAvatar,
                                                isCloudSynced = true
                                            )
                                        }
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = post.authorName,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = PrimaryText
                                    )
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = formatTimeAgo(post.createdAt),
                                            fontSize = 11.sp,
                                            color = SecondaryText
                                        )
                                        if (!post.location.isNullOrBlank()) {
                                            Text(" • ", fontSize = 11.sp, color = SecondaryText)
                                            Text(
                                                text = "📍 ${post.location}",
                                                fontSize = 11.sp,
                                                color = AccentBlue,
                                                fontWeight = FontWeight.Medium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        Text(" • ", fontSize = 11.sp, color = SecondaryText)
                                        Icon(
                                            imageVector = if (post.audienceType == AudienceType.FRIENDS) Icons.Outlined.People else Icons.Outlined.Public,
                                            contentDescription = null,
                                            tint = SecondaryText,
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                // Signature Stamp Badge (rotates -3 deg, border 1.5dp AccentRed)
                                Box(
                                    modifier = Modifier
                                        .rotate(-3f)
                                        .border(1.5.dp, AccentRed, RoundedCornerShape(5.dp))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = (post.stampTitle?.take(8) ?: "MEMORY").uppercase(),
                                        color = AccentRed,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        letterSpacing = 1.sp
                                    )
                                }
                            }

                            // --- CAPTION ---
                            if (!post.caption.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = post.caption ?: "",
                                    fontSize = 14.sp,
                                    color = PrimaryText,
                                    lineHeight = 20.sp
                                )
                            }

                            // --- STAMP IMAGE DISPLAY ---
                            Spacer(modifier = Modifier.height(12.dp))
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = WarmPaperBg,
                                border = androidx.compose.foundation.BorderStroke(1.dp, StampBorderDefault),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        val domainStamp = Stamp(
                                            id = post.stampId,
                                            stampNumber = "#STAMP-${post.stampId.take(8)}",
                                            title = post.stampTitle ?: "Tem kỷ niệm",
                                            imageUrl = post.stampUrl ?: "",
                                            creatorId = post.authorId,
                                            creatorName = post.authorName,
                                            ownerId = currentUser.userId,
                                            ownerName = currentUser.displayName,
                                            createdDate = formatTimeAgo(post.createdAt),
                                            memoryDate = formatTimeAgo(post.createdAt),
                                            location = post.location ?: "Việt Nam",
                                            caption = post.caption ?: ""
                                        )
                                        onStampClick(domainStamp)
                                    }
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(1.2f)
                                        .padding(12.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    AsyncImage(
                                        model = MemoImageProcessor.resolveImageModel(post.stampUrl),
                                        contentDescription = post.stampTitle,
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(RoundedCornerShape(12.dp))
                                    )
                                }
                            }

                            // --- ENGAGEMENT STATS COUNTER ---
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Filled.Favorite,
                                        contentDescription = null,
                                        tint = AccentRed,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "${post.reactionCount} lượt thích",
                                        fontSize = 12.sp,
                                        color = SecondaryText,
                                        fontWeight = FontWeight.Medium
                                    )
                                }

                                Text(
                                    text = "${post.commentCount} bình luận",
                                    fontSize = 12.sp,
                                    color = SecondaryText,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.clickable {
                                        expandedPostComments = if (isExpanded) expandedPostComments - post.id else expandedPostComments + post.id
                                    }
                                )
                            }

                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 10.dp),
                                color = WarmPaperBg
                            )

                            // --- ACTION BUTTONS (Thích | Bình luận | Lưu vào Kho | Nhắn tin) ---
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Thích button
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable {
                                            coroutineScope.launch {
                                                feedRepo.toggleLike(post.id)
                                            }
                                        }
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (isLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                        contentDescription = null,
                                        tint = if (isLiked) AccentRed else SecondaryText,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (isLiked) "Đã thích" else "Thích",
                                        fontSize = 12.sp,
                                        fontWeight = if (isLiked) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isLiked) AccentRed else SecondaryText
                                    )
                                }

                                // Bình luận button
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable {
                                            expandedPostComments = if (isExpanded) expandedPostComments - post.id else expandedPostComments + post.id
                                        }
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Outlined.ChatBubbleOutline,
                                        contentDescription = null,
                                        tint = SecondaryText,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Bình luận", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = SecondaryText)
                                }

                                // Lưu vào Kho button
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable {
                                            coroutineScope.launch {
                                                val draft = StampDraft(
                                                    originalImagePath = post.stampUrl ?: "",
                                                    renderedImagePath = post.stampUrl ?: "",
                                                    title = post.stampTitle ?: "Tem kỷ niệm",
                                                    location = post.location ?: "Việt Nam",
                                                    memoryDate = post.createdAt,
                                                    note = post.caption ?: ""
                                                )
                                                stampRepo.saveStamp(draft)
                                                Toast.makeText(context, "Đã lưu con tem vào Kho của bạn! 📮", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Outlined.BookmarkBorder,
                                        contentDescription = null,
                                        tint = AccentBlue,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Lưu tem", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = AccentBlue)
                                }

                                // Nhắn tin button
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable {
                                            profilePreviewUser = UserProfile(
                                                userId = post.authorId,
                                                username = post.authorName.lowercase().replace(" ", "_"),
                                                displayName = post.authorName,
                                                avatarUrl = post.authorAvatar,
                                                isCloudSynced = true
                                            )
                                        }
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Outlined.Send,
                                        contentDescription = null,
                                        tint = SecondaryText,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }

                            // --- EXPANDABLE COMMENTS SECTION ---
                            AnimatedVisibility(
                                visible = isExpanded,
                                enter = expandVertically(),
                                exit = shrinkVertically()
                            ) {
                                Column(modifier = Modifier.padding(top = 10.dp)) {
                                    HorizontalDivider(color = WarmPaperBg)
                                    Spacer(modifier = Modifier.height(8.dp))

                                    // Display existing comments
                                    if (post.comments.isEmpty()) {
                                        Text(
                                            text = "Chưa có bình luận nào. Hãy gửi suy nghĩ đầu tiên!",
                                            fontSize = 11.sp,
                                            color = SecondaryText,
                                            modifier = Modifier.padding(vertical = 4.dp)
                                        )
                                    } else {
                                        post.comments.forEach { c ->
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 4.dp),
                                                verticalAlignment = Alignment.Top
                                            ) {
                                                AsyncImage(
                                                    model = c.authorAvatar.ifBlank { "https://i.pravatar.cc/150?u=${c.authorId}" },
                                                    contentDescription = c.authorName,
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier
                                                        .size(28.dp)
                                                        .clip(CircleShape)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Surface(
                                                    shape = RoundedCornerShape(14.dp),
                                                    color = WarmPaperBg,
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                                                        Text(
                                                            text = c.authorName,
                                                            fontSize = 12.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = PrimaryText
                                                        )
                                                        Text(
                                                            text = c.content,
                                                            fontSize = 12.sp,
                                                            color = PrimaryText
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    // Inline comment input field
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        AsyncImage(
                                            model = currentUser.avatarUrl.ifBlank { "https://i.pravatar.cc/150?u=${currentUser.userId}" },
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .size(28.dp)
                                                .clip(CircleShape)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        OutlinedTextField(
                                            value = currentCommentText,
                                            onValueChange = { text ->
                                                commentInputs = commentInputs + (post.id to text)
                                            },
                                            placeholder = { Text("Viết bình luận...", fontSize = 12.sp, color = SecondaryText) },
                                            shape = RoundedCornerShape(20.dp),
                                            modifier = Modifier.weight(1f),
                                            singleLine = true,
                                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                                            keyboardActions = KeyboardActions(onSend = {
                                                if (currentCommentText.isNotBlank()) {
                                                    coroutineScope.launch {
                                                        feedRepo.addComment(post.id, currentCommentText)
                                                        commentInputs = commentInputs + (post.id to "")
                                                    }
                                                }
                                            }),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                unfocusedBorderColor = StampBorderDefault,
                                                focusedBorderColor = AccentRed
                                            )
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        IconButton(
                                            onClick = {
                                                if (currentCommentText.isNotBlank()) {
                                                    coroutineScope.launch {
                                                        feedRepo.addComment(post.id, currentCommentText)
                                                        commentInputs = commentInputs + (post.id to "")
                                                    }
                                                }
                                            },
                                            enabled = currentCommentText.isNotBlank()
                                        ) {
                                            Icon(
                                                Icons.Outlined.Send,
                                                contentDescription = "Gửi",
                                                tint = if (currentCommentText.isNotBlank()) AccentRed else SecondaryText,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatTimeAgo(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    val seconds = diff / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    val days = hours / 24

    return when {
        days > 0 -> "$days ngày trước"
        hours > 0 -> "$hours giờ trước"
        minutes > 0 -> "$minutes phút trước"
        else -> "Vừa xong"
    }
}
