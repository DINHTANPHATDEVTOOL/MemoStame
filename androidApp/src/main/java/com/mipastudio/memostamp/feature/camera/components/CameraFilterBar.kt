package com.mipastudio.memostamp.feature.camera.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mipastudio.memostamp.core.processor.CameraFilterSpec
import com.mipastudio.memostamp.core.processor.FilterPresets
import com.mipastudio.memostamp.ui.theme.AccentRed

@Composable
fun CameraFilterBar(
    activeFilter: CameraFilterSpec,
    onSelectFilter: (CameraFilterSpec) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        items(FilterPresets.ALL, key = { it.id }) { preset ->
            val selected = activeFilter.id == preset.id
            Column(
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable { onSelectFilter(preset) }
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = preset.name,
                    color = if (selected) Color.White else Color.White.copy(alpha = 0.68f),
                    fontSize = 11.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .size(width = if (selected) 16.dp else 3.dp, height = 3.dp)
                        .clip(CircleShape)
                        .background(if (selected) AccentRed else Color.Transparent)
                )
            }
        }
    }
}
