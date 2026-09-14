package com.mipastudio.memostamp.feature.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.mipastudio.memostamp.R
import com.mipastudio.memostamp.data.repository.UserAuthRepository
import com.mipastudio.memostamp.domain.model.CommentSubmissionError
import com.mipastudio.memostamp.domain.model.FeedComment
import com.mipastudio.memostamp.domain.model.FeedPost
import com.mipastudio.memostamp.domain.model.FeedReply
import com.mipastudio.memostamp.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostDetailScreen(
    postId: String,
    onNavigateBack: () -> Unit,
    onReplyWithStamp: (String) -> Unit,
    viewModel: PostDetailViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val authRepo = remember(context) { UserAuthRepository.getInstance(context) }
    val currentUser by authRepo.currentUser.collectAsState()
    var showMenu by remember { mutableStateOf(false) }
    var commentText by remember { mutableStateOf("") }
    var activeLightboxReply by remember { mutableStateOf<FeedReply?>(null) }

    LaunchedEffect(postId) {
        viewModel.loadPost(context, postId)
    }

    // Safe Draft Clearing: Clear only if draft in field still matches the submitted snapshot!
    LaunchedEffect(uiState.commentSuccessToken) {
        val token = uiState.commentSuccessToken
        if (token != null) {
            if (commentText.trim() == token) {
                commentText = ""
            }
            viewModel.consumeCommentSuccess()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.post_detail_title), fontFamily = StampSerifFontFamily) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                            tint = PrimaryText
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = stringResource(R.string.post_detail_options),
                            tint = PrimaryText
                        )
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.post_detail_remove_feed)) },
                            onClick = {
                                showMenu = false
                                viewModel.removePostFromFeed(context, postId, onNavigateBack)
                            }
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    stringResource(R.string.post_detail_delete_memory),
                                    color = MaterialTheme.colorScheme.error
                                )
                            },
                            onClick = {
                                showMenu = false
                                uiState.post?.stampId?.let { stampId ->
                                    viewModel.deleteMemory(context, stampId, onNavigateBack)
                                }
                            }
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = WarmPaperBg)
            )
        },
        containerColor = WarmPaperBg
    ) { padding ->
        val post = uiState.post
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = AccentRed)
            }
        } else if (post == null || uiState.postUnavailable) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.post_detail_unavailable),
                    style = MaterialTheme.typography.bodyLarge,
                    color = SecondaryText
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { viewModel.loadPost(context, postId) },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentRed),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text(stringResource(R.string.common_retry), color = Color.White)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                // Author Header
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        AsyncImage(
                            model = post.authorAvatar,
                            contentDescription = post.authorName,
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(SurfaceSoft),
                            contentScale = ContentScale.Crop
                        )
                        Column {
                            Text(
                                text = post.authorName,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = PrimaryText
                                )
                            )
                            if (!post.location.isNullOrBlank()) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    com.mipastudio.memostamp.ui.icon.MemoStampIcon(
                                        iconKey = "location",
                                        contentDescription = null,
                                        modifier = Modifier.size(12.dp),
                                        tint = AccentBlue
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = post.location,
                                        style = MaterialTheme.typography.bodySmall.copy(color = AccentBlue, fontSize = 11.sp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Hero Stamp
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                            .height(310.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(SurfaceSoft.copy(alpha = 0.4f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .width(240.dp)
                                .height(300.dp)
                                .shadow(8.dp, RoundedCornerShape(14.dp))
                                .background(Color.White)
                        ) {
                            AsyncImage(
                                model = post.stampUrl,
                                contentDescription = post.stampTitle,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }
                }

                // Caption
                if (!post.caption.isNullOrBlank()) {
                    item {
                        Text(
                            text = post.caption,
                            style = MaterialTheme.typography.bodyLarge.copy(color = PrimaryText, lineHeight = 22.sp),
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp)
                        )
                    }
                }

                // Interaction Bar: ♡ Like | 💬 Comment | 📮 Stamp Reply
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 8.dp)
                            .background(SurfaceSoft.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TextButton(onClick = { viewModel.toggleLike(context, post.id) }) {
                            Icon(
                                imageVector = if (post.isLikedByMe) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = stringResource(R.string.post_detail_like),
                                tint = if (post.isLikedByMe) AccentRed else PrimaryText
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "${post.reactionCount}",
                                color = if (post.isLikedByMe) AccentRed else PrimaryText,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.ChatBubbleOutline,
                                contentDescription = stringResource(R.string.post_detail_comments),
                                tint = PrimaryText,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = "${post.commentCount}", color = PrimaryText, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = { onReplyWithStamp(post.id) },
                            colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                            shape = RoundedCornerShape(20.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                com.mipastudio.memostamp.ui.icon.MemoStampIcon(
                                    iconKey = "reply_stamp",
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = Color.White
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = stringResource(R.string.post_detail_reply_with_stamp),
                                    fontSize = 12.sp,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }

                // Stamp Reply Chain Section
                if (post.replies.isNotEmpty()) {
                    item {
                        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                            Text(
                                text = stringResource(R.string.post_detail_reply_chain_title, post.replyCount),
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = StampSerifFontFamily,
                                    color = PrimaryText
                                )
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                        }
                    }

                    items(post.replies, key = { it.id }) { reply ->
                        PostDetailReplyItem(
                            reply = reply,
                            onClick = { activeLightboxReply = reply }
                        )
                    }
                }

                // Comments Section
                item {
                    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
                        HorizontalDivider(color = UIBorder)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = stringResource(R.string.post_comments_title, post.commentCount),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontFamily = StampSerifFontFamily,
                                color = PrimaryText
                            )
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }

                items(post.comments, key = { it.id }) { comment ->
                    PostDetailCommentItem(
                        comment = comment,
                        isOwnComment = comment.authorId == currentUser.userId,
                        onDeleteComment = { viewModel.deleteComment(context, post.id, comment.id) }
                    )
                }

                // Comment Submission Error Banner
                if (uiState.commentSubmissionError != null) {
                    item {
                        val errorRes = when (uiState.commentSubmissionError) {
                            CommentSubmissionError.UNAUTHENTICATED -> R.string.comment_error_unauthenticated
                            CommentSubmissionError.FORBIDDEN -> R.string.comment_error_forbidden
                            CommentSubmissionError.RATE_LIMITED -> R.string.comment_error_rate_limited
                            CommentSubmissionError.NETWORK_UNAVAILABLE -> R.string.comment_error_network
                            CommentSubmissionError.SERVER_FAILURE -> R.string.comment_error_server
                            else -> R.string.comment_error_server
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 4.dp)
                                .background(AccentRed.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = stringResource(errorRes),
                                style = MaterialTheme.typography.bodySmall,
                                color = AccentRed,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = { viewModel.clearCommentError() },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = stringResource(R.string.common_close),
                                    tint = AccentRed,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                // Comment Input Row
                item {
                    val trimmed = commentText.trim()
                    val isValid = trimmed.isNotEmpty() && trimmed.length <= 500

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = commentText,
                            onValueChange = { commentText = it },
                            placeholder = { Text(stringResource(R.string.post_add_comment_placeholder), fontSize = 13.sp) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(24.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AccentRed,
                                unfocusedBorderColor = UIBorder,
                                focusedContainerColor = Color.White,
                                unfocusedContainerColor = Color.White
                            )
                        )
                        IconButton(
                            onClick = {
                                if (isValid && !uiState.isSubmittingComment) {
                                    viewModel.addComment(context, post.id, trimmed)
                                }
                            },
                            enabled = isValid && !uiState.isSubmittingComment,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(if (isValid && !uiState.isSubmittingComment) AccentRed else SurfaceSoft)
                        ) {
                            if (uiState.isSubmittingComment) {
                                CircularProgressIndicator(
                                    color = Color.White,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(18.dp)
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Send,
                                    contentDescription = stringResource(R.string.post_send_comment),
                                    tint = if (isValid) Color.White else SecondaryText,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Mini Reply Lightbox
        if (activeLightboxReply != null) {
            ReplyLightboxDialog(
                reply = activeLightboxReply!!,
                onDismiss = { activeLightboxReply = null }
            )
        }
    }
}

@Composable
fun PostDetailReplyItem(
    reply: FeedReply,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(width = 64.dp, height = 74.dp)
                    .shadow(2.dp, RoundedCornerShape(8.dp))
                    .background(WarmPaperBg)
                    .padding(4.dp),
                contentAlignment = Alignment.Center
            ) {
                if (!reply.replyStampUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = reply.replyStampUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    com.mipastudio.memostamp.ui.icon.MemoStampIcon(
                        iconKey = "stamp",
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = AccentRed
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = reply.authorName,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = PrimaryText)
                )
                if (!reply.note.isNullOrBlank()) {
                    Text(
                        text = "“${reply.note}”",
                        style = MaterialTheme.typography.bodySmall.copy(color = PrimaryText.copy(alpha = 0.85f))
                    )
                }
            }
        }
    }
}

@Composable
fun PostDetailCommentItem(
    comment: FeedComment,
    isOwnComment: Boolean,
    onDeleteComment: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        AsyncImage(
            model = comment.authorAvatar,
            contentDescription = comment.authorName,
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(SurfaceSoft),
            contentScale = ContentScale.Crop
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = comment.authorName,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = PrimaryText, fontSize = 13.sp)
            )
            Text(
                text = comment.content,
                style = MaterialTheme.typography.bodySmall.copy(color = PrimaryText, fontSize = 13.sp)
            )
        }

        if (isOwnComment) {
            IconButton(
                onClick = onDeleteComment,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = stringResource(R.string.post_detail_delete_comment),
                    tint = SecondaryText,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
fun ReplyLightboxDialog(
    reply: FeedReply,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                AsyncImage(
                    model = reply.replyStampUrl,
                    contentDescription = stringResource(R.string.post_detail_stamp_reply_badge),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Fit
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.post_detail_reply_by, reply.authorName),
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = PrimaryText
                )
                if (!reply.note.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = reply.note,
                        fontSize = 14.sp,
                        color = SecondaryText
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = AccentRed),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(stringResource(R.string.common_close), color = Color.White)
                }
            }
        }
    }
}

