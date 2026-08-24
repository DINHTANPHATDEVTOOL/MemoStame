package com.mipastudio.memostamp.feature.auth

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.mipastudio.memostamp.ui.theme.*
import com.mipastudio.memostamp.data.repository.UserAuthRepository
import com.mipastudio.memostamp.data.repository.UserProfile
import com.mipastudio.memostamp.feature.profile.components.SupabaseConfigDialog
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthScreen(
    canNavigateBack: Boolean = true,
    onNavigateBack: () -> Unit = {},
    onAuthSuccess: (UserProfile) -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val authRepo = remember(context) { UserAuthRepository.getInstance(context) }
    val currentUser by authRepo.currentUser.collectAsState()
    val allAccounts by authRepo.allAccounts.collectAsState()

    var isRegisterMode by remember { mutableStateOf(false) }
    var showSupabaseModal by remember { mutableStateOf(false) }
    var identifier by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    // Register extra fields
    var displayName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var bio by remember { mutableStateOf("") }
    var selectedCity by remember { mutableStateOf("Đà Lạt") }
    var selectedAvatarIndex by remember { mutableIntStateOf(0) }
    var isLoading by remember { mutableStateOf(false) }

    val presetAvatars = listOf(
        "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=300",
        "https://images.unsplash.com/photo-1539571696357-5a69c17a67c6?w=300",
        "https://images.unsplash.com/photo-1517841905240-472988babdf9?w=300",
        "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=300",
        "https://images.unsplash.com/photo-1494790108377-be9c29b29330?w=300"
    )

    val cities = listOf("Đà Lạt 🌸", "Sài Gòn 🏙️", "Hà Nội 🏛️", "Nha Trang 🌊", "Hội An 🏮")

    fun handleLogin() {
        if (identifier.isBlank()) {
            Toast.makeText(context, "Vui lòng nhập tên người dùng hoặc email", Toast.LENGTH_SHORT).show()
            return
        }
        isLoading = true
        coroutineScope.launch {
            val result = authRepo.login(identifier, password)
            isLoading = false
            result.fold(
                onSuccess = { profile ->
                    Toast.makeText(context, "Chào mừng trở lại, ${profile.displayName}! 📮", Toast.LENGTH_SHORT).show()
                    onAuthSuccess(profile)
                },
                onFailure = { err ->
                    Toast.makeText(context, err.message ?: "Đăng nhập thất bại", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }

    fun handleRegister() {
        if (identifier.isBlank()) {
            Toast.makeText(context, "Vui lòng nhập tên tài khoản (username)", Toast.LENGTH_SHORT).show()
            return
        }
        if (password.length < 4) {
            Toast.makeText(context, "Mật khẩu phải từ 4 ký tự", Toast.LENGTH_SHORT).show()
            return
        }
        isLoading = true
        coroutineScope.launch {
            val result = authRepo.register(
                displayName = displayName.ifBlank { identifier },
                username = identifier,
                email = email.ifBlank { "$identifier@memostamp.app" },
                password = password,
                city = selectedCity.split(" ").first(),
                bio = bio.ifBlank { "Người sưu tầm dấu tem bưu chính" },
                avatarUrl = presetAvatars[selectedAvatarIndex]
            )
            isLoading = false
            result.fold(
                onSuccess = { profile ->
                    Toast.makeText(context, "Đăng ký thành công tài khoản @${profile.username}! 🎉", Toast.LENGTH_SHORT).show()
                    onAuthSuccess(profile)
                },
                onFailure = { err ->
                    Toast.makeText(context, err.message ?: "Đăng ký thất bại", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (isRegisterMode) "Đăng ký tài khoản" else "Đăng nhập MemoStamp",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryText
                    )
                },
                navigationIcon = {
                    if (canNavigateBack) {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back", tint = PrimaryText)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = WarmPaperBg)
            )
        },
        containerColor = WarmPaperBg
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Brand Logo / Stamp Badge
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = SurfaceWhite,
                shadowElevation = 2.dp,
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color(0xFFEA4335).copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("📮", fontSize = 36.sp)
                }
            }

            Text(
                text = "Ký Ức & Dấu Tem Bưu Chính",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = PrimaryText
            )
            Text(
                text = "Lưu giữ hành trình và trao đổi bưu thiếp cùng bạn bè",
                fontSize = 12.sp,
                color = SecondaryText,
                modifier = Modifier.padding(top = 4.dp, bottom = 20.dp)
            )

            // Mode Selector Segmented Tabs
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = SurfaceSoft,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    TabButton(
                        text = "Đăng Nhập",
                        isSelected = !isRegisterMode,
                        modifier = Modifier.weight(1f),
                        onClick = { isRegisterMode = false }
                    )
                    TabButton(
                        text = "Đăng Ký Mới",
                        isSelected = isRegisterMode,
                        modifier = Modifier.weight(1f),
                        onClick = { isRegisterMode = true }
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Form Fields
            AnimatedVisibility(visible = isRegisterMode) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    // Avatar Selection Row
                    Text("Chọn ảnh đại diện:", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = PrimaryText)
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(presetAvatars.indices.toList()) { idx ->
                            val isSelected = selectedAvatarIndex == idx
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(CircleShape)
                                    .border(
                                        width = if (isSelected) 3.dp else 1.dp,
                                        color = if (isSelected) AccentRed else Color.Transparent,
                                        shape = CircleShape
                                    )
                                    .clickable { selectedAvatarIndex = idx }
                            ) {
                                AsyncImage(
                                    model = presetAvatars[idx],
                                    contentDescription = "Avatar $idx",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = displayName,
                        onValueChange = { displayName = it },
                        label = { Text("Tên hiển thị") },
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Outlined.Badge, contentDescription = null, tint = AccentRed) },
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = SurfaceWhite,
                            unfocusedContainerColor = SurfaceWhite,
                            focusedBorderColor = AccentRed
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("Email liên hệ") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Outlined.Email, contentDescription = null, tint = AccentRed) },
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = SurfaceWhite,
                            unfocusedContainerColor = SurfaceWhite,
                            focusedBorderColor = AccentRed
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // City Chips
                    Text("Thành phố hoạt động:", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = PrimaryText)
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(cities) { c ->
                            val isSel = selectedCity == c
                            FilterChip(
                                selected = isSel,
                                onClick = { selectedCity = c },
                                label = { Text(c, fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = AccentRed,
                                    selectedLabelColor = Color.White,
                                    containerColor = SurfaceWhite
                                )
                            )
                        }
                    }

                    OutlinedTextField(
                        value = bio,
                        onValueChange = { bio = it },
                        label = { Text("Tiểu sử") },
                        maxLines = 2,
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = SurfaceWhite,
                            unfocusedContainerColor = SurfaceWhite,
                            focusedBorderColor = AccentRed
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Common Fields (Username/ID + Password)
            OutlinedTextField(
                value = identifier,
                onValueChange = { identifier = it },
                label = { Text(if (isRegisterMode) "ID người dùng (@id kết bạn)" else "ID (@username) hoặc Email") },
                placeholder = { Text(if (isRegisterMode) "vd: phat_memostamp" else "Nhập ID hoặc email") },
                supportingText = if (isRegisterMode) {
                    { Text("ID này là duy nhất, dùng để bạn bè tìm kiếm và gửi lời mời kết bạn", fontSize = 11.sp, color = SecondaryText) }
                } else null,
                singleLine = true,
                leadingIcon = { Icon(Icons.Outlined.Person, contentDescription = null, tint = AccentRed) },
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = SurfaceWhite,
                    unfocusedContainerColor = SurfaceWhite,
                    focusedBorderColor = AccentRed
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(14.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Mật khẩu") },
                singleLine = true,
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    if (isRegisterMode) handleRegister() else handleLogin()
                }),
                leadingIcon = { Icon(Icons.Outlined.Lock, contentDescription = null, tint = AccentRed) },
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
                            contentDescription = "Toggle password"
                        )
                    }
                },
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = SurfaceWhite,
                    unfocusedContainerColor = SurfaceWhite,
                    focusedBorderColor = AccentRed
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Primary Action Button
            Button(
                onClick = {
                    if (isRegisterMode) handleRegister() else handleLogin()
                },
                enabled = !isLoading,
                colors = ButtonDefaults.buttonColors(containerColor = AccentRed),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                } else {
                    Text(
                        text = if (isRegisterMode) "Tạo Tài Khoản & Bắt Đầu" else "Đăng Nhập",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Switch Mode Prompt Button
            TextButton(
                onClick = { isRegisterMode = !isRegisterMode },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (isRegisterMode) "Đã có tài khoản? Đăng nhập ngay" else "Chưa có tài khoản? Đăng ký tài khoản mới",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AccentRed
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        if (showSupabaseModal) {
            SupabaseConfigDialog(
                onDismiss = { showSupabaseModal = false }
            )
        }
    }
}

@Composable
private fun TabButton(
    text: String,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) SurfaceWhite else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 13.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = if (isSelected) PrimaryText else SecondaryText
        )
    }
}
