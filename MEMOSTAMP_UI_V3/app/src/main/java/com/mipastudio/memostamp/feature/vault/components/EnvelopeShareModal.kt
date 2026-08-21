package com.mipastudio.memostamp.feature.vault.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MarkEmailRead
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import coil.compose.rememberAsyncImagePainter
import com.mipastudio.memostamp.core.theme.*
import com.mipastudio.memostamp.core.ui.StampGeometry
import com.mipastudio.memostamp.data.local.StampEntity
import java.io.File

@Composable
fun EnvelopeShareModal(
    stamp: StampEntity,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var isOpen by remember { mutableStateOf(false) }

    val flapRotation by animateFloatAsState(
        targetValue = if (isOpen) 180f else 0f,
        animationSpec = tween(durationMillis = 600),
        label = "flapRotation"
    )

    val stampOffsetPx by animateFloatAsState(
        targetValue = if (isOpen) -60f else 0f,
        animationSpec = tween(durationMillis = 700),
        label = "stampOffset"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = {},
        containerColor = StampCreamBg,
        shape = RoundedCornerShape(20.dp),
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.MarkEmailRead, contentDescription = null, tint = PostalRed)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "AIRMAIL ENVELOPE",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = StampDarkInk
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = StampSubtleInk)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Interactive Envelope Box
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp)
                        .shadow(10.dp, RoundedCornerShape(12.dp))
                        .background(Color(0xFFFBF8F2), RoundedCornerShape(12.dp))
                        .border(1.5.dp, StampBorderDefault, RoundedCornerShape(12.dp))
                        .clickable { isOpen = !isOpen },
                    contentAlignment = Alignment.Center
                ) {
                    // Airmail Border Stripe Top & Bottom
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .align(Alignment.TopCenter)
                            .graphicsLayer { rotationX = flapRotation }
                            .background(PostalRed)
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .align(Alignment.BottomCenter)
                            .background(AirmailBlue)
                    )

                    // Envelope Pocket Back & Stamp Inside
                    Box(
                        modifier = Modifier
                            .offset(y = stampOffsetPx.dp)
                            .width(170.dp)
                            .aspectRatio(StampGeometry.ASPECT_RATIO),
                        contentAlignment = Alignment.Center
                    ) {
                        val imageModel = remember(stamp.stampImagePath) {
                            val file = File(stamp.stampImagePath)
                            if (file.exists() && file.length() > 0) file else stamp.stampImagePath
                        }
                        Image(
                            painter = rememberAsyncImagePainter(model = imageModel),
                            contentDescription = stamp.title,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    // Wax Seal Center Button (When Envelope is Closed)
                    if (!isOpen) {
                        Surface(
                            onClick = { isOpen = true },
                            shape = CircleShape,
                            color = PostalRed,
                            shadowElevation = 8.dp,
                            modifier = Modifier.size(64.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "💌\nSEAL",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color.White,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.align(Alignment.Center)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = if (isOpen) "Tap Envelope to Close 📮" else "Tap Wax Seal to Open Envelope ✉️",
                    fontSize = 12.sp,
                    color = AirmailBlue,
                    fontWeight = FontWeight.Medium
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Social Share Action Button
                Button(
                    onClick = {
                        shareStampEnvelope(context, stamp)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PostalRed),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "SHARE MEMORY ENVELOPE",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
        }
    )
}

private fun shareStampEnvelope(context: Context, stamp: StampEntity) {
    try {
        val file = File(stamp.stampImagePath)
        if (!file.exists()) {
            Toast.makeText(context, "Stamp image file not found", Toast.LENGTH_SHORT).show()
            return
        }

        val contentUri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/*"
            putExtra(Intent.EXTRA_STREAM, contentUri)
            putExtra(
                Intent.EXTRA_TEXT,
                "📮 ${stamp.title}\n📍 ${stamp.location ?: "MemoStamp Memory"}\n“${stamp.note}”\n\nShared via MemoStamp ✨"
            )
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        context.startActivity(Intent.createChooser(shareIntent, "Share Memory Stamp via..."))
    } catch (e: Exception) {
        e.printStackTrace()
        Toast.makeText(context, "Unable to share stamp: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
    }
}
