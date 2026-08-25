package com.mipastudio.memostamp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.mipastudio.memostamp.ui.theme.*
import com.mipastudio.memostamp.data.repository.UserProfile

@Composable
fun UserProfileDialog(
    user: UserProfile,
    isFriend: Boolean,
    onDismiss: () -> Unit,
    onOpenChat: () -> Unit = {},
    onSendTrade: () -> Unit = {},
    onAddFriend: () -> Unit = {},
    onUnfriend: () -> Unit = {}
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = StampCreamBg,
        shape = RoundedCornerShape(20.dp),
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header Airmail Stripe Accent
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                        .background(PostalRed)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // User Avatar with Passport Ring
                Box(
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = user.avatarUrl.ifBlank { "https://i.pravatar.cc/150?u=${user.userId}" },
                        contentDescription = user.displayName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .border(3.dp, VintageGold, CircleShape)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Display Name & Username
                Text(
                    text = user.displayName,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = StampDarkInk,
                    fontFamily = AppDisplayFontFamily
                )
                Text(
                    text = "@${user.username}",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = PostalRed,
                    fontFamily = FontFamily.Monospace
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Location badge
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = AirmailBlue.copy(alpha = 0.1f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Outlined.LocationOn, contentDescription = null, tint = AirmailBlue, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (user.city.isNotBlank()) user.city else "Việt Nam",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = AirmailBlue
                        )
                    }
                }

                if (user.bio.isNotBlank()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "“${user.bio}”",
                        fontSize = 12.sp,
                        color = StampSubtleInk,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Passport Stamp Badge
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = WarmPaperBg,
                    border = androidx.compose.foundation.BorderStroke(1.dp, StampBorderDefault),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("STATUS", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = StampSubtleInk, fontFamily = FontFamily.Monospace)
                            Text(if (isFriend) "🤝 BẠN BÈ" else "👤 KHÁCH", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = StampDarkInk)
                        }
                        Divider(modifier = Modifier.height(24.dp).width(1.dp), color = StampBorderDefault)
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("SYNC", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = StampSubtleInk, fontFamily = FontFamily.Monospace)
                            Text("🟢 ONLINE", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = SuccessGreen)
                        }
                    }
                }
                if (isFriend) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(SurfaceWhite, shape = RoundedCornerShape(16.dp))
                            .border(1.dp, StampBorderDefault, shape = RoundedCornerShape(16.dp))
                            .padding(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Outlined.CollectionsBookmark, contentDescription = null, tint = PostalRed, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Kho Tem & Album (${user.displayName})",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = StampDarkInk
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            val sampleFriendCollections = listOf(
                                "✈️ Travel & Places" to "12 tem",
                                "☕ Coffee & Food" to "8 tem",
                                "🌿 Daily Life" to "15 tem"
                            )
                            sampleFriendCollections.forEach { (name, count) ->
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = WarmPaperBg,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(6.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(name, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = StampDarkInk, maxLines = 1)
                                        Text(count, fontSize = 9.sp, color = StampSubtleInk)
                                        Text("👥 Bạn bè", fontSize = 8.sp, color = SuccessGreen, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Action Buttons
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                onOpenChat()
                                onDismiss()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = PostalRed),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Outlined.Chat, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Nhắn tin 💬", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = {
                                onSendTrade()
                                onDismiss()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = AirmailBlue),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Outlined.CardGiftcard, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Tặng tem 📮", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    if (isFriend) {
                        OutlinedButton(
                            onClick = {
                                onUnfriend()
                                onDismiss()
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = PostalRed),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Outlined.PersonRemove, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Hủy kết bạn", fontSize = 12.sp)
                        }
                    } else {
                        Button(
                            onClick = {
                                onAddFriend()
                                onDismiss()
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Outlined.PersonAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Kết bạn 🤝", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Đóng", color = StampSubtleInk)
            }
        }
    )
}
