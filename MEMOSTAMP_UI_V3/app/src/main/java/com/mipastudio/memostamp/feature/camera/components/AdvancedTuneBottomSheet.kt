package com.mipastudio.memostamp.feature.camera.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mipastudio.memostamp.core.processor.CameraFilterSpec
import com.mipastudio.memostamp.core.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdvancedTuneBottomSheet(
    activeFilter: CameraFilterSpec,
    onDismissRequest: () -> Unit,
    onTuneChange: (exposure: Float, contrast: Float, saturation: Float, warmth: Float, fade: Float, grain: Float, vignette: Float) -> Unit,
    onReset: () -> Unit
) {
    var exp by remember(activeFilter) { mutableFloatStateOf(activeFilter.exposure) }
    var con by remember(activeFilter) { mutableFloatStateOf(activeFilter.contrast) }
    var sat by remember(activeFilter) { mutableFloatStateOf(activeFilter.saturation) }
    var wrm by remember(activeFilter) { mutableFloatStateOf(activeFilter.warmth) }
    var fde by remember(activeFilter) { mutableFloatStateOf(activeFilter.fade) }
    var grn by remember(activeFilter) { mutableFloatStateOf(activeFilter.grain) }
    var vig by remember(activeFilter) { mutableFloatStateOf(activeFilter.vignette) }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        containerColor = SurfaceWhite,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp, vertical = 10.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Tune filter", style = MaterialTheme.typography.headlineMedium)
                    Text(activeFilter.name, color = SecondaryText, fontSize = 12.sp)
                }
                TextButton(onClick = {
                    onReset()
                    exp = 0f; con = 0f; sat = 0f; wrm = 0f; fde = 0f; grn = 0f; vig = 0f
                }) { Text("Reset", color = AccentRed, fontWeight = FontWeight.Bold) }
            }

            Spacer(modifier = Modifier.height(12.dp))
            TuneSliderItem("Exposure", exp, -0.5f..0.5f) { exp = it; onTuneChange(exp, con, sat, wrm, fde, grn, vig) }
            TuneSliderItem("Contrast", con, -0.5f..0.5f) { con = it; onTuneChange(exp, con, sat, wrm, fde, grn, vig) }
            TuneSliderItem("Saturation", sat, -1f..1f) { sat = it; onTuneChange(exp, con, sat, wrm, fde, grn, vig) }
            TuneSliderItem("Warmth", wrm, -0.5f..0.5f) { wrm = it; onTuneChange(exp, con, sat, wrm, fde, grn, vig) }
            TuneSliderItem("Fade", fde, 0f..0.5f) { fde = it; onTuneChange(exp, con, sat, wrm, fde, grn, vig) }
            TuneSliderItem("Grain", grn, 0f..0.5f) { grn = it; onTuneChange(exp, con, sat, wrm, fde, grn, vig) }
            TuneSliderItem("Vignette", vig, 0f..0.5f) { vig = it; onTuneChange(exp, con, sat, wrm, fde, grn, vig) }
            Spacer(modifier = Modifier.height(22.dp))
        }
    }
}

@Composable
private fun TuneSliderItem(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.padding(vertical = 7.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, color = PrimaryText, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text(String.format("%.2f", value), color = SecondaryText, fontSize = 12.sp)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = SurfaceDark,
                activeTrackColor = SurfaceDark,
                inactiveTrackColor = SurfaceSoft
            )
        )
    }
}
