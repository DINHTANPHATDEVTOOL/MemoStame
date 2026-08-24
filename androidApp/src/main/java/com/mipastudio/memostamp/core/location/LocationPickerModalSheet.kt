package com.mipastudio.memostamp.core.location

import android.Manifest
import android.location.Location
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mipastudio.memostamp.ui.theme.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationPickerModalSheet(
    initialLocation: String = "",
    onDismiss: () -> Unit,
    onLocationSelected: (locationName: String, suggestedStampTitle: String?, story: GroundedPostmarkStory?) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var hasLocationPermission by remember { mutableStateOf(LocationHelper.hasLocationPermission(context)) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedCityChip by remember { mutableStateOf("Đà Lạt") }
    var selectedCategoryFilter by remember { mutableStateOf("ALL") }

    var isSearching by remember { mutableStateOf(false) }
    var isLocatingGps by remember { mutableStateOf(false) }
    var placesList by remember { mutableStateOf<List<GroundedPlace>>(emptyList()) }
    var currentGpsLocation by remember { mutableStateOf<Location?>(null) }
    var gpsAddressName by remember { mutableStateOf<String?>(null) }
    var searchJob by remember { mutableStateOf<Job?>(null) }

    val cities = listOf("Đà Lạt 🌸", "Sài Gòn 🏙️", "Hà Nội 🏛️", "Hội An 🏮", "Đà Nẵng 🌊", "Phú Quốc 🏖️", "Sapa 🏔️", "Huế 🏯")
    val categories = listOf(
        "ALL" to "Tất cả",
        "LANDMARK" to "Biểu tượng 🏛️",
        "CAFE" to "Cà phê hoài niệm ☕",
        "NATURE" to "Thiên nhiên 🌲",
        "HERITAGE" to "Di tích bưu chính 📮",
        "RESTAURANT" to "Ẩm thực phố 🍜"
    )

    // Execute ranking search with Google Maps Algorithm & AI Grounding
    fun performSearch(query: String, city: String? = null, isDeepAiSearch: Boolean = false) {
        searchJob?.cancel()
        searchJob = coroutineScope.launch {
            if (isDeepAiSearch) {
                isSearching = true
            }
            val effectiveQuery = query.trim()
            val results = GeminiMapsGroundingService.searchPlacesWithMaps(
                context = context,
                query = effectiveQuery,
                currentCity = city ?: selectedCityChip.split(" ").first(),
                userLat = currentGpsLocation?.latitude,
                userLng = currentGpsLocation?.longitude,
                categoryFilter = selectedCategoryFilter
            )
            placesList = results
            isSearching = false
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        hasLocationPermission = granted
        if (granted) {
            isLocatingGps = true
            LocationHelper.fetchCurrentLocationCoordinates(context) { loc ->
                currentGpsLocation = loc
                LocationHelper.fetchCurrentLocation(context) { addressStr ->
                    isLocatingGps = false
                    gpsAddressName = addressStr
                    performSearch("", city = null, isDeepAiSearch = true) // 2km radius discovery around coordinates
                }
            }
        }
    }

    // Auto debounce when typing (Google Maps autocomplete experience: "pho", "starb", "nguy"...)
    LaunchedEffect(searchQuery) {
        if (searchQuery.isNotBlank()) {
            delay(150) // Fast 150ms debounce for local prefix/fuzzy + distance ranking
            performSearch(searchQuery, city = null, isDeepAiSearch = false)
        } else if (currentGpsLocation != null) {
            performSearch("", city = null, isDeepAiSearch = false)
        }
    }

    LaunchedEffect(Unit) {
        if (!hasLocationPermission) {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        } else {
            LocationHelper.fetchCurrentLocationCoordinates(context) { loc ->
                currentGpsLocation = loc
                if (loc != null) {
                    LocationHelper.fetchCurrentLocation(context) { addressStr ->
                        gpsAddressName = addressStr
                        performSearch("", city = null, isDeepAiSearch = true)
                    }
                }
            }
        }
        performSearch("", selectedCityChip, isDeepAiSearch = true)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = SurfaceWhite,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .padding(horizontal = 20.dp)
        ) {
            // Header with Google Maps AI Grounding badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Chọn Địa Điểm & Dấu Tem",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryText
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFF4285F4).copy(alpha = 0.12f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.AutoAwesome,
                                    contentDescription = "Maps AI",
                                    tint = Color(0xFF1A73E8),
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Google Maps Grounded AI",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1A73E8)
                                )
                            }
                        }
                    }
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Outlined.Close, contentDescription = "Close", tint = SecondaryText)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Search Bar Row with real Google Maps search button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Gõ tên, quán cafe, món ngon...", fontSize = 13.sp) },
                    leadingIcon = {
                        Icon(Icons.Outlined.Search, contentDescription = "Search", tint = AccentRed, modifier = Modifier.size(20.dp))
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = {
                                searchQuery = ""
                                performSearch("", selectedCityChip, isDeepAiSearch = false)
                            }) {
                                Icon(Icons.Outlined.Clear, contentDescription = "Clear", modifier = Modifier.size(18.dp))
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = { performSearch(searchQuery, isDeepAiSearch = true) }
                    ),
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentRed,
                        unfocusedBorderColor = Color.Gray.copy(alpha = 0.25f),
                        focusedContainerColor = WarmPaperBg,
                        unfocusedContainerColor = WarmPaperBg
                    ),
                    modifier = Modifier.weight(1f)
                )

                Button(
                    onClick = { performSearch(searchQuery, isDeepAiSearch = true) },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentRed),
                    shape = RoundedCornerShape(16.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 14.dp)
                ) {
                    Icon(Icons.Outlined.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Tìm", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Current GPS Quick Action with 2km Radius
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFFEA4335).copy(alpha = 0.08f))
                    .clickable {
                        if (!hasLocationPermission) {
                            locationPermissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                            )
                        } else {
                            isLocatingGps = true
                            LocationHelper.fetchCurrentLocationCoordinates(context) { loc ->
                                currentGpsLocation = loc
                                LocationHelper.fetchCurrentLocation(context) { addressStr ->
                                    isLocatingGps = false
                                    gpsAddressName = addressStr
                                    searchQuery = ""
                                    performSearch("", city = null, isDeepAiSearch = true) // 2km radius lookup around GPS
                                }
                            }
                        }
                    }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isLocatingGps) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = AccentRed)
                } else {
                    Icon(Icons.Outlined.MyLocation, contentDescription = "GPS", tint = AccentRed, modifier = Modifier.size(18.dp))
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (gpsAddressName != null) "Vị trí GPS: $gpsAddressName" else "Định vị vị trí hiện tại của bạn",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AccentRed
                    )
                    Text(
                        text = if (currentGpsLocation != null) "Gợi ý địa điểm Google Maps trong bán kính 2km quanh bạn" else "Khám phá địa điểm tem trong bán kính 2km xung quanh bạn",
                        fontSize = 11.sp,
                        color = SecondaryText
                    )
                }
                Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = AccentRed, modifier = Modifier.size(18.dp))
            }

            Spacer(modifier = Modifier.height(12.dp))

            // City chips
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(cities) { city ->
                    val isSelected = selectedCityChip == city
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            selectedCityChip = city
                            searchQuery = ""
                            performSearch("", city, isDeepAiSearch = true)
                        },
                        label = { Text(city, fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AccentRed,
                            selectedLabelColor = Color.White,
                            containerColor = WarmPaperBg
                        ),
                        shape = RoundedCornerShape(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Category filter chips
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(categories) { (key, label) ->
                    val isSelected = selectedCategoryFilter == key
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) AccentGold.copy(alpha = 0.15f) else Color.Transparent)
                            .border(
                                width = 1.dp,
                                color = if (isSelected) AccentGold else Color.Gray.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable {
                                selectedCategoryFilter = key
                                performSearch(searchQuery, selectedCityChip, isDeepAiSearch = false)
                            }
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = label,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) Color(0xFF8D6E1A) else SecondaryText
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Results List
            if (isSearching) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = AccentRed, strokeWidth = 2.5.dp)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Đang tra cứu dữ liệu Google Maps...",
                            fontSize = 13.sp,
                            color = SecondaryText
                        )
                    }
                }
            } else {
                val filteredPlaces = placesList.filter {
                    if (selectedCategoryFilter == "ALL") true else it.category == selectedCategoryFilter
                }

                if (filteredPlaces.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Outlined.LocationOff, contentDescription = null, tint = SecondaryText, modifier = Modifier.size(36.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Không tìm thấy địa điểm phù hợp", color = SecondaryText, fontSize = 13.sp)
                            if (searchQuery.isNotBlank()) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Button(
                                    onClick = {
                                        onLocationSelected(searchQuery, searchQuery, null)
                                        onDismiss()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = AccentRed),
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Text("Dùng tên: \"$searchQuery\"")
                                }
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 24.dp)
                    ) {
                        items(filteredPlaces) { place ->
                            GroundedPlaceCard(
                                place = place,
                                isSelected = initialLocation.contains(place.name),
                                onSelect = {
                                    coroutineScope.launch {
                                        val story = GeminiMapsGroundingService.generateGroundedPostmarkNote(place.name, place.address)
                                        onLocationSelected(place.name, place.stampTitleSuggestion, story)
                                        onDismiss()
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GroundedPlaceCard(
    place: GroundedPlace,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    val categoryIcon = when (place.category) {
        "CAFE" -> Icons.Outlined.Coffee
        "NATURE" -> Icons.Outlined.Park
        "HERITAGE" -> Icons.Outlined.AccountBalance
        "STREET" -> Icons.Outlined.Storefront
        "RESTAURANT" -> Icons.Outlined.Restaurant
        else -> Icons.Outlined.Place
    }

    val categoryColor = when (place.category) {
        "CAFE" -> Color(0xFF8D6E63)
        "NATURE" -> Color(0xFF2E7D32)
        "HERITAGE" -> Color(0xFFC2185B)
        "STREET" -> Color(0xFFE65100)
        "RESTAURANT" -> Color(0xFFD84315)
        else -> Color(0xFF1976D2)
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (isSelected) AccentRedSoft else SurfaceWhite,
        shadowElevation = if (isSelected) 0.dp else 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (isSelected) 1.5.dp else 1.dp,
                color = if (isSelected) AccentRed else Color.Gray.copy(alpha = 0.15f),
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onSelect)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Category Icon Badge
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(categoryColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = categoryIcon,
                    contentDescription = place.category,
                    tint = categoryColor,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = place.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = PrimaryText,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (place.distanceFormatted != null) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFFE8F0FE)
                            ) {
                                Text(
                                    text = "📍 ${place.distanceFormatted}",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1967D2),
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                )
                            }
                        }
                        if (place.rating != null) {
                            Text(
                                text = place.rating,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFF29900)
                            )
                        }
                    }
                }

                if (place.address.isNotBlank()) {
                    Text(
                        text = place.address,
                        fontSize = 12.sp,
                        color = SecondaryText,
                        maxLines = 1
                    )
                }

                if (place.description.isNotBlank()) {
                    Text(
                        text = place.description,
                        fontSize = 11.sp,
                        color = TertiaryText,
                        lineHeight = 15.sp,
                        modifier = Modifier.padding(top = 3.dp)
                    )
                }

                if (place.stampTitleSuggestion.isNotBlank()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Icon(
                            Icons.Outlined.Loyalty,
                            contentDescription = "Tag",
                            tint = AccentGold,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Gợi ý tem: ${place.stampTitleSuggestion}",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF8D6E1A)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Icon(
                imageVector = if (isSelected) Icons.Outlined.CheckCircle else Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = if (isSelected) AccentRed else TertiaryText,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
