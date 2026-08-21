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
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.mipastudio.memostamp.core.theme.*
import com.mipastudio.memostamp.domain.model.StampDraft
import com.mipastudio.memostamp.feature.camera.CameraScreen
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
    object Home : NavItem("home", "Home", Icons.Outlined.Home)
    object Vault : NavItem("vault", "Vault", Icons.Outlined.CollectionsBookmark)
    object Camera : NavItem("camera", "Camera", Icons.Outlined.PhotoCamera)
    object Friends : NavItem("friends", "Friends", Icons.Outlined.People)
}

@Composable
fun MemoStampNavGraph(
    navController: NavHostController = rememberNavController()
) {
    val navItems = listOf(NavItem.Home, NavItem.Vault, NavItem.Camera, NavItem.Friends)
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val showBottomBar = currentRoute in listOf(
        NavItem.Home.route,
        NavItem.Vault.route,
        NavItem.Friends.route
    )

    Scaffold(
        containerColor = WarmPaperBg,
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
                        color = SurfaceWhite.copy(alpha = 0.98f),
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
            startDestination = NavItem.Camera.route,
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
                    onNavigateToHome = { navController.popBackStack() }
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
                    onNavigateBack = { navController.popBackStack() },
                    onSavedSuccess = {
                        navController.navigate(NavItem.Vault.route) {
                            popUpTo(NavItem.Camera.route)
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
                    onOpenStampDetail = { stampId -> navController.navigate("stamp_detail/$stampId") }
                )
            }

            composable("passport") {
                PassportScreen(onNavigateBack = { navController.popBackStack() })
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
}

@Composable
private fun MinimalNavItem(
    item: NavItem,
    selected: Boolean,
    onClick: () -> Unit
) {
    val iconSize by animateDpAsState(
        targetValue = if (selected) 24.dp else 22.dp,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 520f),
        label = "navIcon"
    )

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = item.icon,
            contentDescription = item.title,
            tint = if (selected) PrimaryText else TertiaryText,
            modifier = Modifier.size(iconSize)
        )
        Spacer(modifier = Modifier.height(3.dp))
        Box(
            modifier = Modifier
                .size(width = if (selected) 14.dp else 4.dp, height = 3.dp)
                .clip(CircleShape)
                .background(if (selected) AccentRed else Color.Transparent)
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
