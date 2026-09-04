package com.mipastudio.memostamp.feature.chat

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Send
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.mipastudio.memostamp.core.processor.MemoImageProcessor
import com.mipastudio.memostamp.ui.theme.*
import com.mipastudio.memostamp.data.local.StampEntity
import com.mipastudio.memostamp.data.repository.UserAuthRepository
import com.mipastudio.memostamp.data.repository.UserProfile
import com.mipastudio.memostamp.data.repository.ChatRepository
import com.mipastudio.memostamp.data.repository.StampRepository
import com.mipastudio.memostamp.domain.model.DirectMessage
import com.mipastudio.memostamp.domain.model.StampDraft
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    recipientUserId: String,
    onNavigateBack: () -> Unit,
    onOpenStampDetail: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val authRepo = remember(context) { UserAuthRepository.getInstance(context) }
    val chatRepo = remember(context) { ChatRepository.getInstance(context) }
    val stampRepo = remember(context) { StampRepository.getInstance(context) }

    val currentUser by authRepo.currentUser.collectAsState()
    val allAccounts by authRepo.allAccounts.collectAsState()
    val allMessages by chatRepo.messages.collectAsState()

    val recipient = remember(allAccounts, recipientUserId) {
        allAccounts.find { it.userId == recipientUserId } ?: UserProfile(
            userId = recipientUserId,
            username = "friend",
            displayName = "Người bạn bưu chính",
            avatarUrl = ""
        )
    }

    val conversationMessages = remember(allMessages, recipient.userId, currentUser.userId) {
        chatRepo.getMessagesBetween(currentUser.userId, recipient.userId)
    }

    var textInput by remember { mutableStateOf("") }
    var showStampPicker by remember { mutableStateOf(false) }
    var selectedStampToSend by remember { mutableStateOf<StampEntity?>(null) }
    val myStamps by stampRepo.observeStamps().collectAsState(initial = emptyList())

    var viewingStampMessage by remember { mutableStateOf<DirectMessage?>(null) }

    var loadError by remember(recipient.userId) { mutableStateOf<String?>(null) }
    var initialLoadFinished by remember(recipient.userId) { mutableStateOf(false) }
    var isRetryingLoad by remember(recipient.userId) { mutableStateOf(false) }

    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    fun performLoad(isRetry: Boolean = false) {
        if (isRetry && isRetryingLoad) return
        if (isRetry) {
            isRetryingLoad = true
        }
        coroutineScope.launch {
            val res = chatRepo.loadConversation(recipient.userId)
            initialLoadFinished = true
            isRetryingLoad = false
            if (res.isFailure) {
                loadError = "Không thể tải cuộc trò chuyện. Kiểm tra kết nối và thử lại."
            } else {
                loadError = null
            }
        }
    }

    DisposableEffect(recipient.userId) {
        chatRepo.activeChattingUserId = recipient.userId
        performLoad(isRetry = false)
        coroutineScope.launch {
            chatRepo.markAsReadCloud(recipient.userId)
        }
        onDispose {
            chatRepo.activeChattingUserId = null
        }
    }

    LaunchedEffect(conversationMessages) {
        if (conversationMessages.any { it.senderId == recipient.userId && !it.isRead }) {
            chatRepo.markAsReadCloud(recipient.userId)
        }
    }

    LaunchedEffect(conversationMessages.size) {
        if (conversationMessages.isNotEmpty()) {
            listState.animateScrollToItem(conversationMessages.size - 1)
        }
    }

    fun handleSendMessage() {
        val clean = textInput.trim()
        if (clean.isBlank() && selectedStampToSend == null) return

        val stamp = selectedStampToSend
        if (stamp != null && !com.mipastudio.memostamp.domain.model.isValidRemoteStampUrl(stamp.stampImagePath)) {
            Toast.makeText(context, "Ảnh tem chưa được đồng bộ để chia sẻ giữa các thiết bị.", Toast.LENGTH_SHORT).show()
        }

        coroutineScope.launch {
            val res = chatRepo.sendMessageCloud(
                recipient = recipient,
                text = clean,
                stampId = stamp?.id,
                stampTitle = stamp?.title,
                stampImageUrl = stamp?.stampImagePath,
                stampLocation = stamp?.location
            )
            if (res.isSuccess) {
                textInput = ""
                selectedStampToSend = null
                focusManager.clearFocus()
            } else {
                Toast.makeText(context, "Gửi tin nhắn thất bại: ${res.exceptionOrNull()?.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Scaffold(
        containerColor = WarmPaperBg,
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        AsyncImage(
                            model = recipient.avatarUrl.ifBlank { "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=100" },
                            contentDescription = recipient.displayName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .border(1.dp, AccentRed.copy(alpha = 0.4f), CircleShape)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = recipient.displayName,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = PrimaryText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "@${recipient.username}",
                                fontSize = 11.sp,
                                color = AccentRed,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Trở về", tint = PrimaryText)
                    }
                },
                actions = {
                    IconButton(onClick = { showStampPicker = true }) {
                        Icon(Icons.Outlined.LocalPostOffice, contentDescription = "Gửi tem", tint = AccentRed)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceWhite)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
        ) {
            // Messages List
            if (!initialLoadFinished && conversationMessages.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = AccentRed,
                        modifier = Modifier.size(36.dp),
                        strokeWidth = 3.dp
                    )
                }
            } else if (loadError != null && conversationMessages.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
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
                                    imageVector = Icons.Outlined.CloudOff,
                                    contentDescription = null,
                                    tint = AccentRed,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "Không thể tải cuộc trò chuyện",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryText,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Kiểm tra kết nối và thử lại.",
                            fontSize = 12.sp,
                            color = SecondaryText,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { performLoad(isRetry = true) },
                            enabled = !isRetryingLoad,
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AccentRed)
                        ) {
                            if (isRetryingLoad) {
                                CircularProgressIndicator(
                                    color = Color.White,
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                            } else {
                                Icon(Icons.Outlined.Refresh, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                            }
                            Text("Thử lại", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            } else if (conversationMessages.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
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
                                    imageVector = Icons.Outlined.MarkEmailUnread,
                                    contentDescription = null,
                                    tint = AccentRed,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "Bắt đầu cuộc trò chuyện với ${recipient.displayName}",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryText,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Gửi tin nhắn hoặc đính kèm một con tem bưu chính để kết nối hoài niệm! 📮",
                            fontSize = 12.sp,
                            color = SecondaryText,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedButton(
                            onClick = { showStampPicker = true },
                            shape = RoundedCornerShape(16.dp),
                            border = ButtonDefaults.outlinedButtonBorder.copy(brush = androidx.compose.ui.graphics.SolidColor(AccentRed))
                        ) {
                            Icon(Icons.Outlined.CardGiftcard, contentDescription = null, tint = AccentRed, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Chọn con tem gửi ngay", color = AccentRed, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    if (loadError != null) {
                        Surface(
                            color = AccentRedSoft,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Warning,
                                    contentDescription = null,
                                    tint = AccentRed,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Đang hiển thị tin nhắn đã lưu. Chưa thể đồng bộ.",
                                    fontSize = 11.sp,
                                    color = PrimaryText,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(
                                    onClick = { performLoad(isRetry = true) },
                                    enabled = !isRetryingLoad,
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    if (isRetryingLoad) {
                                        CircularProgressIndicator(
                                            color = AccentRed,
                                            modifier = Modifier.size(12.dp),
                                            strokeWidth = 1.5.dp
                                        )
                                    } else {
                                        Text("Thử lại", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AccentRed)
                                    }
                                }
                            }
                        }
                    }
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(conversationMessages, key = { it.id }) { msg ->
                            val isMe = msg.senderId == currentUser.userId
                            ChatMessageBubble(
                                message = msg,
                                isMe = isMe,
                                onStampClick = {
                                    viewingStampMessage = msg
                                }
                            )
                        }
                    }
                }
            }

            // Selected Stamp Preview Bar before sending
            AnimatedVisibility(visible = selectedStampToSend != null) {
                selectedStampToSend?.let { stamp ->
                    Surface(
                        color = SurfaceDark,
                        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AsyncImage(
                                model = stamp.stampImagePath,
                                contentDescription = stamp.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(RoundedCornerShape(8.dp))
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Đính kèm tem thư:", fontSize = 10.sp, color = AccentRedSoft)
                                Text(
                                    stamp.title,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            IconButton(onClick = { selectedStampToSend = null }) {
                                Icon(Icons.Outlined.Close, contentDescription = "Hủy", tint = Color.White, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }

            // Bottom Chat Input Bar
            Surface(
                color = SurfaceWhite,
                shadowElevation = 8.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Attach Stamp Icon Button
                    IconButton(
                        onClick = { showStampPicker = true },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.CardGiftcard,
                            contentDescription = "Gửi tem",
                            tint = if (selectedStampToSend != null) AccentRed else SecondaryText,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    // Text Input Field
                    OutlinedTextField(
                        value = textInput,
                        onValueChange = { textInput = it },
                        placeholder = {
                            Text(
                                if (selectedStampToSend != null) "Viết lời nhắn kèm con tem..." else "Nhập tin nhắn...",
                                fontSize = 13.sp,
                                color = TertiaryText
                            )
                        },
                        shape = RoundedCornerShape(24.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AccentRed.copy(alpha = 0.6f),
                            unfocusedBorderColor = SurfaceDark.copy(alpha = 0.1f),
                            focusedContainerColor = WarmPaperBg,
                            unfocusedContainerColor = WarmPaperBg,
                            cursorColor = AccentRed
                        ),
                        maxLines = 4,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { handleSendMessage() }),
                        modifier = Modifier.weight(1f)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    // Send Button
                    val canSend = textInput.isNotBlank() || selectedStampToSend != null
                    IconButton(
                        onClick = { handleSendMessage() },
                        enabled = canSend,
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(if (canSend) AccentRed else Color.LightGray.copy(alpha = 0.4f))
                    ) {
                        Icon(
                            Icons.AutoMirrored.Outlined.Send,
                            contentDescription = "Gửi",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }

    // Stamp Picker BottomSheet Modal
    if (showStampPicker) {
        ModalBottomSheet(
            onDismissRequest = { showStampPicker = false },
            containerColor = SurfaceWhite
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp)
                    .padding(bottom = 30.dp)
            ) {
                Text(
                    text = "Chọn tem để đính kèm tin nhắn 📮",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryText
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Gửi dấu ấn bưu chính của bạn cho ${recipient.displayName}",
                    fontSize = 12.sp,
                    color = SecondaryText
                )
                Spacer(modifier = Modifier.height(14.dp))

                if (myStamps.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 30.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Kho tem của bạn đang trống. Hãy chụp và tạo tem trước nhé!", fontSize = 12.sp, color = SecondaryText)
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.heightIn(max = 360.dp)
                    ) {
                        items(myStamps, key = { it.id }) { stamp ->
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = WarmPaperBg,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedStampToSend = stamp
                                        showStampPicker = false
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AsyncImage(
                                        model = stamp.stampImagePath,
                                        contentDescription = stamp.title,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(54.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(stamp.title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = PrimaryText)
                                        Text(stamp.location ?: "Bưu cục MemoStamp", fontSize = 11.sp, color = SecondaryText)
                                    }
                                    Button(
                                        onClick = {
                                            selectedStampToSend = stamp
                                            showStampPicker = false
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = AccentRed),
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Text("Chọn", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Stamp Detail & Save-to-Vault Modal Dialog
    viewingStampMessage?.let { msg ->
        AlertDialog(
            onDismissRequest = { viewingStampMessage = null },
            containerColor = SurfaceWhite,
            title = {
                Text(
                    text = msg.stampTitle ?: "Dấu ấn tem kỷ niệm 📮",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = PrimaryText
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = WarmPaperBg,
                        shadowElevation = 2.dp,
                        modifier = Modifier
                            .fillMaxWidth(0.85f)
                            .aspectRatio(0.8f)
                    ) {
                        if (com.mipastudio.memostamp.domain.model.isValidRemoteStampUrl(msg.stampImageUrl)) {
                            AsyncImage(
                                model = msg.stampImageUrl,
                                contentDescription = msg.stampTitle,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(12.dp))
                            )
                        } else {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp)
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = Icons.Outlined.LocalPostOffice,
                                        contentDescription = null,
                                        tint = AccentRed,
                                        modifier = Modifier.size(48.dp)
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        text = msg.stampTitle ?: "Tem kỷ niệm",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = PrimaryText,
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "Ảnh tem chưa được đồng bộ để chia sẻ giữa các thiết bị.",
                                        fontSize = 11.sp,
                                        color = SecondaryText,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "📍 ${msg.stampLocation ?: "Việt Nam"}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AccentBlue
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Người gửi: ${msg.senderName}",
                        fontSize = 11.sp,
                        color = SecondaryText
                    )
                    if (msg.text.isNotBlank() && !msg.text.startsWith("📮 Đã gửi con tem")) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = WarmPaperBg,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "“${msg.text}”",
                                fontSize = 12.sp,
                                color = PrimaryText,
                                modifier = Modifier.padding(8.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            },
            confirmButton = {
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
                            stampRepo.saveStamp(draft)
                            Toast.makeText(context, "Đã lưu con tem vào Kho của bạn thành công! 📮", Toast.LENGTH_SHORT).show()
                            viewingStampMessage = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen)
                ) {
                    Icon(Icons.Outlined.SaveAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Lưu vào Kho tem")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewingStampMessage = null }) {
                    Text("Đóng")
                }
            }
        )
    }
}

@Composable
private fun ChatMessageBubble(
    message: DirectMessage,
    isMe: Boolean,
    onStampClick: () -> Unit
) {
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val formattedTime = remember(message.createdAt) { timeFormat.format(Date(message.createdAt)) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        if (!isMe) {
            AsyncImage(
                model = message.senderAvatar.ifBlank { "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=100" },
                contentDescription = message.senderName,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
            )
            Spacer(modifier = Modifier.width(6.dp))
        }

        Column(
            horizontalAlignment = if (isMe) Alignment.End else Alignment.Start,
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(
                    topStart = 16.dp,
                    topEnd = 16.dp,
                    bottomStart = if (isMe) 16.dp else 4.dp,
                    bottomEnd = if (isMe) 4.dp else 16.dp
                ),
                color = if (isMe) AccentRed else SurfaceWhite,
                shadowElevation = 1.dp
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    // Attached Stamp Card inside Chat Bubble
                    val hasStamp = !message.stampId.isNullOrBlank() || !message.stampTitle.isNullOrBlank() || !message.stampImageUrl.isNullOrBlank()
                    if (hasStamp) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isMe) Color.Black.copy(alpha = 0.2f) else WarmPaperBg,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 6.dp)
                                .clickable {
                                    onStampClick()
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (com.mipastudio.memostamp.domain.model.isValidRemoteStampUrl(message.stampImageUrl)) {
                                    AsyncImage(
                                        model = message.stampImageUrl,
                                        contentDescription = message.stampTitle,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                    )
                                } else {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(if (isMe) Color.White.copy(alpha = 0.2f) else AccentRedSoft)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.LocalPostOffice,
                                            contentDescription = null,
                                            tint = if (isMe) Color.White else AccentRed,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        message.stampTitle ?: "Tem kỷ niệm",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isMe) Color.White else PrimaryText,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        message.stampLocation ?: "Việt Nam",
                                        fontSize = 10.sp,
                                        color = if (isMe) Color.White.copy(alpha = 0.8f) else SecondaryText,
                                        maxLines = 1
                                    )
                                    Text(
                                        "Chạm để xem chi tiết ↗",
                                        fontSize = 9.sp,
                                        color = if (isMe) AccentRedSoft else AccentRed,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    if (message.text.isNotBlank()) {
                        Text(
                            text = message.text,
                            fontSize = 13.sp,
                            color = if (isMe) Color.White else PrimaryText
                        )
                    }

                    Row(
                        modifier = Modifier
                            .align(Alignment.End)
                            .padding(top = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = formattedTime,
                            fontSize = 9.sp,
                            color = if (isMe) Color.White.copy(alpha = 0.7f) else TertiaryText
                        )
                        if (isMe) {
                            if (message.isRead) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        text = "Đã xem",
                                        fontSize = 8.5.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Color.White.copy(alpha = 0.85f)
                                    )
                                    Text(
                                        text = "✓✓",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = AccentBlueSoft
                                    )
                                }
                            } else {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        text = "Đã gửi",
                                        fontSize = 8.5.sp,
                                        color = Color.White.copy(alpha = 0.65f)
                                    )
                                    Text(
                                        text = "✓",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White.copy(alpha = 0.65f)
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
