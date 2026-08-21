package com.mipastudio.memostamp.feature.friends

import android.widget.Toast
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.mipastudio.memostamp.core.model.Stamp
import com.mipastudio.memostamp.core.model.User
import com.mipastudio.memostamp.core.model.UserStats
import com.mipastudio.memostamp.core.repository.SampleDataRepository
import com.mipastudio.memostamp.core.theme.*
import com.mipastudio.memostamp.core.ui.EnvelopeModal
import com.mipastudio.memostamp.core.ui.StampGeometry
import com.mipastudio.memostamp.data.local.StampEntity
import com.mipastudio.memostamp.data.repository.StampRepository
import com.mipastudio.memostamp.domain.model.StampDraft
import kotlinx.coroutines.launch

data class TradeOfferItem(
    val id: String,
    val senderName: String,
    val note: String,
    val time: String,
    val imageUrl: String,
    val location: String,
    var status: String = "PENDING" // PENDING, ACCEPTED, DECLINED
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendsAndTradeScreen(
    onOpenStampDetail: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val initialFriends = remember { SampleDataRepository.sampleFriends }
    var friendsList by remember { mutableStateOf(initialFriends) }
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Inbox (Trades), 1: Friends

    var showAddFriendDialog by remember { mutableStateOf(false) }
    var selectedTradeOffer by remember { mutableStateOf<TradeOfferItem?>(null) }
    var friendToTradeWith by remember { mutableStateOf<User?>(null) }
    var myStamps by remember { mutableStateOf<List<StampEntity>>(emptyList()) }

    // Fetch user's local stamps for trading
    LaunchedEffect(Unit) {
        val repo = StampRepository.getInstance(context)
        repo.observeStamps().collect { list ->
            myStamps = list
        }
    }

    // Dynamic Inbox items state
    var inboxItems by remember {
        mutableStateOf(
            listOf(
                TradeOfferItem(
                    id = "trade_1",
                    senderName = "Minh Nguyen",
                    note = "Greeting from Da Lat! 🌲 Exchange for your sunset stamp?",
                    time = "2h ago",
                    imageUrl = "https://images.unsplash.com/photo-1506744038136-46273834b3fb?w=600",
                    location = "Da Lat, Vietnam"
                ),
                TradeOfferItem(
                    id = "trade_2",
                    senderName = "Linh Tran",
                    note = "Sea sunset memory 🌊 Want to trade stamps!",
                    time = "1d ago",
                    imageUrl = "https://images.unsplash.com/photo-1507525428034-b723cf961d3e?w=600",
                    location = "Nha Trang, Vietnam"
                )
            )
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Friends",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = AppDisplayFontFamily,
                            color = PrimaryText
                        )
                        Text(
                            text = "Memories are better with your people",
                            fontSize = 11.sp,
                            color = SecondaryText
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showAddFriendDialog = true }) {
                        Icon(Icons.Outlined.PersonAdd, contentDescription = "Add Friend", tint = PrimaryText)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = WarmPaperBg)
            )
        },
        containerColor = WarmPaperBg
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp)
        ) {
            // Filter Tabs
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val tabs = listOf("Inbox (${inboxItems.count { it.status == "PENDING" }})", "Friends (${friendsList.size})")
                tabs.forEachIndexed { idx, label ->
                    val selected = selectedTab == idx
                    Surface(
                        shape = RoundedCornerShape(22.dp),
                        color = if (selected) SurfaceDark else SurfaceWhite,
                        modifier = Modifier.clickable { selectedTab = idx }
                    ) {
                        Text(
                            text = label,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (selected) SurfaceWhite else PrimaryText,
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            when (selectedTab) {
                0 -> { // Trade Inbox
                    if (inboxItems.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No pending trade offers 📭", fontSize = 14.sp, color = SecondaryText)
                        }
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(inboxItems) { item ->
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = SurfaceWhite,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { selectedTradeOffer = item }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(if (item.status == "ACCEPTED") "🤝" else "💌", fontSize = 28.sp)
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "${item.senderName} sent a trade offer",
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = PrimaryText
                                            )
                                            Text(
                                                text = "“${item.note}”",
                                                fontSize = 12.sp,
                                                color = SecondaryText,
                                                maxLines = 1
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(item.time, fontSize = 10.sp, color = SecondaryText.copy(alpha = 0.7f))
                                        }

                                        if (item.status == "ACCEPTED") {
                                            Surface(
                                                shape = RoundedCornerShape(12.dp),
                                                color = SageGreen.copy(alpha = 0.15f)
                                            ) {
                                                Text(
                                                    text = "Traded ✓",
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = SageGreen,
                                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                                )
                                            }
                                        } else {
                                            Surface(
                                                shape = RoundedCornerShape(12.dp),
                                                color = AccentRed.copy(alpha = 0.1f)
                                            ) {
                                                Text(
                                                    text = "Review >",
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = AccentRed,
                                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                1 -> { // Friends List
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(friendsList) { friend ->
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = SurfaceWhite,
                                modifier = Modifier.fillMaxWidth()
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
                                            .size(44.dp)
                                            .clip(CircleShape)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(friend.displayName, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = PrimaryText)
                                        Text("@${friend.username}", fontSize = 12.sp, color = SecondaryText)
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = WarmPaperBg,
                                        modifier = Modifier.clickable { friendToTradeWith = friend }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Outlined.SwapHoriz, contentDescription = "Trade", tint = AccentRed, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Trade 🤝", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = PrimaryText)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Trade Detail Modal Dialog (Reviewing Incoming Offer)
        selectedTradeOffer?.let { offer ->
            AlertDialog(
                onDismissRequest = { selectedTradeOffer = null },
                containerColor = SurfaceWhite,
                title = {
                    Text("Trade Offer from ${offer.senderName} 🤝", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = PrimaryText)
                },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.7f)
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
                                    val repo = StampRepository.getInstance(context)
                                    val draft = StampDraft(
                                        originalImagePath = offer.imageUrl,
                                        renderedImagePath = offer.imageUrl,
                                        title = "Stamp from ${offer.senderName}",
                                        location = offer.location,
                                        memoryDate = System.currentTimeMillis(),
                                        note = offer.note
                                    )
                                    val res = repo.saveStamp(draft)
                                    res.fold(
                                        onSuccess = { entity ->
                                            offer.status = "ACCEPTED"
                                            inboxItems = inboxItems.map { if (it.id == offer.id) offer else it }
                                            Toast.makeText(context, "Trade Accepted! Stamp saved to your Vault 📮", Toast.LENGTH_LONG).show()
                                            selectedTradeOffer = null
                                            onOpenStampDetail(entity.id)
                                        },
                                        onFailure = { err ->
                                            Toast.makeText(context, "Failed: ${err.message}", Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = SageGreen)
                        ) {
                            Text("Accept Trade 🤝")
                        }
                    }
                },
                dismissButton = {
                    TextButton(onClick = { selectedTradeOffer = null }) {
                        Text("Close")
                    }
                }
            )
        }

        // Send Trade Offer Dialog Modal (Picker from My Stamps)
        friendToTradeWith?.let { friend ->
            var selectedStampId by remember { mutableStateOf<String?>(null) }
            var tradeNote by remember { mutableStateOf("Here is my memory stamp for you! 📮") }

            AlertDialog(
                onDismissRequest = { friendToTradeWith = null },
                containerColor = SurfaceWhite,
                title = {
                    Text("Offer Stamp to @${friend.username}", fontWeight = FontWeight.Bold, color = PrimaryText)
                },
                text = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text("Select a stamp from your Vault:", fontSize = 12.sp, color = SecondaryText)
                        Spacer(modifier = Modifier.height(8.dp))

                        if (myStamps.isEmpty()) {
                            Text("Your Vault is empty! Create a stamp first.", fontSize = 12.sp, color = AccentRed)
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
                            label = { Text("Message to @${friend.username}") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            friendToTradeWith = null
                            Toast.makeText(context, "Trade offer sent to @${friend.username}! 📮", Toast.LENGTH_SHORT).show()
                        },
                        enabled = selectedStampId != null || myStamps.isEmpty(),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentRed)
                    ) {
                        Text("Send Trade Offer ✉️")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { friendToTradeWith = null }) {
                        Text("Cancel")
                    }
                }
            )
        }

        // Add Friend Dialog Modal
        if (showAddFriendDialog) {
            var friendTag by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { showAddFriendDialog = false },
                title = { Text("Add Friend", fontWeight = FontWeight.Bold, color = PrimaryText) },
                text = {
                    OutlinedTextField(
                        value = friendTag,
                        onValueChange = { friendTag = it },
                        placeholder = { Text("Username (e.g. @phat_stamp)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (friendTag.isNotBlank()) {
                                val newFriend = User(
                                    id = "f_" + System.currentTimeMillis(),
                                    username = friendTag.removePrefix("@"),
                                    displayName = friendTag.removePrefix("@").replaceFirstChar { it.uppercase() },
                                    avatarUrl = "https://i.pravatar.cc/150?u=${friendTag.hashCode()}",
                                    bio = "Stamp Collector 📮",
                                    stats = UserStats(10, 2, 5, 3),
                                    isFriend = true
                                )
                                friendsList = friendsList + newFriend
                                showAddFriendDialog = false
                                Toast.makeText(context, "Friend request sent!", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentRed)
                    ) {
                        Text("Add Friend")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showAddFriendDialog = false }) {
                        Text("Cancel")
                    }
                },
                containerColor = SurfaceWhite
            )
        }
    }
}

