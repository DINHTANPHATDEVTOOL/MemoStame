package com.mipastudio.memostamp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.PersonOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.mipastudio.memostamp.data.remote.supabase.SupabaseBlockedUser
import com.mipastudio.memostamp.data.repository.UserProfile
import com.mipastudio.memostamp.ui.theme.*

/**
 * Dialog to confirm blocking a user.
 * Explains clearly that friendship and pending contact will be removed and future contact prevented.
 */
@Composable
fun BlockUserConfirmationDialog(
    targetUserName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Outlined.Block,
                contentDescription = null,
                tint = AccentRed,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(
                text = "Chặn $targetUserName?",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = PrimaryText
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Khi chặn người này:",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = PrimaryText
                )
                Text(
                    text = "• Quan hệ bạn bè và các lời mời kết bạn đang chờ sẽ bị hủy ngay lập tức.\n" +
                           "• Cả hai sẽ không thể gửi tin nhắn hoặc lời mời kết bạn cho nhau.\n" +
                           "• Không thể tương tác (bình luận, thả cảm xúc) trên bài viết của nhau.",
                    fontSize = 13.sp,
                    color = SecondaryText,
                    lineHeight = 18.sp
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = AccentRed)
            ) {
                Text("Chặn người dùng", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Hủy", color = SecondaryText)
            }
        },
        containerColor = SurfaceWhite,
        shape = RoundedCornerShape(16.dp)
    )
}

data class ReportCategoryOption(
    val key: String,
    val label: String,
    val description: String
)

val REPORT_CATEGORIES = listOf(
    ReportCategoryOption("spam", "Tin rác / Spam", "Gửi tin nhắn hoặc quảng cáo phiền toái lặp lại"),
    ReportCategoryOption("harassment", "Quấy rối / Đe dọa", "Có hành vi công kích, xúc phạm hoặc đe dọa"),
    ReportCategoryOption("impersonation", "Mạo danh", "Giả vờ là người khác hoặc tổ chức"),
    ReportCategoryOption("inappropriate_content", "Nội dung không phù hợp", "Hình ảnh hoặc nội dung phản cảm"),
    ReportCategoryOption("other", "Lý do khác", "Hành vi vi phạm tiêu chuẩn cộng đồng khác")
)

/**
 * Dialog to submit an abuse / moderation report for a user.
 * Bounded categories and optional length-limited note.
 */
@Composable
fun ReportUserDialog(
    targetUserName: String,
    onSubmit: (category: String, note: String?) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedCategory by remember { mutableStateOf("spam") }
    var noteText by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Outlined.Flag,
                contentDescription = null,
                tint = AccentRed,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(
                text = "Báo cáo $targetUserName",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = PrimaryText
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Chọn lý do báo cáo:",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = PrimaryText
                )

                REPORT_CATEGORIES.forEach { category ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { selectedCategory = category.key }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (selectedCategory == category.key),
                            onClick = { selectedCategory = category.key },
                            colors = RadioButtonDefaults.colors(selectedColor = AccentRed)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text(
                                text = category.label,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = PrimaryText
                            )
                            Text(
                                text = category.description,
                                fontSize = 11.sp,
                                color = SecondaryText
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                OutlinedTextField(
                    value = noteText,
                    onValueChange = {
                        if (it.length <= 1000) noteText = it
                    },
                    label = { Text("Ghi chú thêm (tùy chọn)") },
                    placeholder = { Text("Mô tả chi tiết sự việc...") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3,
                    supportingText = {
                        Text(
                            text = "${noteText.length}/1000",
                            modifier = Modifier.fillMaxWidth(),
                            color = SecondaryText,
                            fontSize = 11.sp
                        )
                    },
                    shape = RoundedCornerShape(10.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (!isSubmitting) {
                        isSubmitting = true
                        onSubmit(selectedCategory, noteText.trim().ifEmpty { null })
                    }
                },
                enabled = !isSubmitting,
                colors = ButtonDefaults.buttonColors(containerColor = AccentRed)
            ) {
                Text("Gửi báo cáo", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSubmitting) {
                Text("Hủy", color = SecondaryText)
            }
        },
        containerColor = SurfaceWhite,
        shape = RoundedCornerShape(16.dp)
    )
}

/**
 * Dialog to manage blocked users list with an Unblock action.
 */
@Composable
fun BlockedUsersManagementDialog(
    blockedUsers: List<SupabaseBlockedUser>,
    allAccounts: List<UserProfile>,
    onUnblock: (blockedUserId: String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Outlined.PersonOff,
                contentDescription = null,
                tint = AccentRed,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(
                text = "Danh sách người dùng đã chặn",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = PrimaryText
            )
        },
        text = {
            if (blockedUsers.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Bạn chưa chặn người dùng nào.",
                        color = SecondaryText,
                        fontSize = 14.sp
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(blockedUsers, key = { it.blockedId }) { block ->
                        val profile = allAccounts.find { it.userId == block.blockedId } ?: UserProfile(
                            userId = block.blockedId,
                            displayName = "Người dùng",
                            username = block.blockedId.take(8)
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(WarmPaperBg)
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AsyncImage(
                                model = profile.avatarUrl.ifBlank { "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=100" },
                                contentDescription = profile.displayName,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .border(1.dp, UIBorder, CircleShape)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = profile.displayName,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = PrimaryText,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "@${profile.username}",
                                    fontSize = 11.sp,
                                    color = SecondaryText,
                                    maxLines = 1
                                )
                            }
                            OutlinedButton(
                                onClick = { onUnblock(block.blockedId) },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                shape = RoundedCornerShape(12.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, AccentRed)
                            ) {
                                Text("Bỏ chặn", color = AccentRed, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Đóng", color = PrimaryText, fontWeight = FontWeight.Bold)
            }
        },
        containerColor = SurfaceWhite,
        shape = RoundedCornerShape(16.dp)
    )
}
