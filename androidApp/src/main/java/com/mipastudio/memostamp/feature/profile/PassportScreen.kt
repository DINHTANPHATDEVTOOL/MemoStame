package com.mipastudio.memostamp.feature.profile

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.mipastudio.memostamp.ui.theme.*
import com.mipastudio.memostamp.ui.components.ThemeSelectorModalSheet
import com.mipastudio.memostamp.data.remote.CloudSyncEngine
import com.mipastudio.memostamp.data.repository.UserAuthRepository
import com.mipastudio.memostamp.data.remote.supabase.SupabaseConfig
import com.mipastudio.memostamp.data.repository.StampRepository
import com.mipastudio.memostamp.feature.profile.components.SupabaseConfigDialog
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PassportScreen(
    onNavigateBack: () -> Unit = {},
    onNavigateToAuth: () -> Unit = {},
    onLogout: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val repo = remember(context) { StampRepository.getInstance(context) }
    val authRepo = remember(context) { UserAuthRepository.getInstance(context) }
    val syncEngine = remember(context) { CloudSyncEngine.getInstance(context) }

    val currentUser by authRepo.currentUser.collectAsState()
    val allAccounts by authRepo.allAccounts.collectAsState()
    val syncStatus by syncEngine.syncStatus.collectAsState()
    val roomStamps by repo.observeStamps().collectAsState(initial = emptyList())
    val friendIds by authRepo.friendIds.collectAsState()
    val roomCollections by repo.observeCollections().collectAsState(initial = emptyList())
    val displayedVisas = remember(roomStamps) {
        if (roomStamps.isNotEmpty()) {
            roomStamps.map { stamp ->
                com.mipastudio.memostamp.domain.model.PassportVisa(
                    countryOrCity = stamp.location.orEmpty().ifBlank { stamp.title },
                    date = "Stamp #${stamp.id.take(6).uppercase()}",
                    category = "✈ Travel",
                    stampCode = "#${stamp.id.take(8).uppercase()}"
                )
            }
        } else {
            listOf(
                com.mipastudio.memostamp.domain.model.PassportVisa("Đà Lạt", "12 Aug 2026", "✈ Travel", "#DL-2026-00192"),
                com.mipastudio.memostamp.domain.model.PassportVisa("Sài Gòn", "10 Aug 2026", "☕ Coffee", "#SG-2026-00088"),
                com.mipastudio.memostamp.domain.model.PassportVisa("Vũng Tàu", "02 Aug 2026", "🏖 Beach", "#VT-2026-00304"),
                com.mipastudio.memostamp.domain.model.PassportVisa("Đại Học", "15 Jul 2026", "🎓 Graduation", "#GRAD-2026-0001")
            )
        }
    }

    var showThemeSelector by remember { mutableStateOf(false) }
    var showQrModal by remember { mutableStateOf(false) }
    var showSettingsModal by remember { mutableStateOf(false) }
    var showSupabaseModal by remember { mutableStateOf(false) }
    var showEditProfileModal by remember { mutableStateOf(false) }
    var showCoverOptionsModal by remember { mutableStateOf(false) }
    var showAvatarOptionsModal by remember { mutableStateOf(false) }

    val coverGalleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                val localPath = authRepo.saveMediaUriToLocal(uri, "cover") ?: uri.toString()
                authRepo.updateCoverPhoto(localPath)
                showCoverOptionsModal = false
                Toast.makeText(context, "Đã cập nhật hình nền hồ sơ từ thư viện ảnh! 🖼️✨", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val avatarGalleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                val localPath = authRepo.saveMediaUriToLocal(uri, "avatar") ?: uri.toString()
                authRepo.updateAvatarPhoto(localPath)
                showAvatarOptionsModal = false
                Toast.makeText(context, "Đã cập nhật ảnh đại diện từ thư viện ảnh! 📸✨", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val presetCovers = remember {
        listOf(
            "Rừng thông Đà Lạt" to "https://images.unsplash.com/photo-1506744038136-46273834b3fb?w=1200",
            "Bưu điện Sài Gòn" to "https://images.unsplash.com/photo-1583417319070-4a69db38a482?w=1200",
            "Hội An Đèn Lồng" to "https://images.unsplash.com/photo-1559592413-7cec4d0cae2b?w=1200",
            "Hoàng hôn Hạ Long" to "https://images.unsplash.com/photo-1528127269322-539801943592?w=1200",
            "Giấy Kraft Cổ Điển" to "https://images.unsplash.com/photo-1586075010923-2dd4570fb338?w=1200",
            "Hà Nội Thu Vàng" to "https://images.unsplash.com/photo-1509316975850-ff9c5deb0cd9?w=1200"
        )
    }

    val presetAvatars = remember {
        listOf(
            "Retro Girl" to "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=300",
            "Vintage Boy" to "https://images.unsplash.com/photo-1539571696357-5a69c17a67c6?w=300",
            "Traveler" to "https://images.unsplash.com/photo-1517841905240-472988babdf9?w=300",
            "Classic Gent" to "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=300",
            "Art Stamp" to "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?w=300"
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Hồ Sơ & Passport", fontSize = 18.sp, fontFamily = AppDisplayFontFamily, fontWeight = FontWeight.Bold, color = PrimaryText)
                        Text("Hộ chiếu lưu giữ dấu tem ký ức", fontSize = 11.sp, color = SecondaryText)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back", tint = PrimaryText)
                    }
                },
                actions = {
                    IconButton(onClick = { showThemeSelector = true }) {
                        Icon(Icons.Outlined.Palette, contentDescription = "Chọn giao diện", tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = { showQrModal = true }) {
                        Icon(Icons.Outlined.QrCode, contentDescription = "QR Code", tint = MaterialTheme.colorScheme.onBackground)
                    }
                    IconButton(onClick = { showSettingsModal = true }) {
                        Icon(Icons.Outlined.Settings, contentDescription = "Settings", tint = MaterialTheme.colorScheme.onBackground)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Profile Cover Banner & Overlapping Avatar Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            ) {
                // Cover Background Image
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    shadowElevation = 3.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        AsyncImage(
                            model = currentUser.coverUrl.ifBlank { "https://images.unsplash.com/photo-1506744038136-46273834b3fb?w=1200" },
                            contentDescription = "Cover photo",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        // Gradient Overlay for readability
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            Color.Transparent,
                                            Color.Black.copy(alpha = 0.45f)
                                        )
                                    )
                                )
                        )

                        // Quick Button to change Cover Photo from phone gallery or presets
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color.Black.copy(alpha = 0.6f),
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(10.dp)
                                .clickable { showCoverOptionsModal = true }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Icon(
                                    Icons.Outlined.PhotoCamera,
                                    contentDescription = "Đổi hình nền",
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Đổi hình nền",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }

                        // Badge showing postal seal watermark
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color.White.copy(alpha = 0.85f),
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text("📮 Passport Cover", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = PrimaryText)
                            }
                        }
                    }
                }

                // Overlapping Avatar positioned at bottom center
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .offset(y = 44.dp)
                ) {
                    Box(contentAlignment = Alignment.BottomEnd) {
                        AsyncImage(
                            model = currentUser.avatarUrl.ifBlank { "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=300" },
                            contentDescription = currentUser.displayName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(88.dp)
                                .clip(CircleShape)
                                .border(3.dp, Color.White, CircleShape)
                                .clickable { showAvatarOptionsModal = true }
                        )
                        // Camera badge for Avatar
                        Surface(
                            shape = CircleShape,
                            color = AccentRed,
                            shadowElevation = 3.dp,
                            modifier = Modifier
                                .size(28.dp)
                                .clickable { showAvatarOptionsModal = true }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Outlined.CameraAlt, contentDescription = "Đổi Avatar", tint = Color.White, modifier = Modifier.size(15.dp))
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(48.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.padding(top = 2.dp)
            ) {
                Text(
                    text = currentUser.displayName.ifBlank { "Người dùng MemoStamp" },
                    fontSize = 20.sp,
                    fontFamily = AppDisplayFontFamily,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryText
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    Icons.Outlined.Verified,
                    contentDescription = "Verified",
                    tint = AccentBlue,
                    modifier = Modifier.size(18.dp)
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(top = 2.dp)
            ) {
                Text("@${currentUser.username.ifBlank { "user" }}", fontSize = 13.sp, color = SecondaryText)
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = AccentBlueSoft
                ) {
                    Text(
                        text = "📍 ${currentUser.city.ifBlank { "Đà Lạt" }}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = AccentBlue,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            val userBio = currentUser.bio.orEmpty()
            if (userBio.isNotBlank()) {
                Text(
                    text = userBio,
                    fontSize = 12.sp,
                    color = SecondaryText,
                    modifier = Modifier.padding(top = 6.dp, start = 20.dp, end = 20.dp),
                    lineHeight = 16.sp
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Primary Action Bar: Edit Profile, Share QR Code, Cloud Connection
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { showEditProfileModal = true },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentRed),
                    modifier = Modifier.weight(1f).height(42.dp)
                ) {
                    Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Chỉnh sửa hồ sơ", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }

                OutlinedButton(
                    onClick = { showQrModal = true },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f).height(42.dp)
                ) {
                    Icon(Icons.Outlined.QrCode, contentDescription = null, modifier = Modifier.size(16.dp), tint = PrimaryText)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Chia sẻ Passport", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = PrimaryText)
                }

                IconButton(
                    onClick = { showSettingsModal = true },
                    modifier = Modifier
                        .size(42.dp)
                        .background(WarmPaperBg, RoundedCornerShape(14.dp))
                        .border(1.dp, UIBorder, RoundedCornerShape(14.dp))
                ) {
                    Icon(Icons.Outlined.Settings, contentDescription = "Cài đặt", tint = PrimaryText, modifier = Modifier.size(20.dp))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Theme Style Picker Card
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showThemeSelector = true }
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Outlined.Palette, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Phong cách: ${LocalAppThemeStyle.current.emoji} ${LocalAppThemeStyle.current.title}",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Chạm để chọn 1 trong 5 phong cách tem thư & giao diện",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Stats Overview Card (Instagram Style)
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = SurfaceWhite,
                shadowElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${roomStamps.size}", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = AccentRed)
                        Text("Tem dán", fontSize = 11.sp, color = SecondaryText)
                    }
                    Box(modifier = Modifier.width(1.dp).height(32.dp).background(UIBorder))
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${friendIds.size}", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = AccentBlue)
                        Text("Bạn bè", fontSize = 11.sp, color = SecondaryText)
                    }
                    Box(modifier = Modifier.width(1.dp).height(32.dp).background(UIBorder))
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${roomStamps.count { it.note.isNotBlank() }}", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = PrimaryText)
                        Text("Ký ức", fontSize = 11.sp, color = SecondaryText)
                    }
                    Box(modifier = Modifier.width(1.dp).height(32.dp).background(UIBorder))
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${roomCollections.size}", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFFD97706))
                        Text("Bộ sưu tập", fontSize = 11.sp, color = SecondaryText)
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Section: Passport Visas & Stamps
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Dấu Thị Thực & Ký Ức Đã Đóng",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = AppDisplayFontFamily,
                    color = PrimaryText
                )
                Text(
                    text = "${displayedVisas.size} dấu",
                    fontSize = 12.sp,
                    color = SecondaryText
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Visas Card List
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = SurfaceWhite,
                shadowElevation = 1.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    displayedVisas.forEachIndexed { index, visa ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(visa.countryOrCity, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = PrimaryText)
                                Text(visa.date, fontSize = 12.sp, color = SecondaryText)
                            }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = WarmPaperBg
                            ) {
                                Text(visa.category, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = AccentRed, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                            }
                        }
                        if (index < displayedVisas.size - 1) {
                            HorizontalDivider(color = UIBorder, thickness = 0.5.dp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }

        // Change Cover Wallpaper Dialog (Gallery Picker + Presets)
        if (showCoverOptionsModal) {
            AlertDialog(
                onDismissRequest = { showCoverOptionsModal = false },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Wallpaper, contentDescription = null, tint = AccentRed, modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Đổi hình nền hồ sơ", fontWeight = FontWeight.Bold, color = PrimaryText, fontSize = 17.sp)
                    }
                },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            text = "Tùy chỉnh ảnh bìa Passport để thể hiện phong cách du hành của bạn:",
                            fontSize = 12.sp,
                            color = SecondaryText
                        )

                        // Primary Button: Open Device Photo Gallery
                        Button(
                            onClick = {
                                coverGalleryLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AccentRed),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                        ) {
                            Icon(Icons.Outlined.PhotoLibrary, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Chọn ảnh từ thư viện điện thoại", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }

                        HorizontalDivider(color = UIBorder)

                        Text("Hoặc chọn hình nền bưu chính vintage:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = PrimaryText)

                        // Preset Cover Grid / Column
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            presetCovers.forEach { (name, url) ->
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = WarmPaperBg,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            coroutineScope.launch {
                                                authRepo.updateCoverPhoto(url)
                                                showCoverOptionsModal = false
                                                Toast.makeText(context, "Đã đổi sang hình nền: $name! ✨", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(6.dp)
                                    ) {
                                        AsyncImage(
                                            model = url,
                                            contentDescription = name,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .size(width = 54.dp, height = 36.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(name, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = PrimaryText)
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showCoverOptionsModal = false }) {
                        Text("Đóng")
                    }
                },
                containerColor = SurfaceWhite
            )
        }

        // Change Avatar Dialog (Gallery Picker + Presets)
        if (showAvatarOptionsModal) {
            AlertDialog(
                onDismissRequest = { showAvatarOptionsModal = false },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.AccountCircle, contentDescription = null, tint = AccentRed, modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Đổi ảnh đại diện", fontWeight = FontWeight.Bold, color = PrimaryText, fontSize = 17.sp)
                    }
                },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Button: Open Device Photo Gallery for Avatar
                        Button(
                            onClick = {
                                avatarGalleryLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AccentRed),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                        ) {
                            Icon(Icons.Outlined.PhotoLibrary, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Chọn ảnh từ thư viện điện thoại", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }

                        HorizontalDivider(color = UIBorder)

                        Text("Gợi ý ảnh đại diện phong cách vintage:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = PrimaryText)

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            presetAvatars.forEach { (name, url) ->
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable {
                                            coroutineScope.launch {
                                                authRepo.updateAvatarPhoto(url)
                                                showAvatarOptionsModal = false
                                                Toast.makeText(context, "Đã cập nhật ảnh đại diện! ✨", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                ) {
                                    AsyncImage(
                                        model = url,
                                        contentDescription = name,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(42.dp)
                                            .clip(CircleShape)
                                            .border(1.5.dp, AccentRed, CircleShape)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(name, fontSize = 9.sp, color = SecondaryText, maxLines = 1)
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showAvatarOptionsModal = false }) {
                        Text("Đóng")
                    }
                },
                containerColor = SurfaceWhite
            )
        }

        // Edit Profile Dialog
        if (showEditProfileModal) {
            var editName by remember { mutableStateOf(currentUser.displayName) }
            var editBio by remember { mutableStateOf(currentUser.bio) }
            var editCity by remember { mutableStateOf(currentUser.city) }

            AlertDialog(
                onDismissRequest = { showEditProfileModal = false },
                title = { Text("Chỉnh sửa hồ sơ", fontWeight = FontWeight.Bold, color = PrimaryText) },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Quick Media Customization Row
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedButton(
                                onClick = {
                                    showEditProfileModal = false
                                    showCoverOptionsModal = true
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Outlined.Wallpaper, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Đổi hình nền", fontSize = 11.sp)
                            }
                            OutlinedButton(
                                onClick = {
                                    showEditProfileModal = false
                                    showAvatarOptionsModal = true
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Outlined.AccountBox, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Đổi avatar", fontSize = 11.sp)
                            }
                        }

                        OutlinedTextField(
                            value = editName,
                            onValueChange = { editName = it },
                            label = { Text("Tên hiển thị") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = editCity,
                            onValueChange = { editCity = it },
                            label = { Text("Thành phố") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = editBio,
                            onValueChange = { editBio = it },
                            label = { Text("Tiểu sử (Bio)") },
                            maxLines = 3,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                authRepo.updateProfile(editName, editBio, null, editCity)
                                showEditProfileModal = false
                                Toast.makeText(context, "Đã cập nhật hồ sơ! ✨", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentRed)
                    ) {
                        Text("Lưu")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showEditProfileModal = false }) { Text("Hủy") }
                },
                containerColor = SurfaceWhite
            )
        }

        // QR Modal
        if (showQrModal) {
            AlertDialog(
                onDismissRequest = { showQrModal = false },
                title = { Text("Mã QR Passport của bạn", fontWeight = FontWeight.Bold, color = PrimaryText) },
                text = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        Text("Quét mã để kết nối và trao đổi tem bưu chính", fontSize = 13.sp, color = SecondaryText)
                        Spacer(modifier = Modifier.height(16.dp))
                        Box(
                            modifier = Modifier
                                .size(160.dp)
                                .background(WarmPaperBg, RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("📮", fontSize = 32.sp)
                                Spacer(modifier = Modifier.height(6.dp))
                                Text("@${currentUser.username}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = PrimaryText)
                                Text("ID: ${currentUser.userId.take(8)}", fontSize = 10.sp, color = SecondaryText)
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showQrModal = false }) { Text("Đóng") }
                },
                containerColor = SurfaceWhite
            )
        }

        // Settings Modal
        if (showSettingsModal) {
            AlertDialog(
                onDismissRequest = { showSettingsModal = false },
                title = { Text("Cài đặt tài khoản", fontWeight = FontWeight.Bold, color = PrimaryText) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AsyncImage(
                                model = currentUser.avatarUrl,
                                contentDescription = currentUser.displayName,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(currentUser.displayName, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = PrimaryText)
                                Text("@${currentUser.username} • ${currentUser.email}", fontSize = 11.sp, color = SecondaryText)
                            }
                        }
                        
                        Text("📍 Vị trí: ${currentUser.city}", fontSize = 12.sp, color = SecondaryText)
                        
                        HorizontalDivider(color = UIBorder)

                        Button(
                            onClick = {
                                authRepo.logout()
                                showSettingsModal = false
                                Toast.makeText(context, "Đã đăng xuất tài khoản", Toast.LENGTH_SHORT).show()
                                onLogout()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = AccentRedSoft),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Đăng xuất tài khoản", color = AccentRed, fontWeight = FontWeight.Bold)
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showSettingsModal = false }) { Text("Đóng") }
                },
                containerColor = SurfaceWhite
            )
        }

        if (showThemeSelector) {
            ThemeSelectorModalSheet(
                onDismiss = { showThemeSelector = false }
            )
        }

        if (showSupabaseModal) {
            SupabaseConfigDialog(
                onDismiss = { showSupabaseModal = false }
            )
        }
    }
}
