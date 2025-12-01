package wtf.anurag.hojo.ui

import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import wtf.anurag.hojo.ui.apps.filemanager.FileManagerApp
import wtf.anurag.hojo.ui.apps.quicklink.QuickLinkModal
import wtf.anurag.hojo.ui.apps.wallpaper.WallpaperEditor
import wtf.anurag.hojo.ui.components.AppDock
import wtf.anurag.hojo.ui.components.StatusWidget
import wtf.anurag.hojo.ui.theme.HojoTheme
import wtf.anurag.hojo.ui.viewmodels.ConnectivityViewModel
import wtf.anurag.hojo.ui.viewmodels.QuickLinkViewModel

@Composable
fun MainScreen() {
        val navController = rememberNavController()
        val colors = HojoTheme.colors

        NavHost(navController = navController, startDestination = "home") {
                composable(
                        "home",
                        enterTransition = {
                                slideInHorizontally(
                                        animationSpec = tween(300),
                                        initialOffsetX = { -it }
                                )
                        },
                        exitTransition = {
                                slideOutHorizontally(
                                        animationSpec = tween(300),
                                        targetOffsetX = { -it }
                                )
                        },
                        popEnterTransition = {
                                slideInHorizontally(
                                        animationSpec = tween(300),
                                        initialOffsetX = { -it }
                                )
                        },
                        popExitTransition = {
                                slideOutHorizontally(
                                        animationSpec = tween(300),
                                        targetOffsetX = { it }
                                )
                        }
                ) {
                        val connectivityViewModel: ConnectivityViewModel = hiltViewModel()
                        val quickLinkViewModel: QuickLinkViewModel = hiltViewModel()

                        val isConnected by connectivityViewModel.isConnected.collectAsState()
                        val isConnecting by connectivityViewModel.isConnecting.collectAsState()
                        val storageStatus by connectivityViewModel.storageStatus.collectAsState()

                        val quickLinkVisible by quickLinkViewModel.quickLinkVisible.collectAsState()
                        val quickLinkUrl by quickLinkViewModel.quickLinkUrl.collectAsState()
                        val converting by quickLinkViewModel.converting.collectAsState()
                        val quickLinkError by quickLinkViewModel.errorMessage.collectAsState()

                        Column(
                                modifier =
                                        Modifier.fillMaxSize()
                                                .background(colors.windowBg)
                                                .statusBarsPadding()
                                                .padding(16.dp)
                        ) {
                                StatusWidget(
                                        isConnected = isConnected,
                                        isConnecting = isConnecting,
                                        storageStatus = storageStatus,
                                        onConnect = { connectivityViewModel.handleConnect() }
                                )

                                Spacer(modifier = Modifier.height(24.dp))

                                AppDock(
                                        onAction = { actionId ->
                                                when (actionId) {
                                                        "File Manager" ->
                                                                navController.navigate(
                                                                        "file_manager"
                                                                )
                                                        "Quick Link" ->
                                                                quickLinkViewModel
                                                                        .setQuickLinkVisible(true)
                                                        "Wallpaper Editor" ->
                                                                navController.navigate("wallpaper")
                                                        "Converter" ->
                                                                navController.navigate("converter")
                                                }
                                        }
                                )
                        }

                        QuickLinkModal(
                                visible = quickLinkVisible,
                                url = quickLinkUrl,
                                converting = converting,
                                errorMessage = quickLinkError,
                                onClose = { quickLinkViewModel.setQuickLinkVisible(false) },
                                onChangeUrl = { quickLinkViewModel.setQuickLinkUrl(it) },
                                onSubmit = { quickLinkViewModel.handleConvertAndUpload() },
                                onDismissError = { quickLinkViewModel.clearError() }
                        )
                }

                composable(
                        "file_manager",
                        enterTransition = {
                                slideInHorizontally(
                                        animationSpec = tween(300),
                                        initialOffsetX = { it }
                                )
                        },
                        exitTransition = {
                                slideOutHorizontally(
                                        animationSpec = tween(300),
                                        targetOffsetX = { it }
                                )
                        },
                        popEnterTransition = {
                                slideInHorizontally(
                                        animationSpec = tween(300),
                                        initialOffsetX = { -it }
                                )
                        },
                        popExitTransition = {
                                slideOutHorizontally(
                                        animationSpec = tween(300),
                                        targetOffsetX = { it }
                                )
                        }
                ) {
                        val connectivityViewModel: ConnectivityViewModel = hiltViewModel()
                        FileManagerApp(
                                onBack = { navController.popBackStack() },
                                connectivityViewModel = connectivityViewModel
                        )
                }

                composable(
                        "wallpaper",
                        enterTransition = {
                                slideInHorizontally(
                                        animationSpec = tween(300),
                                        initialOffsetX = { it }
                                )
                        },
                        exitTransition = {
                                slideOutHorizontally(
                                        animationSpec = tween(300),
                                        targetOffsetX = { it }
                                )
                        },
                        popEnterTransition = {
                                slideInHorizontally(
                                        animationSpec = tween(300),
                                        initialOffsetX = { -it }
                                )
                        },
                        popExitTransition = {
                                slideOutHorizontally(
                                        animationSpec = tween(300),
                                        targetOffsetX = { it }
                                )
                        }
                ) {
                        val connectivityViewModel: ConnectivityViewModel = hiltViewModel()
                        val deviceBaseUrl by connectivityViewModel.deviceBaseUrl.collectAsState()
                        WallpaperEditor(
                                onBack = { navController.popBackStack() },
                                baseUrl = deviceBaseUrl
                        )
                }

                composable(
                        "converter",
                        enterTransition = {
                                slideInHorizontally(
                                        animationSpec = tween(300),
                                        initialOffsetX = { it }
                                )
                        },
                        exitTransition = {
                                slideOutHorizontally(
                                        animationSpec = tween(300),
                                        targetOffsetX = { it }
                                )
                        },
                        popEnterTransition = {
                                slideInHorizontally(
                                        animationSpec = tween(300),
                                        initialOffsetX = { -it }
                                )
                        },
                        popExitTransition = {
                                slideOutHorizontally(
                                        animationSpec = tween(300),
                                        targetOffsetX = { it }
                                )
                        }
                ) {
                        val connectivityViewModel: ConnectivityViewModel = hiltViewModel()
                        wtf.anurag.hojo.ui.apps.converter.ConverterApp(
                                onBack = { navController.popBackStack() },
                                connectivityViewModel = connectivityViewModel
                        )
                }
        }
}
