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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.mipastudio.memostamp.R
import com.mipastudio.memostamp.core.processor.MemoImageProcessor
import com.mipastudio.memostamp.ui.theme.*
import com.mipastudio.memostamp.ui.components.ThemeSelectorModalSheet
import com.mipastudio.memostamp.ui.components.StampGeometry
import com.mipastudio.memostamp.ui.components.UserProfileDialog
import com.mipastudio.memostamp.ui.components.BlockUserConfirmationDialog
import com.mipastudio.memostamp.ui.components.ReportUserDialog
import com.mipastudio.memostamp.data.local.StampEntity
import com.mipastudio.memostamp.data.repository.FriendRequest
import com.mipastudio.memostamp.data.repository.UserAuthRepository
import com.mipastudio.memostamp.data.repository.UserProfile
import com.mipastudio.memostamp.data.repository.ChatRepository
import com.mipastudio.memostamp.data.repository.StampRepository
import com.mipastudio.memostamp.data.remote.supabase.SupabaseConfig
import com.mipastudio.memostamp.data.remote.supabase.SupabaseTradeRequestRecord
import com.mipastudio.memostamp.data.remote.supabase.SupabaseReceivedStampRecord
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
    val cloudTrades by authRepo.tradeRequests.collectAsState()
    val receivedStamps by authRepo.receivedStamps.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var selectedTab by remember { mutableIntStateOf(4) } // Default: 4 (Tin nhắn Messenger)
    var selectedTradeOffer by remember { mutableStateOf<TradeOfferItem?>(null) }
    var friendToTradeWith by remember { mutableStateOf<UserProfile?>(null) }
    var userToUnfriend by remember { mutableStateOf<UserProfile?>(null) }
    var userToBlock by remember { mutableStateOf<UserProfile?>(null) }
    var userToReport by remember { mutableStateOf<UserProfile?>(null) }
    var profilePreviewUser by remember { mutableStateOf<UserProfile?>(null) }
    var showThemeSelector by remember { mutableStateOf(false) }

    val currentUserId = currentUser.userId
    val prefs = remember(context, currentUserId) { context.getSharedPreferences("memo_inbox_prefs_$currentUserId", Context.MODE_PRIVATE) }
    var dismissedInboxIds by remember(currentUserId) {
        mutableStateOf(prefs.getStringSet("processed_ids", emptySet()) ?: emptySet())
    }

    fun dismissInboxItem(id: String) {
        val updated = dismissedInboxIds + id
        dismissedInboxIds = updated
        prefs.edit().putStringSet("processed_ids", updated).apply()
        // Non-destructive: DO NOT delete chatRepo message. Chat history remains intact.
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

    val incomingCloudTrades = remember(cloudTrades, currentUser) {
        cloudTrades.filter { it.recipientId == currentUser.userId && it.status.equals("PENDING", ignoreCase = true) }
    }

    val outgoingCloudTrades = remember(cloudTrades, currentUser) {
        cloudTrades.filter { it.senderId == currentUser.userId && it.status.equals("PENDING", ignoreCase = true) }
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
        Toast.makeText(context, context.getString(R.string.friends_copy_id_success, text), Toast.LENGTH_SHORT).show()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = stringResource(R.string.friends_trade_title),
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
                        Icon(Icons.Outlined.Palette, contentDescription = stringResource(R.string.friends_theme_select), tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = {
                        selectedTab = 1
                    }) {
                        Icon(Icons.Outlined.PersonSearch, contentDescription = stringResource(R.string.friends_find_by_id), tint = MaterialTheme.colorScheme.onBackground)
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
                val inboxCount = visiblePostcards.size + incomingCloudTrades.size + receivedStamps.size
                val tabs = listOf(
                    (if (unreadChatCount > 0) "💬 ${stringResource(R.string.friends_tab_chat)} ($unreadChatCount)" else "💬 ${stringResource(R.string.friends_tab_chat)}") to 4,
                    "📩 ${stringResource(R.string.friends_tab_requests, incomingRequests.size)}" to 2,
                    "👥 ${stringResource(R.string.friends_tab_friends, friendsList.size)}" to 0,
                    "🔍 ${stringResource(R.string.common_search)}" to 1,
                    "📮 " + (if (inboxCount > 0) stringResource(R.string.friends_tab_inbox, inboxCount) else stringResource(R.string.friends_inbox_empty_title)) to 3
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
                                    stringResource(R.string.friends_empty_title),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = PrimaryText
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    stringResource(R.string.friends_empty_desc, currentUser.username),
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
                                        Text(stringResource(R.string.friends_copy_my_id), fontSize = 12.sp)
                                    }
                                    Button(
                                        onClick = { selectedTab = 1 },
                                        colors = ButtonDefaults.buttonColors(containerColor = AccentRed),
                                        shape = RoundedCornerShape(16.dp)
                                    ) {
                                        Icon(Icons.Outlined.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(stringResource(R.string.friends_find_new), fontSize = 12.sp)
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
                                    Text(stringResource(R.string.friends_my_id_label), fontSize = 11.sp, color = SecondaryText)
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
                                    Text(stringResource(R.string.common_copy), fontSize = 11.sp, fontWeight = FontWeight.Bold)
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
                                            stringResource(R.string.friends_search_exact_id_hint),
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
                                text = if (cleanQ.isBlank()) stringResource(R.string.friends_explore_users, liveSearchResults.size) else stringResource(R.string.friends_search_results, liveSearchResults.size),
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
                                                if (cleanQ.isBlank()) stringResource(R.string.friends_no_users_found) else stringResource(R.string.friends_no_users_with_id, cleanQ),
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
                                            coroutineScope.launch {
                                                val res = authRepo.sendFriendRequest(user)
                                                res.fold(
                                                    onSuccess = {
                                                        Toast.makeText(context, context.getString(R.string.friends_invite_sent_to, user.username), Toast.LENGTH_SHORT).show()
                                                    },
                                                    onFailure = { err ->
                                                        val errMsg = err.message ?: ""
                                                        val displayMsg = if (errMsg.contains("RATE_LIMITED", ignoreCase = true) || errMsg.contains("429")) {
                                                            "You're doing that too quickly. Please try again shortly."
                                                        } else {
                                                            errMsg.ifBlank { context.getString(R.string.common_error) }
                                                        }
                                                        Toast.makeText(context, displayMsg, Toast.LENGTH_SHORT).show()
                                                    }
                                                )
                                            }
                                        },
                                        onCancelRequest = {
                                            coroutineScope.launch {
                                                val res = authRepo.cancelFriendRequest(user.userId)
                                                res.fold(
                                                    onSuccess = { Toast.makeText(context, context.getString(R.string.friends_invite_cancelled), Toast.LENGTH_SHORT).show() },
                                                    onFailure = { err -> Toast.makeText(context, err.message ?: context.getString(R.string.common_error), Toast.LENGTH_SHORT).show() }
                                                )
                                            }
                                        },
                                        onAcceptRequest = {
                                            incomingReq?.let { req ->
                                                coroutineScope.launch {
                                                    val res = authRepo.acceptFriendRequest(req.id)
                                                    res.fold(
                                                        onSuccess = {
                                                            Toast.makeText(context, context.getString(R.string.friends_invite_accepted_with, user.username), Toast.LENGTH_SHORT).show()
                                                            selectedTab = 0
                                                        },
                                                        onFailure = { err ->
                                                            Toast.makeText(context, err.message ?: context.getString(R.string.common_error), Toast.LENGTH_SHORT).show()
                                                        }
                                                    )
                                                }
                                            }
                                        },
                                        onDeclineRequest = {
                                            incomingReq?.let { req ->
                                                coroutineScope.launch {
                                                    val res = authRepo.declineFriendRequest(req.id)
                                                    res.fold(
                                                        onSuccess = { Toast.makeText(context, context.getString(R.string.friends_decline_request), Toast.LENGTH_SHORT).show() },
                                                        onFailure = { err -> Toast.makeText(context, err.message ?: context.getString(R.string.common_error), Toast.LENGTH_SHORT).show() }
                                                    )
                                                }
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
                                    stringResource(R.string.friends_incoming_title, incomingRequests.size),
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
                                        Text(stringResource(R.string.friends_incoming_empty), fontSize = 12.sp, color = SecondaryText)
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
                                            Text(stringResource(R.string.friends_wants_to_be_friends), fontSize = 11.sp, color = SecondaryText)
                                        }

                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Button(
                                                onClick = {
                                                    coroutineScope.launch {
                                                        val res = authRepo.acceptFriendRequest(req.id)
                                                        res.fold(
                                                            onSuccess = {
                                                                Toast.makeText(context, context.getString(R.string.friends_invite_accepted_with, req.senderUsername), Toast.LENGTH_SHORT).show()
                                                                selectedTab = 0
                                                            },
                                                            onFailure = { err ->
                                                                Toast.makeText(context, err.message ?: context.getString(R.string.common_error), Toast.LENGTH_SHORT).show()
                                                            }
                                                        )
                                                    }
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen),
                                                shape = RoundedCornerShape(12.dp),
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                            ) {
                                                Text(stringResource(R.string.friends_accept_request), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }
                                            OutlinedButton(
                                                onClick = {
                                                    coroutineScope.launch {
                                                        val res = authRepo.declineFriendRequest(req.id)
                                                        res.fold(
                                                            onSuccess = { Toast.makeText(context, context.getString(R.string.friends_decline_request), Toast.LENGTH_SHORT).show() },
                                                            onFailure = { err -> Toast.makeText(context, err.message ?: context.getString(R.string.common_error), Toast.LENGTH_SHORT).show() }
                                                        )
                                                    }
                                                },
                                                shape = RoundedCornerShape(12.dp),
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                                            ) {
                                                Text(stringResource(R.string.friends_decline_request), fontSize = 11.sp, color = SecondaryText)
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
                                stringResource(R.string.friends_outgoing_title, outgoingRequests.size),
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
                                        Text(stringResource(R.string.friends_outgoing_empty), fontSize = 12.sp, color = SecondaryText)
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
                                            Text(stringResource(R.string.friends_waiting_acceptance), fontSize = 10.sp, color = AccentBlue)
                                        }

                                        OutlinedButton(
                                            onClick = {
                                                coroutineScope.launch {
                                                    val res = authRepo.cancelFriendRequest(req.recipientId)
                                                    res.fold(
                                                        onSuccess = { Toast.makeText(context, context.getString(R.string.friends_invite_cancelled), Toast.LENGTH_SHORT).show() },
                                                        onFailure = { err -> Toast.makeText(context, err.message ?: context.getString(R.string.common_error), Toast.LENGTH_SHORT).show() }
                                                    )
                                                }
                                            },
                                            shape = RoundedCornerShape(12.dp),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                                        ) {
                                            Text(stringResource(R.string.friends_cancel_request), fontSize = 11.sp, color = AccentRed)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                3 -> { // TAB 3: Hộp thư lưu niệm (Tem nhận được & Thư bưu chính)
                    val totalItems = visiblePostcards.size + incomingCloudTrades.size + outgoingCloudTrades.size + receivedStamps.size + visibleInboxItems.size
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
                                    stringResource(R.string.friends_inbox_empty_title),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = PrimaryText
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    stringResource(R.string.friends_inbox_empty_desc),
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
                                    Text(stringResource(R.string.trade_btn), fontSize = 12.sp)
                                }
                            }
                        }
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            // Section 1: Lời đề nghị trao đổi tem nhận được
                            if (incomingCloudTrades.isNotEmpty()) {
                                item {
                                    Text(
                                        stringResource(R.string.friends_trade_incoming_title, incomingCloudTrades.size),
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = PrimaryText
                                    )
                                }
                                items(incomingCloudTrades, key = { it.id }) { trade ->
                                    Surface(
                                        shape = RoundedCornerShape(16.dp),
                                        color = SurfaceWhite,
                                        shadowElevation = 1.5.dp,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(14.dp)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    trade.senderDisplayName.ifBlank { trade.senderUsername },
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = PrimaryText
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(stringResource(R.string.trade_offer_received), fontSize = 12.sp, color = SecondaryText)
                                            }
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                val mediaUrl = if (trade.stampMediaPath.startsWith("http")) trade.stampMediaPath else "${SupabaseConfig.getSupabaseUrl(context).trimEnd('/')}/storage/v1/object/public/stamp-media/${trade.stampMediaPath}"
                                                AsyncImage(
                                                    model = MemoImageProcessor.resolveImageModel(mediaUrl),
                                                    contentDescription = trade.stampName,
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier
                                                        .size(64.dp)
                                                        .clip(RoundedCornerShape(8.dp))
                                                )
                                                Spacer(modifier = Modifier.width(10.dp))
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(trade.stampName, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = PrimaryText)
                                                    if (!trade.note.isNullOrBlank()) {
                                                        Text("“${trade.note}”", fontSize = 11.sp, color = SecondaryText)
                                                    }
                                                    Text("Bộ sưu tập bưu chính #2026", fontSize = 10.sp, color = AccentBlue)
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
                                                        coroutineScope.launch {
                                                            val res = authRepo.declineTradeRequest(trade.id)
                                                            res.fold(
                                                                onSuccess = { Toast.makeText(context, context.getString(R.string.trade_status_declined), Toast.LENGTH_SHORT).show() },
                                                                onFailure = { err -> Toast.makeText(context, err.message ?: context.getString(R.string.common_error), Toast.LENGTH_SHORT).show() }
                                                            )
                                                        }
                                                    },
                                                    shape = RoundedCornerShape(12.dp),
                                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                                ) {
                                                    Text(stringResource(R.string.friends_decline_request), fontSize = 11.sp, color = SecondaryText)
                                                }

                                                Spacer(modifier = Modifier.width(8.dp))

                                                Button(
                                                    onClick = {
                                                        coroutineScope.launch {
                                                            val res = authRepo.acceptTradeRequest(trade.id)
                                                            res.fold(
                                                                onSuccess = {
                                                                    Toast.makeText(context, context.getString(R.string.trade_accepted_toast), Toast.LENGTH_SHORT).show()
                                                                },
                                                                onFailure = { err ->
                                                                    Toast.makeText(context, err.message ?: context.getString(R.string.common_error), Toast.LENGTH_SHORT).show()
                                                                }
                                                            )
                                                        }
                                                    },
                                                    colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen),
                                                    shape = RoundedCornerShape(12.dp),
                                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                                ) {
                                                    Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(stringResource(R.string.friends_accept_request), fontSize = 11.sp)
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // Section 2: Tem đã nhận từ trao đổi (Vault đã lưu trữ)
                            if (receivedStamps.isNotEmpty()) {
                                item {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        stringResource(R.string.friends_trade_received_title, receivedStamps.size),
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = PrimaryText
                                    )
                                }
                                items(receivedStamps, key = { it.id }) { rStamp ->
                                    Surface(
                                        shape = RoundedCornerShape(16.dp),
                                        color = SurfaceWhite,
                                        shadowElevation = 1.dp,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            val mediaUrl = if (rStamp.mediaPath.startsWith("http")) rStamp.mediaPath else "${SupabaseConfig.getSupabaseUrl(context).trimEnd('/')}/storage/v1/object/public/stamp-media/${rStamp.mediaPath}"
                                            AsyncImage(
                                                model = MemoImageProcessor.resolveImageModel(mediaUrl),
                                                contentDescription = rStamp.stampName,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier
                                                    .size(56.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                            )
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(rStamp.stampName, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = PrimaryText)
                                                Text(stringResource(R.string.stamp_saved_vault_notice), fontSize = 11.sp, color = SuccessGreen)
                                            }
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = AccentRed.copy(alpha = 0.1f)
                                            ) {
                                                Text(
                                                    stringResource(R.string.stamp_owned_badge),
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = AccentRed,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // Section 3: Đề nghị trao đổi đã gửi đi
                            if (outgoingCloudTrades.isNotEmpty()) {
                                item {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        stringResource(R.string.friends_trade_outgoing_title, outgoingCloudTrades.size),
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = PrimaryText
                                    )
                                }
                                items(outgoingCloudTrades, key = { it.id }) { outTrade ->
                                    Surface(
                                        shape = RoundedCornerShape(16.dp),
                                        color = SurfaceWhite,
                                        shadowElevation = 1.dp,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            val mediaUrl = if (outTrade.stampMediaPath.startsWith("http")) outTrade.stampMediaPath else "${SupabaseConfig.getSupabaseUrl(context).trimEnd('/')}/storage/v1/object/public/stamp-media/${outTrade.stampMediaPath}"
                                            AsyncImage(
                                                model = MemoImageProcessor.resolveImageModel(mediaUrl),
                                                contentDescription = outTrade.stampName,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier
                                                    .size(48.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                            )
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text("@${outTrade.recipientUsername}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = PrimaryText)
                                                Text(outTrade.stampName, fontSize = 11.sp, color = SecondaryText)
                                                Text(stringResource(R.string.friends_waiting_response), fontSize = 10.sp, color = AccentBlue)
                                            }
                                            OutlinedButton(
                                                onClick = {
                                                    coroutineScope.launch {
                                                        val res = authRepo.cancelTradeRequest(outTrade.id)
                                                        res.fold(
                                                            onSuccess = { Toast.makeText(context, context.getString(R.string.trade_status_cancelled), Toast.LENGTH_SHORT).show() },
                                                            onFailure = { err -> Toast.makeText(context, err.message ?: context.getString(R.string.common_error), Toast.LENGTH_SHORT).show() }
                                                        )
                                                    }
                                                },
                                                shape = RoundedCornerShape(10.dp),
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                            ) {
                                                Text(stringResource(R.string.trade_cancel_button), fontSize = 10.sp, color = AccentRed)
                                            }
                                        }
                                    }
                                }
                            }

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
                                                    stringResource(R.string.chat_stamp_default_title),
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
                                                val isValidStampUrl = remember(msg.stampImageUrl) {
                                                    com.mipastudio.memostamp.domain.model.DirectMessage.isValidRemoteStampUrl(msg.stampImageUrl)
                                                }
                                                if (isValidStampUrl && !msg.stampImageUrl.isNullOrBlank()) {
                                                    AsyncImage(
                                                        model = msg.stampImageUrl,
                                                        contentDescription = msg.stampTitle,
                                                        contentScale = ContentScale.Crop,
                                                        modifier = Modifier
                                                            .size(72.dp)
                                                            .clip(RoundedCornerShape(8.dp))
                                                    )
                                                } else {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(72.dp)
                                                            .clip(RoundedCornerShape(8.dp))
                                                            .background(WarmPaperBg),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Text("📮", fontSize = 28.sp)
                                                    }
                                                }
                                                Spacer(modifier = Modifier.width(12.dp))
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        msg.stampTitle ?: stringResource(R.string.chat_stamp_default_title),
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
                                                    if (msg.text.isNotBlank() && !msg.text.startsWith("📮")) {
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
                                                    Toast.makeText(context, context.getString(R.string.trade_status_declined), Toast.LENGTH_SHORT).show()
                                                },
                                                shape = RoundedCornerShape(12.dp),
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                            ) {
                                                Icon(Icons.Outlined.Close, contentDescription = null, modifier = Modifier.size(14.dp), tint = SecondaryText)
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(stringResource(R.string.friends_decline_request), fontSize = 11.sp, color = SecondaryText)
                                            }

                                            Spacer(modifier = Modifier.width(6.dp))

                                            OutlinedButton(
                                                onClick = { onOpenChat(msg.senderId) },
                                                shape = RoundedCornerShape(12.dp),
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                            ) {
                                                Icon(Icons.AutoMirrored.Outlined.Chat, contentDescription = null, modifier = Modifier.size(14.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(stringResource(R.string.friends_tab_chat), fontSize = 11.sp)
                                            }

                                            Spacer(modifier = Modifier.width(6.dp))

                                            Button(
                                                onClick = {
                                                    coroutineScope.launch {
                                                        val validRemoteUrl = if (com.mipastudio.memostamp.domain.model.DirectMessage.isValidRemoteStampUrl(msg.stampImageUrl)) msg.stampImageUrl ?: "" else ""
                                                        val draft = StampDraft(
                                                            originalImagePath = validRemoteUrl,
                                                            renderedImagePath = validRemoteUrl,
                                                            title = msg.stampTitle?.takeIf { it.isNotBlank() } ?: msg.senderName,
                                                            location = msg.stampLocation?.takeIf { it.isNotBlank() } ?: "Việt Nam",
                                                            memoryDate = msg.createdAt,
                                                            note = msg.text
                                                        )
                                                        repo.saveStamp(draft)
                                                        dismissInboxItem(msg.id)
                                                        Toast.makeText(context, context.getString(R.string.chat_stamp_saved_toast), Toast.LENGTH_SHORT).show()
                                                    }
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen),
                                                shape = RoundedCornerShape(12.dp),
                                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                            ) {
                                                Icon(Icons.Outlined.SaveAlt, contentDescription = null, modifier = Modifier.size(14.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(stringResource(R.string.chat_stamp_save_btn), fontSize = 11.sp)
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
                                Surface(
                                    shape = CircleShape,
                                    color = AccentRedSoft,
                                    modifier = Modifier.size(64.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Outlined.Forum,
                                            contentDescription = null,
                                            tint = AccentRed,
                                            modifier = Modifier.size(28.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(14.dp))
                                Text(
                                    stringResource(R.string.chat_conversations_empty_title),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = PrimaryText
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    stringResource(R.string.chat_conversations_empty_desc),
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
                                    Text(stringResource(R.string.chat_view_friends_btn), fontSize = 12.sp)
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
                                                    lastMsg == null -> stringResource(R.string.chat_tap_to_message)
                                                    lastMsg.stampImageUrl != null -> "📮 [${lastMsg.stampTitle ?: stringResource(R.string.chat_stamp_default_title)}] ${lastMsg.text}"
                                                    else -> lastMsg.text
                                                }
                                                val displayText = if (isMe) stringResource(R.string.chat_message_prefix_you, basePreview) else basePreview

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
                                                            text = "${stringResource(R.string.chat_status_seen)} ✓✓",
                                                            fontSize = 10.sp,
                                                            color = AccentRed,
                                                            fontWeight = FontWeight.Medium
                                                        )
                                                    } else {
                                                        Text(
                                                            text = "${stringResource(R.string.chat_status_sent)} ✓",
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
                                                            text = "${conv.unreadCount}",
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
                    Text(stringResource(R.string.friends_unfriend_confirm_title, "@${friend.username}"), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = PrimaryText)
                },
                text = {
                    Text(
                        stringResource(R.string.friends_unfriend_confirm_msg, friend.displayName),
                        fontSize = 13.sp,
                        color = SecondaryText
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                val res = authRepo.unfriend(friend.userId)
                                userToUnfriend = null
                                res.fold(
                                    onSuccess = { Toast.makeText(context, context.getString(R.string.friends_invite_cancelled), Toast.LENGTH_SHORT).show() },
                                    onFailure = { err -> Toast.makeText(context, err.message ?: context.getString(R.string.common_error), Toast.LENGTH_SHORT).show() }
                                )
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentRed)
                    ) {
                        Text(stringResource(R.string.friends_unfriend_btn), fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { userToUnfriend = null }) {
                        Text(stringResource(R.string.common_close))
                    }
                }
            )
        }

        userToBlock?.let { targetUser ->
            BlockUserConfirmationDialog(
                targetUserName = targetUser.displayName,
                onConfirm = {
                    val uid = targetUser.userId
                    userToBlock = null
                    coroutineScope.launch {
                        val res = authRepo.blockUser(uid)
                        if (res.isSuccess) {
                            Toast.makeText(context, context.getString(R.string.safety_block_success), Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, res.exceptionOrNull()?.message ?: context.getString(R.string.common_error), Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                onDismiss = { userToBlock = null }
            )
        }

        userToReport?.let { targetUser ->
            ReportUserDialog(
                targetUserName = targetUser.displayName,
                onSubmit = { category, note ->
                    val uid = targetUser.userId
                    userToReport = null
                    coroutineScope.launch {
                        val res = authRepo.reportUser(
                            targetUserId = uid,
                            category = category,
                            note = note,
                            entityType = "user",
                            entityId = uid
                        )
                        if (res.isSuccess) {
                            Toast.makeText(context, context.getString(R.string.common_success), Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, res.exceptionOrNull()?.message ?: context.getString(R.string.common_error), Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                onDismiss = { userToReport = null }
            )
        }

        // Trade Detail Modal Dialog
        selectedTradeOffer?.let { offer ->
            AlertDialog(
                onDismissRequest = { selectedTradeOffer = null },
                containerColor = SurfaceWhite,
                title = {
                    Text(stringResource(R.string.friends_trade_offer_dialog_title, offer.senderName), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = PrimaryText)
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
                                        title = offer.senderName,
                                        location = offer.location,
                                        memoryDate = System.currentTimeMillis(),
                                        note = offer.note
                                    )
                                    val res = repo.saveStamp(draft)
                                    res.fold(
                                        onSuccess = { entity ->
                                            offer.status = "ACCEPTED"
                                            inboxItems = inboxItems.map { if (it.id == offer.id) offer else it }
                                            Toast.makeText(context, context.getString(R.string.chat_stamp_saved_toast), Toast.LENGTH_LONG).show()
                                            selectedTradeOffer = null
                                            onOpenStampDetail(entity.id)
                                        },
                                        onFailure = { err ->
                                            Toast.makeText(context, err.message ?: context.getString(R.string.common_error), Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen)
                        ) {
                            Text(stringResource(R.string.friends_trade_accept_btn))
                        }
                    }
                },
                dismissButton = {
                    TextButton(onClick = { selectedTradeOffer = null }) {
                        Text(stringResource(R.string.common_close))
                    }
                }
            )
        }

        // Send Trade Offer Dialog Modal
        friendToTradeWith?.let { friend ->
            val defaultTradeNote = stringResource(R.string.friends_trade_default_note)
            var selectedStampId by remember { mutableStateOf<String?>(null) }
            var tradeNote by remember { mutableStateOf(defaultTradeNote) }

            AlertDialog(
                onDismissRequest = { friendToTradeWith = null },
                containerColor = SurfaceWhite,
                title = {
                    Text(stringResource(R.string.friends_trade_send_title, friend.username), fontWeight = FontWeight.Bold, color = PrimaryText)
                },
                text = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.friends_trade_select_stamp), fontSize = 12.sp, color = SecondaryText)
                        Spacer(modifier = Modifier.height(8.dp))

                        if (myStamps.isEmpty()) {
                            Text(stringResource(R.string.friends_trade_empty_vault), fontSize = 12.sp, color = AccentRed)
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
                            label = { Text(stringResource(R.string.friends_trade_note_hint, friend.username)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val sel = myStamps.find { it.id == selectedStampId }
                            if (sel == null) {
                                Toast.makeText(context, context.getString(R.string.friends_trade_select_stamp), Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            val noteText = tradeNote.ifBlank { defaultTradeNote }

                            coroutineScope.launch {
                                val tradeRes = com.mipastudio.memostamp.data.remote.CloudSyncEngine.getInstance(context)
                                    .sendCloudTradeRequest(recipientUsername = friend.username, stamp = sel, note = noteText)
                                tradeRes.fold(
                                    onSuccess = { tradeId ->
                                        friendToTradeWith = null
                                        Toast.makeText(context, context.getString(R.string.friends_invite_sent_to, friend.username), Toast.LENGTH_SHORT).show()
                                    },
                                    onFailure = { err ->
                                        val errMsg = err.message ?: ""
                                        val displayMsg = if (errMsg.contains("RATE_LIMITED", ignoreCase = true) || errMsg.contains("429")) {
                                            "You're doing that too quickly. Please try again shortly."
                                        } else {
                                            errMsg
                                        }
                                        Toast.makeText(context, displayMsg, Toast.LENGTH_LONG).show()
                                    }
                                )
                            }
                        },
                        enabled = selectedStampId != null,
                        colors = ButtonDefaults.buttonColors(containerColor = AccentRed)
                    ) {
                        Text(stringResource(R.string.friends_trade_send_btn))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { friendToTradeWith = null }) {
                        Text(stringResource(R.string.common_cancel))
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
                            onFailure = { err ->
                                val errMsg = err.message ?: ""
                                val displayMsg = if (errMsg.contains("RATE_LIMITED", ignoreCase = true) || errMsg.contains("429")) {
                                    "You're doing that too quickly. Please try again shortly."
                                } else {
                                    errMsg.ifBlank { "Chưa thể gửi lời mời" }
                                }
                                Toast.makeText(context, displayMsg, Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                },
                onUnfriend = {
                    userToUnfriend = targetUser
                },
                onBlock = {
                    userToBlock = targetUser
                },
                onReport = {
                    userToReport = targetUser
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
                                    Text(stringResource(R.string.friends_tab_chat), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
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
                                    Text(stringResource(R.string.trade_btn), fontSize = 11.sp, fontWeight = FontWeight.Medium, color = PrimaryText)
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
                                    Icon(Icons.Outlined.PersonRemove, contentDescription = stringResource(R.string.friends_unfriend_btn), tint = SecondaryText, modifier = Modifier.size(13.dp))
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
                                    stringResource(R.string.friends_accept_request),
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
                                    stringResource(R.string.friends_decline_request),
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
                                Text(stringResource(R.string.friends_pending_request), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = AccentBlue)
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
                                Text(stringResource(R.string.friends_send_invite), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }
}

