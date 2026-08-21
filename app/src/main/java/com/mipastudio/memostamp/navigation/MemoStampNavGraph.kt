package com.mipastudio.memostamp.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.mipastudio.memostamp.core.notification.InAppNotificationBannerHost
import com.mipastudio.memostamp.core.theme.*
import com.mipastudio.memostamp.data.remote.UserAuthRepository
import com.mipastudio.memostamp.domain.model.StampDraft
import com.mipastudio.memostamp.feature.auth.AuthScreen
import com.mipastudio.memostamp.feature.camera.CameraScreen
import com.mipastudio.memostamp.feature.chat.ChatScreen
import com.mipastudio.memostamp.feature.collection.CollectionScreen
import com.mipastudio.memostamp.feature.editor.StampEditorScreen
import com.mipastudio.memostamp.feature.friends.FriendsAndTradeScreen
import com.mipastudio.memostamp.feature.home.HomeScreen
import com.mipastudio.memostamp.feature.memorynote.MemoryNoteScreen
import com.mipastudio.memostamp.feature.profile.PassportScreen
import com.mipastudio.memostamp.feature.vault.StampDetailScreen
import com.mipastudio.memostamp.feature.vault.StampVaultScreen

sealed class NavItem(
    val route: String,
    val title: String,
    val icon: ImageVector
) {
    object Home : NavItem("home", "Trang chủ", Icons.Outlined.Home)
    object Vault : NavItem("vault", "Bộ tem", Icons.Outlined.CollectionsBookmark)
    object Camera : NavItem("camera", "Tạo tem", Icons.Outlined.PhotoCamera)
    object Friends : NavItem("friends", "Bạn bè", Icons.Outlined.People)
    object Profile : NavItem("passport", "Hồ sơ", Icons.Outlined.Person)
}

@Composable
fun MemoStampNavGraph(
    navController: NavHostController = rememberNavController(),
    targetScreen: Pair<String?, String?>? = null,
    onTargetScreenHandled: () -> Unit = {}
) {
    val context = LocalContext.current
    val authRepo = remember(context) { UserAuthRepository.getInstance(context) }
    val isInitiallyLoggedIn = remember { authRepo.isUserLoggedIn() }

    LaunchedEffect(targetScreen) {
        if (targetScreen != null) {
            val (screen, userId) = targetScreen
            when (screen) {
                "CHAT" -> {
                    if (!userId.isNullOrBlank()) {
                        navController.navigate("chat/$userId") {
                            launchSingleTop = true
                        }
                    } else {
                        navController.navigate(NavItem.Friends.route) {
                            launchSingleTop = true
                        }
                    }
                }
                "FRIENDS" -> {
                    navController.navigate(NavItem.Friends.route) {
                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            }
            onTargetScreenHandled()
        }
    }

    val navItems = listOf(NavItem.Home, NavItem.Vault, NavItem.Camera, NavItem.Friends, NavItem.Profile)
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val showBottomBar = currentRoute in listOf(
        NavItem.Home.route,
        NavItem.Vault.route,
        NavItem.Friends.route,
        NavItem.Profile.route
    )

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                AnimatedVisibility(
                    visible = showBottomBar,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 18.dp, vertical = 10.dp),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
                            shape = RoundedCornerShape(30.dp),
                            tonalElevation = 0.dp,
                            shadowElevation = 10.dp,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(66.dp)
                                    .padding(horizontal = 10.dp),
                                horizontalArrangement = Arrangement.SpaceAround,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                navItems.forEach { item ->
                                    if (item == NavItem.Camera) {
                                        CameraNavButton {
                                            navController.navigate(NavItem.Camera.route) {
                                                popUpTo(navController.graph.startDestinationId)
                                                launchSingleTop = true
                                            }
                                        }
                                    } else {
                                        MinimalNavItem(
                                            item = item,
                                            selected = currentRoute == item.route,
                                            onClick = {
                                                navController.navigate(item.route) {
                                                    popUpTo(navController.graph.startDestinationId) { saveState = true }
                                                    launchSingleTop = true
                                                    restoreState = true
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
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = if (isInitiallyLoggedIn) NavItem.Home.route else "auth",
                modifier = Modifier.padding(if (showBottomBar) innerPadding else PaddingValues(0.dp))
            ) {
                composable(NavItem.Home.route) {
                    HomeScreen(
                        onCreateStampClick = { navController.navigate(NavItem.Camera.route) },
                        onStampClick = { stamp -> navController.navigate("stamp_detail/${stamp.id}") },
                        onCollectionClick = { colId -> navController.navigate("collection?collectionId=$colId") },
                        onProfileClick = { navController.navigate("passport") }
                    )
                }

            composable(NavItem.Vault.route) {
                StampVaultScreen(
                    onNavigateToCamera = { navController.navigate(NavItem.Camera.route) },
                    onStampClick = { stamp -> navController.navigate("stamp_detail/${stamp.id}") },
                    onCollectionClick = { colId -> navController.navigate("collection?collectionId=$colId") }
                )
            }

            composable(NavItem.Camera.route) {
                CameraScreen(
                    onNavigateToVault = { navController.navigate(NavItem.Vault.route) },
                    onNavigateToNote = { draftId -> navController.navigate("memory_note?draftId=$draftId") },
                    onNavigateToHome = {
                        navController.navigate(NavItem.Home.route) {
                            popUpTo(navController.graph.startDestinationId)
                            launchSingleTop = true
                        }
                    }
                )
            }

            composable(
                route = "memory_note?draftId={draftId}",
                arguments = listOf(
                    navArgument("draftId") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    }
                )
            ) { backStackEntry ->
                val draftId = backStackEntry.arguments?.getString("draftId")
                val draft = StampDraft(
                    id = draftId ?: "",
                    originalImagePath = "",
                    renderedImagePath = ""
                )
                MemoryNoteScreen(
                    draft = draft,
                    draftId = draftId,
                    onNavigateBack = {
                        navController.navigate(NavItem.Home.route) {
                            popUpTo(navController.graph.startDestinationId)
                            launchSingleTop = true
                        }
                    },
                    onSavedSuccess = {
                        navController.navigate(NavItem.Home.route) {
                            popUpTo(navController.graph.startDestinationId)
                            launchSingleTop = true
                        }
                    }
                )
            }

            composable(
                route = "stamp_editor?photoUrl={photoUrl}&stampId={stampId}",
                arguments = listOf(
                    navArgument("photoUrl") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                    navArgument("stampId") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    }
                )
            ) { backStackEntry ->
                val photoUrl = backStackEntry.arguments?.getString("photoUrl")
                val stampId = backStackEntry.arguments?.getString("stampId")
                StampEditorScreen(
                    initialPhotoUrl = photoUrl,
                    stampId = stampId,
                    onNavigateBack = { navController.popBackStack() },
                    onStampSaved = {
                        navController.navigate(NavItem.Vault.route) {
                            popUpTo(NavItem.Camera.route)
                            launchSingleTop = true
                        }
                    }
                )
            }

            composable(
                route = "collection?collectionId={collectionId}",
                arguments = listOf(
                    navArgument("collectionId") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    }
                )
            ) { backStackEntry ->
                CollectionScreen(
                    initialCollectionId = backStackEntry.arguments?.getString("collectionId"),
                    onStampClick = { stampId -> navController.navigate("stamp_detail/$stampId") }
                )
            }

            composable(NavItem.Friends.route) {
                FriendsAndTradeScreen(
                    onOpenStampDetail = { stampId -> navController.navigate("stamp_detail/$stampId") },
                    onOpenChat = { friendUserId -> navController.navigate("chat/$friendUserId") }
                )
            }

            composable(
                route = "chat/{userId}",
                arguments = listOf(navArgument("userId") { type = NavType.StringType })
            ) { backStackEntry ->
                val userId = backStackEntry.arguments?.getString("userId") ?: ""
                ChatScreen(
                    recipientUserId = userId,
                    onNavigateBack = { navController.popBackStack() },
                    onOpenStampDetail = { stampId -> navController.navigate("stamp_detail/$stampId") }
                )
            }

            composable("passport") {
                PassportScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToAuth = { navController.navigate("auth") },
                    onLogout = {
                        navController.navigate("auth") {
                            popUpTo(0) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                )
            }

            composable("auth") {
                val canGoBack = navController.previousBackStackEntry != null
                AuthScreen(
                    canNavigateBack = canGoBack,
                    onNavigateBack = {
                        if (canGoBack) {
                            navController.popBackStack()
                        }
                    },
                    onAuthSuccess = {
                        if (canGoBack) {
                            navController.popBackStack()
                        } else {
                            navController.navigate(NavItem.Home.route) {
                                popUpTo("auth") { inclusive = true }
                                launchSingleTop = true
                            }
                        }
                    }
                )
            }

            composable(
                route = "stamp_detail/{stampId}",
                arguments = listOf(navArgument("stampId") { type = NavType.StringType })
            ) { backStackEntry ->
                val stampId = backStackEntry.arguments?.getString("stampId") ?: ""
                StampDetailScreen(
                    stampId = stampId,
                    onNavigateBack = { navController.popBackStack() },
                    onEditStamp = { sId -> navController.navigate("stamp_editor?stampId=$sId") }
                )
            }
        }
    }

    InAppNotificationBannerHost(
        onNavigateToRoute = { route ->
            if (route.isNotBlank()) {
                if (route == "friends") {
                    navController.navigate(NavItem.Friends.route) {
                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                } else {
                    navController.navigate(route) {
                        launchSingleTop = true
                    }
                }
            }
        }
    )
    }
}

@Composable
private fun MinimalNavItem(
    item: NavItem,
    selected: Boolean,
    onClick: () -> Unit
) {
    val iconSize by animateDpAsState(
        targetValue = if (selected) 23.dp else 21.dp,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 520f),
        label = "navIcon"
    )

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                .padding(horizontal = 10.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = item.title,
                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(iconSize)
            )
        }
        Text(
            text = item.title,
            fontSize = 10.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun CameraNavButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .offset(y = (-7).dp)
            .size(58.dp)
            .shadow(10.dp, CircleShape)
            .clip(CircleShape)
            .background(SurfaceDark)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Outlined.PhotoCamera,
            contentDescription = "Camera",
            tint = Color.White,
            modifier = Modifier.size(25.dp)
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = (-8).dp)
                .size(5.dp)
                .clip(CircleShape)
                .background(AccentRed)
        )
    }
}
