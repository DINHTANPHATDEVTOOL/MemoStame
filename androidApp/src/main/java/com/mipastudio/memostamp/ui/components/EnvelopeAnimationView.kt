package com.mipastudio.memostamp.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MarkEmailRead
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mipastudio.memostamp.domain.model.Stamp
import com.mipastudio.memostamp.ui.theme.*

@Composable
fun EnvelopeModal(
    senderName: String,
    stamp: Stamp,
    onDismiss: () -> Unit,
    onCollectStamp: (Stamp) -> Unit
) {
    var isOpen by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.65f))
            .clickable { /* prevent back click */ },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp)
        ) {
            Text(
                text = if (isOpen) "🎁 MEMORY UNLOCKED!" else "💌 YOU HAVE A NEW MEMORY",
                fontSize = 18.sp,
                fontWeight = FontWeight.Black,
                color = StampCreamBg,
                letterSpacing = 1.sp
            )
            Text(
                text = "$senderName sent you a postage memory.",
                fontSize = 13.sp,
                color = StampCreamBg.copy(alpha = 0.8f),
                modifier = Modifier.padding(top = 4.dp, bottom = 20.dp)
            )

            AnimatedVisibility(
                visible = !isOpen,
                exit = fadeOut() + shrinkVertically()
            ) {
                // Closed Envelope with Wax Seal
                Box(
                    modifier = Modifier
                        .width(300.dp)
                        .height(200.dp)
                        .shadow(12.dp, RoundedCornerShape(12.dp))
                        .background(StampPaperCard, RoundedCornerShape(12.dp))
                        .border(2.dp, AirmailBlue.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                        .clickable { isOpen = true }
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // Airmail Border stripes on envelope edge
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .border(1.dp, PostalRed, RoundedCornerShape(8.dp))
                    )

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        // Wax Seal Stamp Icon
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .shadow(6.dp, CircleShape)
                                .background(PostalRed, CircleShape)
                                .border(2.dp, VintageGold, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "✉",
                                fontSize = 32.sp,
                                color = StampCreamBg
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "TAP TO OPEN ENVELOPE",
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = AirmailBlue,
                            letterSpacing = 1.5.sp
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = isOpen,
                enter = fadeIn() + expandVertically() + slideInVertically { it / 2 }
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    StampCard(
                        stamp = stamp,
                        modifier = Modifier.width(260.dp)
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(
                            onClick = onDismiss,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = StampCreamBg)
                        ) {
                            Text("CLOSE")
                        }

                        Button(
                            onClick = {
                                onCollectStamp(stamp)
                                onDismiss()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = PostalRed)
                        ) {
                            Icon(Icons.Default.MarkEmailRead, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("SAVE TO ALBUM", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
