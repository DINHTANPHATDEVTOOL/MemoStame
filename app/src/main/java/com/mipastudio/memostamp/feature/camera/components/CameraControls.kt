package com.mipastudio.memostamp.feature.camera.components

import androidx.camera.core.ImageCapture
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.DynamicFeed
import androidx.compose.material.icons.outlined.FlashAuto
import androidx.compose.material.icons.outlined.FlashOff
import androidx.compose.material.icons.outlined.FlashOn
import androidx.compose.material.icons.outlined.FlipCameraAndroid
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mipastudio.memostamp.core.theme.AccentRed
import com.mipastudio.memostamp.feature.camera.CaptureState
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun CameraControls(
    captureState: CaptureState,
    flashMode: Int,
    zoomRatio: Float,
    minZoomRatio: Float = 1.0f,
    maxZoomRatio: Float = 5.0f,
    hasUltraWideCamera: Boolean = false,
    onZoomSelected: (Float) -> Unit,
    onCaptureClick: () -> Unit,
    onFlashClick: () -> Unit,
    onLensToggleClick: () -> Unit,
    onGalleryClick: () -> Unit,
    onVaultClick: () -> Unit,
    onCloseClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    filterBarContent: (@Composable () -> Unit)? = null
) {
    val isEnabled = captureState == CaptureState.READY
    val zoomPresets = remember(minZoomRatio, maxZoomRatio, hasUltraWideCamera) {
        buildList {
            if (hasUltraWideCamera || minZoomRatio < 1f) {
                add(((minZoomRatio * 10f).roundToInt() / 10f).coerceAtLeast(0.5f))
            }
            if (1f in minZoomRatio..maxZoomRatio || hasUltraWideCamera) add(1f)
            listOf(2f, 3f, 5f).forEach { candidate ->
                if (candidate > minZoomRatio && candidate <= maxZoomRatio) add(candidate)
            }
            if (isEmpty()) add(1f)
        }.distinct().sorted()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(top = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.35f))
                    .clickable { onCloseClick() }
                    .padding(horizontal = 12.dp, vertical = 7.dp)
            ) {
                Icon(
                    Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "Quay lại Bảng tin",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "Bảng tin",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GlassCameraButton(
                    onClick = onLensToggleClick,
                    enabled = isEnabled
                ) {
                    Icon(
                        Icons.Outlined.FlipCameraAndroid,
                        contentDescription = "Switch camera",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }

                GlassCameraButton(
                    onClick = onFlashClick,
                    enabled = isEnabled
                ) {
                    val icon = when (flashMode) {
                        ImageCapture.FLASH_MODE_ON -> Icons.Outlined.FlashOn
                        ImageCapture.FLASH_MODE_AUTO -> Icons.Outlined.FlashAuto
                        else -> Icons.Outlined.FlashOff
                    }
                    Icon(
                        icon,
                        contentDescription = "Flash",
                        tint = if (flashMode == ImageCapture.FLASH_MODE_ON) Color.White else Color.White.copy(alpha = 0.82f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.28f))
                    .padding(horizontal = 5.dp, vertical = 3.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                zoomPresets.forEach { z ->
                    val selected = abs(zoomRatio - z) < 0.18f
                    val label = if (z % 1f == 0f) "${z.toInt()}×" else "${(z * 10).roundToInt() / 10f}×"
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(if (selected) Color.White else Color.Transparent)
                            .clickable(enabled = isEnabled) { onZoomSelected(z) }
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            label,
                            color = if (selected) Color.Black else Color.White.copy(alpha = 0.78f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            filterBarContent?.invoke()
            if (filterBarContent != null) Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                GlassCameraButton(onClick = onGalleryClick, enabled = isEnabled, size = 48.dp) {
                    Icon(
                        Icons.Outlined.PhotoLibrary,
                        contentDescription = "Gallery",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }

                val shutterScale by animateFloatAsState(
                    targetValue = if (captureState == CaptureState.PRESSING) 0.88f else 1f,
                    animationSpec = spring(dampingRatio = 0.7f, stiffness = 620f),
                    label = "shutter"
                )
                Box(
                    modifier = Modifier
                        .scale(shutterScale)
                        .size(78.dp)
                        .shadow(8.dp, CircleShape)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.96f))
                        .clickable(enabled = isEnabled, onClick = onCaptureClick)
                        .padding(5.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .border(2.dp, Color.Black.copy(alpha = 0.12f), CircleShape)
                            .background(Color.White),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(13.dp)
                                .clip(CircleShape)
                                .background(AccentRed)
                        )
                    }
                }

                GlassCameraButton(onClick = onVaultClick, enabled = isEnabled, size = 48.dp) {
                    Icon(
                        Icons.Outlined.Collections,
                        contentDescription = "Vault",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun GlassCameraButton(
    onClick: () -> Unit,
    enabled: Boolean,
    size: androidx.compose.ui.unit.Dp = 40.dp,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.30f))
            .border(1.dp, Color.White.copy(alpha = 0.12f), CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
        content = content
    )
}
