package com.watch.watchtofriend

import android.Manifest
import android.app.PictureInPictureParams
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.watch.watchtofriend.ui.components.LocalPip
import com.watch.watchtofriend.ui.components.PipController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.watch.watchtofriend.navigation.Screen
import com.watch.watchtofriend.ui.auth.AuthViewModel
import com.watch.watchtofriend.ui.auth.LoginScreen
import com.watch.watchtofriend.ui.home.HomeScreen
import com.watch.watchtofriend.ui.room.CreateRoomScreen
import com.watch.watchtofriend.ui.room.JoinRoomScreen
import com.watch.watchtofriend.ui.dm.DmScreen
import com.watch.watchtofriend.ui.SplashScreen
import com.watch.watchtofriend.ui.theme.WatchToFriendTheme
import com.watch.watchtofriend.ui.watch.WatchRoomScreen
import com.watch.watchtofriend.ui.admin.AdminScreen
import com.watch.watchtofriend.invite.InviteLinkRouter
import com.watch.watchtofriend.notifications.NotificationHelper
import com.watch.watchtofriend.notifications.NotificationRouter
import com.watch.watchtofriend.ui.locale.LocaleHelper
import com.google.firebase.auth.FirebaseAuth
import androidx.compose.runtime.DisposableEffect

class MainActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }
    // PiP: izleme ekranı uygun olduğunda true; ev tuşuna basınca PiP'e geçeriz.
    private var pipEligible = false
    private val inPipState = mutableStateOf(false)
    private val notifTrigger = mutableStateOf(0)

    private fun maybeEnterPip() {
        if (!pipEligible) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) return
        try {
            enterPictureInPictureMode(
                PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(16, 9))
                    .build()
            )
        } catch (_: Exception) {}
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        maybeEnterPip()
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        inPipState.value = isInPictureInPictureMode
    }

    private val notifPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* sonuç sessizce işlenir */ }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        NotificationRouter.consumeFromIntent(intent)
        InviteLinkRouter.consumeFromIntent(intent)
        notifTrigger.value++
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NotificationRouter.consumeFromIntent(intent)
        InviteLinkRouter.consumeFromIntent(intent)
        // Android 13+ push bildirim izni iste
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        enableEdgeToEdge()
        setContent {
            val configuration = androidx.compose.ui.platform.LocalConfiguration.current
            val themeMode = com.watch.watchtofriend.ui.theme.ThemePref.mode.intValue
            val systemDark = (configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
            val darkTheme = when (themeMode) {
                1 -> false
                2 -> true
                else -> systemDark
            }
            WatchToFriendTheme(darkTheme = darkTheme) {
                val navController = rememberNavController()
                // viewModel() Activity'ye bağlı — döndürmede/config change'de sıfırlanmaz.
                // remember { AuthViewModel() } her recomposition'da yeni örnek üretebilir.
                val authViewModel: AuthViewModel = viewModel()
                val notifBump by notifTrigger
                var homeInitialTab by remember { mutableIntStateOf(NotificationRouter.consumeHomeTab() ?: 0) }
                val isOnline by com.watch.watchtofriend.ui.components.rememberIsOnline()

                DisposableEffect(navController) {
                    val listener = FirebaseAuth.AuthStateListener { auth ->
                        if (auth.currentUser != null) return@AuthStateListener
                        val route = navController.currentDestination?.route ?: return@AuthStateListener
                        if (route == Screen.Login.route || route == Screen.Splash.route) return@AuthStateListener
                        navController.navigate(Screen.Login.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                    FirebaseAuth.getInstance().addAuthStateListener(listener)
                    onDispose { FirebaseAuth.getInstance().removeAuthStateListener(listener) }
                }

                LaunchedEffect(notifBump, authViewModel.isLoggedIn) {
                    if (!authViewModel.isLoggedIn) return@LaunchedEffect
                    // Soğuk başlangıç davetini Splash işler; burada yalnızca uygulama açıkken gelen linkler
                    if (notifBump > 0) {
                        InviteLinkRouter.takePendingCode()?.let { code ->
                            navController.navigate(Screen.JoinRoom.withOptionalCode(code)) {
                                launchSingleTop = true
                            }
                            return@LaunchedEffect
                        }
                    }
                    val nav = NotificationRouter.consume() ?: return@LaunchedEffect
                    when (nav.type) {
                        NotificationHelper.TYPE_FRIEND -> {
                            homeInitialTab = nav.homeTab ?: 1
                            navController.navigate(Screen.Home.route) {
                                launchSingleTop = true
                            }
                        }
                        NotificationHelper.TYPE_ROOM_INVITE,
                        NotificationHelper.TYPE_ROOM_MESSAGE,
                        NotificationHelper.TYPE_GENERAL -> {
                            val roomId = nav.roomId?.takeIf { it.isNotBlank() }
                            if (roomId != null) {
                                navController.navigate(Screen.WatchRoom.withArgs(roomId)) {
                                    launchSingleTop = true
                                }
                            } else if (nav.type == NotificationHelper.TYPE_ROOM_INVITE) {
                                homeInitialTab = nav.homeTab ?: 1
                                navController.navigate(Screen.Home.route) { launchSingleTop = true }
                            }
                        }
                        NotificationHelper.TYPE_DM -> {
                            val dmId = nav.dmId?.takeIf { it.isNotBlank() } ?: return@LaunchedEffect
                            val name = nav.dmName?.takeIf { it.isNotBlank() } ?: "Mesaj"
                            navController.navigate(Screen.DmScreen.withArgs(dmId, name)) {
                                launchSingleTop = true
                            }
                        }
                    }
                }
                val pip = PipController(
                    isInPip = inPipState.value,
                    setEligible = { pipEligible = it },
                    enterPip = { maybeEnterPip() }
                )

                CompositionLocalProvider(LocalPip provides pip) {
                androidx.compose.foundation.layout.Column(
                    modifier = androidx.compose.ui.Modifier.fillMaxSize()
                ) {
                    if (!isOnline) com.watch.watchtofriend.ui.components.OfflineBanner()
                    androidx.compose.foundation.layout.Box(
                        modifier = androidx.compose.ui.Modifier
                            .weight(1f)
                            .windowInsetsPadding(WindowInsets.safeDrawing)
                    ) {
                NavHost(
                    navController = navController,
                    startDestination = Screen.Splash.route,
                    enterTransition = {
                        slideIntoContainer(
                            AnimatedContentTransitionScope.SlideDirection.Left,
                            animationSpec = tween(320)
                        ) + fadeIn(tween(320))
                    },
                    exitTransition = {
                        slideOutOfContainer(
                            AnimatedContentTransitionScope.SlideDirection.Left,
                            animationSpec = tween(320)
                        ) + fadeOut(tween(320))
                    },
                    popEnterTransition = {
                        slideIntoContainer(
                            AnimatedContentTransitionScope.SlideDirection.Right,
                            animationSpec = tween(320)
                        ) + fadeIn(tween(320))
                    },
                    popExitTransition = {
                        slideOutOfContainer(
                            AnimatedContentTransitionScope.SlideDirection.Right,
                            animationSpec = tween(320)
                        ) + fadeOut(tween(320))
                    }
                ) {
                    composable(Screen.Splash.route) {
                        SplashScreen(
                            isLoggedIn = authViewModel.isLoggedIn,
                            onSessionInvalid = { authViewModel.logout() },
                            onFinished = {
                                val dest = if (authViewModel.isLoggedIn) {
                                    val code = InviteLinkRouter.takePendingCode()
                                    if (code != null) Screen.JoinRoom.withOptionalCode(code)
                                    else Screen.Home.route
                                } else {
                                    Screen.Login.route
                                }
                                navController.navigate(dest) {
                                    popUpTo(Screen.Splash.route) { inclusive = true }
                                }
                            }
                        )
                    }
                    composable(Screen.Login.route) {
                        LoginScreen(
                            onLoginSuccess = {
                                val inviteCode = InviteLinkRouter.takePendingCode()
                                val dest = if (inviteCode != null) {
                                    Screen.JoinRoom.withOptionalCode(inviteCode)
                                } else {
                                    Screen.Home.route
                                }
                                navController.navigate(dest) {
                                    popUpTo(Screen.Login.route) { inclusive = true }
                                }
                            },
                            vm = authViewModel
                        )
                    }
                    composable(Screen.Home.route) {
                        HomeScreen(
                            onCreateRoom = { navController.navigate(Screen.CreateRoom.route) },
                            onJoinRoom = { navController.navigate(Screen.JoinRoom.route) },
                            onRoomClick = { roomId -> navController.navigate(Screen.WatchRoom.withArgs(roomId)) },
                            onDmClick = { dmId, otherName ->
                                navController.navigate(Screen.DmScreen.withArgs(dmId, otherName))
                            },
                            onLogout = {
                                authViewModel.logout()
                                navController.navigate(Screen.Login.route) {
                                    popUpTo(Screen.Home.route) { inclusive = true }
                                }
                            },
                            onAdminPanel = { navController.navigate(Screen.Admin.route) },
                            initialTab = homeInitialTab
                        )
                    }
                    composable(Screen.Admin.route) {
                        AdminScreen(onBack = { navController.popBackStack() })
                    }
                    composable(Screen.CreateRoom.route) {
                        CreateRoomScreen(
                            onBack = { navController.popBackStack() },
                            onRoomCreated = { roomId ->
                                navController.navigate(Screen.WatchRoom.withArgs(roomId)) {
                                    popUpTo(Screen.Home.route)
                                }
                            }
                        )
                    }
                    composable(
                        route = Screen.JoinRoom.route,
                        arguments = listOf(
                            navArgument("code") {
                                type = NavType.StringType
                                nullable = true
                                defaultValue = null
                            }
                        )
                    ) { backStackEntry ->
                        val inviteCode = backStackEntry.arguments?.getString("code")
                        JoinRoomScreen(
                            initialCode = inviteCode,
                            onBack = { navController.popBackStack() },
                            onJoined = { roomId ->
                                navController.navigate(Screen.WatchRoom.withArgs(roomId)) {
                                    popUpTo(Screen.Home.route)
                                }
                            }
                        )
                    }
                    composable(Screen.WatchRoom.route) { backStackEntry ->
                        val roomId = backStackEntry.arguments?.getString("roomId") ?: ""
                        WatchRoomScreen(
                            roomId = roomId,
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable(Screen.DmScreen.route) { backStackEntry ->
                        val dmId = backStackEntry.arguments?.getString("dmId") ?: ""
                        val otherName = backStackEntry.arguments?.getString("otherName") ?: ""
                        DmScreen(
                            dmId = dmId,
                            otherName = otherName,
                            onBack = { navController.popBackStack() }
                        )
                    }
                }
                    }
                }
                }
            }
        }
    }
}
