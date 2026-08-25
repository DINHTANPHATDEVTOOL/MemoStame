package com.mipastudio.memostamp.feature.friends

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.mipastudio.memostamp.core.processor.MemoImageProcessor
import com.mipastudio.memostamp.ui.theme.*
import com.mipastudio.memostamp.ui.components.ThemeSelectorModalSheet
import com.mipastudio.memostamp.ui.components.StampGeometry
import com.mipastudio.memostamp.ui.components.UserProfileDialog
import com.mipastudio.memostamp.data.local.StampEntity
import com.mipastudio.memostamp.data.repository.FriendRequest
import com.mipastudio.memostamp.data.repository.UserAuthRepository
import com.mipastudio.memostamp.data.repository.UserProfile
import com.mipastudio.memostamp.data.repository.ChatRepository
import com.mipastudio.memostamp.data.repository.StampRepository
import com.mipastudio.memostamp.domain.model.StampDraft
import kotlinx.coroutines.launch

data class TradeOfferItem(
    val id: String,
    val senderId: String,
    val senderName: String,
    val recipientId: String,
    val note: String,
    val time: String,
    val imageUrl: String,
    val location: String,
    var status: String = "PENDING" // PENDING, ACCEPTED, DECLINED
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendsAndTradeScreen(
    onOpenStampDetail: (String) -> Unit = {},
    onOpenChat: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val authRepo = remember(context) { UserAuthRepository.getInstance(context) }
    val repo = remember(context) { StampRepository.getInstance(context) }
    val chatRepo = remember(context) { ChatRepository.getInstance(context) }

    val currentUser by authRepo.currentUser.collectAsState()
    val allAccounts by authRepo.allAccounts.collectAsState()
    val friendIds by authRepo.friendIds.collectAsState()
    val friendRequests by authRepo.friendRequests.collectAsState()
    val allChatMessages by chatRepo.messages.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var selectedTab by remember { mutableIntStateOf(4) } // Default: 4 (Tin nhắn Messenger)
    var selectedTradeOffer by remember { mutableStateOf<TradeOfferItem?>(null) }
    var friendToTradeWith by remember { mutableStateOf<UserProfile?>(null) }
    var userToUnfriend by remember { mutableStateOf<UserProfile?>(null) }
    var profilePreviewUser by remember { mutableStateOf<UserProfile?>(null) }
    var showThemeSelector by remember { mutableStateOf(false) }

    val prefs = remember { context.getSharedPreferences("memo_inbox_prefs", Context.MODE_PRIVATE) }
    var dismissedInboxIds by remember {
        mutableStateOf(prefs.getStringSet("dismissed_ids", emptySet()) ?: emptySet())
    }

    fun dismissInboxItem(id: String) {
        val updated = dismissedInboxIds + id
        dismissedInboxIds = updated
        prefs.edit().putStringSet("dismissed_ids", updated).apply()
        coroutineScope.launch {
            try {
                chatRepo.deleteMessage(id)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    var myStamps by remember { mutableStateOf<List<StampEntity>>(emptyList()) }

    // Fetch user's local stamps for trading
    LaunchedEffect(Unit) {
        repo.observeStamps().collect { list ->
            myStamps = list
        }
    }

    // Trade inbox starts empty (no fake items)
    var inboxItems by remember { mutableStateOf<List<TradeOfferItem>>(emptyList()) }

    val friendsList = remember(allAccounts, friendIds, friendRequests, currentUser) {
        friendIds.filter { it != currentUser.userId }.map { fId ->
            allAccounts.find { it.userId == fId } ?: run {
                val req = friendRequests.find { it.senderId == fId || it.recipientId == fId }
                val isSender = req?.senderId == fId
                UserProfile(
                    userId = fId,
                    username = if (req != null) (if (isSender) req.senderUsername else req.recipientUsername) else fId,
                    displayName = if (req != null) (if (isSender) req.senderDisplayName else req.recipientDisplayName) else "Bạn bè",
                    avatarUrl = if (req != null) (if (isSender) req.senderAvatar else req.recipientAvatar) else "https://i.pravatar.cc/150?u=$fId",
                    isCloudSynced = true
                )
            }
        }
    }

    val conversationList = remember(friendsList, allChatMessages, currentUser) {
        chatRepo.getConversationList(friendsList)
    }

    val unreadChatCount = remember(conversationList) {
        conversationList.sumOf { it.unreadCount }
    }

    val incomingRequests = remember(friendRequests, currentUser) {
        friendRequests.filter { it.recipientId == currentUser.userId && it.status.equals("PENDING", ignoreCase = true) }
    }

    val outgoingRequests = remember(friendRequests, currentUser) {
        friendRequests.filter { it.senderId == currentUser.userId && it.status.equals("PENDING", ignoreCase = true) }
    }

    val receivedPostcards = remember(allChatMessages, currentUser) {
        allChatMessages.filter { it.recipientId == currentUser.userId && !it.stampImageUrl.isNullOrBlank() }
            .sortedByDescending { it.createdAt }
    }

    val visiblePostcards = remember(receivedPostcards, dismissedInboxIds) {
        receivedPostcards.filter { !dismissedInboxIds.contains(it.id) }
    }

    val visibleInboxItems = remember(inboxItems, dismissedInboxIds) {
        inboxItems.filter { !dismissedInboxIds.contains(it.id) }
    }

    var isSearching by remember { mutableStateOf(false) }
    var liveSearchResults by remember { mutableStateOf<List<UserProfile>>(emptyList()) }

    // Trigger immediate cloud sync when screen opens or tab switches
    LaunchedEffect(selectedTab) {
        authRepo.triggerSync()
    }

    LaunchedEffect(searchQuery, allAccounts) {
        val q = searchQuery.trim().lowercase().removePrefix("@")
        if (q.isBlank()) {
            isSearching = false
            liveSearchResults = allAccounts.filter { it.userId != currentUser.userId }
        } else {
            isSearching = true
            val results = authRepo.searchUsers(q)
            liveSearchResults = results.filter { it.userId != currentUser.userId }
            isSearching = false
        }
    }

    fun copyToClipboard(text: String, label: String = "ID MemoStamp") {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "Đã sao chép $text vào bộ nhớ tạm! 📋", Toast.LENGTH_SHORT).show()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Bạn Bè & Kết Nối",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = AppDisplayFontFamily,
                            color = PrimaryText
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "ID: @${currentUser.username}",
                                fontSize = 11.sp,
                                color = AccentRed,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { showThemeSelector = true }) {
                        Icon(Icons.Outlined.Palette, contentDescription = "Chọn giao diện", tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = {
                        selectedTab = 1
                    }) {
                        Icon(Icons.Outlined.PersonSearch, contentDescription = "Tìm bạn bằng ID", tint = MaterialTheme.colorScheme.onBackground)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            // Messenger Navigation Tabs with Badges (Scrollable for all screen sizes)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val tabs = listOf(
                    (if (unreadChatCount > 0) "💬 Chat ($unreadChatCount)" else "💬 Chat") to 4,
                    "📩 Lời mời (${incomingRequests.size})" to 2,
                    "👥 Bạn bè (${friendsList.size})" to 0,
                    "🔍 Tìm kiếm" to 1,
                    "📮 Hộp thư" + (if (visiblePostcards.size + visibleInboxItems.size > 0) " (${visiblePostcards.size + visibleInboxItems.size})" else "") to 3
                )
                tabs.forEach { (label, idx) ->
                    val selected = selectedTab == idx
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = if (selected) AccentRed else SurfaceWhite,
                        shadowElevation = if (selected) 2.dp else 0.dp,
                        modifier = Modifier.clickable { selectedTab = idx }
                    ) {
                        Text(
                            text = label,
                            fontSize = 11.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            color = if (selected) Color.White else PrimaryText,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            when (selectedTab) {
                0 -> { // TAB 0: Danh sách bạn bè chính thức
                    if (friendsList.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(vertical = 40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("👥", fontSize = 48.sp)
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    "Danh sách bạn bè đang trống",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = PrimaryText
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    "Chia sẻ ID @${currentUser.username} hoặc tìm kiếm ID bạn bè để gửi lời mời!",
                                    fontSize = 12.sp,
                                    color = SecondaryText,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 24.dp)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(
                                        onClick = { copyToClipboard("@${currentUser.username}") },
                                        shape = RoundedCornerShape(16.dp)
                                    ) {
                                        Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Sao chép ID của bạn", fontSize = 12.sp)
                                    }
                                    Button(
                                        onClick = { selectedTab = 1 },
                                        colors = ButtonDefaults.buttonColors(containerColor = AccentRed),
                                        shape = RoundedCornerShape(16.dp)
                                    ) {
                                        Icon(Icons.Outlined.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Tìm bạn mới", fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(friendsList, key = { it.userId }) { friend ->
                                FriendCard(
                                    user = friend,
                                    isFriend = true,
                                    isPending = false,
                                    hasIncoming = false,
                                    onSendRequest = {},
                                    onCancelRequest = {},
                                    onAcceptRequest = {},
                                    onDeclineRequest = {},
                                    onUnfriend = { userToUnfriend = friend },
                                    onSendTrade = { friendToTradeWith = friend },
                                    onOpenChat = { onOpenChat(friend.userId) },
                                    onOpenProfile = { profilePreviewUser = friend }
                                )
                            }
                        }
                    }
                }

                1 -> { // TAB 1: Tìm kiếm bạn bè bằng ID duy nhất
                    Column(modifier = Modifier.fillMaxSize()) {
                        // User's own Stamp ID Banner Card
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = AccentRedSoft,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = SurfaceWhite,
                                    modifier = Modifier.size(42.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text("📮", fontSize = 22.sp)
                                    }
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("ID MemoStamp của bạn", fontSize = 11.sp, color = SecondaryText)
                                    Text(
                                        "@${currentUser.username}",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = AccentRed
                                    )
                                }
                                Button(
                                    onClick = { copyToClipboard("@${currentUser.username}") },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = AccentRed),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy", tint = Color.White, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Sao chép", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Search Box
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = SurfaceWhite,
                            shadowElevation = 1.dp,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Outlined.Search,
                                    contentDescription = null,
                                    tint = if (searchQuery.isNotBlank()) AccentRed else SecondaryText,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                OutlinedTextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    placeholder = {
                                        Text(
                                            "Nhập chính xác ID (ví dụ: @phat_memostamp)",
                                            fontSize = 13.sp,
                                            color = TertiaryText
                                        )
                                    },
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = Color.Transparent,
                                        unfocusedBorderColor = Color.Transparent,
                                        cursorColor = AccentRed
                                    ),
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                    keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                                    modifier = Modifier.weight(1f)
                                )
                                if (searchQuery.isNotBlank()) {
                                    IconButton(
                                        onClick = { searchQuery = "" },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Outlined.Clear, contentDescription = "Clear", tint = SecondaryText, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        val cleanQ = searchQuery.trim().lowercase().removePrefix("@")

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (cleanQ.isBlank()) "Khám phá người dùng trên Supabase (${liveSearchResults.size})" else "Kết quả tìm kiếm (${liveSearchResults.size})",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = PrimaryText
                            )
                            if (isSearching) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = AccentRed
                                )
                            }
                        }

                        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (liveSearchResults.isEmpty() && !isSearching) {
                                item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 30.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text("🔍", fontSize = 36.sp)
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(
                                                if (cleanQ.isBlank()) "Chưa tìm thấy người dùng nào trên hệ thống" else "Không tìm thấy người dùng nào với ID \"$cleanQ\"",
                                                color = SecondaryText,
                                                fontSize = 13.sp,
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                    }
                                }
                            } else {
                                items(liveSearchResults, key = { it.userId }) { user ->
                                    val isUserFriend = friendIds.contains(user.userId)
                                    val isPending = authRepo.isRequestPendingTo(user.userId)
                                    val incomingReq = authRepo.getIncomingRequestFrom(user.userId)

                                    FriendCard(
                                        user = user,
                                        isFriend = isUserFriend,
                                        isPending = isPending,
                                        hasIncoming = incomingReq != null,
                                        onSendRequest = {
                                            val res = authRepo.sendFriendRequest(user)
                                            res.fold(
                                                onSuccess = {
                                                    Toast.makeText(context, "Đã gửi lời mời kết bạn đến @${user.username}! ✉️", Toast.LENGTH_SHORT).show()
                                                },
                                                onFailure = { err ->
                                                    Toast.makeText(context, err.message ?: "Không thể gửi lời mời", Toast.LENGTH_SHORT).show()
                                                }
                                            )
                                        },
                                        onCancelRequest = {
                                            authRepo.cancelFriendRequest(user.userId)
                                            Toast.makeText(context, "Đã thu hồi lời mời kết bạn", Toast.LENGTH_SHORT).show()
                                        },
                                        onAcceptRequest = {
                                            incomingReq?.let { req ->
                                                val res = authRepo.acceptFriendRequest(req.id)
                                                res.fold(
                                                    onSuccess = {
                                                        Toast.makeText(context, "Đã chấp nhận lời mời kết bạn từ @${user.username}! 🤝", Toast.LENGTH_SHORT).show()
                                                        selectedTab = 0
                                                    },
                                                    onFailure = { err ->
                                                        Toast.makeText(context, err.message ?: "Không thể chấp nhận lời mời", Toast.LENGTH_SHORT).show()
                                                    }
                                                )
                                            }
                                        },
                                        onDeclineRequest = {
                                            incomingReq?.let { req ->
                                                authRepo.declineFriendRequest(req.id)
                                                Toast.makeText(context, "Đã từ chối lời mời kết bạn", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                         onUnfriend = { userToUnfriend = user },
                                        onSendTrade = { friendToTradeWith = user },
                                        onOpenChat = { onOpenChat(user.userId) },
                                        onOpenProfile = { profilePreviewUser = user }
                                    )
                                }
                            }
                        }
                    }
                }

                2 -> { // TAB 2: Quản lý lời mời kết bạn (Nhận & Đã gửi)
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        // Section: Lời mời nhận được
                        item {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "Lời mời kết bạn nhận được (${incomingRequests.size})",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = PrimaryText
                                )
                            }
                        }

                        if (incomingRequests.isEmpty()) {
                            item {
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = SurfaceWhite,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(24.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("Chưa có lời mời kết bạn nào gửi đến bạn 📭", fontSize = 12.sp, color = SecondaryText)
                                    }
                                }
                            }
                        } else {
                            items(incomingRequests, key = { it.id }) { req ->
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = SurfaceWhite,
                                    shadowElevation = 1.dp,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        AsyncImage(
                                            model = req.senderAvatar,
                                            contentDescription = req.senderDisplayName,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .size(46.dp)
                                                .clip(CircleShape)
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(req.senderDisplayName, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = PrimaryText)
                                            Text("@${req.senderUsername}", fontSize = 11.sp, color = AccentRed, fontWeight = FontWeight.SemiBold)
                                            Text("Muốn kết bạn với bạn", fontSize = 11.sp, color = SecondaryText)
                                        }

                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Button(
                                                onClick = {
                                                    val res = authRepo.acceptFriendRequest(req.id)
                                                    res.fold(
                                                        onSuccess = {
                                                            Toast.makeText(context, "Đã trở thành bạn bè với @${req.senderUsername}! 🤝", Toast.LENGTH_SHORT).show()
                                                            selectedTab = 0
                                                        },
                                                        onFailure = { err ->
                                                            Toast.makeText(context, err.message ?: "Không thể chấp nhận lời mời", Toast.LENGTH_SHORT).show()
                                                        }
                                                    )
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen),
                                                shape = RoundedCornerShape(12.dp),
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                            ) {
                                                Text("Chấp nhận", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }
                                            OutlinedButton(
                                                onClick = {
                                                    authRepo.declineFriendRequest(req.id)
                                                    Toast.makeText(context, "Đã từ chối lời mời", Toast.LENGTH_SHORT).show()
                                                },
                                                shape = RoundedCornerShape(12.dp),
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                                            ) {
                                                Text("Từ chối", fontSize = 11.sp, color = SecondaryText)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Section: Lời mời đã gửi đi
                        item {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                "Lời mời đã gửi đi (${outgoingRequests.size})",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = PrimaryText
                            )
                        }

                        if (outgoingRequests.isEmpty()) {
                            item {
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = SurfaceWhite,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(20.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("Không có lời mời nào đang chờ duyệt", fontSize = 12.sp, color = SecondaryText)
                                    }
                                }
                            }
                        } else {
                            items(outgoingRequests, key = { it.id }) { req ->
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = SurfaceWhite,
                                    shadowElevation = 1.dp,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        AsyncImage(
                                            model = req.recipientAvatar,
                                            contentDescription = req.recipientDisplayName,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .size(46.dp)
                                                .clip(CircleShape)
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(req.recipientDisplayName, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = PrimaryText)
                                            Text("@${req.recipientUsername}", fontSize = 11.sp, color = SecondaryText)
                                            Text("Đang chờ đối phương chấp nhận...", fontSize = 10.sp, color = AccentBlue)
                                        }

                                        OutlinedButton(
                                            onClick = {
                                                authRepo.cancelFriendRequest(req.recipientId)
                                                Toast.makeText(context, "Đã thu hồi lời mời kết bạn", Toast.LENGTH_SHORT).show()
                                            },
                                            shape = RoundedCornerShape(12.dp),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                                        ) {
                                            Text("Thu hồi", fontSize = 11.sp, color = AccentRed)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                3 -> { // TAB 3: Hộp thư lưu niệm (Tem nhận được & Thư bưu chính)
                    val totalItems = visiblePostcards.size + visibleInboxItems.size
                    if (totalItems == 0) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(vertical = 40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("📮", fontSize = 48.sp)
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    "Hộp thư chưa có tem hoặc thiệp nào",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = PrimaryText
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "Khi bạn bè gửi tặng tem hoặc đính kèm tem trong tin nhắn, con tem sẽ xuất hiện ở đây!",
                                    fontSize = 12.sp,
                                    color = SecondaryText,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 24.dp)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(
                                    onClick = { selectedTab = 0 },
                                    colors = ButtonDefaults.buttonColors(containerColor = AccentRed),
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    Icon(Icons.Outlined.CardGiftcard, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Gửi tặng tem cho bạn bè", fontSize = 12.sp)
                                }
                            }
                        }
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            // Show received chat stamps
                            items(visiblePostcards, key = { it.id }) { msg ->
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = SurfaceWhite,
                                    shadowElevation = 1.5.dp,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(14.dp)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            AsyncImage(
                                                model = msg.senderAvatar.ifBlank { "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=100" },
                                                contentDescription = msg.senderName,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier
                                                    .size(36.dp)
                                                    .clip(CircleShape)
                                                    .clickable {
                                                        profilePreviewUser = allAccounts.find { it.userId == msg.senderId }
                                                            ?: UserProfile(userId = msg.senderId, displayName = msg.senderName, avatarUrl = msg.senderAvatar)
                                                    }
                                            )
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    msg.senderName,
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = PrimaryText,
                                                    modifier = Modifier.clickable {
                                                        profilePreviewUser = allAccounts.find { it.userId == msg.senderId }
                                                            ?: UserProfile(userId = msg.senderId, displayName = msg.senderName, avatarUrl = msg.senderAvatar)
                                                    }
                                                )
                                                val timeStr = remember(msg.createdAt) {
                                                    val sdf = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault())
                                                    sdf.format(java.util.Date(msg.createdAt))
                                                }
                                                Text(timeStr, fontSize = 10.sp, color = TertiaryText)
                                            }
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = AccentRed.copy(alpha = 0.1f)
                                            ) {
                                                Text(
                                                    "Tem kỷ niệm 📮",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = AccentRed,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(10.dp))

                                        // Stamp Image & Details Card
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = WarmPaperBg,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(10.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                AsyncImage(
                                                    model = msg.stampImageUrl,
                                                    contentDescription = msg.stampTitle,
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier
                                                        .size(72.dp)
                                                        .clip(RoundedCornerShape(8.dp))
                                                )
                                                Spacer(modifier = Modifier.width(12.dp))
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        msg.stampTitle ?: "Tem thư kỷ niệm",
                                                        fontSize = 14.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = PrimaryText
                                                    )
                                                    Spacer(modifier = Modifier.height(2.dp))
                                                    Text(
                                                        "📍 ${msg.stampLocation ?: "Việt Nam"}",
                                                        fontSize = 11.sp,
                                                        color = AccentBlue,
                                                        fontWeight = FontWeight.Medium
                                                    )
                                                    if (msg.text.isNotBlank() && !msg.text.startsWith("📮 Đã gửi con tem")) {
                                                        Spacer(modifier = Modifier.height(4.dp))
                                                        Text(
                                                            "“${msg.text}”",
                                                            fontSize = 11.sp,
                                                            color = SecondaryText,
                                                            maxLines = 2
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(10.dp))

                                        // Action buttons: Từ chối, Nhắn tin, Lưu vào Kho tem
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.End,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            OutlinedButton(
                                                onClick = {
                                                    dismissInboxItem(msg.id)
                                                    Toast.makeText(context, "Đã từ chối con tem này ❌", Toast.LENGTH_SHORT).show()
                                                },
                                                shape = RoundedCornerShape(12.dp),
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                            ) {
                                                Icon(Icons.Outlined.Close, contentDescription = null, modifier = Modifier.size(14.dp), tint = SecondaryText)
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Từ chối", fontSize = 11.sp, color = SecondaryText)
                                            }

                                            Spacer(modifier = Modifier.width(6.dp))

                                            OutlinedButton(
                                                onClick = { onOpenChat(msg.senderId) },
                                                shape = RoundedCornerShape(12.dp),
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                            ) {
                                                Icon(Icons.AutoMirrored.Outlined.Chat, contentDescription = null, modifier = Modifier.size(14.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Nhắn tin", fontSize = 11.sp)
                                            }

                                            Spacer(modifier = Modifier.width(6.dp))

                                            Button(
                                                onClick = {
                                                    coroutineScope.launch {
                                                        val draft = StampDraft(
                                                            originalImagePath = msg.stampImageUrl ?: "",
                                                            renderedImagePath = msg.stampImageUrl ?: "",
                                                            title = msg.stampTitle ?: "Tem từ ${msg.senderName}",
                                                            location = msg.stampLocation ?: "Việt Nam",
                                                            memoryDate = msg.createdAt,
                                                            note = msg.text
                                                        )
                                                        repo.saveStamp(draft)
                                                        dismissInboxItem(msg.id)
                                                        Toast.makeText(context, "Đã lưu con tem vào Kho của bạn thành công! 📮", Toast.LENGTH_SHORT).show()
                                                    }
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen),
                                                shape = RoundedCornerShape(12.dp),
                                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                            ) {
                                                Icon(Icons.Outlined.SaveAlt, contentDescription = null, modifier = Modifier.size(14.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Lưu vào Kho", fontSize = 11.sp)
                                            }
                                        }
                                    }
                                }
                            }

                            // Show trade offers in inbox
                            items(visibleInboxItems, key = { it.id }) { item ->
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = SurfaceWhite,
                                    shadowElevation = 1.dp,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(14.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(item.senderName, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = PrimaryText)
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("đã gửi một món quà kỷ niệm", fontSize = 12.sp, color = SecondaryText)
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            AsyncImage(
                                                model = MemoImageProcessor.resolveImageModel(item.imageUrl),
                                                contentDescription = null,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier
                                                    .size(60.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                            )
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text("📍 ${item.location}", fontSize = 11.sp, color = AccentBlue)
                                                Text("“${item.note}”", fontSize = 12.sp, color = PrimaryText)
                                                Text(item.time, fontSize = 10.sp, color = TertiaryText)
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(10.dp))

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.End,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            OutlinedButton(
                                                onClick = {
                                                    dismissInboxItem(item.id)
                                                    Toast.makeText(context, "Đã từ chối con tem này ❌", Toast.LENGTH_SHORT).show()
                                                },
                                                shape = RoundedCornerShape(12.dp),
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                            ) {
                                                Icon(Icons.Outlined.Close, contentDescription = null, modifier = Modifier.size(14.dp), tint = SecondaryText)
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Từ chối", fontSize = 11.sp, color = SecondaryText)
                                            }

                                            Spacer(modifier = Modifier.width(8.dp))

                                            Button(
                                                onClick = {
                                                    coroutineScope.launch {
                                                        val draft = StampDraft(
                                                            originalImagePath = item.imageUrl,
                                                            renderedImagePath = item.imageUrl,
                                                            title = "Tem từ ${item.senderName}",
                                                            location = item.location,
                                                            memoryDate = System.currentTimeMillis(),
                                                            note = item.note
                                                        )
                                                        repo.saveStamp(draft)
                                                        dismissInboxItem(item.id)
                                                        Toast.makeText(context, "Đã lưu con tem vào Kho thành công! 📮", Toast.LENGTH_SHORT).show()
                                                    }
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen),
                                                shape = RoundedCornerShape(12.dp),
                                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                            ) {
                                                Icon(Icons.Outlined.SaveAlt, contentDescription = null, modifier = Modifier.size(14.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Lưu vào Kho", fontSize = 11.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                4 -> { // TAB 4: Tin nhắn trực tiếp (Chat)
                    if (conversationList.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(vertical = 40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("💬", fontSize = 48.sp)
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    "Chưa có cuộc trò chuyện nào",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = PrimaryText
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "Kết nối với bạn bè để trò chuyện và chia sẻ tem thư kỉ niệm nhé!",
                                    fontSize = 12.sp,
                                    color = SecondaryText,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 24.dp)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(
                                    onClick = { selectedTab = 0 },
                                    colors = ButtonDefaults.buttonColors(containerColor = AccentRed),
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    Icon(Icons.Outlined.People, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Xem danh sách bạn bè", fontSize = 12.sp)
                                }
                            }
                        }
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(conversationList, key = { it.otherUser.userId }) { conv ->
                                val friend = conv.otherUser
                                val lastMsg = conv.lastMessage
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = SurfaceWhite,
                                    shadowElevation = 1.dp,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onOpenChat(friend.userId) }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        AsyncImage(
                                            model = friend.avatarUrl,
                                            contentDescription = friend.displayName,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .size(48.dp)
                                                .clip(CircleShape)
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = friend.displayName,
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = PrimaryText
                                                )
                                                if (lastMsg != null) {
                                                    val timeStr = remember(lastMsg.createdAt) {
                                                        val sdf = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                                                        sdf.format(java.util.Date(lastMsg.createdAt))
                                                    }
                                                    Text(timeStr, fontSize = 10.sp, color = TertiaryText)
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                val isMe = lastMsg?.senderId == currentUser.userId
                                                val basePreview = when {
                                                    lastMsg == null -> "Chạm để bắt đầu nhắn tin"
                                                    lastMsg.stampImageUrl != null -> "📮 [Tem: ${lastMsg.stampTitle ?: "Kỷ niệm"}] ${lastMsg.text}"
                                                    else -> lastMsg.text
                                                }
                                                val displayText = if (isMe) "Bạn: $basePreview" else basePreview

                                                Text(
                                                    text = displayText,
                                                    fontSize = 12.sp,
                                                    color = if (conv.unreadCount > 0) PrimaryText else SecondaryText,
                                                    fontWeight = if (conv.unreadCount > 0) FontWeight.Bold else FontWeight.Normal,
                                                    maxLines = 1,
                                                    modifier = Modifier.weight(1f)
                                                )

                                                if (isMe) {
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    if (lastMsg.isRead) {
                                                        Text(
                                                            text = "Đã xem ✓✓",
                                                            fontSize = 10.sp,
                                                            color = AccentRed,
                                                            fontWeight = FontWeight.Medium
                                                        )
                                                    } else {
                                                        Text(
                                                            text = "Đã gửi ✓",
                                                            fontSize = 10.sp,
                                                            color = SecondaryText
                                                        )
                                                    }
                                                }

                                                if (conv.unreadCount > 0) {
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Surface(
                                                        shape = RoundedCornerShape(10.dp),
                                                        color = AccentRed,
                                                        modifier = Modifier.padding(start = 4.dp)
                                                    ) {
                                                        Text(
                                                            text = "${conv.unreadCount} mới",
                                                            fontSize = 10.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = Color.White,
                                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
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
        }

        // Unfriend Confirmation Dialog
        userToUnfriend?.let { friend ->
            AlertDialog(
                onDismissRequest = { userToUnfriend = null },
                containerColor = SurfaceWhite,
                title = {
                    Text("Hủy kết bạn với @${friend.username}?", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = PrimaryText)
                },
                text = {
                    Text(
                        "Bạn có chắc muốn hủy kết bạn với ${friend.displayName}? Sau khi hủy, bạn sẽ cần gửi lại lời mời kết bạn nếu muốn kết nối lại.",
                        fontSize = 13.sp,
                        color = SecondaryText
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            authRepo.unfriend(friend.userId)
                            userToUnfriend = null
                            Toast.makeText(context, "Đã hủy kết bạn với @${friend.username}", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentRed)
                    ) {
                        Text("Xác nhận hủy", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { userToUnfriend = null }) {
                        Text("Đóng")
                    }
                }
            )
        }

        // Trade Detail Modal Dialog
        selectedTradeOffer?.let { offer ->
            AlertDialog(
                onDismissRequest = { selectedTradeOffer = null },
                containerColor = SurfaceWhite,
                title = {
                    Text("Bưu thiếp & Tem từ ${offer.senderName} 🤝", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = PrimaryText)
                },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.75f)
                                .aspectRatio(StampGeometry.ASPECT_RATIO)
                                .clip(RoundedCornerShape(8.dp))
                        ) {
                            AsyncImage(
                                model = offer.imageUrl,
                                contentDescription = offer.note,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("“${offer.note}”", fontSize = 13.sp, color = PrimaryText, textAlign = TextAlign.Center)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("📍 ${offer.location}", fontSize = 11.sp, color = SecondaryText)
                    }
                },
                confirmButton = {
                    if (offer.status == "PENDING") {
                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    val draft = StampDraft(
                                        originalImagePath = offer.imageUrl,
                                        renderedImagePath = offer.imageUrl,
                                        title = "Tem từ ${offer.senderName}",
                                        location = offer.location,
                                        memoryDate = System.currentTimeMillis(),
                                        note = offer.note
                                    )
                                    val res = repo.saveStamp(draft)
                                    res.fold(
                                        onSuccess = { entity ->
                                            offer.status = "ACCEPTED"
                                            inboxItems = inboxItems.map { if (it.id == offer.id) offer else it }
                                            Toast.makeText(context, "Đã nhận tem! Đã lưu vào Kho của bạn 📮", Toast.LENGTH_LONG).show()
                                            selectedTradeOffer = null
                                            onOpenStampDetail(entity.id)
                                        },
                                        onFailure = { err ->
                                            Toast.makeText(context, "Lỗi: ${err.message}", Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen)
                        ) {
                            Text("Nhận Tem & Lưu Vào Kho 🤝")
                        }
                    }
                },
                dismissButton = {
                    TextButton(onClick = { selectedTradeOffer = null }) {
                        Text("Đóng")
                    }
                }
            )
        }

        // Send Trade Offer Dialog Modal
        friendToTradeWith?.let { friend ->
            var selectedStampId by remember { mutableStateOf<String?>(null) }
            var tradeNote by remember { mutableStateOf("Tặng bạn dấu tem kỷ niệm này nhé! 📮") }

            AlertDialog(
                onDismissRequest = { friendToTradeWith = null },
                containerColor = SurfaceWhite,
                title = {
                    Text("Gửi tặng tem cho @${friend.username}", fontWeight = FontWeight.Bold, color = PrimaryText)
                },
                text = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text("Chọn một con tem từ Kho của bạn:", fontSize = 12.sp, color = SecondaryText)
                        Spacer(modifier = Modifier.height(8.dp))

                        if (myStamps.isEmpty()) {
                            Text("Kho tem đang trống! Hãy chụp ảnh hoặc tạo tem trước.", fontSize = 12.sp, color = AccentRed)
                        } else {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(3),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.height(140.dp)
                            ) {
                                items(myStamps) { stamp ->
                                    val isSel = stamp.id == selectedStampId
                                    Box(
                                        modifier = Modifier
                                            .aspectRatio(1f)
                                            .clip(RoundedCornerShape(6.dp))
                                            .clickable { selectedStampId = stamp.id }
                                    ) {
                                        AsyncImage(
                                            model = stamp.stampImagePath,
                                            contentDescription = stamp.title,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                        if (isSel) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .background(AccentRed.copy(alpha = 0.4f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(Icons.Outlined.CheckCircle, contentDescription = "Selected", tint = Color.White)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = tradeNote,
                            onValueChange = { tradeNote = it },
                            label = { Text("Lời nhắn bưu chính gửi @${friend.username}") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val sel = myStamps.find { it.id == selectedStampId }
                            val noteText = tradeNote.ifBlank { "Tặng bạn dấu tem kỷ niệm này nhé! 📮" }
                            val newOffer = TradeOfferItem(
                                id = "trade_" + System.currentTimeMillis(),
                                senderId = currentUser.userId,
                                senderName = currentUser.displayName,
                                recipientId = friend.userId,
                                note = noteText,
                                time = "Vừa xong",
                                imageUrl = sel?.stampImagePath ?: "https://images.unsplash.com/photo-1506744038136-46273834b3fb?w=600",
                                location = sel?.location ?: currentUser.city
                            )
                            inboxItems = listOf(newOffer) + inboxItems

                            // Send through ChatRepository to deliver to Supabase & recipient inbox
                            chatRepo.sendMessage(
                                recipient = friend,
                                text = noteText,
                                stampId = sel?.id,
                                stampTitle = sel?.title ?: "Tem kỷ niệm",
                                stampImageUrl = sel?.stampImagePath,
                                stampLocation = sel?.location ?: currentUser.city
                            )

                            friendToTradeWith = null
                            Toast.makeText(context, "Đã gửi tem và lời nhắn cho @${friend.username}! 📮", Toast.LENGTH_SHORT).show()
                        },
                        enabled = selectedStampId != null || myStamps.isEmpty(),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentRed)
                    ) {
                        Text("Gửi Thư & Tem ✉️")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { friendToTradeWith = null }) {
                        Text("Hủy")
                    }
                }
            )
        }

        profilePreviewUser?.let { targetUser ->
            val isFriend = friendIds.contains(targetUser.userId)
            UserProfileDialog(
                user = targetUser,
                isFriend = isFriend,
                onDismiss = { profilePreviewUser = null },
                onOpenChat = {
                    onOpenChat(targetUser.userId)
                },
                onSendTrade = {
                    friendToTradeWith = targetUser
                },
                onAddFriend = {
                    coroutineScope.launch {
                        val result = authRepo.sendFriendRequest(targetUser)
                        result.fold(
                            onSuccess = { Toast.makeText(context, "Đã gửi lời mời kết bạn đến @${targetUser.username}! 📩", Toast.LENGTH_SHORT).show() },
                            onFailure = { err -> Toast.makeText(context, err.message ?: "Chưa thể gửi lời mời", Toast.LENGTH_SHORT).show() }
                        )
                    }
                },
                onUnfriend = {
                    userToUnfriend = targetUser
                }
            )
        }

        if (showThemeSelector) {
            ThemeSelectorModalSheet(
                onDismiss = { showThemeSelector = false }
            )
        }
    }
}

@Composable
private fun FriendCard(
    user: UserProfile,
    isFriend: Boolean,
    isPending: Boolean,
    hasIncoming: Boolean,
    onSendRequest: () -> Unit,
    onCancelRequest: () -> Unit,
    onAcceptRequest: () -> Unit,
    onDeclineRequest: () -> Unit,
    onUnfriend: () -> Unit,
    onSendTrade: () -> Unit,
    onOpenChat: () -> Unit = {},
    onOpenProfile: () -> Unit = {}
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = SurfaceWhite,
        shadowElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = user.avatarUrl,
                contentDescription = user.displayName,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .clickable { onOpenProfile() }
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onOpenProfile() }
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        user.displayName,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryText
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("📍 ${user.city}", fontSize = 10.sp, color = AccentBlue)
                }
                Text(
                    "@${user.username}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AccentRed
                )
                if (user.bio.isNotBlank()) {
                    Text(
                        user.bio,
                        fontSize = 11.sp,
                        color = SecondaryText,
                        maxLines = 1
                    )
                }
            }

            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                when {
                    isFriend -> {
                        // Action buttons for existing Friends: Chat, Send stamp & Unfriend
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = AccentRed,
                                modifier = Modifier.clickable { onOpenChat() }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Outlined.ChatBubbleOutline, contentDescription = "Chat", tint = Color.White, modifier = Modifier.size(13.dp))
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text("Nhắn tin", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = WarmPaperBg,
                                modifier = Modifier.clickable { onSendTrade() }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Outlined.SwapHoriz, contentDescription = "Trade", tint = AccentRed, modifier = Modifier.size(13.dp))
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text("Tặng tem", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = PrimaryText)
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color.LightGray.copy(alpha = 0.25f),
                                modifier = Modifier.clickable { onUnfriend() }
                            ) {
                                Box(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 5.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Outlined.PersonRemove, contentDescription = "Hủy bạn", tint = SecondaryText, modifier = Modifier.size(13.dp))
                                }
                            }
                        }
                    }

                    hasIncoming -> {
                        // Incoming Friend Request: Accept or Decline
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = SuccessGreen,
                                modifier = Modifier.clickable { onAcceptRequest() }
                            ) {
                                Text(
                                    "Chấp nhận",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color.LightGray.copy(alpha = 0.3f),
                                modifier = Modifier.clickable { onDeclineRequest() }
                            ) {
                                Text(
                                    "Từ chối",
                                    fontSize = 11.sp,
                                    color = SecondaryText,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }

                    isPending -> {
                        // Outgoing Request Pending: Show Pending & Allow Cancel
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = AccentBlueSoft,
                            modifier = Modifier.clickable { onCancelRequest() }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Outlined.HourglassTop, contentDescription = null, tint = AccentBlue, modifier = Modifier.size(12.dp))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("Đã gửi (Thu hồi)", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = AccentBlue)
                            }
                        }
                    }

                    else -> {
                        // Send Friend Request Button
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = AccentRed,
                            modifier = Modifier.clickable { onSendRequest() }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Outlined.PersonAdd, contentDescription = null, tint = Color.White, modifier = Modifier.size(13.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Gửi lời mời", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }
}

